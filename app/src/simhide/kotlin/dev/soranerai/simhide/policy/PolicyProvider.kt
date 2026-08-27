package dev.soranerai.simhide.policy

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import dev.soranerai.simhide.model.SimHideConfig
import java.io.File

/**
 * UID/package-filtered endpoint for applications explicitly selected as targets.
 * URI grants make the provider visible on Android 11+, while this class ensures
 * that a caller can receive only its own policy and referenced profile.
 */
class PolicyProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val callerUid = Binder.getCallingUid()
        val callerPackage = callingPackage
        if (method != METHOD_SNAPSHOT) throw IllegalArgumentException("Unsupported policy method")
        val file = File(requireNotNull(context).createDeviceProtectedStorageContext().filesDir, POLICY_FILE)
        val raw = file.takeIf(File::exists)?.readText() ?: ""
        val json = policyForTarget(raw, callerUid, callerPackage)
        return Bundle().apply {
            putString(KEY_JSON, json)
            putLong(KEY_REVISION, file.lastModified())
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
        throw UnsupportedOperationException("Use call(snapshot)")

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    private fun policyForTarget(raw: String, callerUid: Int, callerPackage: String?): String {
        if (raw.isBlank() || callerPackage.isNullOrBlank()) return ""
        val config = runCatching { SimPolicyCodec.decode(raw) }.getOrElse { return "" }
        val policy = config.appPolicies.firstOrNull {
            it.uid == callerUid && it.packageName == callerPackage
        } ?: return ""
        val profile = policy.profileId?.let { id -> config.profiles.firstOrNull { it.id == id } }
        return SimPolicyCodec.encode(
            SimHideConfig(
                profiles = listOfNotNull(profile),
                appPolicies = listOf(policy),
            ),
        )
    }

    companion object {
        const val AUTHORITY = "dev.soranerai.simhide.policy"
        val POLICY_URI: Uri = Uri.parse("content://$AUTHORITY/policy")
        const val METHOD_SNAPSHOT = "snapshot"
        const val KEY_JSON = "json"
        const val KEY_REVISION = "revision"
        private const val POLICY_FILE = "simhide_policy.json"
    }
}
