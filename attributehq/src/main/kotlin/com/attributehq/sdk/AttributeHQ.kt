package com.attributehq.sdk

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * AttributeHQ Android SDK — mobile attribution tracking.
 *
 * Usage:
 * ```kotlin
 * val config = AttributeHQConfiguration(apiKey = "ak_xxx", appId = "your-uuid")
 * AttributeHQ.initialize(context, config)
 *
 * AttributeHQ.track("purchase", mapOf("amount" to 1500))
 * AttributeHQ.trackRevenue(1500.0, "NGN")
 * ```
 */
object AttributeHQ {

    private var config: ResolvedConfiguration? = null
    private var deviceId: String? = null
    private var gaid: String? = null
    private var installReferrer: String? = null
    private var session: SessionManager? = null
    private var queue: EventQueue? = null
    private var reachability: Reachability? = null
    private var isInitialized = false

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "attributehq-sdk").apply { isDaemon = true }
        }

    /**
     * Initialize the SDK. Must be called before any other method.
     * Typically called in `Application.onCreate()` or `Activity.onCreate()`.
     *
     * @param context Application or Activity context
     * @param configuration SDK configuration with apiKey and appId
     * @throws IllegalArgumentException if config is invalid
     */
    fun initialize(context: Context, configuration: AttributeHQConfiguration) {
        executor.execute {
            if (isInitialized) {
                Logger.warn("SDK already initialized")
                return@execute
            }

            val resolved = ResolvedConfiguration.resolve(configuration)
            this.config = resolved

            Logger.setEnabled(resolved.enableDebugLogging)
            Logger.debug("Initializing SDK v$SDK_VERSION")

            val appContext = context.applicationContext

            // Initialize storage
            val storage = Storage.initialize(appContext)

            // Get or create persistent device ID
            this.deviceId = getOrCreateDeviceId(storage)
            Logger.debug("Device ID: ${this.deviceId}")

            // Fetch GAID async
            GAIDManager.getGAID(appContext, executor) { fetchedGaid ->
                this.gaid = fetchedGaid
                if (fetchedGaid != null) {
                    this.deviceId = fetchedGaid
                    storage.setString("device_id", fetchedGaid)
                    Logger.debug("Device ID updated to GAID: $fetchedGaid")
                }
            }

            // Fetch Install Referrer async
            InstallReferrerManager.getInstallReferrer(appContext) { referrer ->
                this.installReferrer = referrer
            }

            // Initialize session manager
            this.session = SessionManager(storage)

            // Initialize reachability
            this.reachability = Reachability.initialize(appContext)

            // Initialize event queue
            val eventQueue = EventQueue(resolved, storage, executor)
            eventQueue.onFlush = { events, completion ->
                sendEvents(events, completion)
            }
            eventQueue.setupReachabilityListener()
            this.queue = eventQueue

            // Track install on first launch
            trackInstallIfNeeded(appContext, storage)

            // App lifecycle observer
            setupLifecycleObserver()

            isInitialized = true
            Logger.debug("SDK initialized successfully")
        }
    }

    /**
     * Track a custom event.
     *
     * @param eventName Event name (e.g., "purchase", "signup", "level_up")
     * @param properties Optional key-value properties
     */
    fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        executor.execute {
            if (!ensureInitialized()) return@execute

            val event = QueuedEvent(
                eventName = eventName,
                properties = properties,
                timestamp = System.currentTimeMillis(),
                sessionId = session!!.getSessionId()
            )

            queue?.enqueue(event)
        }
    }

    /**
     * Track a revenue event.
     *
     * @param amount Revenue amount
     * @param currency Currency code (default: "NGN")
     * @param properties Optional additional properties
     */
    fun trackRevenue(
        amount: Double,
        currency: String = "NGN",
        properties: Map<String, Any?> = emptyMap()
    ) {
        val props = properties.toMutableMap()
        props["amount"] = amount
        props["currency"] = currency
        track("revenue", props)
    }

    /**
     * Identify a user (e.g., after login).
     *
     * @param userId User identifier
     * @param properties Optional user properties
     */
    fun identify(userId: String, properties: Map<String, Any?> = emptyMap()) {
        executor.execute {
            if (!ensureInitialized()) return@execute

            Storage.getInstance().setString("user_id", userId)

            val props = properties.toMutableMap()
            props["user_id"] = userId
            track("identify", props)

            Logger.debug("User identified: $userId")
        }
    }

    /** Manually flush all queued events to the server. */
    fun flush() {
        executor.execute {
            if (!ensureInitialized()) return@execute
            queue?.flush()
        }
    }

    /**
     * Get the cached attribution result from the initial install tracking.
     *
     * @return Attribution result, or null if install hasn't been tracked yet
     */
    fun getAttribution(): AttributionResult? {
        val storage = try { Storage.getInstance() } catch (_: Exception) { return null }
        val jsonStr = storage.getJsonString("attribution") ?: return null
        return try {
            AttributionResult.fromJson(JSONObject(jsonStr))
        } catch (_: Exception) {
            null
        }
    }

    // region Private: Device ID

    private fun getOrCreateDeviceId(storage: Storage): String {
        // Try stored device ID
        val existing = storage.getString("device_id")
        if (existing != null) return existing

        // Generate new UUID and persist
        val uuid = UUID.randomUUID().toString()
        storage.setString("device_id", uuid)
        return uuid
    }

    // endregion

    // region Private: Install Tracking

    private fun trackInstallIfNeeded(context: Context, storage: Storage) {
        if (storage.getBool("installed")) {
            Logger.debug("Install already tracked")
            return
        }

        val config = config ?: return
        val deviceId = deviceId ?: return

        Logger.debug("Tracking install...")

        val deviceInfo = DeviceInfo.collect(context)

        val body = InstallRequestBody(
            appId = config.appId,
            deviceId = deviceId,
            sdkVersion = SDK_VERSION,
            osVersion = deviceInfo.osVersion,
            deviceModel = deviceInfo.deviceModel,
            screenResolution = deviceInfo.screenResolution,
            timezone = deviceInfo.timezone,
            language = deviceInfo.language,
            gaid = gaid,
            installReferrer = installReferrer
        ).toJson()

        NetworkClient.post(
            path = "/api/v1/installs",
            body = body,
            config = config
        ) { result ->
            result.onSuccess { response ->
                storage.setBool("installed", true)

                // Cache attribution result
                val dataJson = response.optJSONObject("data")
                if (dataJson != null) {
                    storage.setJsonString("attribution", dataJson.toString())
                    val attribution = AttributionResult.fromJson(dataJson)
                    Logger.debug("Install tracked: ${attribution.matchType.value} (${attribution.confidence}%)")
                } else {
                    Logger.debug("Install tracked (no attribution data in response)")
                }
            }.onFailure { error ->
                // Don't set installed flag — will retry on next launch
                Logger.warn("Install tracking failed: ${error.message}")
            }
        }
    }

    // endregion

    // region Private: Event Sending

    private fun sendEvents(events: List<QueuedEvent>, completion: (Boolean) -> Unit) {
        val config = config ?: run { completion(false); return }
        val deviceId = deviceId ?: run { completion(false); return }

        val body = EventsBatchRequest(
            appId = config.appId,
            deviceId = deviceId,
            events = events,
            sdkVersion = SDK_VERSION
        ).toJson()

        NetworkClient.post(
            path = "/api/v1/events",
            body = body,
            config = config
        ) { result ->
            completion(result.isSuccess)
        }
    }

    // endregion

    // region Private: Lifecycle

    private fun setupLifecycleObserver() {
        try {
            // Must be called on main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onStop(owner: LifecycleOwner) {
                            Logger.debug("App entering background, flushing events...")
                            executor.execute { queue?.flush() }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Logger.error("Failed to set up lifecycle observer", e)
        }
    }

    // endregion

    // region Private: Guard

    private fun ensureInitialized(): Boolean {
        if (!isInitialized) {
            Logger.warn("SDK not initialized. Call initialize() first.")
            return false
        }
        return true
    }

    // endregion
}
