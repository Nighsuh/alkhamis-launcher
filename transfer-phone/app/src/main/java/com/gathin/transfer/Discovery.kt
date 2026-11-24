package com.gathin.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Finds TVs advertising `_alkhamis._tcp`. Tests can supply [preview] instead. */
object Discovery {
    var preview: List<FoundTv>? = null
    private var manager: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null

    fun known(): List<FoundTv> = preview ?: emptyList()

    fun start(ctx: Context, onFound: (FoundTv) -> Unit) {
        if (preview != null) return
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        stop()
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
                        onFound(FoundTv(resolved.serviceName ?: host, host, resolved.port))
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
    }
}
