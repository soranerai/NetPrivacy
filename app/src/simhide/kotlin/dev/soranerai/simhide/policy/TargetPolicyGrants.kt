package dev.soranerai.simhide.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.soranerai.simhide.data.SimConfigStore

/** Maintains the narrow URI visibility grants required by Android 11+. */
internal class TargetPolicyGrants(context: Context) {
    private val context = context.applicationContext
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

    fun sync(previousPackages: Set<String>, currentPackages: Set<String>): Result<Unit> = runCatching {
        currentPackages.toSet().let { activePackages ->
        previousPackages.asSequence()
            .filterNot(activePackages::contains)
            .forEach { context.revokeUriPermission(it, PolicyProvider.POLICY_URI, flags) }

        activePackages.forEach {
            context.grantUriPermission(it, PolicyProvider.POLICY_URI, flags)
        }
        }
    }
}

/** Restores non-persisted URI grants before ordinary applications start. */
class PolicyGrantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SimConfigStore(context).restoreTargetGrants()
    }
}
