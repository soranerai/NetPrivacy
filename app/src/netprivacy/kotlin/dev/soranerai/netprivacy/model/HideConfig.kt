package dev.soranerai.netprivacy.model

/** Complete module configuration, persisted atomically and shared with hooked processes. */
data class HideConfig(
    val simProfiles: List<SimProfile> = BuiltInSimProfiles.all,
    val simPolicies: List<AppSimPolicy> = emptyList(),
    val favoriteSimProfileIds: List<String> = emptyList(),
    val wifiProfiles: List<WifiProfile> = BuiltInWifiProfiles.all,
    val wifiPolicies: List<AppWifiPolicy> = emptyList(),
)
