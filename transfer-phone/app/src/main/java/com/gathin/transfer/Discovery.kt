package com.gathin.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

/** Finds TVs advertising `_alkhamis._tcp`. Tests can supply [preview] instead. */
object Discovery {
    var preview: List<FoundTv>? = null
    private var manager: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null

    fun known(): List<FoundTv> = preview ?: emptyList()

    fun start(ctx: Context, onFound: (FoundTv) -> Unit) {
        if (preview != null) return
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        stop()
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicast = wifi?.createMulticastLock("alkhamis-discovery")?.also {
            it.setReferenceCounted(false)
            try { it.acquire() } catch (ignored: Exception) { }
        }
        val next = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String?, error: Int) {}
            override fun onStopDiscoveryFailed(type: String?, error: Int) {}
            override fun onDiscoveryStarted(type: String?) {}
            override fun onDiscoveryStopped(type: String?) {}
            override fun onServiceLost(service: NsdServiceInfo?) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo?, error: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val named = resolved.attributes?.get("name")?.let { String(it, Charsets.UTF_8) }
                        val label = named?.ifBlank { null } ?: resolved.serviceName ?: host
                        onFound(FoundTv(label, host, resolved.port))
                    }
                })
            }
        }
        nsd.discoverServices("_alkhamis._tcp.", NsdManager.PROTOCOL_DNS_SD, next)
        manager = nsd
        listener = next
    }

    fun stop() {
        val nsd = manager
        val current = listener
        if (nsd != null && current != null) {
            try {
                nsd.stopServiceDiscovery(current)
            } catch (ignored: Exception) { }
        }
        manager = null
        listener = null
        try { if (multicast?.isHeld == true) multicast?.release() } catch (ignored: Exception) { }
        multicast = null
    }
}
