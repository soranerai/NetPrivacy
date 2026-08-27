package dev.soranerai.simhide.policy

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.io.File

/**
 * Narrow IPC endpoint for system telephony processes.
 *
 * It is exported only so Phone Services can resolve it through the normal
 * ActivityManager provider path; authorization is enforced from the actual
 * Binder caller UID, not from an intent permission a third-party app can hold.
 */
class PolicyProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        requireTrustedCaller()
        if (method != METHOD_SNAPSHOT) throw IllegalArgumentException("Unsupported policy method")
        val file = File(requireNotNull(context).createDeviceProtectedStorageContext().filesDir, POLICY_FILE)
        val json = file.takeIf(File::exists)?.readText() ?: ""
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

    private fun requireTrustedCaller() {
        when (Binder.getCallingUid()) {
            Process.SYSTEM_UID, PHONE_UID -> Unit
            else -> throw SecurityException("SIM Hide policy is restricted to system telephony")
        }
    }

    companion object {
        const val AUTHORITY = "dev.soranerai.simhide.policy"
        const val METHOD_SNAPSHOT = "snapshot"
        const val KEY_JSON = "json"
        const val KEY_REVISION = "revision"
        private const val POLICY_FILE = "simhide_policy.json"
        private const val PHONE_UID = 1001
    }
}
