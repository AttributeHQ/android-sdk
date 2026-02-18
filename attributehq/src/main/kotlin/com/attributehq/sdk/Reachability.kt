package com.attributehq.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Network reachability monitor using ConnectivityManager.
 * Notifies listeners when connectivity changes.
 */
internal class Reachability private constructor(context: Context) {

    @Volatile
    var isReachable: Boolean = false
        private set

    var onReachabilityChanged: ((Boolean) -> Unit)? = null

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasReachable = isReachable
            isReachable = true
            if (!wasReachable) {
                Logger.debug("Network available")
                onReachabilityChanged?.invoke(true)
            }
        }

        override fun onLost(network: Network) {
            isReachable = false
            Logger.debug("Network lost")
            onReachabilityChanged?.invoke(false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val wasReachable = isReachable
            isReachable = hasInternet
            if (hasInternet && !wasReachable) {
                onReachabilityChanged?.invoke(true)
            }
        }
    }

    init {
        // Check initial state
        isReachable = checkCurrentConnectivity()

        // Register for changes
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Logger.error("Failed to register network callback", e)
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    @Suppress("DEPRECATION")
    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        @Volatile
        private var instance: Reachability? = null

        fun initialize(context: Context): Reachability {
            return instance ?: synchronized(this) {
                instance ?: Reachability(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): Reachability? = instance
    }
}
