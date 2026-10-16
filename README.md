# AetherDNS - Native Android Private DNS & Ad-Sinkhole Controller

AetherDNS adalah aplikasi Android modern (Jetpack Compose + Material 3) yang berfungsi sebagai pengendali DNS-over-TLS (DoT) bawaan sistem operasi Android untuk mitigasi dan penyaringan (*sinkholing*) iklan dan telemetri tanpa membebani baterai dan RAM perangkat.

---

## Arsitektur & Keunggulan Teknis

1. **Zero Battery & Zero RAM Overhead (Native Level)**:
   - Tidak menggunakan `VpnService` lokal yang mem-parsing paket data di CPU.
   - Konfigurasi langsung disematkan pada Android OS Network Stack (`Settings.Global.PRIVATE_DNS_MODE` dan `Settings.Global.PRIVATE_DNS_SPECIFIER`).
   - Sistem operasi Android sendiri yang menjalankan koneksi DoT terenkripsi di port 853 melalui daemon kernel C++ (`netd`).
2. **Bebas Monopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.
3. **Persisten Setelah Reboot**:
   - Pengaturan tersimpan permanen di database sistem Android.
4. **Live Diagnostic / Sinkhole Audit**:
   - Memiliki engine audit real-time (`DnsAuditor`) yang melakukan pengujian resolusi terhadap domain iklan utama (`pagead2.googlesyndication.com`, `ads.doubleclick.net`, `applovin.com`) untuk memverifikasi apakah sinkhole (0.0.0.0 / NXDOMAIN) aktif.

---

## Preset DNS Filtering Bawaan

| Profile | Hostname DoT | Kategori | Cakupan Proteksi |
| :--- | :--- | :--- | :--- |
| **AdGuard DNS (Rekomendasi)** | `dns.adguard-dns.com` | Anti-Iklan & Tracker | Banner, pop-up, rewarded ads di aplikasi & browser |
| **AdGuard Family** | `family.adguard-dns.com` | Keluarga & Anti-Iklan | Anti-iklan + proteksi konten dewasa + SafeSearch |
| **NextDNS Public** | `dns.nextdns.io` | Kecepatan & Privasi | Anycast DoT berlatensi rendah |
| **Cloudflare Security (1.1.1.2)** | `security.cloudflare-dns.com` | Anti-Malware | Blokir phishing, malware, dan ransomware |
| **Mullvad Privacy** | `adblock.doh.mullvad.net` | Privasi Maksimal | Zero-log resolver dengan filter ad agresif |
| **Custom Hostname** | *(Bebas ditentukan)* | Kustomisasi | NextDNS ID pribadi atau instance VPS (AdGuard Home / Pi-hole) |

---

## Petunjuk Penggunaan

### 1. Instalasi APK
Pasang file [AetherDNS_v1.0.0_debug.apk](file:///c:/Users/TOOLS%20UBH/OneDrive%20-%20Indonesiapower/Documents/Aplikasi%20Mod/AetherDNS_v1.0.0_debug.apk) pada perangkat Android (minimal Android 9.0 / Pie).

### 2. Aktivasi 1-Klik Otomatis (Izin WRITE_SECURE_SETTINGS)
Agar aplikasi dapat mengubah pengaturan DNS sistem secara otomatis tanpa perlu membuka menu pengaturan manual, berikan izin ini 1 kali melalui ADB:

```bash
adb shell pm grant com.security.aetherdns android.permission.WRITE_SECURE_SETTINGS
```
*(Bisa juga dijalankan lewat aplikasi **Shizuku** atau **Termux** jika perangkat sudah di-root/wireless debugging).*

### 3. Penggunaan Tanpa ADB (Mode Bantuan / Manual)
Jika pengguna belum menghubungkan ke PC:
- Pilih preset DNS yang diinginkan di dalam aplikasi.
- Klik tombol **"Buka DNS HP"**.
- Aplikasi akan otomatis menyalin hostname ke clipboard dan membuka menu pengaturan Private DNS bawaan HP. Pengguna cukup menempel (*paste*) hostname tersebut.
