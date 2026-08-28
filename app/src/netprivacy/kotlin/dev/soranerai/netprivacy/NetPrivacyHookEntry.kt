package dev.soranerai.netprivacy

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.soranerai.netprivacy.hooks.TargetTelephonyHooks
import dev.soranerai.netprivacy.hooks.TargetWifiHooks
import dev.soranerai.netprivacy.policy.TargetProcessPolicyBridge

/** Installs hooks only inside applications explicitly selected in LSPosed scope. */
class NetPrivacyHookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE || lpparam.packageName == "android") return

        NetPrivacyLog.info("installing app-only hooks for ${lpparam.packageName} (${lpparam.processName})")
        TargetTelephonyHooks.install(lpparam.classLoader)
        TargetWifiHooks.install(lpparam.classLoader)
        XposedBridge.hookAllMethods(Application::class.java, "attach", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = param.args.firstOrNull() as? Context ?: return
                TargetProcessPolicyBridge.attach(context, lpparam.packageName)
            }
        })
    }

    private companion object {
        const val MODULE_PACKAGE = "dev.soranerai.netprivacy"
    }
}
