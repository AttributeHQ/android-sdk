package com.attributehq.sdk

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Fetch Play's referrer once, with a bounded wait even when the service disconnects. */
internal object InstallReferrerManager {
    fun getInstallReferrer(context: Context, scheduler: ScheduledExecutorService, callback: (String?, Boolean) -> Unit) {
        val client = try { InstallReferrerClient.newBuilder(context).build() }
        catch (_: Exception) { callback(null, true); return }
        fetch(client, scheduler, callback = callback)
    }

    internal fun fetch(connection: InstallReferrerClient, scheduler: ScheduledExecutorService,
                       timeoutMillis: Long = 5000, callback: (String?, Boolean) -> Unit) {
        val finished = AtomicBoolean(false)
        val finish: (String?, Boolean) -> Unit = { value, retryable ->
            if (finished.compareAndSet(false, true)) {
                try { connection.endConnection() } catch (_: Exception) { }
                callback(value, retryable)
            }
        }
        val timeout = scheduler.schedule({ finish(null, true) }, timeoutMillis, TimeUnit.MILLISECONDS)
        try {
            connection.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            try { finish(connection.installReferrer.installReferrer, false) }
                            catch (_: Exception) { finish(null, true) }
                        }
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> finish(null, false)
                        else -> finish(null, true)
                    }
                    timeout.cancel(false)
                }
                override fun onInstallReferrerServiceDisconnected() {
                    finish(null, true)
                    timeout.cancel(false)
                }
            })
        } catch (_: Exception) {
            finish(null, true)
            timeout.cancel(false)
        }
    }
}
