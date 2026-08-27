package dev.soranerai.simhide.policy

import java.io.File
import java.util.concurrent.TimeUnit

/** Publishes only a fully written local policy to the system_server-owned directory. */
internal object RootPolicyPublisher {
    private const val DIRECTORY = "/data/system/simhide"
    private const val TARGET = "$DIRECTORY/policy.json"
    private const val PHONE_TARGET = "/data/user_de/0/com.android.phone/files/simhide_policy.json"

    fun publish(source: File): Result<Unit> = runCatching {
        // source is an application-controlled constant path, never user input.
        val command = "mkdir -p $DIRECTORY && chmod 0700 $DIRECTORY && cp ${source.absolutePath} $DIRECTORY/.policy.tmp && chown system:system $DIRECTORY/.policy.tmp && chmod 0640 $DIRECTORY/.policy.tmp && mv $DIRECTORY/.policy.tmp $TARGET && cp ${source.absolutePath} ${PHONE_TARGET}.tmp && chown radio:radio ${PHONE_TARGET}.tmp && chmod 0640 ${PHONE_TARGET}.tmp && restorecon ${PHONE_TARGET}.tmp && mv ${PHONE_TARGET}.tmp $PHONE_TARGET"
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        check(process.waitFor(8, TimeUnit.SECONDS)) { "Превышено время ожидания root-публикации" }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.exitValue() == 0) { output.ifBlank { "root отказал в записи $TARGET" } }
    }
}
