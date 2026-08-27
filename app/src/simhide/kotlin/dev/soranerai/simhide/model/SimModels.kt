package dev.soranerai.simhide.model

/** Values presented to a selected app. A profile never stores real SIM identifiers. */
data class SimProfile(
    val id: String,
    val name: String,
    val countryIso: String,
    val mcc: String,
    val mnc: String,
    val operatorName: String,
    val networkType: SimNetworkType = SimNetworkType.LTE,
    val roaming: Boolean = false,
    val builtIn: Boolean = false,
    /** Optional MSISDN presented to protected apps; never inferred from the real SIM. */
    val phoneNumber: String = "",
)

enum class SimNetworkType(val displayName: String) {
    GSM("2G GSM"),
    UMTS("3G UMTS"),
    LTE("4G LTE"),
    NR("5G NR"),
}

enum class SimVisibilityMode {
    /** Do not change any telephony data for this application. */
    PASSTHROUGH,
    /** Report no usable SIM and hide subscription and cell data. */
    HIDE,
    /** Return the profile values and hide real subscription and cell data. */
    PROFILE,
}

data class SimFilterSet(
    val operator: Boolean = true,
    val subscription: Boolean = true,
    val cellInfo: Boolean = true,
    val identifiers: Boolean = true,
)

data class AppSimPolicy(
    val packageName: String,
    val userId: Int,
    val uid: Int,
    val mode: SimVisibilityMode,
    val profileId: String? = null,
    val filters: SimFilterSet = SimFilterSet(),
)

data class SimHideConfig(
    val profiles: List<SimProfile> = BuiltInSimProfiles.all,
    val appPolicies: List<AppSimPolicy> = emptyList(),
)

object BuiltInSimProfiles {
    val all =
        listOf(
            SimProfile("builtin-us-tmobile", "United States · T-Mobile", "us", "310", "260", "T-Mobile", SimNetworkType.NR, builtIn = true),
            SimProfile("builtin-de-telekom", "Germany · Telekom", "de", "262", "01", "Telekom", SimNetworkType.LTE, builtIn = true),
            SimProfile("builtin-gb-ee", "United Kingdom · EE", "gb", "234", "30", "EE", SimNetworkType.NR, builtIn = true),
            SimProfile("builtin-jp-docomo", "Japan · NTT DOCOMO", "jp", "440", "10", "NTT DOCOMO", SimNetworkType.LTE, builtIn = true),
        )
}

fun SimProfile.validationError(): String? = when {
    name.isBlank() -> "Укажите имя пресета"
    !countryIso.matches(Regex("[a-zA-Z]{2}")) -> "ISO-код страны должен состоять из 2 букв"
    !mcc.matches(Regex("\\d{3}")) -> "MCC должен состоять из 3 цифр"
    !mnc.matches(Regex("\\d{2,3}")) -> "MNC должен состоять из 2 или 3 цифр"
    operatorName.isBlank() -> "Укажите имя оператора"
    phoneNumber.isNotBlank() && !phoneNumber.matches(Regex("\\+?[0-9]{3,15}")) -> "MSISDN: от 3 до 15 цифр, с необязательным +"
    else -> null
}
