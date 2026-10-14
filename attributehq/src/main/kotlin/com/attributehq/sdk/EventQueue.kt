package com.attributehq.sdk

import org.json.JSONArray
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Event queue with batching, offline persistence, and auto-flush.
 * Mirrors the iOS SDK's EventQueue pattern.
 */
internal class EventQueue(
    private val config: ResolvedConfiguration,
    private val storage: Storage,
    private val scheduler: ScheduledExecutorService,
    private val isOnline: () -> Boolean = { Reachability.getInstance()?.isReachable ?: true }
) {
    private val queue = mutableListOf<QueuedEvent>()
    private var isFlushing = false
    private var flushTimerFuture: ScheduledFuture<*>? = null

    /**
     * Called when events need to be sent to the server.
     * Implementation should call the success callback with true/false.
     */
    var onFlush: ((List<QueuedEvent>, (Boolean) -> Unit) -> Unit)? = null

    init {
        restore()
        startFlushTimer()
    }

    /** Add an event to the queue. Auto-flushes when batch size is reached. */
    @Synchronized
    fun enqueue(event: QueuedEvent) {
        // Never evict an event whose delivery may already be in flight.
        if (queue.size >= config.maxQueueSize) {
            Logger.warn("Event queue full; new event was not queued")
            return
        }
        queue.add(QueuedEvent.fromJson(event.toJson()))

        persist()
        Logger.debug("Event queued: ${event.eventName} (${queue.size} pending)")

        // Auto-flush if batch size reached
        if (queue.size >= config.batchSize) {
            flush()
        }
    }

    /** Flush pending events to the server. */
    @Synchronized
    fun flush(completion: (() -> Unit)? = null) {
        if (isFlushing || queue.isEmpty()) {
            completion?.invoke()
            return
        }

        if (!isOnline()) {
            Logger.debug("Offline, skipping flush (${queue.size} events queued)")
            completion?.invoke()
            return
        }

        val sender = onFlush ?: run { completion?.invoke(); return }
        if (!persist()) { completion?.invoke(); return }
        isFlushing = true
        val batch = queue.take(config.batchSize).map { QueuedEvent.fromJson(it.toJson()) }
        val ids = batch.map { it.eventId }.toSet()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val acknowledge: (Boolean) -> Unit = { success ->
            if (completed.compareAndSet(false, true)) {
                synchronized(this) {
                    if (success) {
                        queue.removeAll { it.eventId in ids }
                        persist()
                    }
                    isFlushing = false
                }
                completion?.invoke()
            }
        }
        // Keep the complete batch on disk until the API acknowledges all items.
        try { sender(batch, acknowledge) }
        catch (_: Exception) { acknowledge(false) }
    }

    /** Number of events currently in the queue. */
    val count: Int get() = synchronized(this) { queue.size }

    /** Set up reachability listener to flush when network comes back. */
    fun setupReachabilityListener() {
        Reachability.getInstance()?.onReachabilityChanged = { isReachable ->
            if (isReachable) {
                Logger.debug("Network restored, flushing pending events")
                scheduler.execute { flush() }
            }
        }
    }

    /** Stop the flush timer. */
    fun stop() {
        flushTimerFuture?.cancel(false)
        flushTimerFuture = null
    }

    // region Private

    private fun startFlushTimer() {
        flushTimerFuture = scheduler.scheduleAtFixedRate(
            { flush() },
            config.flushIntervalSeconds,
            config.flushIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    private fun persist(): Boolean {
        try {
            val jsonArray = JSONArray()
            queue.forEach { jsonArray.put(it.toJson()) }
            val saved = storage.setJsonString("event_queue", jsonArray.toString())
            if (!saved) Logger.error("Failed to persist event queue")
            return saved
        } catch (e: Exception) {
            Logger.error("Failed to persist event queue")
            return false
        }
    }

    private fun restore() {
        try {
            val jsonStr = storage.getJsonString("event_queue") ?: return
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                queue.add(QueuedEvent.fromJson(jsonArray.getJSONObject(i)))
            }
            persist() // Persist IDs assigned to legacy events before any network retry.
            if (queue.isNotEmpty()) {
                Logger.debug("Restored ${queue.size} queued events")
            }
        } catch (e: Exception) {
            Logger.error("Failed to restore event queue: ${e.message}")
            queue.clear()
        }
    }

    // endregion
}
