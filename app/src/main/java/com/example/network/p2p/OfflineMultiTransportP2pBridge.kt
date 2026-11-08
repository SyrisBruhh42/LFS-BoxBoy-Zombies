package com.example.network.p2p

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/**
 * Track 8: Offline Local Host Multi-Transport P2P Bridge (USB + Wi-Fi LAN + Bluetooth RFCOMM).
 * Allows any device to act as the authoritative Local Host completely offline over:
 * 1. USB Tethering / ADB Bridge (`rndis0`, `usb0`, `ncm0`, or `127.0.0.1:48899` ADB reverse/forward)
 * 2. Wi-Fi LAN / Hotspot (`wlan0`, `ap0`, `p2p-wlan0` UDP `48898` + TCP `48899`)
 * 3. Bluetooth Classic RFCOMM (`SPP_STANDARD_UUID` / `BLUETOOTH_BOXBOYS_SERVICE_UUID`)
 */
enum class OfflineHostTransportKind(
    val displayName: String,
    val badgeCode: String,
    val maxRecommendedMtuBytes: Int,
    val defaultTickHz: Int
) {
    USB_DIRECT_BRIDGE("USB Direct / Tether (Device-to-Device)", "USB-P2P", 65_536, 60),
    WIFI_LAN_HOTSPOT("Wi-Fi LAN / Mobile Hotspot", "WIFI-LAN", 32_768, 30),
    BLUETOOTH_RFCOMM_BLE("Bluetooth Offline P2P Mesh", "BT-MESH", 16_384, 20),
    BACKEND_DEDICATED_SERVER("Oasis Backend Server (:19448 + Ollama AI)", "SERVER", 65_536, 60)
}

data class DiscoveredTransportInterface(
    val interfaceName: String,
    val ipv4Address: String,
    val transportKind: OfflineHostTransportKind,
    val endpointUri: String
)

data class BluetoothRfcommPeerLink(
    val deviceName: String,
    val deviceAddress: String,
    val socket: BluetoothSocket,
    val reader: BufferedReader,
    val writer: PrintWriter
) {
    fun sendLine(line: String): Boolean {
        return runCatching {
            synchronized(writer) {
                writer.println(line)
            }
            true
        }.getOrDefault(false)
    }

    fun close() {
        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { socket.close() }
    }
}

object OfflineMultiTransportP2pBridge {
    const val P2P_TCP_PORT = 48899
    const val P2P_UDP_BEACON_PORT = 48898
    const val BLUETOOTH_SERVICE_NAME = "LfsBoxBoysCoopRfcomm"
    val BLUETOOTH_BOXBOYS_SERVICE_UUID: UUID = UUID.fromString("8e7f42b0-9c12-4a88-b512-00424f58424f")
    val BLUETOOTH_SPP_STANDARD_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val USB_INTERFACE_PREFIXES = listOf("rndis", "usb", "ncm", "eth_usb")
    private val WIFI_INTERFACE_PREFIXES = listOf("wlan", "ap", "p2p", "swlan", "wl")
    private val CELLULAR_TUNNEL_PREFIXES = listOf("rmnet", "ccmni", "tun", "dummy")

    @Volatile
    internal var activeBluetoothServerSocket: BluetoothServerSocket? = null

    /**
     * Checks whether runtime `BLUETOOTH_CONNECT` permission is granted (Android 12+ API 31+).
     */
    fun hasBluetoothConnectPermission(context: Context?): Boolean {
        if (context == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Closes any active RFCOMM [BluetoothServerSocket] before re-hosting (`BB-V4-D00-69d43f7f`).
     */
    @Synchronized
    fun closeBluetoothRfcommServer() {
        runCatching { activeBluetoothServerSocket?.close() }
        activeBluetoothServerSocket = null
    }

    /**
     * Opens an RFCOMM [BluetoothServerSocket] for offline Bluetooth P2P hosting (`FP-N05`).
     * Automatically closes any prior [activeBluetoothServerSocket] before re-listening (`BB-V4-D00-69d43f7f`).
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun openBluetoothRfcommServer(
        context: Context?,
        serviceUuid: UUID = BLUETOOTH_BOXBOYS_SERVICE_UUID
    ): Result<BluetoothServerSocket> {
        return runCatching {
            closeBluetoothRfcommServer()
            require(hasBluetoothConnectPermission(context)) {
                "BLUETOOTH_CONNECT permission is required to host over Bluetooth RFCOMM"
            }
            val btManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                ?: error("BluetoothAdapter unavailable on this device")
            require(adapter.isEnabled) { "Bluetooth adapter is disabled" }
            val srv = adapter.listenUsingInsecureRfcommWithServiceRecord(BLUETOOTH_SERVICE_NAME, serviceUuid)
            activeBluetoothServerSocket = srv
            srv
        }
    }

    fun startBluetoothRfcommHosting(
        context: Context?,
        serviceUuid: UUID = BLUETOOTH_BOXBOYS_SERVICE_UUID
    ): Result<BluetoothServerSocket> = openBluetoothRfcommServer(context, serviceUuid)

    /**
     * Connects to a paired [BluetoothDevice] over RFCOMM (`BLUETOOTH_BOXBOYS_SERVICE_UUID` with
     * fallback to standard SPP `00001101-0000-1000-8000-00805F9B34FB`).
     */
    @SuppressLint("MissingPermission")
    fun connectBluetoothRfcommPeer(
        context: Context?,
        device: BluetoothDevice,
        serviceUuid: UUID = BLUETOOTH_BOXBOYS_SERVICE_UUID
    ): Result<BluetoothRfcommPeerLink> {
        return runCatching {
            require(hasBluetoothConnectPermission(context)) {
                "BLUETOOTH_CONNECT permission is required to connect over Bluetooth RFCOMM"
            }
            val btManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.adapter?.cancelDiscovery()
            val socket = runCatching {
                device.createInsecureRfcommSocketToServiceRecord(serviceUuid).apply { connect() }
            }.getOrElse {
                device.createInsecureRfcommSocketToServiceRecord(BLUETOOTH_SPP_STANDARD_UUID).apply { connect() }
            }
            BluetoothRfcommPeerLink(
                deviceName = runCatching { device.name.orEmpty() }.getOrDefault("BT-Survivor"),
                deviceAddress = runCatching { device.address.orEmpty() }.getOrDefault("00:00:00:00:00:00"),
                socket = socket,
                reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)),
                writer = PrintWriter(socket.outputStream, true)
            )
        }
    }

    /**
     * Scans local OS network interfaces to auto-detect active USB tethering (`rndis0`/`usb0`)
     * and Wi-Fi LAN/Hotspot (`wlan0`/`ap0`) interfaces for zero-configuration offline hosting.
     * When [context] is provided, checks Bluetooth adapter and `BLUETOOTH_CONNECT` permission (`BB-V4-D00-8db57dac`).
     */
    fun discoverOfflineHostInterfaces(context: Context? = null): List<DiscoveredTransportInterface> {
        val discovered = mutableListOf<DiscoveredTransportInterface>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val nameLower = nif.name.lowercase()
                if (CELLULAR_TUNNEL_PREFIXES.any { nameLower.startsWith(it) }) continue

                val transport = when {
                    USB_INTERFACE_PREFIXES.any { nameLower.startsWith(it) } ->
                        OfflineHostTransportKind.USB_DIRECT_BRIDGE
                    WIFI_INTERFACE_PREFIXES.any { nameLower.startsWith(it) } ->
                        OfflineHostTransportKind.WIFI_LAN_HOTSPOT
                    else -> OfflineHostTransportKind.WIFI_LAN_HOTSPOT
                }
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        discovered.add(
                            DiscoveredTransportInterface(
                                interfaceName = nif.name,
                                ipv4Address = ip,
                                transportKind = transport,
                                endpointUri = "tcp://$ip:$P2P_TCP_PORT"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore interface enumeration restrictions in sandboxed environments
        }

        // Always include ADB USB Loopback Bridge
        discovered.add(
            DiscoveredTransportInterface(
                interfaceName = "adb-usb-loopback",
                ipv4Address = "127.0.0.1",
                transportKind = OfflineHostTransportKind.USB_DIRECT_BRIDGE,
                endpointUri = "tcp://127.0.0.1:$P2P_TCP_PORT"
            )
        )

        val isBtAvailable = if (context == null) {
            true
        } else {
            hasBluetoothConnectPermission(context) && runCatching {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                adapter?.isEnabled == true
            }.getOrDefault(false)
        }

        if (isBtAvailable) {
            discovered.add(
                DiscoveredTransportInterface(
                    interfaceName = "bt-rfcomm-channel",
                    ipv4Address = "bt://rfcomm",
                    transportKind = OfflineHostTransportKind.BLUETOOTH_RFCOMM_BLE,
                    endpointUri = "bt-rfcomm://$BLUETOOTH_BOXBOYS_SERVICE_UUID"
                )
            )
        }
        return discovered
    }

    /**
     * Chunks a serialized packet into MTU-safe frames for Bluetooth RFCOMM/BLE or USB/Wi-Fi.
     */
    fun framePayloadForTransport(
        payload: String,
        transport: OfflineHostTransportKind
    ): List<String> {
        val maxChars = (transport.maxRecommendedMtuBytes / 2).coerceAtLeast(1024)
        if (payload.length <= maxChars) return listOf("FRAME:1/1:$payload")
        val slices = payload.chunked(maxChars)
        return slices.mapIndexed { idx, chunk ->
            "FRAME:${idx + 1}/${slices.size}:$chunk"
        }
    }
}

