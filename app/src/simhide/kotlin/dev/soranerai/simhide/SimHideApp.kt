package dev.soranerai.simhide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.soranerai.simhide.data.SimConfigStore
import dev.soranerai.simhide.model.*
import dev.soranerai.simhide.ui.InstalledApp
import dev.soranerai.simhide.ui.loadLaunchableApps
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
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) } }
    fun save(next: SimHideConfig) {
        val result = store.write(next)
        config = next
        publishError = result.exceptionOrNull()?.message
    }
    MaterialTheme {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.TARGETS, { tab = Tab.TARGETS }, { Icon(Icons.Default.Apps, null) }, label = { Text("Цели") })
                NavigationBarItem(tab == Tab.PRESETS, { tab = Tab.PRESETS }, { Icon(Icons.Default.SimCard, null) }, label = { Text("SIM-пресеты") })
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                publishError?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Изменения сохранены локально, но не применены: $message", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton({ publishError = null }) { Text("Закрыть") }
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
    var editing by remember { mutableStateOf<InstalledApp?>(null) }
    val policies = config.appPolicies.associateBy { it.packageName to it.uid }
    val visible = apps.orEmpty().filter { app ->
        (!selectedOnly || policies.containsKey(app.packageName to app.uid)) &&
            (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
    }
    Column(modifier.fillMaxSize()) {
        Header("Цели", "Назначайте профиль отдельно для каждого приложения.")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 8.dp), label = { Text("Поиск приложений") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
        AssistChip({ selectedOnly = !selectedOnly }, { Text(if (selectedOnly) "Только назначенные" else "Все приложения") }, Modifier.padding(horizontal = 20.dp))
        when {
            apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Загружаем приложения…") }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ничего не найдено") }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.packageName }) { app -> TargetRow(app, policies[app.packageName to app.uid], config.profiles) { editing = app } }
            }
        }
    }
    editing?.let { app -> PolicyDialog(app, policies[app.packageName to app.uid], config.profiles, { editing = null }) { policy ->
        val rest = config.appPolicies.filterNot { it.packageName == app.packageName && it.uid == app.uid }
        save(config.copy(appPolicies = if (policy.mode == SimVisibilityMode.PASSTHROUGH) rest else rest + policy)); editing = null
    } }
}

@Composable private fun Header(title: String, subtitle: String) = Column(Modifier.padding(20.dp, 18.dp, 20.dp, 0.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TargetRow(app: InstalledApp, policy: AppSimPolicy?, profiles: List<SimProfile>, click: () -> Unit) = Card(onClick = click, modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            policy?.let { Text(profiles.firstOrNull { p -> p.id == it.profileId }?.name ?: "SIM скрыта", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
        if (policy != null) Icon(Icons.Default.SimCard, "Профиль", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PolicyDialog(app: InstalledApp, existing: AppSimPolicy?, profiles: List<SimProfile>, dismiss: () -> Unit, save: (AppSimPolicy) -> Unit) {
    var mode by remember { mutableStateOf(existing?.mode ?: SimVisibilityMode.PROFILE) }
    var profileId by remember { mutableStateOf(existing?.profileId ?: profiles.firstOrNull()?.id) }
    AlertDialog(dismiss, title = { Text(app.label) }, text = { Column {
        Text("Режим отображения SIM", style = MaterialTheme.typography.labelLarge)
        listOf(SimVisibilityMode.PASSTHROUGH to "Не изменять", SimVisibilityMode.HIDE to "Скрыть данные SIM", SimVisibilityMode.PROFILE to "Применить пресет").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { mode = value }, verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == value, { mode = value }); Text(label) }
        }
        if (mode == SimVisibilityMode.PROFILE) {
            Divider(Modifier.padding(vertical = 8.dp)); Text("Профиль", style = MaterialTheme.typography.labelLarge)
            profiles.forEach { profile -> Row(Modifier.fillMaxWidth().clickable { profileId = profile.id }, verticalAlignment = Alignment.CenterVertically) { RadioButton(profile.id == profileId, { profileId = profile.id }); Text(profile.name) } }
        }
    } }, confirmButton = { Button({ save(AppSimPolicy(app.packageName, 0, app.uid, mode, if (mode == SimVisibilityMode.PROFILE) profileId else null)) }) { Text("Сохранить") } }, dismissButton = { TextButton(dismiss) { Text("Отмена") } })
}

@Composable
private fun Presets(modifier: Modifier, config: SimHideConfig, save: (SimHideConfig) -> Unit) {
    var query by remember { mutableStateOf("") }; var editor by remember { mutableStateOf(false) }
    val profiles = config.profiles.filter { it.name.contains(query, true) || it.operatorName.contains(query, true) || it.countryIso.contains(query, true) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header("SIM-пресеты", "Встроенные профили можно использовать сразу; свои — создать ниже.")
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp, 16.dp), label = { Text("Поиск пресетов") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(profiles, key = { it.id }) { ProfileCard(it) } }
        }
        FloatingActionButton({ editor = true }, Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Default.Add, "Добавить") }
    }
    if (editor) ProfileEditor({ editor = false }) { profile -> save(config.copy(profiles = config.profiles + profile)); editor = false }
}

@Composable private fun ProfileCard(profile: SimProfile) = Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
    Row { Text(profile.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); if (profile.builtIn) Text("Встроен", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    Text("${profile.operatorName} · ${profile.mcc}${profile.mnc} · ${profile.countryIso.uppercase()}")
    Text(profile.networkType.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
} }

@Composable
private fun ProfileEditor(dismiss: () -> Unit, save: (SimProfile) -> Unit) {
    var name by remember { mutableStateOf("") }; var country by remember { mutableStateOf("") }; var mcc by remember { mutableStateOf("") }; var mnc by remember { mutableStateOf("") }; var operator by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(dismiss, title = { Text("Новый SIM-пресет") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Field("Название", name) { name = it }; Field("ISO страны, например DE", country) { country = it.lowercase() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { Field("MCC", mcc) { mcc = it.filter(Char::isDigit) } }; Box(Modifier.weight(1f)) { Field("MNC", mnc) { mnc = it.filter(Char::isDigit) } } }
        Field("Оператор", operator) { operator = it }
    } }, confirmButton = { Button({
        val profile = SimProfile("custom-${UUID.randomUUID()}", name.trim(), country.trim(), mcc, mnc, operator.trim(), SimNetworkType.LTE)
        error = profile.validationError(); if (error == null) save(profile)
    }) { Text("Создать") } }, dismissButton = { TextButton(dismiss) { Text("Отмена") } })
}

@Composable private fun Field(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
