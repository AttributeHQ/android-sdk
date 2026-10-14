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
    @Volatile private var deviceId: String? = null
    private var installReferrer: String? = null
    private var session: SessionManager? = null
    private var queue: EventQueue? = null
    private var evidenceUploader: EvidenceUploader? = null
    private var reachability: Reachability? = null
    @Volatile private var privacy: PrivacyState? = null
    private var appContext: Context? = null
    private var isInitialized = false
    private var installInFlight = false
    private var referrerReady = false

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
        val resolved = ResolvedConfiguration.resolve(configuration)
        val initialCollection = configuration.initialCollectionEnabled
        val initialSharing = configuration.initialAdvertisingSharing
        val initialTracking = configuration.initialApplicationTrackingEnabled
        executor.execute {
            if (isInitialized) {
                Logger.warn("SDK already initialized")
                return@execute
            }

            this.config = resolved

            Logger.setEnabled(resolved.enableDebugLogging)
            Logger.debug("Initializing SDK v$SDK_VERSION")

            val appContext = context.applicationContext

            // Initialize storage
            val storage = Storage.initialize(appContext, resolved.appId)

            this.appContext = appContext
            val state = PrivacyState(storage)
            privacy = state
            initialCollection?.let { state.setEnabled(it) }
            initialSharing?.let { state.setSharing(it) }
            initialTracking?.let { storage.setBool("application_tracking_enabled", it) }
            if (!state.enabled) storage.setCollectionEnabled(false)
            if (state.enabled) startCollection(appContext, storage)

            // App lifecycle observer
            setupLifecycleObserver(appContext, storage)
            executor.scheduleWithFixedDelay({
                trackInstallIfNeeded(appContext, storage)
                if (isCollectionEnabled()) evidenceUploader?.flush()
            },
                30, 30, java.util.concurrent.TimeUnit.SECONDS)

            isInitialized = true
            Logger.debug("SDK initialized successfully")
        }
    }

    private fun startCollection(context: Context, storage: Storage) {
        val state = privacy ?: return
        val ticket = state.ticket() ?: return
        val resolved = config ?: return
        deviceId = getOrCreateDeviceId(storage) ?: return
        if (resolved.enableSandboxEvidenceUpload && evidenceUploader == null) {
            evidenceUploader = EvidenceUploader(storage, resolved.appId, deviceId!!,
                dispatch = { task -> executor.execute(task) }, send = { body, acknowledge ->
                    val permission = state.ticket()
                    if (permission == null) acknowledge(null)
                    else NetworkClient.post("/v1/mobile-evidence", body, resolved,
                        maySend = { state.permits(permission) }) { result ->
                        val id = result.getOrNull()?.optJSONObject("data")?.opt("observation_id") as? String
                        acknowledge(if (state.permits(permission)) id else null)
                    }
                })
        }
        refreshEvidenceInternal()
        session = SessionManager(storage)
        reachability = Reachability.initialize(context)
        if (queue == null) {
            queue = EventQueue(resolved, storage, executor).also { eventQueue ->
                eventQueue.onFlush = { events, completion -> sendEvents(events, completion) }
                eventQueue.setupReachabilityListener()
            }
        } else queue?.resume()
        referrerReady = false
        installInFlight = false
        InstallReferrerManager.getInstallReferrer(context, executor) { referrer ->
            executor.execute {
                if (!state.permits(ticket)) return@execute
                if (!referrer.isNullOrBlank()) storage.setString("install_referrer", referrer)
                installReferrer = referrer?.takeIf { it.isNotBlank() } ?: storage.getString("install_referrer")
                referrerReady = true
                trackInstallIfNeeded(context, storage)
            }
        }
    }

    /**
     * Apply collection choice in SDK call order. Disabling discards pending events
     * and local user/session state. Requests already started may finish; this is
     * not server erasure. Completion runs on the main thread after local application
     * and reports whether the preference was persisted. A failed write stays disabled
     * for this process. Call only after initialize().
     */
    fun setCollectionEnabled(enabled: Boolean, completion: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val state = privacy
            val context = appContext
            var saved = false
            if (isInitialized && state != null && context != null) {
                if (enabled && state.enabled) saved = true
                else {
                    // Clear memory first so a late transport acknowledgement cannot restore it.
                    queue?.suspendAndClear()
                    val purged = evidenceUploader?.discardPending() ?: true
                    saved = state.setEnabled(enabled) && purged
                    session = null
                    installInFlight = false
                    referrerReady = false
                    if (state.enabled) startCollection(context, Storage.getInstance())
                }
            }
            completion?.let { callback ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(saved) }
            }
        }
    }

    /** False before initialization and while collection is disabled. */
    fun isCollectionEnabled(): Boolean = privacy?.enabled == true

    /** Persist preference and report a fresh observation when sandbox evidence upload is enabled. */
    fun setAdvertisingSharing(preference: AdvertisingSharing, completion: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val saved = if (isInitialized) privacy?.setSharing(preference) == true else false
            if (saved) refreshEvidenceInternal()
            completion?.let { callback ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(saved) }
            }
        }
    }

    fun getAdvertisingSharing(): AdvertisingSharing = privacy?.sharing ?: AdvertisingSharing.UNKNOWN

    /** App-level decision, not an Android system permission or an advertising identifier. */
    fun setApplicationTrackingEnabled(enabled: Boolean, completion: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val saved = if (isInitialized) Storage.getInstance().setBool("application_tracking_enabled", enabled) else false
            if (saved) refreshEvidenceInternal()
            completion?.let { callback -> android.os.Handler(android.os.Looper.getMainLooper()).post { callback(saved) } }
        }
    }

    fun refreshConversionEvidence() { executor.execute { refreshEvidenceInternal() } }

    private fun captureEvidence(): JSONObject {
        val tracking = try { Storage.getInstance().getBool("application_tracking_enabled") } catch (_: Exception) { false }
        return ConversionEvidenceCollector.capture(appContext!!, tracking, privacy!!.sharing)
    }

    private fun refreshEvidenceInternal() {
        if (!isCollectionEnabled()) return
        val uploader = evidenceUploader ?: return
        val context = try { captureEvidence() } catch (_: Exception) { null }
        uploader.observe(context)
    }

    /** Capture immutable event-time evidence, not a conversion or delivery grant.
     * Retry the same eventId to retrieve the original ticket. Requires explicit sandbox opt-in.
     */
    fun prepareConversionEvidence(eventId: String, completion: (Result<ConversionEvidenceTicket>) -> Unit) {
        executor.execute {
            val result = runCatching {
                check(isCollectionEnabled()) { "Collection disabled" }
                val uploader = evidenceUploader ?: error("Sandbox evidence upload is not enabled")
                uploader.ticket(eventId) ?: uploader.retain(eventId, captureEvidence())
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { completion(result) }
        }
    }

    /** Call only after your authenticated backend durably accepts the event ticket. */
    fun completeConversionEvidence(eventId: String, completion: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val completed = isCollectionEnabled() && evidenceUploader?.complete(eventId) == true
            completion?.let { callback -> android.os.Handler(android.os.Looper.getMainLooper()).post { callback(completed) } }
        }
    }

    /**
     * Track a custom event.
     *
     * @param eventName Event name (e.g., "purchase", "signup", "level_up")
     * @param properties Optional key-value properties
     */
    fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        require(eventName.isNotBlank() && eventName.length <= 255) { "Event name must contain 1–255 characters" }
        val snapshot = JSONObject(properties.toJSONObject().toString()).toMap()
        executor.execute {
            if (!ensureInitialized()) return@execute

            val event = QueuedEvent(
                eventName = eventName,
                properties = snapshot,
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
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val snapshot = JSONObject(properties.toJSONObject().toString()).toMap()
        executor.execute {
            if (!ensureInitialized()) return@execute

            Storage.getInstance().setString("user_id", userId)

            val props = snapshot.toMutableMap()
            props["user_id"] = userId
            queue?.enqueue(QueuedEvent("identify", props, System.currentTimeMillis(), session!!.getSessionId()))

            Logger.debug("User identified")
        }
    }

    /** App-scoped installation ID; null until asynchronous initialization completes. */
    fun getAppInstanceId(): String? = if (isCollectionEnabled()) deviceId else null

    /** Manually flush all queued events to the server. */
    fun flush() {
        executor.execute {
            if (!ensureInitialized()) return@execute
            queue?.flush()
            evidenceUploader?.flush()
        }
    }

    /**
     * Get the cached attribution result from the initial install tracking.
     *
     * @return Attribution result, or null if install hasn't been tracked yet
     */
    fun getAttribution(): AttributionResult? {
        if (!isCollectionEnabled()) return null
        val storage = try { Storage.getInstance() } catch (_: Exception) { return null }
        val jsonStr = storage.getJsonString("attribution") ?: return null
        return try {
            AttributionResult.fromJson(JSONObject(jsonStr))
        } catch (_: Exception) {
            null
        }
    }

    // region Private: Device ID

    private fun getOrCreateDeviceId(storage: Storage): String? {
        // Try stored device ID
        val existing = storage.getString("device_id")
        if (existing != null) return existing

        // Generate new UUID and persist
        val uuid = UUID.randomUUID().toString()
        return uuid.takeIf { storage.setString("device_id", it) }
    }

    // endregion

    // region Private: Install Tracking

    private fun trackInstallIfNeeded(context: Context, storage: Storage) {
        val state = privacy ?: return
        val ticket = state.ticket() ?: return
        if (!referrerReady || installInFlight) return
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
            installReferrer = installReferrer
        ).toJson()

        installInFlight = true
        NetworkClient.post(path = ApiContract.INSTALLS_PATH, body = body, config = config,
            maySend = { state.permits(ticket) }) { result ->
            executor.execute {
                if (!state.permits(ticket)) return@execute
                installInFlight = false
                val data = ApiContract.installAttribution(result.getOrNull())
                if (data != null) {
                    if (storage.setJsonString("attribution", data.toString())) {
                        storage.setBool("installed", true)
                    }
                } else {
                    Logger.warn("Install was not acknowledged; will retry")
                }
            }
        }
    }

    // endregion

    // region Private: Event Sending

    private fun sendEvents(events: List<QueuedEvent>, completion: (Boolean) -> Unit) {
        val state = privacy ?: run { completion(false); return }
        val ticket = state.ticket() ?: run { completion(false); return }
        val config = config ?: run { completion(false); return }
        val deviceId = deviceId ?: run { completion(false); return }

        val body = EventsBatchRequest(
            appId = config.appId,
            deviceId = deviceId,
            events = events,
            sdkVersion = SDK_VERSION
        ).toJson()

        NetworkClient.post(
            path = ApiContract.EVENTS_PATH,
            body = body,
            config = config,
            maySend = { state.permits(ticket) }
        ) { result ->
            completion(state.permits(ticket) && ApiContract.acceptedEvents(result.getOrNull(), events.size))
        }
    }

    // endregion

    // region Private: Lifecycle

    private fun setupLifecycleObserver(context: Context, storage: Storage) {
        try {
            // Must be called on main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) {
                            executor.execute { trackInstallIfNeeded(context, storage); refreshEvidenceInternal() }
                        }
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
        return isCollectionEnabled() && session != null
    }

    // endregion
}
