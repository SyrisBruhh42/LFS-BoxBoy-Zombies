package com.example.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Dedicated P2P Connectivity Module built on [java.net.ServerSocket], [java.net.Socket],
 * and [java.net.DatagramSocket] for zero-configuration local network multiplayer discovery
 * and low-latency message passing between Android devices.
 */
object P2pConnectivityModule {

    const val DISCOVERY_UDP_PORT = DEFAULT_P2P_UDP_PORT
    const val MESSAGING_TCP_PORT = DEFAULT_P2P_TCP_PORT

    private val _discoveredHosts = MutableStateFlow<List<String>>(emptyList())
    val discoveredHosts: StateFlow<List<String>> = _discoveredHosts.asStateFlow()

    private val _receivedMessages = MutableStateFlow<List<P2pNetworkMessage>>(emptyList())
    val receivedMessages: StateFlow<List<P2pNetworkMessage>> = _receivedMessages.asStateFlow()

    /**
     * Broadcasts a UDP discovery beacon on the local subnet and listens for peer responses.
     */
    fun startLanDiscovery(
        scope: CoroutineScope,
        serviceTag: String = "BOXBOY_HOST",
        udpPort: Int = DISCOVERY_UDP_PORT
    ): Job = scope.launch(Dispatchers.IO) {
        var udpSocket: DatagramSocket? = null
        val localIp = LocalP2pSocketManager.resolveLocalIpv4()
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(udpPort))
                soTimeout = 1200
            }
            val payload = "$serviceTag:$localIp:$MESSAGING_TCP_PORT".toByteArray(Charsets.UTF_8)
            val buf = ByteArray(256)
            while (isActive) {
                try {
                    udpSocket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            InetAddress.getByName("255.255.255.255"),
                            udpPort
                        )
                    )
                } catch (_: Exception) {
                }
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    udpSocket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (text.startsWith("$serviceTag:")) {
                        val hostIp = text.removePrefix("$serviceTag:").substringBefore(":")
                        if (hostIp.isNotBlank() && hostIp != localIp) {
                            _discoveredHosts.value = (_discoveredHosts.value + hostIp).distinct()
                        }
                    }
                } catch (_: Exception) {
                }
                delay(1000L)
            }
        } catch (_: Exception) {
        } finally {
            udpSocket?.close()
        }
    }

    /**
     * Starts a TCP [ServerSocket] listener that accepts peer [Socket] connections
     * and dispatches newline-delimited JSON messages.
     */
    fun openServerSocket(
        scope: CoroutineScope,
        port: Int = MESSAGING_TCP_PORT,
        onMessageReceived: (P2pNetworkMessage) -> Unit
    ): Job = scope.launch(Dispatchers.IO) {
        var server: ServerSocket? = null
        try {
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            while (isActive) {
                val clientSocket = server.accept()
                clientSocket.tcpNoDelay = true
                launch(Dispatchers.IO) {
                    readSocketLoop(clientSocket, onMessageReceived)
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                server?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Sends a single structured JSON message to a peer [ServerSocket] using [java.net.Socket].
     */
    suspend fun sendDirectSocketMessage(
        hostIp: String,
        port: Int = MESSAGING_TCP_PORT,
        senderTag: String,
        messageContent: String
    ): Result<P2pNetworkMessage> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(hostIp, port), 3500)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val envelope = P2pNetworkMessage(
                    packetType = "TACTICAL_MSG",
                    senderTag = senderTag,
                    content = messageContent,
                    isOutgoing = true
                )
                val json = JSONObject().apply {
                    put("type", envelope.packetType)
                    put("sender", envelope.senderTag)
                    put("text", envelope.content)
                    put("ts", envelope.timestampMs)
                }
                writer.println(json.toString())
                envelope
            }
        }
    }

    private fun readSocketLoop(
        socket: Socket,
        onMessageReceived: (P2pNetworkMessage) -> Unit
    ) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            var line: String? = reader.readLine()
            while (line != null) {
                val obj = JSONObject(line)
                val msg = P2pNetworkMessage(
                    packetType = obj.optString("type", "TACTICAL_MSG"),
                    senderTag = obj.optString("sender", socket.inetAddress?.hostAddress ?: "Peer"),
                    content = obj.optString("text", line),
                    isOutgoing = false,
                    timestampMs = obj.optLong("ts", System.currentTimeMillis())
                )
                _receivedMessages.value = (_receivedMessages.value + msg).takeLast(30)
                onMessageReceived(msg)
                line = reader.readLine()
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }
}
