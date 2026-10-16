# Panduan Pengembangan AI — AetherDNS

Dokumen ini diperuntukkan bagi asisten AI (Claude Code, Cursor, Windsurf, Copilot, Cline, Aider, Antigravity, dsb.) yang membaca, menganalisis, atau melanjutkan pengembangan basis kode **AetherDNS**.

---

## 1. Ikhtisar Arsitektur & Filosofi Sistem

**AetherDNS** adalah pengendali Private DNS (DNS-over-TLS / DoT) tingkat native Android yang beroperasi langsung pada *system settings* OS (`Settings.Global`), bukan sebagai *user-space proxy* berbasis `VpnService`.

### Keunggulan Desain:
1. **0% Beban Baterai & RAM (Native OS Network Daemon)**:
   - Aplikasi tidak memproses atau mem-parsing paket data di background.
   - Eksekusi koneksi TLS (port 853) dilakukan langsung oleh daemon C++ (`netd` / `resolv`) bawaan Android kernel.
   - Setelah konfigurasi disetel, aplikasi dapat ditutup sepenuhnya dan proteksi tetap aktif 100%.
2. **Quick Settings Tile (Status Bar 1-Klik)**:
   - Menggunakan `AetherDnsTileService` dengan ikon monokrom `ic_tile_shield` (hanya aktif saat QS panel terbuka, tanpa meta-data `ACTIVE_TILE` agar `onStartListening()` terpanggil saat panel dibuka).
   - Mendukung proteksi lockscreen (`unlockAndRun`) dan otomatis sinkronisasi status aktif/nonaktif.
   - Menggunakan `STATE_INACTIVE` saat izin belum ada agar klik tetap diterima sistem dan diarahkan ke asisten ADB di `MainActivity`.
3. **Pre-flight DoT Handshake Probe**:
   - `DotProbe` menguji handshake TLS port 853 dengan SNI dan verifikasi sertifikat sebelum mengaktifkan mode strict, melindungi pengguna dari kegagalan total resolusi DNS.
4. **Verifikasi Status Kernel OS Langsung**:
   - Menggunakan `LinkProperties.isPrivateDnsActive` (API 28+) untuk memverifikasi apakah Android OS telah berhasil menyelesaikan enkripsi DoT.
5. **Tidak Memonopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.
6. **Persisten Pasca-Reboot**:
   - Pengaturan tersimpan di database sistem `Settings.Global`.

---

## 2. Struktur Modul & Tanggung Jawab Kode

Basis kode berlokasi di `app/src/main/java/com/security/aetherdns/`:

- `model/`:
  - `DnsProfile.kt`: Data class profil DoT resolver dan koleksi `DefaultProfiles` (AdGuard DNS, AdGuard Family, Mullvad Privacy, Cloudflare Security 1.1.1.2, NextDNS Anycast, dan Hostname Kustom).
- `util/`:
  - `DnsManager.kt`: Wrapper manajemen konfigurasi sistem.
    - Membaca & menulis `Settings.Global.PRIVATE_DNS_MODE` (`"off"`, `"opportunistic"`, `"hostname"`).
    - Membaca & menulis `Settings.Global.PRIVATE_DNS_SPECIFIER` (DoT hostname target).
    - `isProtectionActive(context)`: Single source of truth status proteksi.
    - `getSystemLinkPropertiesStatus(context)`: Membaca status DoT langsung dari kernel network daemon OS.
    - Memeriksa izin `Manifest.permission.WRITE_SECURE_SETTINGS`.
    - Mengintegrasikan `HostnameValidator` sebelum menyimpan konfigurasi.
    - Mengirim notifikasi pembaruan ke tile melalui `TileService.requestListeningState()`.
  - `DotProbe.kt`: Pre-flight test koneksi port 853 DoT via SSLSocket + SNI.
  - `HostnameValidator.kt`: Validasi ketat FQDN (RFC 1035 / RFC 1123) dan normalisasi IDN Punycode.
  - `DnsAuditor.kt`: Engine audit sinkhole real-time dengan domain kontrol konektivitas (`connectivitycheck.gstatic.com`).
- `service/`:
  - `AetherDnsTileService.kt`: `TileService` untuk Quick Settings panel.
    - Menangani toggle ON/OFF langsung dari status bar.
    - Menangani pembukaan `MainActivity` dengan `PendingIntent` pada Android 14+ (API 34+).
    - Membungkus aksi dengan `unlockAndRun` jika layar dalam keadaan terkunci (`isSecure`).
- `ui/`:
  - `AetherDnsApp.kt`: Antarmuka utama Jetpack Compose (Material 3).
    - Menampilkan badge status validasi kernel OS (`LinkProperties`).
    - Menjalankan uji `DotProbe` sebelum menerapkan hostname DoT baru.
    - Siklus hidup otomatis (`LifecycleEventObserver` pada `ON_RESUME`) untuk memuat ulang status sistem.
    - Perintah ADB dinamis berdasarkan `context.packageName` (mendukung debug dan release build).
    - Tombol pintasan pendaftaran tile ke Status Bar pada Android 13+ (`StatusBarManager.requestAddTileService`).
    - Validasi real-time pada kolom input Hostname Kustom dengan `supportingText` deskriptif.
- `MainActivity.kt`: Entrypoint aktivitas dengan `enableEdgeToEdge()`.

---

## 3. Perintah Build & Kompilasi

```powershell
# Menjalankan Unit Tests (HostnameValidator, dsb.)
.\gradlew.bat test

# Kompilasi Debug APK
.\gradlew.bat assembleDebug

# Kompilasi Release APK (dengan ProGuard/R8 shrinking & minification)
.\gradlew.bat assembleRelease

# Bersihkan build cache
.\gradlew.bat clean
```

> **Catatan Windows / OneDrive**: `build.gradle.kts` root telah mengalihkan `buildDirectory` ke direktori lokal temporer (`C:\Users\...\AppData\Local\Temp\gradle_build\AetherDNS\...`) untuk menghindari *file lock* oleh sinkronisasi cloud OneDrive.
