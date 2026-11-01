package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

data class InterfaceProbeRecord(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isPointToPoint: Boolean,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>
)

data class NetworkHealthSnapshot(
    val isTailscaleActive: Boolean = false,
    val tailscaleIp: String? = null,
    val isVpnProfileActive: Boolean = false,
    val activeVpnInterfaces: List<String> = emptyList(),
    val detectedPrivateInterfaces: List<String> = emptyList(),
    val localLanIp: String = "127.0.0.1",
    val manualPrivateOverride: Boolean = false,
    val lastCheckedEpochMs: Long = System.currentTimeMillis()
) {
    /**
     * True if an active Tailscale mesh interface (`tailscale0`, `ts0`, `100.64.0.0/10` CGNAT, or `fd7a:115c:a1e0::`)
     * or a specific VPN tunnel interface (`tun*`, `wg*`, `utun*`, `zerotier*`, `nebula*`, `ppp*`, or Android `TRANSPORT_VPN`)
     * is currently active.
     */
    val isPrivateNetworkDetected: Boolean
        get() = isTailscaleActive || isVpnProfileActive

    /**
     * Controls whether the UI displays the LFS Oasis Server configuration card on the Host/Join screen.
     */
    val shouldShowOasisServerConfig: Boolean
        get() = isPrivateNetworkDetected || manualPrivateOverride

    val statusHeadline: String
        get() = when {
            isTailscaleActive -> "TAILSCALE PRIVATE MESH ACTIVE (${tailscaleIp ?: activeVpnInterfaces.firstOrNull() ?: "ts0"})"
            isVpnProfileActive -> "PRIVATE VPN PROFILE ACTIVE (${activeVpnInterfaces.joinToString(", ").ifBlank { "TRANSPORT_VPN" }})"
            manualPrivateOverride -> "MANUAL PRIVATE NETWORK OVERRIDE ENABLED (LFS Oasis Card Unlocked)"
            else -> "PUBLIC / STANDARD LAN ($localLanIp) — No Tailscale or VPN Profile Detected"
        }
}

/**
 * Monitors device network interfaces and Android `ConnectivityManager` capabilities to detect
 * active Tailscale mesh interfaces (`100.64.0.0/10`, `tailscale0`, `ts0`, `utun*`) or specific
 * VPN profiles (`wg0`, `tun0`, `zerotier*`, `nebula*`, `TRANSPORT_VPN`).
 *
 * Used by the Host/Join screen (`P2pMultiplayerSyncDialog`) to conditionally display the
 * LFS Oasis Server configuration card when the user is on a private network.
 */
class NetworkHealthService(
    private val scope: CoroutineScope,
    private val appContext: Context? = null
) {
    private val _healthState = MutableStateFlow(NetworkHealthSnapshot())
    val healthState: StateFlow<NetworkHealthSnapshot> = _healthState.asStateFlow()

    /**
     * Public StateFlow alias for observing connectivity & VPN/Tailscale status in the UI.
     */
    val connectivityStatus: StateFlow<NetworkHealthSnapshot>
        get() = healthState

    private var monitorJob: Job? = null
    private var networkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshNetworkHealth()
        }

        override fun onLost(network: Network) {
            refreshNetworkHealth()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasVpn = runCatching {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }.getOrDefault(false)
            scope.launch(Dispatchers.IO) {
                refreshNetworkHealthInternal(explicitVpnTransport = hasVpn)
            }
        }
    }

    init {
        registerConnectivityCallbackIfAvailable()
        refreshNetworkHealth()
        startPeriodicHealthCheck()
    }

    private fun registerConnectivityCallbackIfAvailable() {
        val ctx = appContext ?: return
        if (networkCallbackRegistered) return
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    fun startPeriodicHealthCheck(intervalMs: Long = 6_000L) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshNetworkHealthInternal()
                delay(intervalMs)
            }
        }
    }

    fun refreshNetworkHealth() {
        scope.launch(Dispatchers.IO) {
            refreshNetworkHealthInternal()
        }
    }

    fun setManualPrivateNetworkOverride(enabled: Boolean) {
        _healthState.value = _healthState.value.copy(
            manualPrivateOverride = enabled,
            lastCheckedEpochMs = System.currentTimeMillis()
        )
    }

    private fun refreshNetworkHealthInternal(explicitVpnTransport: Boolean? = null) {
        val currentOverride = _healthState.value.manualPrivateOverride
        val records = collectSystemInterfaceRecords()
        val systemVpnTransport = explicitVpnTransport ?: checkAndroidVpnTransport(appContext)
        val evaluated = evaluateInterfaces(
            records = records,
            androidVpnTransportActive = systemVpnTransport,
            manualPrivateOverride = currentOverride
        )
        _healthState.value = evaluated
    }

    companion object {
        private val TAILSCALE_INTERFACE_PREFIXES = listOf("tailscale", "ts0", "ts1")
        private val VPN_PROFILE_INTERFACE_PREFIXES = listOf(
            "tun", "utun", "wg", "ppp", "zerotier", "zt", "nebula", "tap", "ipsec", "ikev2"
        )

        /**
         * Checks if an IPv4 address belongs to the Tailscale CGNAT space (`100.64.0.0/10`, i.e., `100.64.0.0` – `100.127.255.255`).
         */
        fun isTailscaleCgnatIpv4(ip: String): Boolean {
            val parts = ip.trim().split('.')
            if (parts.size != 4) return false
            val first = parts[0].toIntOrNull() ?: return false
            val second = parts[1].toIntOrNull() ?: return false
            val third = parts[2].toIntOrNull() ?: return false
            val fourth = parts[3].toIntOrNull() ?: return false
            if (third !in 0..255 || fourth !in 0..255) return false
            return first == 100 && second in 64..127
        }

        /**
         * Checks if an IPv6 address belongs to the Tailscale ULA prefix (`fd7a:115c:a1e0::/48`).
         */
        fun isTailscaleUlaIpv6(ip: String): Boolean {
            val normalized = ip.trim().lowercase().substringBefore('%')
            return normalized.startsWith("fd7a:115c:a1e0:")
        }

        fun isTailscaleInterfaceName(name: String): Boolean {
            val lower = name.trim().lowercase()
            return TAILSCALE_INTERFACE_PREFIXES.any { lower.startsWith(it) }
        }

        fun isVpnProfileInterfaceName(name: String): Boolean {
            val lower = name.trim().lowercase()
            return isTailscaleInterfaceName(lower) ||
                VPN_PROFILE_INTERFACE_PREFIXES.any { lower.startsWith(it) }
        }

        /**
         * Pure evaluation function that inspects interface records + Android VPN capability flag
         * to produce a deterministic `NetworkHealthSnapshot`.
         */
        fun evaluateInterfaces(
            records: List<InterfaceProbeRecord>,
            androidVpnTransportActive: Boolean = false,
            manualPrivateOverride: Boolean = false
        ): NetworkHealthSnapshot {
            var tailscaleFound = false
            var tailscaleIp: String? = null
            val vpnInterfaces = mutableListOf<String>()
            val privateInterfaces = mutableListOf<String>()
            var bestLanIp = "127.0.0.1"

            for (iface in records) {
                if (!iface.isUp || iface.isLoopback) continue

                val hasTailscaleName = isTailscaleInterfaceName(iface.name)
                val tsIpv4 = iface.ipv4Addresses.firstOrNull { isTailscaleCgnatIpv4(it) }
                val tsIpv6 = iface.ipv6Addresses.firstOrNull { isTailscaleUlaIpv6(it) }

                if (hasTailscaleName || tsIpv4 != null || tsIpv6 != null) {
                    tailscaleFound = true
                    if (tailscaleIp == null) {
                        tailscaleIp = tsIpv4 ?: iface.ipv4Addresses.firstOrNull() ?: tsIpv6
                    }
                    vpnInterfaces.add(iface.name)
                    privateInterfaces.add("${iface.name} (${tsIpv4 ?: tsIpv6 ?: "Tailscale"})")
                    continue
                }

                val isVpnIface = isVpnProfileInterfaceName(iface.name) || iface.isPointToPoint
                if (isVpnIface && (iface.ipv4Addresses.isNotEmpty() || iface.ipv6Addresses.isNotEmpty())) {
                    vpnInterfaces.add(iface.name)
                    val vpnIp = iface.ipv4Addresses.firstOrNull() ?: iface.ipv6Addresses.firstOrNull().orEmpty()
                    privateInterfaces.add("${iface.name} ($vpnIp)")
                }

                for (ipv4 in iface.ipv4Addresses) {
                    if (ipv4 != "127.0.0.1" && bestLanIp == "127.0.0.1") {
                        bestLanIp = ipv4
                    }
                }
            }

            if (bestLanIp == "127.0.0.1" && tailscaleIp != null) {
                bestLanIp = tailscaleIp
            }

            val isVpnActive = tailscaleFound || androidVpnTransportActive || vpnInterfaces.isNotEmpty()

            return NetworkHealthSnapshot(
                isTailscaleActive = tailscaleFound,
                tailscaleIp = tailscaleIp,
                isVpnProfileActive = isVpnActive,
                activeVpnInterfaces = vpnInterfaces.distinct(),
                detectedPrivateInterfaces = privateInterfaces.distinct(),
                localLanIp = bestLanIp,
                manualPrivateOverride = manualPrivateOverride,
                lastCheckedEpochMs = System.currentTimeMillis()
            )
        }

        private fun collectSystemInterfaceRecords(): List<InterfaceProbeRecord> {
            return try {
                val enumeration = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
                Collections.list(enumeration).map { ni ->
                    val ipv4 = mutableListOf<String>()
                    val ipv6 = mutableListOf<String>()
                    for (addr in Collections.list(ni.inetAddresses)) {
                        when (addr) {
                            is Inet4Address -> addr.hostAddress?.let { ipv4.add(it) }
                            is Inet6Address -> addr.hostAddress?.let { ipv6.add(it.substringBefore('%')) }
                        }
                    }
                    InterfaceProbeRecord(
                        name = ni.name ?: "unknown",
                        isUp = runCatching { ni.isUp }.getOrDefault(false),
                        isLoopback = runCatching { ni.isLoopback }.getOrDefault(false),
                        isPointToPoint = runCatching { ni.isPointToPoint }.getOrDefault(false),
                        ipv4Addresses = ipv4,
                        ipv6Addresses = ipv6
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun checkAndroidVpnTransport(context: Context?): Boolean {
            if (context == null) return false
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
                val activeNet = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNet) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } catch (_: Exception) {
                false
            }
        }
    }
}
