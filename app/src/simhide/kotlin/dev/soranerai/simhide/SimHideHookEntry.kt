package dev.soranerai.simhide

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.soranerai.simhide.policy.PhoneProcessPolicyBridge
import dev.soranerai.simhide.policy.SystemServerPolicyBridge

/** Installs the policy bridge before individual telephony hooks. */
class SimHideHookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.processName) {
            "android" -> SystemServerPolicyBridge.install()
            // This process is intentionally a Binder client: it must never read /data/system.
            "com.android.phone" -> PhoneProcessPolicyBridge.current()
        }
    }
}
