package dev.soranerai.simhide.model

import dev.soranerai.simhide.R

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

enum class SimNetworkType(val labelRes: Int) {
    GSM(R.string.network_gsm),
    UMTS(R.string.network_umts),
    LTE(R.string.network_lte),
    NR(R.string.network_nr),
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

/** Synthetic Wi-Fi and DHCP values reserved for future hooks. */
object BuiltInSimProfiles {
    val all =
        listOf(
            SimProfile("builtin-us-tmobile", "United States · T-Mobile", "us", "310", "260", "T-Mobile", SimNetworkType.NR, builtIn = true, phoneNumber = "+12025550148"),
            SimProfile("builtin-de-telekom", "Germany · Telekom", "de", "262", "01", "Telekom", SimNetworkType.LTE, builtIn = true, phoneNumber = "+4915212345678"),
            SimProfile("builtin-gb-ee", "United Kingdom · EE", "gb", "234", "30", "EE", SimNetworkType.NR, builtIn = true, phoneNumber = "+447911123456"),
            SimProfile("builtin-jp-docomo", "Japan · NTT DOCOMO", "jp", "440", "10", "NTT DOCOMO", SimNetworkType.LTE, builtIn = true, phoneNumber = "+819012345678"),
            SimProfile("builtin-fr-orange", "France · Orange", "fr", "208", "01", "Orange", SimNetworkType.LTE, builtIn = true, phoneNumber = "+33612345678"),
            SimProfile("builtin-ca-rogers", "Canada · Rogers", "ca", "302", "720", "Rogers", SimNetworkType.LTE, builtIn = true, phoneNumber = "+14165550148"),
            SimProfile("builtin-au-telstra", "Australia · Telstra", "au", "505", "01", "Telstra", SimNetworkType.NR, builtIn = true, phoneNumber = "+61412345678"),
            SimProfile("builtin-nl-kpn", "Netherlands · KPN", "nl", "204", "08", "KPN", SimNetworkType.LTE, builtIn = true, phoneNumber = "+31612345678"),
            SimProfile("builtin-pl-orange", "Poland · Orange", "pl", "260", "01", "Orange", SimNetworkType.LTE, builtIn = true, phoneNumber = "+48512123456"),
            SimProfile("builtin-ru-mts", "Russia · MTS", "ru", "250", "01", "MTS", SimNetworkType.LTE, builtIn = true, phoneNumber = "+79211234567"),
            SimProfile("builtin-tr-turkcell", "Türkiye · Turkcell", "tr", "286", "01", "Turkcell", SimNetworkType.LTE, builtIn = true, phoneNumber = "+905321234567"),
            SimProfile("builtin-kz-kcell", "Kazakhstan · Kcell", "kz", "401", "01", "Kcell", SimNetworkType.LTE, builtIn = true, phoneNumber = "+77011234567"),
        )
}

fun List<String>.validFavoriteIds(profiles: List<SimProfile>): List<String> =
    distinct().filter { id -> profiles.any { it.id == id } }.take(5)

fun List<SimProfile>.uniqueCountries(): List<SimProfile> =
    distinctBy { it.countryIso.lowercase() }


fun SimProfile.validationErrorRes(): Int? = when {
    name.isBlank() -> R.string.validation_name
    !countryIso.matches(Regex("[a-zA-Z]{2}")) -> R.string.validation_country_iso
    !mcc.matches(Regex("\\d{3}")) -> R.string.validation_mcc
    !mnc.matches(Regex("\\d{2,3}")) -> R.string.validation_mnc
    operatorName.isBlank() -> R.string.validation_operator
    phoneNumber.isNotBlank() && !phoneNumber.matches(Regex("\\+?[0-9]{3,15}")) -> R.string.validation_msisdn
    else -> null
}
