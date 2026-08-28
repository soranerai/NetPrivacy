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

private enum class Tab { TARGETS, PRESETS }

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
    fun save(next: SimHideConfig) {
        val result = store.write(next)
        config = next
        publishError = result.exceptionOrNull()?.message
    }
    SimHideTheme {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.TARGETS, { tab = Tab.TARGETS }, { Icon(Icons.Default.Apps, null) }, label = { Text(stringResource(R.string.tab_targets)) })
                NavigationBarItem(tab == Tab.PRESETS, { tab = Tab.PRESETS }, { Icon(Icons.Default.SimCard, null) }, label = { Text(stringResource(R.string.tab_presets)) })
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
                if (tab == Tab.TARGETS) Targets(Modifier.weight(1f), apps, config, ::save)
                else Presets(Modifier.weight(1f), config, ::save)
            }
        }
    }
}

@Composable
private fun Targets(modifier: Modifier, apps: List<InstalledApp>?, config: SimHideConfig, save: (SimHideConfig) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedOnly by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InstalledApp?>(null) }
    val policies = config.appPolicies.associateBy { it.packageName to it.uid }
    val visible = apps.orEmpty().filter { app ->
        (!selectedOnly || policies.containsKey(app.packageName to app.uid)) &&
            (showSystemApps || !app.isSystem) &&
            (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
    }
    val selected = visible.filter { policies.containsKey(it.packageName to it.uid) }
    val unselected = visible.filterNot { policies.containsKey(it.packageName to it.uid) }
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
                    items(selected, key = { "selected-${it.packageName}-${it.uid}" }) { app -> TargetRow(app, policies[app.packageName to app.uid], config.profiles) { editing = app } }
                }
                if (unselected.isNotEmpty()) {
                    item(key = "all-header") { TargetSection(stringResource(if (selected.isEmpty()) R.string.apps_section else R.string.other_section, unselected.size)) }
                    items(unselected, key = { "all-${it.packageName}-${it.uid}" }) { app -> TargetRow(app, null, config.profiles) { editing = app } }
                }
            }
        }
    }
    editing?.let { app -> PolicyDialog(app, policies[app.packageName to app.uid], config.profiles, config.favoriteProfileIds, { editing = null }) { policy ->
        val rest = config.appPolicies.filterNot { it.packageName == app.packageName && it.uid == app.uid }
        save(config.copy(appPolicies = if (policy.mode == SimVisibilityMode.PASSTHROUGH) rest else rest + policy)); editing = null
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
private fun TargetRow(app: InstalledApp, policy: AppSimPolicy?, profiles: List<SimProfile>, click: () -> Unit) = Card(onClick = click, modifier = Modifier.fillMaxWidth()) {
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
        if (policy != null) Icon(Icons.Default.SimCard, stringResource(R.string.profile_icon), tint = MaterialTheme.colorScheme.primary)
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
            Divider(Modifier.padding(vertical = 8.dp)); Text(stringResource(R.string.profile), style = MaterialTheme.typography.labelLarge)
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
private fun Presets(modifier: Modifier, config: SimHideConfig, save: (SimHideConfig) -> Unit) {
    var query by remember { mutableStateOf("") }; var editorOpen by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<SimProfile?>(null) }
    val profiles = config.profiles
        .filter { it.name.contains(query, true) || it.operatorName.contains(query, true) || it.countryIso.contains(query, true) }
        .sortedWith(compareByDescending<SimProfile> { it.id in config.favoriteProfileIds }.thenBy { it.name })
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(R.string.presets_title, R.string.presets_subtitle)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp, 16.dp), label = { Text(stringResource(R.string.search_presets)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    isFavorite = profile.id in config.favoriteProfileIds,
                    favoritesFull = config.favoriteProfileIds.size >= 5,
                    onFavoriteToggle = {
                        val ids = if (profile.id in config.favoriteProfileIds) config.favoriteProfileIds - profile.id else config.favoriteProfileIds + profile.id
                        save(config.copy(favoriteProfileIds = ids.validFavoriteIds(config.profiles)))
                    },
                    onEdit = { editing = profile; editorOpen = true }, onReset = {
                    BuiltInSimProfiles.all.firstOrNull { it.id == profile.id }?.let { builtin ->
                        save(config.copy(profiles = config.profiles.map { if (it.id == builtin.id) builtin else it }))
                    }
                })
            } }
        }
        FloatingActionButton({ editing = null; editorOpen = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
    }
    if (editorOpen) ProfileEditor(editing, config.profiles, { editorOpen = false }) { edited ->
        val next = if (editing == null) config.profiles + edited else config.profiles.map { if (it.id == edited.id) edited else it }
        save(config.copy(profiles = next)); editorOpen = false
    }
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
