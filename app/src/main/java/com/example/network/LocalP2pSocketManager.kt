package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
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
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LocalP2pSocket"
const val DEFAULT_P2P_TCP_PORT = 48899
const val DEFAULT_P2P_UDP_PORT = 48898
const val MAX_REMOTE_PEERS = 3

enum class LocalP2pRole {
    IDLE,
    HOST,
    CLIENT
}

data class PeerConnection(
    val peerId: String,
    val peerAddress: String,
    val socket: Socket,
    val writer: PrintWriter,
    val connectedAtMs: Long = System.currentTimeMillis()
)

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
    val peerId: String = "peer_default",
    val peerName: String = "Co-op Survivor",
    val playerName: String = peerName,
    val x: Float = 420f,
    val y: Float = 420f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val aimAngleRad: Float = 0f,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val shield: Int = 60,
    val weaponId: String = "pistol",
    val weaponLevel: Int = 1,
    val isFiring: Boolean = false,
    val isDowned: Boolean = false,
    val bleedoutRemainingMs: Long = 30_000L,
    val reviveProgress: Float = 0f,
    val score: Int = 500,
    val kills: Int = 0,
    val wave: Int = 1,
    val isAiBot: Boolean = false,
    val botPersonaId: String = "",
    val visorColorHex: Long = 0xFF00E5FF,
    val radioCalloutText: String = "",
    val radioCalloutTtlMs: Long = 0L,
    val lastUpdatedMs: Long = 0L,
    val updatedAtMs: Long = lastUpdatedMs
) {
    val callsign: String
        get() = playerName.ifBlank { peerName }
}

data class P2pSessionStatus(
    val isHosting: Boolean = false,
    val isConnected: Boolean = false,
    val transportMode: SyncTransportMode = SyncTransportMode.LFS_HOSTED_SERVER,
    val localIpAddress: String = "127.0.0.1",
    val connectedPeerAddress: String = "",
    val connectedPeerCount: Int = 0,
    val discoveredPeers: List<String> = emptyList(),
    val messageHistory: List<P2pNetworkMessage> = emptyList(),
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val statusLog: String = "Oasis Ready -- Host or Join LFS Server (LFS-XXXXXX), Cloud Relay, or LAN :48899",
    val lastSyncedSlotSummary: String = ""
)

/**
 * 4-Player Star-Mesh Peer-to-Peer Connectivity Module (`FP-N05`) using [java.net.ServerSocket],
 * [java.net.Socket], and [java.net.DatagramSocket] for:
 * 1. Multi-subnet UDP LAN broadcast beacons (`InterfaceAddress.broadcast` + `255.255.255.255`)
 *    and `WifiManager.MulticastLock` acquisition.
 * 2. Up to [MAX_REMOTE_PEERS] (`3` remote peers = `4` players total) concurrent TCP connections
 *    with automatic host-side packet relay (`TACTICAL_CALLOUT`, `SQUAD_REVIVE_PULSE`, `WAVE_SYNC`, `COOP_STATE`).
 */
class LocalP2pSocketManager(
    private val scope: CoroutineScope,
    private val onRemoteSaveReceived: (CryptoSyncEngine.GameSaveSnapshot) -> Unit,
    private val onTacticalMessageReceived: (P2pNetworkMessage) -> Unit = {},
    private val appContext: Context? = null
) {
    private val _sessionStatus = MutableStateFlow(P2pSessionStatus(localIpAddress = resolveLocalLanIp()))
    val sessionStatus: StateFlow<P2pSessionStatus> = _sessionStatus.asStateFlow()

    private val _coopPeerState = MutableStateFlow<CoopPeerState?>(null)
    val coopPeerState: StateFlow<CoopPeerState?> = _coopPeerState.asStateFlow()

    private val _remotePeerStates = MutableStateFlow<Map<String, CoopPeerState>>(emptyMap())
    val remotePeerStates: StateFlow<Map<String, CoopPeerState>> = _remotePeerStates.asStateFlow()

    val peerSockets = ConcurrentHashMap<String, PeerConnection>()

    @Volatile
    var role: LocalP2pRole = LocalP2pRole.IDLE
        private set

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        startPassiveLanDiscoveryScanner()
    }

    fun acquireMulticastLock(context: Context? = appContext) {
        val ctx = context ?: return
        runCatching {
            val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("BoxBoysLanDiscoveryLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        }
    }

    fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
    }

    fun refreshLocalIp() {
        _sessionStatus.value = _sessionStatus.value.copy(localIpAddress = resolveLocalLanIp())
    }

    fun setTransportMode(mode: SyncTransportMode) {
        val ip = if (mode == SyncTransportMode.USB_ADB) "127.0.0.1" else resolveLocalLanIp()
        _sessionStatus.value = _sessionStatus.value.copy(
            transportMode = mode,
            localIpAddress = ip,
            statusLog = when (mode) {
                SyncTransportMode.LFS_HOSTED_SERVER -> "LFS Dedicated Server (U1/U2) ready via play.lewisfamilysystems.com"
                SyncTransportMode.LFS_LOCAL_HOST_TUNNEL -> "Host on Device + LFS Tunnel ready via sync.lewisfamilysystems.com"
                SyncTransportMode.FIRESTORE_CLOUD_RELAY -> "Firestore Live Room Relay ready (6-char room code)"
                SyncTransportMode.LAN_WIFI -> "LAN Mode active ($ip:$DEFAULT_P2P_TCP_PORT, up to 4 players)"
                SyncTransportMode.USB_ADB -> "USB Tunnel ready (adb forward tcp:$DEFAULT_P2P_TCP_PORT tcp:$DEFAULT_P2P_TCP_PORT)"
                SyncTransportMode.BLUETOOTH_P2P -> "P2P Encrypted Capsule & Direct Socket mode ready"
            }
        )
    }

    /**
     * Listens for UDP LAN discovery beacons on [DEFAULT_P2P_UDP_PORT] and broadcasts to all active
     * subnet broadcast addresses (`InterfaceAddress.broadcast`) in addition to `255.255.255.255`.
     */
    fun startPassiveLanDiscoveryScanner() {
        if (discoveryJob?.isActive == true) return
        acquireMulticastLock()
        val localIp = resolveLocalLanIp()
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
                            val broadcastTargets = resolveSubnetBroadcastAddresses()
                            for (bcastAddr in broadcastTargets) {
                                runCatching {
                                    val bcastPacket = DatagramPacket(
                                        beaconBytes,
                                        beaconBytes.size,
                                        bcastAddr,
                                        DEFAULT_P2P_UDP_PORT
                                    )
                                    udpSocket.send(bcastPacket)
                                }
                            }
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
     * Opens a [java.net.ServerSocket] on [DEFAULT_P2P_TCP_PORT], broadcasts UDP LAN discovery beacons,
     * and accepts up to [MAX_REMOTE_PEERS] concurrent peer connections (`4` players total).
     */
    fun startHosting(passphrase: String) {
        startAcceptServer(passphrase)
    }

    fun startAcceptServer(passphrase: String) {
        stopActiveTcpSessionOnly()
        role = LocalP2pRole.HOST
        val localIp = resolveLocalLanIp()
        _sessionStatus.value = _sessionStatus.value.copy(
            isHosting = true,
            isConnected = false,
            localIpAddress = localIp,
            statusLog = "ServerSocket listening on $localIp:$DEFAULT_P2P_TCP_PORT (4-Player Hub active)..."
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
                    if (peerSockets.size >= MAX_REMOTE_PEERS) {
                        runCatching {
                            val rejectWriter = PrintWriter(client.getOutputStream(), true)
                            rejectWriter.println("""{"type":"ROOM_FULL","maxRemotePeers":$MAX_REMOTE_PEERS}""")
                            client.close()
                        }
                        continue
                    }
                    scope.launch(Dispatchers.IO) {
                        handleConnectedSocket(
                            socket = client,
                            passphrase = passphrase,
                            isHostSide = true,
                            isHostRelay = true
                        )
                    }
                }
            } catch (e: Exception) {
                if (_sessionStatus.value.isHosting) {
                    _sessionStatus.value = _sessionStatus.value.copy(
                        isHosting = false,
                        isConnected = peerSockets.isNotEmpty(),
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
        role = LocalP2pRole.CLIENT
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
                handleConnectedSocket(sock, passphrase, isHostSide = false, isHostRelay = false)
            } catch (e: Exception) {
                Log.w(TAG, "Peer connection failed", e)
                _sessionStatus.value = _sessionStatus.value.copy(
                    isConnected = false,
                    statusLog = "Connection to $cleanHost:$DEFAULT_P2P_TCP_PORT failed (${e.localizedMessage ?: "timeout"})."
                )
            }
        }
    }

    suspend fun handleConnectedSocket(
        socket: Socket,
        passphrase: String,
        isHostSide: Boolean,
        isHostRelay: Boolean = isHostSide
    ) = withContext(Dispatchers.IO) {
        val peerAddr = socket.inetAddress?.hostAddress ?: "peer"
        val peerPort = socket.port
        val peerId = "$peerAddr:$peerPort"
        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            val connection = PeerConnection(
                peerId = peerId,
                peerAddress = peerAddr,
                socket = socket,
                writer = writer
            )
            peerSockets[peerId] = connection
            val roleLabel = if (isHostSide) "Host ServerSocket" else "Client Socket"
            appendMessageToHistory(
                P2pNetworkMessage(
                    packetType = "HANDSHAKE",
                    senderTag = "SYS",
                    content = "$roleLabel linked with $peerId (${peerSockets.size}/$MAX_REMOTE_PEERS peers)",
                    isOutgoing = true
                )
            )
            _sessionStatus.value = _sessionStatus.value.copy(
                isConnected = true,
                connectedPeerAddress = peerSockets.values.joinToString(", ") { it.peerAddress },
                connectedPeerCount = peerSockets.size,
                statusLog = "Connected to ${peerSockets.size} P2P Peer(s) ($peerAddr) -- Bidirectional Hub Active!"
            )

            val helloJson = JSONObject().apply {
                put("type", "HELLO_HANDSHAKE")
                put("sender", if (isHostSide) "Host-SquadLeader" else "Client-Reinforcement")
                put("peerId", peerId)
                put("text", "P2P Co-op Link Established on TCP :$DEFAULT_P2P_TCP_PORT")
            }
            writer.println(helloJson.toString())

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String? = reader.readLine()
            while (line != null && isActive) {
                processIncomingPacket(line, passphrase, sourcePeerId = peerId, isHostRelay = isHostRelay)
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Socket stream ended ($peerId): ${e.message}")
        } finally {
            peerSockets.remove(peerId)
            runCatching { socket.close() }
            val updatedStates = _remotePeerStates.value.toMutableMap().apply { remove(peerId) }
            _remotePeerStates.value = updatedStates
            _coopPeerState.value = updatedStates.values.firstOrNull()
            val stillConnected = peerSockets.isNotEmpty()
            _sessionStatus.value = _sessionStatus.value.copy(
                isConnected = stillConnected,
                connectedPeerAddress = peerSockets.values.joinToString(", ") { it.peerAddress },
                connectedPeerCount = peerSockets.size,
                statusLog = if (stillConnected) {
                    "Peer $peerAddr left; ${peerSockets.size} peer(s) still connected."
                } else {
                    "All peers disconnected."
                }
            )
        }
    }

    fun broadcastToAllPeers(rawLine: String, excludePeerId: String? = null): Int {
        var delivered = 0
        val deadPeers = mutableListOf<String>()
        for ((id, conn) in peerSockets) {
            if (excludePeerId != null && id == excludePeerId) continue
            try {
                synchronized(conn.writer) {
                    conn.writer.println(rawLine)
                }
                delivered++
            } catch (_: Exception) {
                deadPeers.add(id)
            }
        }
        for (dead in deadPeers) {
            peerSockets.remove(dead)?.let { runCatching { it.socket.close() } }
        }
        return delivered
    }

    /**
     * Sends a real-time tactical message or callout over all active TCP [Socket] connections.
     */
    fun sendTacticalMessage(senderName: String, messageText: String): Boolean {
        val cleanText = messageText.trim()
        if (cleanText.isEmpty()) return false
        val msg = P2pNetworkMessage(
            packetType = "TACTICAL_MSG",
            senderTag = senderName,
            content = cleanText,
            isOutgoing = true
        )
        appendMessageToHistory(msg)

        if (peerSockets.isNotEmpty() && _sessionStatus.value.isConnected) {
            scope.launch(Dispatchers.IO) {
                try {
                    val obj = JSONObject().apply {
                        put("type", "TACTICAL_MSG")
                        put("sender", senderName)
                        put("text", cleanText)
                        put("ts", msg.timestampMs)
                    }
                    val count = broadcastToAllPeers(obj.toString())
                    _sessionStatus.value = _sessionStatus.value.copy(
                        packetsSent = _sessionStatus.value.packetsSent + count,
                        statusLog = "Sent P2P Message to $count peer(s): \"$cleanText\""
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
        if (peerSockets.isEmpty() || !_sessionStatus.value.isConnected) return
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
                val sent = broadcastToAllPeers(obj.toString())
                if (sent > 0) {
                    _sessionStatus.value = _sessionStatus.value.copy(
                        packetsSent = _sessionStatus.value.packetsSent + sent
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun pushEncryptedSaveOverSocket(snapshot: CryptoSyncEngine.GameSaveSnapshot, passphrase: String): Boolean {
        val encrypted = CryptoSyncEngine.encryptSnapshot(snapshot, passphrase)
        if (peerSockets.isNotEmpty() && _sessionStatus.value.isConnected) {
            scope.launch(Dispatchers.IO) {
                try {
                    val obj = JSONObject().apply {
                        put("type", "SYNC_SAVE")
                        put("capsule", encrypted)
                    }
                    val sent = broadcastToAllPeers(obj.toString())
                    appendMessageToHistory(
                        P2pNetworkMessage(
                            packetType = "SYNC_SAVE",
                            senderTag = "LOCAL",
                            content = "Pushed AES-256-GCM Save '${snapshot.slotName}' (Wave ${snapshot.highestWave}) to $sent peer(s)",
                            isOutgoing = true
                        )
                    )
                    _sessionStatus.value = _sessionStatus.value.copy(
                        packetsSent = _sessionStatus.value.packetsSent + sent,
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

    private fun processIncomingPacket(
        rawLine: String,
        passphrase: String,
        sourcePeerId: String = "peer_default",
        isHostRelay: Boolean = (role == LocalP2pRole.HOST)
    ) {
        try {
            val obj = JSONObject(rawLine)
            val pktType = obj.optString("type")
            _sessionStatus.value = _sessionStatus.value.copy(
                packetsReceived = _sessionStatus.value.packetsReceived + 1
            )

            // When acting as 4-Player Star-Mesh Host, relay tactical/co-op packets to all other connected peers
            if (isHostRelay && pktType in RELAYABLE_PACKET_TYPES && peerSockets.size > 1) {
                broadcastToAllPeers(rawLine, excludePeerId = sourcePeerId)
            }

            when (pktType) {
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
                "TACTICAL_MSG", "TACTICAL_CALLOUT", "SQUAD_REVIVE_PULSE", "WAVE_SYNC" -> {
                    val sender = obj.optString("sender", "Co-op Peer")
                    val text = obj.optString("text", pktType)
                    if (text.isNotBlank()) {
                        val msg = P2pNetworkMessage(
                            packetType = pktType,
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
                    val peerName = obj.optString("name", "Player 2")
                    val peerKey = obj.optString("peerId", sourcePeerId).ifBlank { peerName }
                    val state = CoopPeerState(
                        peerId = peerKey,
                        peerName = peerName,
                        playerName = peerName,
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
                    _coopPeerState.value = state
                    _remotePeerStates.value = _remotePeerStates.value + (peerKey to state)
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
        for ((_, conn) in peerSockets) {
            runCatching { conn.writer.close() }
            runCatching { conn.socket.close() }
        }
        peerSockets.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        role = LocalP2pRole.IDLE
        _coopPeerState.value = null
        _remotePeerStates.value = emptyMap()
    }

    fun stopSession() {
        discoveryJob?.cancel()
        releaseMulticastLock()
        stopActiveTcpSessionOnly()
        _sessionStatus.value = _sessionStatus.value.copy(
            isHosting = false,
            isConnected = false,
            connectedPeerAddress = "",
            connectedPeerCount = 0
        )
    }

    companion object {
        private val RELAYABLE_PACKET_TYPES = setOf(
            "TACTICAL_MSG",
            "TACTICAL_CALLOUT",
            "SQUAD_REVIVE_PULSE",
            "WAVE_SYNC",
            "COOP_STATE"
        )

        private fun scoreNetworkInterface(name: String): Int {
            val lower = name.lowercase()
            return when {
                lower.startsWith("wlan") || lower.startsWith("ap") || lower.startsWith("swlan") -> 100
                lower.startsWith("rndis") || lower.startsWith("usb") || lower.startsWith("eth") -> 90
                lower.startsWith("tailscale") || lower.startsWith("ts") -> 75
                lower.startsWith("rmnet") || lower.startsWith("ccmni") || lower.startsWith("tun") || lower.startsWith("dummy") -> 10
                else -> 50
            }
        }

        fun resolveLocalLanIp(): String {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return "127.0.0.1"
                val candidates = mutableListOf<Pair<Int, String>>()
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val score = scoreNetworkInterface(intf.name ?: "")
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress ?: continue
                            candidates.add(score to ip)
                        }
                    }
                }
                candidates.maxByOrNull { it.first }?.second ?: "127.0.0.1"
            } catch (_: Exception) {
                "127.0.0.1"
            }
        }

        fun resolveLocalIpv4(): String = resolveLocalLanIp()

        fun resolveSubnetBroadcastAddresses(): List<InetAddress> {
            val result = linkedSetOf<InetAddress>()
            runCatching { result.add(InetAddress.getByName("255.255.255.255")) }
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (ifAddr in intf.interfaceAddresses) {
                        val bcast = ifAddr.broadcast
                        if (bcast is Inet4Address) {
                            result.add(bcast)
                        }
                    }
                }
            }
            return result.toList()
        }
    }
}

