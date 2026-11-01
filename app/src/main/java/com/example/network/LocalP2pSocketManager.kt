package com.example.network

import android.util.Log
import com.example.data.CryptoSyncEngine
import com.example.data.SyncTransportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "LocalP2pSocket"
const val DEFAULT_P2P_TCP_PORT = 48899
const val DEFAULT_P2P_UDP_PORT = 48898

/**
 * Structured message envelope passed between peers over [ServerSocket] and [Socket].
 */
data class P2pNetworkMessage(
    val id: Long = System.currentTimeMillis(),
    val packetType: String,
    val senderTag: String,
    val content: String,
    val isOutgoing: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class CoopPeerState(
    val peerName: String = "Co-op Survivor",
    val x: Float = 420f,
    val y: Float = 420f,
    val aimAngleRad: Float = 0f,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val weaponId: String = "pistol",
    val isFiring: Boolean = false,
    val score: Int = 500,
    val kills: Int = 0,
    val lastUpdatedMs: Long = 0L
)

data class P2pSessionStatus(
    val isHosting: Boolean = false,
    val isConnected: Boolean = false,
    val transportMode: SyncTransportMode = SyncTransportMode.LAN_WIFI,
    val localIpAddress: String = "127.0.0.1",
    val connectedPeerAddress: String = "",
    val discoveredPeers: List<String> = emptyList(),
    val messageHistory: List<P2pNetworkMessage> = emptyList(),
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val statusLog: String = "Offline Ready — Host or Join LAN / USB ADB / P2P Socket",
    val lastSyncedSlotSummary: String = ""
)

/**
 * Peer-to-Peer Connectivity Module using [java.net.ServerSocket], [java.net.Socket],
 * and [java.net.DatagramSocket] for:
 * 1. Local network multiplayer discovery (UDP LAN broadcast beacons + passive scanner).
 * 2. Low-latency bidirectional TCP message passing (co-op player telemetry, tactical radio pings,
 *    wave events, and AES-256-GCM encrypted save capsules).
 */
class LocalP2pSocketManager(
    private val scope: CoroutineScope,
    private val onRemoteSaveReceived: (CryptoSyncEngine.GameSaveSnapshot) -> Unit,
    private val onTacticalMessageReceived: (P2pNetworkMessage) -> Unit = {}
) {
    private val _sessionStatus = MutableStateFlow(P2pSessionStatus(localIpAddress = resolveLocalIpv4()))
    val sessionStatus: StateFlow<P2pSessionStatus> = _sessionStatus.asStateFlow()

    private val _coopPeerState = MutableStateFlow<CoopPeerState?>(null)
    val coopPeerState: StateFlow<CoopPeerState?> = _coopPeerState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null

    init {
        startPassiveLanDiscoveryScanner()
    }

    fun refreshLocalIp() {
        _sessionStatus.value = _sessionStatus.value.copy(localIpAddress = resolveLocalIpv4())
    }

    fun setTransportMode(mode: SyncTransportMode) {
        val ip = if (mode == SyncTransportMode.USB_ADB) "127.0.0.1" else resolveLocalIpv4()
        _sessionStatus.value = _sessionStatus.value.copy(
            transportMode = mode,
            localIpAddress = ip,
            statusLog = when (mode) {
                SyncTransportMode.LAN_WIFI -> "LAN Mode active ($ip:$DEFAULT_P2P_TCP_PORT)"
                SyncTransportMode.USB_ADB -> "USB Tunnel ready (adb forward tcp:$DEFAULT_P2P_TCP_PORT tcp:$DEFAULT_P2P_TCP_PORT)"
                SyncTransportMode.BLUETOOTH_P2P -> "P2P Encrypted Capsule & Direct Socket mode ready"
            }
        )
    }

    /**
     * Listens for UDP LAN discovery beacons on [DEFAULT_P2P_UDP_PORT] so joining devices
     * automatically see active hosts on the same Wi-Fi or Hotspot without typing an IP.
     */
    fun startPassiveLanDiscoveryScanner() {
        if (discoveryJob?.isActive == true) return
        val localIp = resolveLocalIpv4()
        discoveryJob = scope.launch(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DEFAULT_P2P_UDP_PORT))
                    soTimeout = 1500
                }
                val buf = ByteArray(256)
                while (isActive) {
                    if (_sessionStatus.value.isHosting) {
                        try {
                            val currentLocalIp = _sessionStatus.value.localIpAddress
                            val beaconBytes = "BOXBOY_HOST:$currentLocalIp:$DEFAULT_P2P_TCP_PORT".toByteArray(Charsets.UTF_8)
                            val bcastPacket = DatagramPacket(
                                beaconBytes,
                                beaconBytes.size,
                                InetAddress.getByName("255.255.255.255"),
                                DEFAULT_P2P_UDP_PORT
                            )
                            udpSocket.send(bcastPacket)
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        val recv = DatagramPacket(buf, buf.size)
                        udpSocket.receive(recv)
                        val msg = String(recv.data, 0, recv.length, Charsets.UTF_8)
                        if (msg.startsWith("BOXBOY_HOST:")) {
                            val hostIp = msg.removePrefix("BOXBOY_HOST:").substringBefore(":")
                            if (hostIp.isNotBlank() && hostIp != localIp) {
                                val updated = (_sessionStatus.value.discoveredPeers + hostIp).distinct()
                                _sessionStatus.value = _sessionStatus.value.copy(discoveredPeers = updated)
                            }
                        }
                    } catch (_: Exception) {
                    }
                    delay(1100L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP discovery socket closed: ${e.message}")
            } finally {
                udpSocket?.close()
            }
        }
    }

    /**
     * Opens a [java.net.ServerSocket] on [DEFAULT_P2P_TCP_PORT] and broadcasts UDP LAN discovery beacons.
     */
    fun startHosting(passphrase: String) {
        stopActiveTcpSessionOnly()
        val localIp = resolveLocalIpv4()
        _sessionStatus.value = _sessionStatus.value.copy(
            isHosting = true,
            isConnected = false,
            localIpAddress = localIp,
            statusLog = "ServerSocket listening on $localIp:$DEFAULT_P2P_TCP_PORT (UDP beacon active)..."
        )
        startPassiveLanDiscoveryScanner()

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket()
                srv.reuseAddress = true
                srv.bind(InetSocketAddress(DEFAULT_P2P_TCP_PORT))
                serverSocket = srv
                while (isActive) {
                    val client = srv.accept()
                    client.tcpNoDelay = true
                    handleConnectedSocket(client, passphrase, isHostSide = true)
                }
            } catch (e: Exception) {
                if (_sessionStatus.value.isHosting) {
                    _sessionStatus.value = _sessionStatus.value.copy(
                        isHosting = false,
                        isConnected = false,
                        statusLog = "Host ServerSocket stopped: ${e.localizedMessage ?: "closed"}"
                    )
                }
            }
        }
    }

    /**
     * Connects a client [java.net.Socket] to a host [ServerSocket] over LAN Wi-Fi, Hotspot, or USB ADB tunnel.
     */
    fun connectToPeer(hostAddress: String, passphrase: String) {
        stopActiveTcpSessionOnly()
        val cleanHost = hostAddress.trim().substringBefore(":").ifBlank { "127.0.0.1" }
        _sessionStatus.value = _sessionStatus.value.copy(
            isHosting = false,
            isConnected = false,
            statusLog = "Connecting Socket to $cleanHost:$DEFAULT_P2P_TCP_PORT..."
        )
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(cleanHost, DEFAULT_P2P_TCP_PORT), 4000)
                handleConnectedSocket(sock, passphrase, isHostSide = false)
            } catch (e: Exception) {
                Log.w(TAG, "Peer connection failed", e)
                _sessionStatus.value = _sessionStatus.value.copy(
                    isConnected = false,
                    statusLog = "Connection to $cleanHost:$DEFAULT_P2P_TCP_PORT failed (${e.localizedMessage ?: "timeout"})."
                )
            }
        }
    }

    private suspend fun handleConnectedSocket(
        socket: Socket,
        passphrase: String,
        isHostSide: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            activeSocket?.close()
            activeSocket = socket
            val writer = PrintWriter(socket.getOutputStream(), true)
            socketWriter = writer
            val peerAddr = socket.inetAddress?.hostAddress ?: "peer"
            val roleLabel = if (isHostSide) "Host ServerSocket" else "Client Socket"
            appendMessageToHistory(
                P2pNetworkMessage(
                    packetType = "HANDSHAKE",
                    senderTag = "SYS",
                    content = "$roleLabel linked with $peerAddr:$DEFAULT_P2P_TCP_PORT",
                    isOutgoing = true
                )
            )
            _sessionStatus.value = _sessionStatus.value.copy(
                isConnected = true,
                connectedPeerAddress = peerAddr,
                statusLog = "Connected to P2P Peer ($peerAddr) — Bidirectional Socket Active!"
            )

            // Send initial HELLO handshake packet over the Socket
            val helloJson = JSONObject().apply {
                put("type", "HELLO_HANDSHAKE")
                put("sender", if (isHostSide) "Host-SquadLeader" else "Client-Reinforcement")
                put("text", "P2P Co-op Link Established on TCP :$DEFAULT_P2P_TCP_PORT")
            }
            writer.println(helloJson.toString())

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String? = reader.readLine()
            while (line != null && isActive) {
                processIncomingPacket(line, passphrase)
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Socket stream ended: ${e.message}")
        } finally {
            _coopPeerState.value = null
            _sessionStatus.value = _sessionStatus.value.copy(
                isConnected = false,
                connectedPeerAddress = "",
                statusLog = "Peer disconnected."
            )
        }
    }

    /**
     * Sends a real-time tactical message or callout over the active TCP [Socket] / [ServerSocket] connection.
     */
    fun sendTacticalMessage(senderName: String, messageText: String): Boolean {
        val cleanText = messageText.trim()
        if (cleanText.isEmpty()) return false
        val writer = socketWriter
        val msg = P2pNetworkMessage(
            packetType = "TACTICAL_MSG",
            senderTag = senderName,
            content = cleanText,
            isOutgoing = true
        )
        appendMessageToHistory(msg)

        if (writer != null && _sessionStatus.value.isConnected) {
            scope.launch(Dispatchers.IO) {
                try {
                    val obj = JSONObject().apply {
                        put("type", "TACTICAL_MSG")
                        put("sender", senderName)
                        put("text", cleanText)
                        put("ts", msg.timestampMs)
                    }
                    writer.println(obj.toString())
                    _sessionStatus.value = _sessionStatus.value.copy(
                        packetsSent = _sessionStatus.value.packetsSent + 1,
                        statusLog = "Sent P2P Message to ${_sessionStatus.value.connectedPeerAddress}: \"$cleanText\""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send P2P message", e)
                }
            }
            return true
        }
        return false
    }

    fun sendPlayerState(
        peerName: String,
        x: Float,
        y: Float,
        aimAngleRad: Float,
        hp: Int,
        maxHp: Int,
        weaponId: String,
        isFiring: Boolean,
        score: Int,
        kills: Int
    ) {
        val writer = socketWriter ?: return
        if (!_sessionStatus.value.isConnected) return
        scope.launch(Dispatchers.IO) {
            try {
                val obj = JSONObject().apply {
                    put("type", "COOP_STATE")
                    put("name", peerName)
                    put("x", x.toDouble())
                    put("y", y.toDouble())
                    put("aim", aimAngleRad.toDouble())
                    put("hp", hp)
                    put("maxHp", maxHp)
                    put("wpn", weaponId)
                    put("fire", isFiring)
                    put("score", score)
                    put("kills", kills)
                }
                writer.println(obj.toString())
                _sessionStatus.value = _sessionStatus.value.copy(
                    packetsSent = _sessionStatus.value.packetsSent + 1
                )
            } catch (_: Exception) {
            }
        }
    }

    fun pushEncryptedSaveOverSocket(snapshot: CryptoSyncEngine.GameSaveSnapshot, passphrase: String): Boolean {
        val writer = socketWriter
        val encrypted = CryptoSyncEngine.encryptSnapshot(snapshot, passphrase)
        if (writer != null && _sessionStatus.value.isConnected) {
            scope.launch(Dispatchers.IO) {
                try {
                    val obj = JSONObject().apply {
                        put("type", "SYNC_SAVE")
                        put("capsule", encrypted)
                    }
                    writer.println(obj.toString())
                    appendMessageToHistory(
                        P2pNetworkMessage(
                            packetType = "SYNC_SAVE",
                            senderTag = "LOCAL",
                            content = "Pushed AES-256-GCM Save '${snapshot.slotName}' (Wave ${snapshot.highestWave})",
                            isOutgoing = true
                        )
                    )
                    _sessionStatus.value = _sessionStatus.value.copy(
                        packetsSent = _sessionStatus.value.packetsSent + 1,
                        lastSyncedSlotSummary = "Pushed '${snapshot.slotName}' (Wave ${snapshot.highestWave}) via encrypted P2P socket",
                        statusLog = "Encrypted AES-256-GCM save pushed to ${_sessionStatus.value.connectedPeerAddress}"
                    )
                } catch (e: Exception) {
                    _sessionStatus.value = _sessionStatus.value.copy(
                        statusLog = "Failed to push save over socket: ${e.localizedMessage}"
                    )
                }
            }
            return true
        }
        return false
    }

    /**
     * Self-contained local loopback test that opens a real [ServerSocket] and [Socket]
     * on `127.0.0.1` (ephemeral port) to verify P2P discovery & message passing on a single device.
     */
    fun runLoopbackSocketVerification(testMessage: String = "PING: BUNKER READY") {
        scope.launch(Dispatchers.IO) {
            var testServer: ServerSocket? = null
            var clientSock: Socket? = null
            var acceptedSock: Socket? = null
            try {
                testServer = ServerSocket(0)
                val port = testServer.localPort
                val serverDeferred = launch(Dispatchers.IO) {
                    acceptedSock = testServer.accept()
                    val reader = BufferedReader(InputStreamReader(acceptedSock!!.getInputStream()))
                    val writer = PrintWriter(acceptedSock!!.getOutputStream(), true)
                    val incoming = reader.readLine()
                    writer.println(
                        JSONObject().apply {
                            put("type", "TACTICAL_MSG")
                            put("sender", "Loopback-ServerSocket:$port")
                            put("text", "ACK ($incoming)")
                        }.toString()
                    )
                }

                clientSock = Socket()
                clientSock.connect(InetSocketAddress("127.0.0.1", port), 2000)
                val clientWriter = PrintWriter(clientSock.getOutputStream(), true)
                val clientReader = BufferedReader(InputStreamReader(clientSock.getInputStream()))
                clientWriter.println(testMessage)
                val reply = clientReader.readLine()
                serverDeferred.join()

                if (reply != null) {
                    processIncomingPacket(reply, "LOOPBACK")
                    _sessionStatus.value = _sessionStatus.value.copy(
                        statusLog = "ServerSocket/Socket Loopback Verified on 127.0.0.1:$port!"
                    )
                }
            } catch (e: Exception) {
                _sessionStatus.value = _sessionStatus.value.copy(
                    statusLog = "Loopback test error: ${e.localizedMessage}"
                )
            } finally {
                try { clientSock?.close() } catch (_: Exception) {}
                try { acceptedSock?.close() } catch (_: Exception) {}
                try { testServer?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun processIncomingPacket(rawLine: String, passphrase: String) {
        try {
            val obj = JSONObject(rawLine)
            _sessionStatus.value = _sessionStatus.value.copy(
                packetsReceived = _sessionStatus.value.packetsReceived + 1
            )
            when (obj.optString("type")) {
                "HELLO_HANDSHAKE" -> {
                    val sender = obj.optString("sender", "Peer")
                    val text = obj.optString("text", "Connected")
                    val msg = P2pNetworkMessage(
                        packetType = "HANDSHAKE",
                        senderTag = sender,
                        content = text,
                        isOutgoing = false
                    )
                    appendMessageToHistory(msg)
                    onTacticalMessageReceived(msg)
                }
                "TACTICAL_MSG" -> {
                    val sender = obj.optString("sender", "Co-op Peer")
                    val text = obj.optString("text", "")
                    if (text.isNotBlank()) {
                        val msg = P2pNetworkMessage(
                            packetType = "TACTICAL_MSG",
                            senderTag = sender,
                            content = text,
                            isOutgoing = false
                        )
                        appendMessageToHistory(msg)
                        _sessionStatus.value = _sessionStatus.value.copy(
                            statusLog = "[$sender]: $text"
                        )
                        onTacticalMessageReceived(msg)
                    }
                }
                "COOP_STATE" -> {
                    _coopPeerState.value = CoopPeerState(
                        peerName = obj.optString("name", "Player 2"),
                        x = obj.optDouble("x", 420.0).toFloat(),
                        y = obj.optDouble("y", 420.0).toFloat(),
                        aimAngleRad = obj.optDouble("aim", 0.0).toFloat(),
                        hp = obj.optInt("hp", 100),
                        maxHp = obj.optInt("maxHp", 100),
                        weaponId = obj.optString("wpn", "pistol"),
                        isFiring = obj.optBoolean("fire", false),
                        score = obj.optInt("score", 500),
                        kills = obj.optInt("kills", 0),
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                }
                "SYNC_SAVE" -> {
                    val capsule = obj.optString("capsule", "")
                    if (capsule.isNotBlank()) {
                        CryptoSyncEngine.decryptSnapshot(capsule, passphrase).onSuccess { snap ->
                            appendMessageToHistory(
                                P2pNetworkMessage(
                                    packetType = "SYNC_SAVE",
                                    senderTag = "PEER",
                                    content = "Received & Decrypted Save '${snap.slotName}' (Wave ${snap.highestWave})",
                                    isOutgoing = false
                                )
                            )
                            _sessionStatus.value = _sessionStatus.value.copy(
                                lastSyncedSlotSummary = "Received '${snap.slotName}' (Wave ${snap.highestWave}) from peer",
                                statusLog = "Decrypted AES-256-GCM save '${snap.slotName}' from peer!"
                            )
                            onRemoteSaveReceived(snap)
                        }.onFailure { err ->
                            _sessionStatus.value = _sessionStatus.value.copy(
                                statusLog = "Save sync rejected: Passphrase mismatch (${err.localizedMessage})"
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun appendMessageToHistory(msg: P2pNetworkMessage) {
        val updated = (_sessionStatus.value.messageHistory + msg).takeLast(20)
        _sessionStatus.value = _sessionStatus.value.copy(messageHistory = updated)
    }

    private fun stopActiveTcpSessionOnly() {
        serverJob?.cancel()
        try {
            socketWriter?.close()
        } catch (_: Exception) {
        }
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        socketWriter = null
        activeSocket = null
        serverSocket = null
        _coopPeerState.value = null
    }

    fun stopSession() {
        discoveryJob?.cancel()
        stopActiveTcpSessionOnly()
        _sessionStatus.value = _sessionStatus.value.copy(
            isHosting = false,
            isConnected = false,
            connectedPeerAddress = ""
        )
    }

    companion object {
        fun resolveLocalIpv4(): String {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
                "127.0.0.1"
            } catch (_: Exception) {
                "127.0.0.1"
            }
        }
    }
}
