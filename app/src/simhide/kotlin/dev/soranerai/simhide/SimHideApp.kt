package dev.soranerai.simhide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.soranerai.simhide.data.SimConfigStore
import dev.soranerai.simhide.model.*
import dev.soranerai.simhide.ui.InstalledApp
import dev.soranerai.simhide.ui.loadLaunchableApps
import dev.soranerai.simhide.ui.theme.SimHideTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class Tab { TARGETS, SIM_PRESETS, WIFI_PRESETS }

@Composable
fun SimHideApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SimConfigStore(context.applicationContext) }
    var config by remember { mutableStateOf(store.read()) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var tab by remember { mutableStateOf(Tab.TARGETS) }
    var publishError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val grantResult = withContext(Dispatchers.IO) { store.restoreTargetGrants() }
        publishError = grantResult.exceptionOrNull()?.message
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    fun save(next: HideConfig) {
        val result = store.write(next)
        config = next
        publishError = result.exceptionOrNull()?.message
    }
    SimHideTheme {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.TARGETS, { tab = Tab.TARGETS }, { Icon(Icons.Default.Apps, null) }, label = { Text(stringResource(R.string.tab_targets)) })
                NavigationBarItem(tab == Tab.SIM_PRESETS, { tab = Tab.SIM_PRESETS }, { Icon(Icons.Default.SimCard, null) }, label = { Text(stringResource(R.string.tab_presets)) })
                NavigationBarItem(tab == Tab.WIFI_PRESETS, { tab = Tab.WIFI_PRESETS }, { Icon(Icons.Default.Wifi, null) }, label = { Text(stringResource(R.string.tab_wifi_presets)) })
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                publishError?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.publish_error, message), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton({ publishError = null }) { Text(stringResource(R.string.close)) }
                        }
                    }
                }
                when (tab) {
                    Tab.TARGETS -> Targets(Modifier.weight(1f), apps, config, ::save)
                    Tab.SIM_PRESETS -> Presets(Modifier.weight(1f), config, ::save)
                    Tab.WIFI_PRESETS -> WifiPresets(Modifier.weight(1f), config, ::save)
                }
            }
        }
    }
}

@Composable
private fun Targets(modifier: Modifier, apps: List<InstalledApp>?, config: HideConfig, save: (HideConfig) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    var simEditing by remember { mutableStateOf<InstalledApp?>(null) }
    var wifiEditing by remember { mutableStateOf<InstalledApp?>(null) }
    val policies = config.simPolicies.associateBy { it.packageName to it.uid }
    val wifiPolicies = config.wifiPolicies.associateBy { it.packageName to it.uid }
    val visible = apps.orEmpty().filter { app ->
        (!selectedOnly || policies.containsKey(app.packageName to app.uid) || wifiPolicies.containsKey(app.packageName to app.uid)) &&
            (showSystemApps || !app.isSystem) &&
            (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
    }
    val selected = visible.filter { policies.containsKey(it.packageName to it.uid) || wifiPolicies.containsKey(it.packageName to it.uid) }
    val unselected = visible.filterNot { policies.containsKey(it.packageName to it.uid) || wifiPolicies.containsKey(it.packageName to it.uid) }
    Column(modifier.fillMaxSize()) {
        Header(R.string.targets_title, R.string.targets_subtitle)
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 0.dp),
        ) {
            Text(
                stringResource(R.string.scope_notice),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 8.dp), label = { Text(stringResource(R.string.search_apps)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selectedOnly, { selectedOnly = !selectedOnly }, label = { Text(stringResource(R.string.assigned)) })
            FilterChip(showSystemApps, { showSystemApps = !showSystemApps }, label = { Text(stringResource(R.string.system_apps)) })
        }
        when {
            apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.loading_apps)) }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.nothing_found)) }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected.isNotEmpty()) {
                    item(key = "selected-header") { TargetSection(stringResource(R.string.selected_section, selected.size)) }
                    items(selected, key = { "selected-${it.packageName}-${it.uid}" }) { app -> TargetRow(app, policies[app.packageName to app.uid], wifiPolicies[app.packageName to app.uid], config.simProfiles, { simEditing = app }, { wifiEditing = app }) }
                }
                if (unselected.isNotEmpty()) {
                    item(key = "all-header") { TargetSection(stringResource(if (selected.isEmpty()) R.string.apps_section else R.string.other_section, unselected.size)) }
                    items(unselected, key = { "all-${it.packageName}-${it.uid}" }) { app -> TargetRow(app, null, null, config.simProfiles, { simEditing = app }, { wifiEditing = app }) }
                }
            }
        }
    }
    simEditing?.let { app -> PolicyDialog(app, policies[app.packageName to app.uid], config.simProfiles, config.favoriteSimProfileIds, { simEditing = null }) { policy ->
        val rest = config.simPolicies.filterNot { it.packageName == app.packageName && it.uid == app.uid }
        save(config.copy(simPolicies = if (policy.mode == SimVisibilityMode.PASSTHROUGH) rest else rest + policy)); simEditing = null
    } }
    wifiEditing?.let { app -> WifiPolicyDialog(app, wifiPolicies[app.packageName to app.uid], config.wifiProfiles, { wifiEditing = null }) { policy ->
        val rest = config.wifiPolicies.filterNot { it.packageName == app.packageName && it.uid == app.uid }
        save(config.copy(wifiPolicies = if (policy == null) rest else rest + policy)); wifiEditing = null
    } }
}

@Composable
private fun TargetSection(title: String) = Text(
    title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(8.dp, 10.dp, 8.dp, 2.dp),
)

@Composable private fun Header(title: Int, subtitle: Int) = Column(Modifier.padding(20.dp, 18.dp, 20.dp, 0.dp)) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TargetRow(app: InstalledApp, policy: AppSimPolicy?, wifiPolicy: AppWifiPolicy?, profiles: List<SimProfile>, onSimClick: () -> Unit, onWifiClick: () -> Unit) = Card(modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) {
            if (app.icon != null) Image(app.icon.asImageBitmap(), app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(contentAlignment = Alignment.Center) { Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            policy?.let { Text(profiles.firstOrNull { p -> p.id == it.profileId }?.name ?: stringResource(R.string.sim_hidden), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onSimClick) {
                Icon(
                    Icons.Default.SimCard,
                    stringResource(R.string.chip_sim),
                    tint = if (policy == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onWifiClick) {
                Icon(
                    Icons.Default.Wifi,
                    stringResource(R.string.chip_wifi),
                    tint = if (wifiPolicy == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PolicyDialog(app: InstalledApp, existing: AppSimPolicy?, profiles: List<SimProfile>, favoriteIds: List<String>, dismiss: () -> Unit, save: (AppSimPolicy) -> Unit) {
    var mode by remember { mutableStateOf(existing?.mode ?: SimVisibilityMode.PROFILE) }
    var profileId by remember { mutableStateOf(existing?.profileId ?: profiles.firstOrNull()?.id) }
    AlertDialog(dismiss, title = { Text(app.label) }, text = { Column {
        Text(stringResource(R.string.sim_visibility_mode), style = MaterialTheme.typography.labelLarge)
        listOf(SimVisibilityMode.PASSTHROUGH to stringResource(R.string.mode_passthrough), SimVisibilityMode.HIDE to stringResource(R.string.mode_hide), SimVisibilityMode.PROFILE to stringResource(R.string.mode_profile)).forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { mode = value }, verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == value, { mode = value }); Text(label) }
        }
        if (mode == SimVisibilityMode.PROFILE) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text(stringResource(R.string.profile), style = MaterialTheme.typography.labelLarge)
            profiles.sortedWith(compareByDescending<SimProfile> { it.id in favoriteIds }.thenBy { it.name }).forEach { profile ->
                Row(Modifier.fillMaxWidth().clickable { profileId = profile.id }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(profile.id == profileId, { profileId = profile.id })
                    Text(profile.name, modifier = Modifier.weight(1f))
                    if (profile.id in favoriteIds) Icon(Icons.Default.Star, stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    } }, confirmButton = { Button({ save(AppSimPolicy(app.packageName, 0, app.uid, mode, if (mode == SimVisibilityMode.PROFILE) profileId else null)) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun WifiPolicyDialog(app: InstalledApp, existing: AppWifiPolicy?, profiles: List<WifiProfile>, dismiss: () -> Unit, save: (AppWifiPolicy?) -> Unit) {
    var profileId by remember { mutableStateOf(existing?.profileId) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(app.label) },
        text = { Column {
            Text(stringResource(R.string.wifi_profile), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().clickable { profileId = null }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(profileId == null, { profileId = null }); Text(stringResource(R.string.not_configured))
            }
            profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth().clickable { profileId = profile.id }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(profileId == profile.id, { profileId = profile.id }); Text(profile.name)
                }
            }
        } },
        confirmButton = { Button({ save(profileId?.let { AppWifiPolicy(app.packageName, 0, app.uid, it) }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Presets(modifier: Modifier, config: HideConfig, save: (HideConfig) -> Unit) {
    var query by remember { mutableStateOf("") }; var editorOpen by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<SimProfile?>(null) }
    val profiles = config.simProfiles
        .filter { it.name.contains(query, true) || it.operatorName.contains(query, true) || it.countryIso.contains(query, true) }
        .sortedWith(compareByDescending<SimProfile> { it.id in config.favoriteSimProfileIds }.thenBy { it.name })
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(R.string.presets_title, R.string.presets_subtitle)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp, 16.dp), label = { Text(stringResource(R.string.search_presets)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    isFavorite = profile.id in config.favoriteSimProfileIds,
                    favoritesFull = config.favoriteSimProfileIds.size >= 5,
                    onFavoriteToggle = {
                        val ids = if (profile.id in config.favoriteSimProfileIds) config.favoriteSimProfileIds - profile.id else config.favoriteSimProfileIds + profile.id
                        save(config.copy(favoriteSimProfileIds = ids.validFavoriteIds(config.simProfiles)))
                    },
                    onEdit = { editing = profile; editorOpen = true }, onReset = {
                    BuiltInSimProfiles.all.firstOrNull { it.id == profile.id }?.let { builtin ->
                        save(config.copy(simProfiles = config.simProfiles.map { if (it.id == builtin.id) builtin else it }))
                    }
                })
            } }
        }
        FloatingActionButton({ editing = null; editorOpen = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
    }
    if (editorOpen) ProfileEditor(editing, config.simProfiles, { editorOpen = false }) { edited ->
        val next = if (editing == null) config.simProfiles + edited else config.simProfiles.map { if (it.id == edited.id) edited else it }
        save(config.copy(simProfiles = next)); editorOpen = false
    }
}

@Composable
private fun WifiPresets(modifier: Modifier, config: HideConfig, save: (HideConfig) -> Unit) {
    var query by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WifiProfile?>(null) }
    val profiles = config.wifiProfiles.filter { it.name.contains(query, true) || it.ssid.contains(query, true) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(R.string.wifi_presets_title, R.string.wifi_presets_subtitle)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp, 16.dp), label = { Text(stringResource(R.string.search_wifi_presets)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    WifiProfileCard(profile, onEdit = { editing = profile; editorOpen = true }, onReset = {
                        BuiltInWifiProfiles.all.firstOrNull { it.id == profile.id }?.let { builtin ->
                            save(config.copy(wifiProfiles = config.wifiProfiles.map { if (it.id == builtin.id) builtin else it }))
                        }
                    })
                }
            }
        }
        FloatingActionButton({ editing = null; editorOpen = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
    }
    if (editorOpen) WifiProfileEditor(editing, { editorOpen = false }) { edited ->
        val next = if (editing == null) config.wifiProfiles + edited else config.wifiProfiles.map { if (it.id == edited.id) edited else it }
        save(config.copy(wifiProfiles = next)); editorOpen = false
    }
}

@Composable
private fun WifiProfileCard(profile: WifiProfile, onEdit: () -> Unit, onReset: () -> Unit) = Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Row { Text(profile.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); if (profile.builtIn) Text(stringResource(R.string.built_in), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        Text(profile.ssid)
        Text(stringResource(R.string.wifi_address_summary, profile.bssid, profile.ipAddress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onEdit) { Text(stringResource(R.string.edit)) }
            if (profile.builtIn && BuiltInWifiProfiles.all.firstOrNull { it.id == profile.id } != profile) TextButton(onReset) { Text(stringResource(R.string.reset)) }
        }
    }
}

@Composable
private fun WifiProfileEditor(existing: WifiProfile?, dismiss: () -> Unit, save: (WifiProfile) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var ssid by remember(existing?.id) { mutableStateOf(existing?.ssid.orEmpty()) }; var bssid by remember(existing?.id) { mutableStateOf(existing?.bssid.orEmpty()) }; var ipAddress by remember(existing?.id) { mutableStateOf(existing?.ipAddress.orEmpty()) }; var speed by remember(existing?.id) { mutableStateOf(existing?.speed.orEmpty()) }; var frequency by remember(existing?.id) { mutableStateOf(existing?.frequency.orEmpty()) }; var gateway by remember(existing?.id) { mutableStateOf(existing?.gateway.orEmpty()) }; var dns1 by remember(existing?.id) { mutableStateOf(existing?.dns1.orEmpty()) }; var dns2 by remember(existing?.id) { mutableStateOf(existing?.dns2.orEmpty()) }; var serverIp by remember(existing?.id) { mutableStateOf(existing?.serverIp.orEmpty()) }; var error by remember(existing?.id) { mutableStateOf<Int?>(null) }
    AlertDialog(dismiss, title = { Text(stringResource(if (existing == null) R.string.new_wifi_preset else R.string.edit_wifi_preset)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Field(R.string.field_name, name) { name = it }; Field(R.string.field_ssid, ssid) { ssid = it }; Field(R.string.field_bssid, bssid) { bssid = it }; Field(R.string.field_ip_address, ipAddress) { ipAddress = it }
        Field(R.string.field_speed_optional, speed) { speed = it }; Field(R.string.field_frequency_optional, frequency) { frequency = it }
        Text(stringResource(R.string.dhcp_info), style = MaterialTheme.typography.labelLarge)
        Field(R.string.field_gateway, gateway) { gateway = it }; Field(R.string.field_dns1, dns1) { dns1 = it }; Field(R.string.field_dns2, dns2) { dns2 = it }; Field(R.string.field_server_ip, serverIp) { serverIp = it }
    } }, confirmButton = { Button({
        val profile = WifiProfile(existing?.id ?: "wifi-${UUID.randomUUID()}", name.trim(), ssid.trim(), bssid.trim(), ipAddress.trim(), speed.trim(), frequency.trim(), gateway.trim(), dns1.trim(), dns2.trim(), serverIp.trim(), existing?.builtIn ?: false)
        error = profile.validationErrorRes(); if (error == null) save(profile)
    }) { Text(stringResource(if (existing == null) R.string.create else R.string.save)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun ProfileCard(profile: SimProfile, isFavorite: Boolean, favoritesFull: Boolean, onFavoriteToggle: () -> Unit, onEdit: () -> Unit, onReset: () -> Unit) = Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(profile.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (profile.builtIn) Text(stringResource(R.string.built_in), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(onFavoriteToggle, enabled = isFavorite || !favoritesFull) {
            Icon(if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite), tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
    }
    Text("${profile.operatorName} · ${profile.mcc}${profile.mnc} · ${profile.countryIso.uppercase()}")
    Text(stringResource(profile.networkType.labelRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (profile.phoneNumber.isNotBlank()) Text(stringResource(R.string.msisdn_value, profile.phoneNumber), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onEdit) { Text(stringResource(R.string.edit)) }
        if (profile.builtIn && BuiltInSimProfiles.all.firstOrNull { it.id == profile.id } != profile) TextButton(onReset) { Text(stringResource(R.string.reset)) }
    }
} }

@Composable
private fun ProfileEditor(existing: SimProfile?, profiles: List<SimProfile>, dismiss: () -> Unit, save: (SimProfile) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var country by remember(existing?.id) { mutableStateOf(existing?.countryIso.orEmpty()) }; var mcc by remember(existing?.id) { mutableStateOf(existing?.mcc.orEmpty()) }; var mnc by remember(existing?.id) { mutableStateOf(existing?.mnc.orEmpty()) }; var operator by remember(existing?.id) { mutableStateOf(existing?.operatorName.orEmpty()) }; var phoneNumber by remember(existing?.id) { mutableStateOf(existing?.phoneNumber.orEmpty()) }; var error by remember(existing?.id) { mutableStateOf<Int?>(null) }
    AlertDialog(dismiss, title = { Text(stringResource(if (existing == null) R.string.new_sim_preset else R.string.edit_sim_preset)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Field(R.string.field_name, name) { name = it }; Field(R.string.field_country_iso, country) { country = it.lowercase() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { Field(R.string.field_mcc, mcc) { mcc = it.filter(Char::isDigit) } }; Box(Modifier.weight(1f)) { Field(R.string.field_mnc, mnc) { mnc = it.filter(Char::isDigit) } } }
        Field(R.string.field_operator, operator) { operator = it }
        Field(R.string.field_msisdn, phoneNumber) { phoneNumber = it.filterIndexed { index, char -> char.isDigit() || (char == '+' && index == 0) } }
    } }, confirmButton = { Button({
        val profile = SimProfile(existing?.id ?: "custom-${UUID.randomUUID()}", name.trim(), country.trim(), mcc, mnc, operator.trim(), existing?.networkType ?: SimNetworkType.LTE, existing?.roaming ?: false, existing?.builtIn ?: false, phoneNumber.trim())
        error = when {
            profiles.any { it.id != profile.id && it.countryIso.equals(profile.countryIso, ignoreCase = true) } -> R.string.validation_country_duplicate
            else -> profile.validationErrorRes()
        }
        if (error == null) save(profile)
    }) { Text(stringResource(R.string.create)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun Field(label: Int, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, singleLine = true)
