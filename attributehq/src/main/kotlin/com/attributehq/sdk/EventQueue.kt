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
    private val scheduler: ScheduledExecutorService
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
    fun enqueue(event: QueuedEvent) {
        queue.add(event)

        // Circular buffer: drop oldest when over limit
        while (queue.size > config.maxQueueSize) {
            queue.removeFirst()
        }

        persist()
        Logger.debug("Event queued: ${event.eventName} (${queue.size} pending)")

        // Auto-flush if batch size reached
        if (queue.size >= config.batchSize) {
            flush()
        }
    }

    /** Flush pending events to the server. */
    fun flush(completion: (() -> Unit)? = null) {
        if (isFlushing || queue.isEmpty()) {
            completion?.invoke()
            return
        }

        val reachability = Reachability.getInstance()
        if (reachability != null && !reachability.isReachable) {
            Logger.debug("Offline, skipping flush (${queue.size} events queued)")
            completion?.invoke()
            return
        }

        isFlushing = true

        val batchCount = minOf(config.batchSize, queue.size)
        val batch = queue.take(batchCount).toList()
        repeat(batchCount) { queue.removeFirst() }
        persist()

        Logger.debug("Flushing ${batch.size} events...")

        onFlush?.invoke(batch) { success ->
            if (!success) {
                // Re-queue failed events at the front
                queue.addAll(0, batch)

                // Trim if over max
                while (queue.size > config.maxQueueSize) {
                    queue.removeLast()
                }

                persist()
                Logger.warn("Flush failed, ${batch.size} events re-queued")
            } else {
                Logger.debug("Flush succeeded: ${batch.size} events sent")
            }

            isFlushing = false
            completion?.invoke()
        } ?: run {
            // No onFlush handler, re-queue
            queue.addAll(0, batch)
            persist()
            isFlushing = false
            completion?.invoke()
        }
    }

    /** Number of events currently in the queue. */
    val count: Int get() = queue.size

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

    private fun persist() {
        try {
            val jsonArray = JSONArray()
            queue.forEach { jsonArray.put(it.toJson()) }
            storage.setJsonString("event_queue", jsonArray.toString())
        } catch (e: Exception) {
            Logger.error("Failed to persist event queue: ${e.message}")
        }
    }

    private fun restore() {
        try {
            val jsonStr = storage.getJsonString("event_queue") ?: return
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                queue.add(QueuedEvent.fromJson(jsonArray.getJSONObject(i)))
            }
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
