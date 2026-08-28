package dev.soranerai.netprivacy.hooks

import android.telephony.TelephonyManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import dev.soranerai.netprivacy.NetPrivacyLog
import dev.soranerai.netprivacy.model.SimFilterSet
import dev.soranerai.netprivacy.model.SimNetworkType
import dev.soranerai.netprivacy.model.SimProfile
import dev.soranerai.netprivacy.model.SimVisibilityMode
import dev.soranerai.netprivacy.policy.PolicySnapshot
import dev.soranerai.netprivacy.policy.TargetProcessPolicyBridge
import java.util.concurrent.ConcurrentHashMap

/** Android 16 client-side hooks. No telephony or system process is required. */
object TargetTelephonyHooks {
    private val loggedHits = ConcurrentHashMap.newKeySet<String>()

    fun install(classLoader: ClassLoader) {
        val telephony = findClass("android.telephony.TelephonyManager", classLoader) ?: return

        hookBefore(telephony, setOf(
            "getNetworkOperator", "getNetworkOperatorForPhone",
            "getSimOperator", "getSimOperatorNumeric", "getSimOperatorNumericForPhone",
        )) { policy, param -> if (policy.filters.operator) param.result = policy.operatorNumeric() }

        hookBefore(telephony, setOf(
            "getNetworkOperatorName", "getNetworkOperatorNameForPhone",
            "getSimOperatorName", "getSimOperatorNameForPhone", "getSimCarrierIdName",
        )) { policy, param -> if (policy.filters.operator) param.result = policy.operatorName() }

        hookBefore(telephony, setOf(
            "getNetworkCountryIso", "getNetworkCountryIsoForPhone",
            "getSimCountryIso", "getSimCountryIsoForPhone",
        )) { policy, param -> if (policy.filters.operator) param.result = policy.countryIso() }

        hookBefore(telephony, setOf("getDataNetworkType", "getNetworkType", "getVoiceNetworkType")) { policy, param ->
            if (policy.filters.operator) param.result = policy.networkType()
        }
        hookBefore(telephony, setOf("isNetworkRoaming")) { policy, param ->
            if (policy.filters.operator) param.result = policy.profile?.roaming ?: false
        }
        hookBefore(telephony, setOf("getSimState")) { policy, param ->
            if (policy.filters.subscription || policy.mode == SimVisibilityMode.HIDE) {
                param.result = if (policy.mode == SimVisibilityMode.HIDE) {
                    TelephonyManager.SIM_STATE_ABSENT
                } else {
                    TelephonyManager.SIM_STATE_READY
                }
            }
        }
        hookBefore(telephony, setOf("getAllCellInfo")) { policy, param ->
            if (policy.filters.cellInfo) param.result = emptyList<Any>()
        }
        hookBefore(telephony, setOf("getCellLocation")) { policy, param ->
            if (policy.filters.cellInfo) param.result = null
        }
        hookBefore(telephony, setOf(
            "getImei", "getMeid", "getDeviceId", "getSubscriberId",
            "getSimSerialNumber", "getGroupIdLevel1",
        )) { policy, param -> if (policy.filters.identifiers) param.result = null }
        hookBefore(telephony, setOf("getLine1Number", "getMsisdn")) { policy, param ->
            if (policy.filters.identifiers) param.result = policy.phoneNumber()
        }
        hookBefore(telephony, setOf("getLine1AlphaTag")) { policy, param ->
            if (policy.filters.identifiers) param.result = policy.operatorName()
        }

        installSubscriptionManagerHooks(classLoader)
        installSubscriptionInfoHooks(classLoader)
    }

    private fun installSubscriptionManagerHooks(classLoader: ClassLoader) {
        val clazz = findClass("android.telephony.SubscriptionManager", classLoader) ?: return
        setOf(
            "getActiveSubscriptionInfoList", "getCompleteActiveSubscriptionInfoList",
            "getAvailableSubscriptionInfoList", "getAccessibleSubscriptionInfoList",
        ).forEach { method ->
            val hooks = XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val policy = effectivePolicy() ?: return
                    if (!policy.filters.subscription || policy.mode != SimVisibilityMode.HIDE) return
                    logHit("SubscriptionManager.$method")
                    param.result = emptyList<Any>()
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val policy = effectivePolicy() ?: return
                    if (!policy.filters.subscription || policy.mode != SimVisibilityMode.PROFILE) return
                    val list = param.result as? List<*> ?: return
                    logHit("SubscriptionManager.$method")
                    param.result = list.take(1)
                }
            })
            logInstalled(clazz, method, hooks.size)
        }
        hookBefore(clazz, setOf("getActiveSubscriptionInfoCount")) { policy, param ->
            if (policy.filters.subscription) {
                param.result = if (policy.mode == SimVisibilityMode.HIDE) 0 else 1
            }
        }
        hookBefore(clazz, setOf("getPhoneNumber")) { policy, param ->
            if (policy.filters.identifiers || policy.filters.subscription) param.result = policy.phoneNumber()
        }
    }

    private fun installSubscriptionInfoHooks(classLoader: ClassLoader) {
        val clazz = findClass("android.telephony.SubscriptionInfo", classLoader) ?: return
        hookBefore(clazz, setOf("getCarrierName", "getDisplayName")) { policy, param ->
            if (policy.filters.subscription) param.result = policy.operatorName()
        }
        hookBefore(clazz, setOf("getCountryIso")) { policy, param ->
            if (policy.filters.subscription) param.result = policy.countryIso()
        }
        hookBefore(clazz, setOf("getMccString")) { policy, param ->
            if (policy.filters.subscription) param.result = policy.profile?.mcc.orEmpty()
        }
        hookBefore(clazz, setOf("getMncString")) { policy, param ->
            if (policy.filters.subscription) param.result = policy.profile?.mnc.orEmpty()
        }
        hookBefore(clazz, setOf("getMcc")) { policy, param ->
            if (policy.filters.subscription) param.result = policy.profile?.mcc?.toIntOrNull() ?: 0
        }
        hookBefore(clazz, setOf("getMnc")) { policy, param ->
            if (policy.filters.subscription) param.result = policy.profile?.mnc?.toIntOrNull() ?: 0
        }
        hookBefore(clazz, setOf("getIccId", "getCardString")) { policy, param ->
            if (policy.filters.identifiers || policy.filters.subscription) param.result = ""
        }
        hookBefore(clazz, setOf("getNumber")) { policy, param ->
            if (policy.filters.identifiers || policy.filters.subscription) param.result = policy.phoneNumber()
        }
    }

    private fun hookBefore(
        clazz: Class<*>,
        names: Set<String>,
        change: (EffectivePolicy, XC_MethodHook.MethodHookParam) -> Unit,
    ) {
        names.forEach { method ->
            val hooks = XposedBridge.hookAllMethods(clazz, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val policy = effectivePolicy() ?: return
                    change(policy, param)
                    logHit("${clazz.simpleName}.$method")
                }
            })
            logInstalled(clazz, method, hooks.size)
        }
    }

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }
            .onFailure { NetPrivacyLog.warn("target class unavailable: $name", it) }
            .getOrNull()

    private fun logInstalled(clazz: Class<*>, method: String, count: Int) {
        if (count > 0) NetPrivacyLog.info("hooked target ${clazz.simpleName}.$method ($count)")
    }

    private fun logHit(method: String) {
        if (loggedHits.add(method)) NetPrivacyLog.info("first policy hit: $method")
    }
}

private data class EffectivePolicy(
    val mode: SimVisibilityMode,
    val filters: SimFilterSet,
    val profile: SimProfile?,
) {
    fun operatorNumeric() = if (mode == SimVisibilityMode.HIDE) "" else "${profile?.mcc.orEmpty()}${profile?.mnc.orEmpty()}"
    fun operatorName() = if (mode == SimVisibilityMode.HIDE) "" else profile?.operatorName.orEmpty()
    fun countryIso() = if (mode == SimVisibilityMode.HIDE) "" else profile?.countryIso.orEmpty()
    fun phoneNumber() = if (mode == SimVisibilityMode.HIDE) "" else profile?.phoneNumber.orEmpty()
    fun networkType() = if (mode == SimVisibilityMode.HIDE) {
        TelephonyManager.NETWORK_TYPE_UNKNOWN
    } else when (profile?.networkType) {
        SimNetworkType.GSM -> TelephonyManager.NETWORK_TYPE_GPRS
        SimNetworkType.UMTS -> TelephonyManager.NETWORK_TYPE_UMTS
        SimNetworkType.NR -> TelephonyManager.NETWORK_TYPE_NR
        else -> TelephonyManager.NETWORK_TYPE_LTE
    }
}

private fun effectivePolicy(snapshot: PolicySnapshot = TargetProcessPolicyBridge.current()): EffectivePolicy? {
    val policy = snapshot.policyForCurrentProcess() ?: return null
    if (policy.mode == SimVisibilityMode.PASSTHROUGH) return null
    return EffectivePolicy(policy.mode, policy.filters, snapshot.profileFor(policy))
}
