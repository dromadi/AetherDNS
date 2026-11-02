package com.security.aetherdns.diagnostics

import android.content.Context
import java.io.File

/**
 * Menyimpan jejak crash terakhir (hanya di perangkat, folder no-backup) agar bisa disertakan di laporan diagnostik.
 * Handler bawaan tetap dipanggil, jadi perilaku crash sistem tidak berubah.
 */
class CrashRecorder(context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun lastCrash(): CrashInfo? = runCatching {
        if (!file.exists()) return null
        val lines = file.readLines()
        val timestamp = lines.getOrNull(0)?.toLongOrNull() ?: return null
        CrashInfo(timestamp, lines.getOrElse(1) { "?" }, lines.drop(2).joinToString("\n"))
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun record(threadName: String, throwable: Throwable) {
        val trace = throwable.stackTraceToString().lineSequence().take(MAX_LINES).joinToString("\n")
        file.writeText("${System.currentTimeMillis()}\n$threadName\n$trace")
    }

    private companion object {
        const val FILE_NAME = "last_crash.txt"
        const val MAX_LINES = 60
    }
}
