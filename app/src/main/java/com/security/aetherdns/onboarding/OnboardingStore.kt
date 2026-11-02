package com.security.aetherdns.onboarding

import android.content.Context

/** Penanda onboarding pertama kali sudah selesai atau dilewati. */
class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() = prefs.edit().putBoolean(KEY_COMPLETED, true).apply()

    /**
     * Onboarding hanya untuk pengguna baru: pengguna lama yang sudah punya izin atau proteksi aktif
     * (mis. update dari versi sebelum onboarding ada) langsung dianggap selesai.
     */
    fun shouldShow(hasPermission: Boolean, isProtectionActive: Boolean): Boolean {
        if (isCompleted()) return false
        if (hasPermission || isProtectionActive) {
            markCompleted()
            return false
        }
        return true
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
