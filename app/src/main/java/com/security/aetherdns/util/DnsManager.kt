package com.security.aetherdns.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

object DnsManager {
    private const val TAG = "DnsManager"
    const val SETTING_PRIVATE_DNS_MODE = "private_dns_mode"
    const val SETTING_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_HOSTNAME = "hostname"

    fun hasSecurePermission(context: Context): Boolean {
        return try {
            context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WRITE_SECURE_SETTINGS: ${e.message}")
            false
        }
    }

    fun getPrivateDnsMode(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, SETTING_PRIVATE_DNS_MODE) ?: MODE_OFF
        } catch (e: Exception) {
            MODE_OFF
        }
    }

    fun getPrivateDnsSpecifier(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, SETTING_PRIVATE_DNS_SPECIFIER) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun applyPrivateDns(context: Context, hostname: String): Result<Unit> {
        return try {
            val resolver = context.contentResolver
            val modeSuccess = Settings.Global.putString(resolver, SETTING_PRIVATE_DNS_MODE, MODE_HOSTNAME)
            val specSuccess = Settings.Global.putString(resolver, SETTING_PRIVATE_DNS_SPECIFIER, hostname.trim())
            if (modeSuccess && specSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gagal memperbarui konfigurasi sistem"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while writing settings", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while writing settings", e)
            Result.failure(e)
        }
    }

    fun disablePrivateDns(context: Context): Result<Unit> {
        return try {
            val resolver = context.contentResolver
            val modeSuccess = Settings.Global.putString(resolver, SETTING_PRIVATE_DNS_MODE, MODE_OFF)
            if (modeSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gagal menonaktifkan DNS"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while disabling DNS", e)
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSystemDnsSettings(context: Context): Boolean {
        val intents = listOf(
            Intent("android.settings.NETWORK_PROVIDER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }
}
