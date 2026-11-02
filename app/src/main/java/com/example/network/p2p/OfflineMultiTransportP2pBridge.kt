package com.example.network.p2p

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/**
 * Track 8: Offline Local Host Multi-Transport P2P Bridge (USB + Wi-Fi LAN + Bluetooth).
 * Allows any device to act as the authoritative Local Host completely offline over:
 * 1. USB Tethering / ADB Bridge (`rndis0`, `usb0`, `ncm0`, or `127.0.0.1:48899` ADB reverse/forward)
 * 2. Wi-Fi LAN / Hotspot (`wlan0`, `ap0`, `p2p-wlan0` UDP `48898` + TCP `48899`)
 * 3. Bluetooth RFCOMM / BLE L2CAP (`UUID 8e7f42b0-9c12-4a88-b512-00424f58424f`)
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

object OfflineMultiTransportP2pBridge {
    const val P2P_TCP_PORT = 48899
    const val P2P_UDP_BEACON_PORT = 48898
    val BLUETOOTH_BOXBOYS_SERVICE_UUID: UUID = UUID.fromString("8e7f42b0-9c12-4a88-b512-00424f58424f")

    private val USB_INTERFACE_PREFIXES = listOf("rndis", "usb", "ncm", "eth_usb")
    private val WIFI_INTERFACE_PREFIXES = listOf("wlan", "ap", "p2p", "swlan", "wl")

    /**
     * Scans local OS network interfaces to auto-detect active USB tethering (`rndis0`/`usb0`)
     * and Wi-Fi LAN/Hotspot (`wlan0`/`ap0`) interfaces for zero-configuration offline hosting.
     */
    fun discoverOfflineHostInterfaces(): List<DiscoveredTransportInterface> {
        val discovered = mutableListOf<DiscoveredTransportInterface>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val nameLower = nif.name.lowercase()
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

        // Always include ADB USB Loopback Bridge & Bluetooth RFCOMM Descriptor
        discovered.add(
            DiscoveredTransportInterface(
                interfaceName = "adb-usb-loopback",
                ipv4Address = "127.0.0.1",
                transportKind = OfflineHostTransportKind.USB_DIRECT_BRIDGE,
                endpointUri = "tcp://127.0.0.1:$P2P_TCP_PORT"
            )
        )
        discovered.add(
            DiscoveredTransportInterface(
                interfaceName = "bt-rfcomm-channel",
                ipv4Address = "bt://rfcomm",
                transportKind = OfflineHostTransportKind.BLUETOOTH_RFCOMM_BLE,
                endpointUri = "bt-rfcomm://$BLUETOOTH_BOXBOYS_SERVICE_UUID"
            )
        )
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
