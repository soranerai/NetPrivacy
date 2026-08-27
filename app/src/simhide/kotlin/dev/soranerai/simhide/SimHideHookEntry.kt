package dev.soranerai.simhide

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.soranerai.simhide.hooks.TargetTelephonyHooks
import dev.soranerai.simhide.policy.TargetProcessPolicyBridge

/** Installs hooks only inside applications explicitly selected in LSPosed scope. */
class SimHideHookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE || lpparam.packageName == "android") return

        SimHideLog.info("installing app-only hooks for ${lpparam.packageName} (${lpparam.processName})")
        TargetTelephonyHooks.install(lpparam.classLoader)
        XposedBridge.hookAllMethods(Application::class.java, "attach", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = param.args.firstOrNull() as? Context ?: return
                TargetProcessPolicyBridge.attach(context, lpparam.packageName)
            }
        })
    }

    private companion object {
        const val MODULE_PACKAGE = "dev.soranerai.simhide"
    }
}
