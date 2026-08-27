package dev.soranerai.simhide.policy

import android.os.Binder
import android.os.FileObserver
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import dev.soranerai.simhide.model.AppSimPolicy
import dev.soranerai.simhide.model.SimHideConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

const val POLICY_SERVICE_NAME = "simhide_policy"
private const val DESCRIPTOR = "dev.soranerai.simhide.IPolicyBridge"
private const val GET_SNAPSHOT = IBinder.FIRST_CALL_TRANSACTION
private const val PHONE_UID = 1001
private val policyFile = File("/data/system/simhide/policy.json")

data class PolicySnapshot(val revision: Long, val config: SimHideConfig) {
    private val policiesByUid = config.appPolicies.associateBy { it.uid }
    private val profilesById = config.profiles.associateBy { it.id }

    fun policyForUid(uid: Int): AppSimPolicy? = policiesByUid[uid]
    fun profileFor(policy: AppSimPolicy) = policy.profileId?.let(profilesById::get)

    companion object { val EMPTY = PolicySnapshot(0, SimHideConfig()) }
}

/** Owns the only filesystem read. Call exclusively from system_server. */
object SystemServerPolicyBridge {
    private val installed = AtomicBoolean(false)
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var observer: FileObserver? = null

    fun current(): PolicySnapshot = snapshot

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        reload()
        registerService(PolicyBridgeBinder(::current))
        observer = object : FileObserver(policyFile.parent ?: "/data/system/simhide", CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == policyFile.name) reload()
            }
        }.also(FileObserver::startWatching)
    }

    private fun reload() {
        val candidate = runCatching { SimPolicyCodec.decode(policyFile.readText()) }.getOrNull() ?: return
        snapshot = PolicySnapshot(snapshot.revision + 1, candidate)
    }

    private fun registerService(service: IBinder) {
        val clazz = Class.forName("android.os.ServiceManager")
        val method = clazz.declaredMethods.firstOrNull {
            it.name == "addService" && it.parameterTypes.size >= 2 && it.parameterTypes[0] == String::class.java && IBinder::class.java.isAssignableFrom(it.parameterTypes[1])
        } ?: error("ServiceManager.addService недоступен")
        val args = method.parameterTypes.mapIndexed { index, type ->
            when (index) {
                0 -> POLICY_SERVICE_NAME
                1 -> service
                else -> when (type) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
        }.toTypedArray()
        method.isAccessible = true
        method.invoke(null, *args)
    }
}

private class PolicyBridgeBinder(private val current: () -> PolicySnapshot) : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
        if (code != GET_SNAPSHOT) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        check(Binder.getCallingUid() == Process.SYSTEM_UID || Binder.getCallingUid() == PHONE_UID) { "Unauthorized policy client" }
        val snapshot = current()
        reply?.writeNoException()
        reply?.writeLong(snapshot.revision)
        reply?.writeString(SimPolicyCodec.encode(snapshot.config))
        return true
    }
}

/** Used in com.android.phone. It polls at most once a second and never reads /data/system. */
object PhoneProcessPolicyBridge {
    private const val POLL_INTERVAL_MS = 1_000L
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var lastPollAt = 0L
    @Volatile private var service: IBinder? = null

    fun current(): PolicySnapshot {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPollAt >= POLL_INTERVAL_MS) synchronized(this) {
            if (now - lastPollAt >= POLL_INTERVAL_MS) {
                lastPollAt = now
                fetch()?.let { snapshot = it }
            }
        }
        return snapshot
    }

    private fun fetch(): PolicySnapshot? = runCatching {
        val binder = service?.takeIf { it.isBinderAlive } ?: lookupService().also { service = it }
        val request = Parcel.obtain(); val response = Parcel.obtain()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            check(binder.transact(GET_SNAPSHOT, request, response, 0)) { "Policy service unavailable" }
            response.readException()
            PolicySnapshot(response.readLong(), SimPolicyCodec.decode(response.readString().orEmpty()))
        } finally { request.recycle(); response.recycle() }
    }.getOrNull()

    private fun lookupService(): IBinder {
        val clazz = Class.forName("android.os.ServiceManager")
        val method = clazz.getDeclaredMethod("getService", String::class.java).apply { isAccessible = true }
        return method.invoke(null, POLICY_SERVICE_NAME) as? IBinder ?: error("Policy service unavailable")
    }
}
