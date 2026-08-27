package dev.soranerai.simhide.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val label: String,
    val packageName: String,
    val uid: Int,
    val isSystem: Boolean,
    val icon: Bitmap?,
)

fun loadLaunchableApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { resolveInfo ->
            val info = resolveInfo.activityInfo.applicationInfo
            InstalledApp(
                label = resolveInfo.loadLabel(packageManager).toString().ifBlank { info.packageName },
                packageName = info.packageName,
                uid = info.uid,
                isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                icon = runCatching { resolveInfo.loadIcon(packageManager).toBitmap(96, 96) }.getOrNull(),
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
