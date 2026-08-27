package dev.soranerai.simhide.policy

import android.os.FileObserver
import dev.soranerai.simhide.model.AppSimPolicy
import dev.soranerai.simhide.model.SimHideConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val policyFile = File("/data/system/simhide/policy.json")
private val phonePolicyFile = File("/data/user_de/0/com.android.phone/files/simhide_policy.json")

data class PolicySnapshot(val revision: Long, val config: SimHideConfig) {
    private val policiesByUid = config.appPolicies.associateBy { it.uid }
    private val profilesById = config.profiles.associateBy { it.id }

    fun policyForUid(uid: Int): AppSimPolicy? = policiesByUid[uid]
    fun profileFor(policy: AppSimPolicy) = policy.profileId?.let(profilesById::get)

    companion object { val EMPTY = PolicySnapshot(0, SimHideConfig()) }
}

/** Owns the system_server policy mirror. */
object SystemServerPolicyBridge {
    private val installed = AtomicBoolean(false)
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var observer: FileObserver? = null

    fun current(): PolicySnapshot = snapshot

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        reload()
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

}

/** Used in com.android.phone. It reads only its radio-labelled DE policy mirror. */
object PhoneProcessPolicyBridge {
    private val installed = AtomicBoolean(false)
    @Volatile private var snapshot = PolicySnapshot.EMPTY
    @Volatile private var observer: FileObserver? = null

    fun current(): PolicySnapshot = snapshot

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        reload()
        val directory = phonePolicyFile.parent ?: return
        observer = object : FileObserver(directory, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == phonePolicyFile.name) reload()
            }
        }.also(FileObserver::startWatching)
    }

    private fun reload() {
        val candidate = runCatching { SimPolicyCodec.decode(phonePolicyFile.readText()) }.getOrNull() ?: return
        snapshot = PolicySnapshot(snapshot.revision + 1, candidate)
    }
}
