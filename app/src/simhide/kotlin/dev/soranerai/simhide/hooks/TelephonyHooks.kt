package dev.soranerai.simhide.hooks

import android.os.Binder
import android.telephony.TelephonyManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import dev.soranerai.simhide.model.SimNetworkType
import dev.soranerai.simhide.model.SimVisibilityMode
import dev.soranerai.simhide.policy.PhoneProcessPolicyBridge
import dev.soranerai.simhide.policy.PolicySnapshot
import dev.soranerai.simhide.policy.SystemServerPolicyBridge

/** Version-tolerant hooks: absent OEM methods are skipped rather than breaking telephony. */
object PhoneTelephonyHooks {
    fun install(classLoader: ClassLoader) {
        listOf("com.android.phone.PhoneInterfaceManager", "com.android.phone.PhoneSubInfoController").forEach { name ->
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()?.let(::installForClass)
        }
    }

    private fun installForClass(clazz: Class<*>) {
        hookStrings(clazz, setOf("getNetworkOperatorForPhone", "getNetworkOperatorForSubscriber")) { it.operatorNumeric() }
        hookStrings(clazz, setOf("getNetworkOperatorName", "getNetworkOperatorNameForPhone", "getSimOperatorNameForPhone")) { it.profile?.operatorName.orEmpty() }
        hookStrings(clazz, setOf("getNetworkCountryIsoForPhone", "getNetworkCountryIsoForSubscriber", "getSimCountryIsoForPhone")) { it.profile?.countryIso.orEmpty() }
        hookInts(clazz, setOf("getDataNetworkType", "getDataNetworkTypeForSubscriber", "getVoiceNetworkTypeForSubscriber")) { it.networkType() }
        hookBooleans(clazz, setOf("isNetworkRoaming", "isNetworkRoamingForSubscriber")) { it.profile?.roaming ?: false }
        hookInts(clazz, setOf("getSimStateForSlotIndex", "getSimState")) { TelephonyManager.SIM_STATE_ABSENT }
        hookCellObjects(clazz, setOf("getAllCellInfo", "getAllCellInfoForSubscriber")) { emptyList<Any>() }
        hookCellObjects(clazz, setOf("getCellLocation", "getCellLocationForSubscriber")) { null }
        hookIdentifierObjects(clazz, setOf("getSubscriberIdForSubscriber", "getSubscriberId", "getIccSerialNumberForSubscriber", "getIccSerialNumber", "getLine1NumberForSubscriber", "getLine1Number")) { null }
    }

    private fun hookStrings(clazz: Class<*>, names: Set<String>, result: (EffectivePolicy) -> String) = hook(clazz, names) { policy, param ->
        if (policy.filters.operator) param.result = result(policy)
    }

    private fun hookInts(clazz: Class<*>, names: Set<String>, result: (EffectivePolicy) -> Int) = hook(clazz, names) { policy, param ->
        if (policy.filters.operator || policy.mode == SimVisibilityMode.HIDE) param.result = result(policy)
    }

    private fun hookBooleans(clazz: Class<*>, names: Set<String>, result: (EffectivePolicy) -> Boolean) = hook(clazz, names) { policy, param ->
        if (policy.filters.operator) param.result = result(policy)
    }

    private fun hookCellObjects(clazz: Class<*>, names: Set<String>, result: (EffectivePolicy) -> Any?) = hook(clazz, names) { policy, param ->
        if (policy.filters.cellInfo) param.result = result(policy)
    }

    private fun hookIdentifierObjects(clazz: Class<*>, names: Set<String>, result: (EffectivePolicy) -> Any?) = hook(clazz, names) { policy, param ->
        if (policy.filters.identifiers) param.result = result(policy)
    }

    private fun hook(clazz: Class<*>, names: Set<String>, change: (EffectivePolicy, XC_MethodHook.MethodHookParam) -> Unit) {
        names.forEach { method -> XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) { effectivePolicy(PhoneProcessPolicyBridge.current())?.let { change(it, param) } }
        }) }
    }
}

object SystemServerSubscriptionHooks {
    fun install(classLoader: ClassLoader) {
        // Android versions/OEMs move the ISub implementation; only hook classes actually in system_server.
        listOf("com.android.server.telephony.subscription.SubscriptionManagerService", "com.android.server.telephony.SubscriptionManagerService").forEach { name ->
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()?.let { clazz ->
                setOf("getActiveSubscriptionInfoList", "getCompleteActiveSubscriptionInfoList", "getAvailableSubscriptionInfoList", "getActiveSubscriptionInfoCount").forEach { method ->
                    XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val policy = effectivePolicy(SystemServerPolicyBridge.current()) ?: return
                            if (policy.mode != SimVisibilityMode.HIDE || !policy.filters.subscription) return
                            param.result = if (param.result is Number) 0 else emptyList<Any>()
                        }
                    })
                }
            }
        }
    }
}

private data class EffectivePolicy(
    val mode: SimVisibilityMode,
    val filters: dev.soranerai.simhide.model.SimFilterSet,
    val profile: dev.soranerai.simhide.model.SimProfile?,
) {
    fun operatorNumeric() = if (mode == SimVisibilityMode.HIDE) "" else "${profile?.mcc.orEmpty()}${profile?.mnc.orEmpty()}"
    fun networkType() = when (profile?.networkType) {
        SimNetworkType.GSM -> TelephonyManager.NETWORK_TYPE_GPRS
        SimNetworkType.UMTS -> TelephonyManager.NETWORK_TYPE_UMTS
        SimNetworkType.NR -> TelephonyManager.NETWORK_TYPE_NR
        else -> TelephonyManager.NETWORK_TYPE_LTE
    }
}

private fun effectivePolicy(snapshot: PolicySnapshot): EffectivePolicy? {
    val policy = snapshot.policyForUid(Binder.getCallingUid()) ?: return null
    if (policy.mode == SimVisibilityMode.PASSTHROUGH) return null
    return EffectivePolicy(policy.mode, policy.filters, snapshot.profileFor(policy))
}
