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
2. **Tidak Memonopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.
3. **Persisten Pasca-Reboot**:
   - Pengaturan tersimpan di database sistem `Settings.Global`.

---

## 2. Struktur Modul & Tanggung Jawab Kode

Basis kode berlokasi di `app/src/main/java/com/security/aetherdns/`:

- `model/`:
  - `DnsProfile.kt`: Data class profil DoT resolver dan koleksi `DefaultProfiles` (AdGuard DNS, AdGuard Family, NextDNS Public, Cloudflare Security 1.1.1.2, Mullvad Privacy, dan Hostname Kustom).
- `util/`:
  - `DnsManager.kt`: Wrapper manajemen konfigurasi sistem.
    - Membaca & menulis `Settings.Global.PRIVATE_DNS_MODE` (`"off"`, `"opportunistic"`, `"hostname"`).
    - Membaca & menulis `Settings.Global.PRIVATE_DNS_SPECIFIER` (DoT hostname target).
    - Memeriksa izin `Manifest.permission.WRITE_SECURE_SETTINGS`.
    - Menyediakan fallback Intent (`openSystemDnsSettings`) untuk membuka antarmuka pengaturan native jika izin belum diberikan.
  - `DnsAuditor.kt`: Engine audit sinkhole real-time.
    - Menguji resolusi domain AdMob (`pagead2.googlesyndication.com`), Google AdService (`adservice.google.com`), DoubleClick (`ads.doubleclick.net`), dan AppLovin (`applovin.com`).
    - Mendeteksi apakah domain di-sinkhole ke `0.0.0.0`, `127.0.0.1`, atau `NXDOMAIN` (UnknownHostException).
- `ui/`:
  - `AetherDnsApp.kt`: Antarmuka utama Jetpack Compose (Material 3).
    - Kartu status proteksi visual dengan toggle 1-klik.
    - Panel audit sinkhole real-time interaktif ("Uji DNS").
    - Pilihan preset resolver dalam bentuk kartu radio.
    - Form hostname kustom (DoT FQDN).
    - Asisten izin ADB terintegrasi dengan tombol "Salin Perintah ADB" dan "Buka DNS HP".
  - `theme/`: Palet warna Material 3, tipografi, dan tema aplikasi.
- `MainActivity.kt`: Entrypoint aktivitas dengan `enableEdgeToEdge()`.

---

## 3. Perintah Build & Kompilasi

Proyek menggunakan Gradle dengan Kotlin DSL (`build.gradle.kts`) dan Gradle Version Catalogs (`libs.versions.toml`).

```powershell
# Kompilasi Debug APK
.\gradlew.bat assembleDebug

# Kompilasi Release APK (dengan ProGuard/R8 shrinking & minification)
.\gradlew.bat assembleRelease

# Bersihkan build cache
.\gradlew.bat clean
```

> **Catatan Windows / OneDrive**: `build.gradle.kts` root telah mengalihkan `buildDirectory` ke direktori lokal temporer (`C:\Users\...\AppData\Local\Temp\gradle_build\AetherDNS\...`) untuk menghindari *file lock* oleh sinkronisasi cloud OneDrive.

---

## 4. Persyaratan Izin & Perintah ADB

Aplikasi membutuhkan izin tingkat sistem:
- `android.permission.WRITE_SECURE_SETTINGS` (Izin terproteksi sistem)

Perintah aktivasi via ADB:
```bash
adb shell pm grant com.security.aetherdns android.permission.WRITE_SECURE_SETTINGS
```

---

## 5. Pedoman Pengembangan Lebih Lanjut

1. **Mempertahankan Zero-Overhead**:
   - Jangan menambahkan background service persisten atau polling loop yang tidak perlu. Aplikasi ini berprinsip *set-and-forget*.
2. **Keamanan Jaringan**:
   - Validasi bahwa hostname DoT yang dimasukkan oleh pengguna adalah FQDN yang valid (bukan URL dengan `https://` dan bukan IP biasa, karena Android Private DNS hanya menerima FQDN untuk verifikasi sertifikat TLS).
3. **Standar Terminologi**:
   - Gunakan terminologi rekayasa perangkat lunak dan analisis jaringan (misal: *DNS Sinkholing*, *Resolver*, *FQDN*, *DoT*, *Telemetry Filtering*).
