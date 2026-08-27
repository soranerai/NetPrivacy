package dev.soranerai.simhide

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.soranerai.simhide.hooks.PhoneTelephonyHooks
import dev.soranerai.simhide.hooks.SystemServerSubscriptionHooks
import dev.soranerai.simhide.policy.PhoneProcessPolicyBridge
import dev.soranerai.simhide.policy.SystemServerPolicyBridge

/** Installs the policy bridge before individual telephony hooks. */
class SimHideHookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.processName) {
            "android" -> {
                SystemServerPolicyBridge.install()
                SystemServerSubscriptionHooks.install(lpparam.classLoader)
            }
            // This process obtains snapshots through the UID-gated policy provider.
            "com.android.phone" -> {
                PhoneProcessPolicyBridge.install()
                PhoneTelephonyHooks.install(lpparam.classLoader)
            }
        }
    }
}
