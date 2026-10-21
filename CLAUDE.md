# Panduan Pengembangan AI — AetherDNS

Dokumen ini diperuntukkan bagi asisten AI (Claude Code, Cursor, Windsurf, Copilot, Cline, Aider, Antigravity, dsb.) yang membaca, menganalisis, atau melanjutkan pengembangan basis kode **AetherDNS**.

---

## 1. Ikhtisar Arsitektur & Filosofi Sistem

**AetherDNS** adalah pengendali Private DNS (DNS-over-TLS / DoT) tingkat native Android yang beroperasi langsung pada *system settings* OS (`Settings.Global`), bukan sebagai *user-space proxy* berbasis `VpnService`. Mengadopsi **Clean Architecture** (Repository Pattern + ViewModel + StateFlow) dengan container manual DI tanpa overhead runtime framework.

### Keunggulan Desain:
1. **0% Beban Baterai & RAM (Native OS Network Daemon)**:
   - Aplikasi tidak memproses atau mem-parsing paket data di background.
   - Eksekusi koneksi TLS (port 853) dilakukan langsung oleh daemon C++ (`netd` / `resolv`) bawaan Android kernel.
   - Setelah konfigurasi disetel, aplikasi dapat ditutup sepenuhnya dan proteksi tetap aktif 100%.
2. **Aktivasi Tanpa PC via Shizuku (Wireless Debugging Android 11+)**:
   - Terintegrasi dengan Shizuku API v13 melalui AIDL UserService (`IGrantService.aidl`).
   - Berjalan pada proses terpisah dengan UID shell (2000) untuk mengeksekusi `pm grant <packageName> android.permission.WRITE_SECURE_SETTINGS`.
   - Menggunakan `daemon(false)` sehingga proses shell langsung dimatikan setelah izin diberikan (0% persistent overhead).
   - Mendukung Sui (Shizuku via Magisk/KernelSU) melalui `Sui.init()`; status binder dicek sebelum pengecekan paket Shizuku.
   - Panggilan binder `pm grant` dijalankan di `Dispatchers.IO`; keberhasilan ditentukan oleh status izin aktual, bukan exit code.
2b. **Aktivasi Bawaan Tanpa Shizuku (Wireless Debugging Android 11+)**:
   - `adb/WirelessAdbActivator` menemukan layanan `_adb-tls-pairing._tcp` lalu `_adb-tls-connect._tcp` milik perangkat sendiri via mDNS (`AdbServiceDiscovery`, alamat dicocokkan ke antarmuka lokal), pairing dengan kode 6 digit, tersambung ke adbd, menjalankan `shell:pm grant <paket> WRITE_SECURE_SETTINGS`, lalu memutus koneksi. Alamat hasil mDNS dicoba dulu, `127.0.0.1` sebagai cadangan.
   - Library: `com.github.MuntashirAkon:libadb-android` (JitPack, dipakai di bawah **Apache-2.0** dari lisensi ganda), `conscrypt-android` (TLS 1.3 + `exportKeyingMaterial` tanpa hidden API), `bcpkix` (sertifikat self-signed). Repo JitPack dibatasi `includeGroupByRegex` di `settings.gradle.kts`.
   - `AdbIdentity`: kunci RSA + sertifikat dibuat di memori, satu instance per proses (libadb meng-cache `SSLContext` statis untuk kunci pertama), tidak pernah ditulis ke disk. Jangan memanggil `close()` (menghancurkan kunci); cukup `disconnect()`.
   - Kode dimasukkan lewat notifikasi `RemoteInput` (`PairingNotifier` → `PairingCodeReceiver`, `exported=false`, `goAsync`) agar dialog kode di Settings tidak tertutup, atau langsung di aplikasi — kolom ini hanya aktif saat `isInMultiWindowMode` karena dialog kode di Settings tertutup begitu pengguna meninggalkannya. mDNS mengutamakan alamat lokal; bila tak ada, endpoint pertama yang terlihat dipakai sebagai cadangan. Batas waktu total 45 s; keberhasilan = status izin aktual. Kegagalan bertipe `ActivationError` (pemetaan exception murni di `ActivationFailures`, diuji `ActivationModelTest`).
   - Shizuku dan ADB PC tetap tersedia sebagai alternatif.
3. **Reaktivitas Tanpa Polling (`ContentObserver` & `NetworkCallback`)**:
   - `DnsRepository` mendaftarkan `ContentObserver` pada database `Settings.Global`. Setiap perubahan DNS dari Settings HP atau Quick Settings Tile langsung memicu pembaruan `StateFlow` secara instan.
   - Mendaftarkan `ConnectivityManager.NetworkCallback` (`onLinkPropertiesChanged`) untuk memantau status validasi DoT kernel OS (`isPrivateDnsActive`) secara real-time.
4. **Pre-flight DoT Handshake Probe & Hostname Verifier**:
   - `DotProbe` menguji handshake TLS port 853 dengan SNI dan verifikasi kesesuaian hostname terhadap Subject Alternative Name (SAN) sertifikat X.509 (`HttpsURLConnection.getDefaultHostnameVerifier()`).
   - Setelah handshake, `DotProbe` mengirim satu query DNS sungguhan (A `example.com`, framing RFC 7858) dan memvalidasi ID, bit QR, serta RCODE respons. Server yang menerima TLS tetapi tidak melayani DNS ditolak (`DotProbeCause.DNS_QUERY`).
   - Hasil probe memuat latensi (`handshakeMs`, `queryMs`). `DotProbe.inspect()` memakai koneksi TLS yang sama untuk meng-query `AdTestDomains` dan menilai blokir (`FilterVerdict`: NXDOMAIN / NODATA / REFUSED / 0.0.0.0) — dipakai fitur "Uji Kecepatan & Filter" sebelum resolver diterapkan.
   - Dilengkapi *self-healing* aman (`DnsApplier`): Jika probe gagal karena resolusi DNS mati (`DotProbeCause.RESOLVE`) saat mode strict aktif, konfigurasi sementara beralih ke `opportunistic`, melakukan retry bertahap (3x percobaan), dan memulihkan setting semula di blok `finally { withContext(NonCancellable) { ... } }` bila resolver baru tidak berhasil **tertulis** (gagal, dibatalkan, atau ditolak sistem).
   - Dilindungi `Mutex` untuk mencegah *race condition* akibat penekanan preset ganda secara cepat.
5. **Quick Settings Tile (Status Bar 1-Klik)**:
   - Menggunakan `AetherDnsTileService` dengan ikon monokrom `ic_tile_shield`.
   - `onStartListening()` terpanggil otomatis saat panel Quick Settings ditarik turun.
   - Mendukung proteksi lockscreen (`unlockAndRun`) dan otomatis sinkronisasi status aktif/nonaktif.
   - Semua toggle tile melewati `DnsRepository` (`disable()` / `enableQuick()`): melanjutkan jeda bila ada, atau menerapkan specifier tersimpan via `DnsApplier` tanpa probe dengan fallback ke resolver default.
6. **Jeda Sementara (Captive Portal) Tanpa Service Persisten**:
   - `PauseController` mematikan mode Private DNS (specifier dibiarkan), menyimpan waktu akhir jeda (`SharedPrefsPauseStore`), dan menjadwalkan satu alarm inexact (`AlarmManager.setAndAllowWhileIdle`, tanpa izin `SCHEDULE_EXACT_ALARM`).
   - `PauseResumeReceiver` mengaktifkan kembali proteksi tanpa probe; juga menangani `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` karena alarm hilang saat reboot/update. Perubahan DNS manual selama jeda dihormati.
7. **Profil Kustom Tersimpan & Helper NextDNS**:
   - `ProfileRepository` menyimpan hingga 20 hostname kustom (SharedPreferences, format baris `id<TAB>nama<TAB>hostname`), divalidasi `HostnameValidator`.
   - `NextDnsHostname` menyusun `<config-id>.dns.nextdns.io` atau `<nama-perangkat>-<config-id>.dns.nextdns.io` (spasi → `--`) dari Config ID / URL DoH / hostname DoT.
8. **Lokalisasi (English default + Bahasa Indonesia)**:
   - Semua teks UI berada di `res/values/strings.xml` (English, default) dan `res/values-in/strings.xml` (Indonesia). Per-app language Android 13+ via `res/xml/locales_config.xml`.
   - Lapisan domain/data **tidak** berisi teks untuk pengguna: kegagalan dikembalikan sebagai tipe (`HostnameError`, `ApplyError`, `PauseError`, `ProfileError`, `GrantError`, `ActivationError`, `DotProbeCause`, `AuditOutcome`) lalu dipetakan ke resource di `ui/UiText.kt`. ViewModel mengirim `UiText`, bukan `String`.
   - Saat menambah teks baru: tambahkan kunci di **kedua** file strings.xml dengan argumen format yang sama.
9. **App Shortcuts (Dinamis)**:
   - `AppShortcuts.publish()` (dipanggil di `MainActivity.onCreate`) mendaftarkan shortcut Aktifkan / Jeda 15 menit / Matikan via `ShortcutManager`. Dinamis, bukan `shortcuts.xml`, karena `targetPackage` statis tidak bisa mengikuti sufiks `.debug`.
   - Target-nya `ShortcutActivity` (transparan, `exported=false`, tanpa UI) yang memanggil `DnsRepository` lalu menampilkan Toast.
9a. **Widget Layar Utama (`widget/StatusWidget`)**:
   - `AppWidgetProvider` + `RemoteViews` (tanpa Glance): status, specifier/waktu jeda, tombol aktif/nonaktif dan jeda 15 menit.
   - Tombol memakai `PendingIntent` ke `ShortcutActivity` (tidak diekspor) — jangan menambah receiver aksi yang diekspor, karena aplikasi lain bisa mematikan proteksi lewat broadcast.
   - Diperbarui oleh `AetherDnsApplication` yang meng-collect `DnsRepository.state` selama proses hidup; `updatePeriodMillis` 30 menit hanya cadangan untuk perubahan dari Settings saat proses mati.
9b. **Ikon**: adaptive icon `mipmap-anydpi/ic_launcher(_round).xml` = `drawable/ic_launcher_background` (gradien `BrandGradientColors`) + `ic_launcher_foreground` (perisai + gelombang, di zona aman 66dp) + `ic_launcher_monochrome` (themed icon Android 13+). `ic_tile_shield` (tile, notifikasi, hero status) memakai lambang yang sama. Ubah ketiganya bersamaan agar konsisten.
10. **Backup**: profil tersimpan ikut Auto Backup; `pause_state` dan `automation` (token rahasia) dikecualikan (`res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`).
11. **Otomasi (Tasker / MacroDroid)**:
   - `AutomationReceiver` (exported, tetapi `android:enabled="false"` di manifest) hanya diaktifkan lewat sakelar di aplikasi (`AutomationSettings` → `setComponentEnabledSetting`). Setiap perintah wajib membawa extra `token` yang dibandingkan waktu-konstan dengan token acak 128-bit.
   - Action: `com.security.aetherdns.action.ENABLE|DISABLE|PAUSE|APPLY`; extra `minutes`, `profile`, `hostname`. Parsing murni di `AutomationProtocol` (diuji di `AutomationProtocolTest`). `APPLY` selalu dengan probe dan dibatasi `withTimeoutOrNull(9 s)`; pembatalan aman karena `DnsApplier` memulihkan konfigurasi.
   - Jangan pernah melonggarkan: tanpa token valid tidak ada perintah yang dijalankan, termasuk `DISABLE`.
12. **Onboarding**: `OnboardingScreen` (3 langkah) tampil sekali untuk pengguna baru; `OnboardingStore.shouldShow()` otomatis menganggap selesai bila izin sudah ada atau proteksi aktif (pengguna lama yang update).
13. **Tidak Memonopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.

---

## 2. Struktur Modul & Tanggung Jawab Kode

Basis kode berlokasi di `app/src/main/java/com/security/aetherdns/`:

- `AetherDnsApplication.kt`: Application class singleton untuk container dependency injection manual (`dnsRepository`, `permissionGranter`).
- `adb/`:
  - `ActivationModel.kt`: `ActivationState`, `ActivationStep`, `ActivationError`, `PairingCode`, `ActivationFailures` (logika murni).
  - `AdbIdentity.kt`, `AdbServiceDiscovery.kt`, `WirelessAdbActivator.kt`: identitas ADB sekali pakai, mDNS, dan alur aktivasi.
  - `PairingNotifier.kt`, `PairingCodeReceiver.kt`: notifikasi input kode dan penerima balasannya.
- `shizuku/`:
  - `IGrantService.aidl`: Interface AIDL transaksi Shizuku (`destroy() = 16777114`, `grantSecureSettings() = 1`).
  - `GrantService.kt`: Implementasi AIDL yang mengeksekusi `pm grant` di sisi proses ber-UID shell.
  - `PermissionGranter.kt`: Interface dan implementasi `ShizukuGranter` (`status()`, `requestPermission()`, `grant()`).
- `data/`:
  - `DnsState.kt`: Model data immutable untuk status DoT sistem.
  - `SecureSettings.kt`: Interface baca/tulis `Settings.Global` Private DNS + implementasi `AndroidSecureSettings`.
  - `DnsApplier.kt`: Pipeline apply murni (validasi → probe → self-healing → tulis) tanpa dependensi Android framework, dilindungi `Mutex`. Diuji penuh di `DnsApplierTest`.
  - `DnsRepository.kt`: Single source of truth state (`StateFlow`), `ContentObserver`, `NetworkCallback`; mendelegasikan apply ke `DnsApplier` dan jeda ke `PauseController`. Satu-satunya jalur tulis konfigurasi DNS (UI, tile, receiver).
  - `PauseController.kt`: Logika jeda murni (interface `PauseStore`, `ResumeAlarm`). Diuji di `PauseControllerTest`.
  - `AndroidPause.kt`: Implementasi `SharedPrefsPauseStore` dan `AlarmManagerResumeAlarm`.
  - `ProfileRepository.kt`: Profil kustom tersimpan (`ProfileStore`, `ProfileCodec`). Diuji di `ProfileRepositoryTest`.
- `model/`:
  - `DnsProfile.kt`: Data class profil DoT resolver dan koleksi `DefaultProfiles` (nama/kategori/deskripsi preset via `@StringRes`).
- `util/`:
  - `DnsManager.kt`: Helper wrapper interaksi native Android (`Settings.Global`, `ConnectivityManager.getLinkProperties`).
  - `DotProbe.kt`: Pre-flight test DoT (SSLSocket + SNI + hostname verifier + query DNS RFC 7858) dengan typed `DotProbeCause` dan latensi. `DotProbeClient` menerima `HostnameVerifier` (verifier default `HttpsURLConnection` selalu menolak di JVM desktop); `object DotProbe` adalah instance default untuk aplikasi.
  - `NextDnsHostname.kt`: Penyusun hostname DoT NextDNS dari Config ID + nama perangkat opsional.
  - `DnsWireFormat.kt`: Encoder query, decoder header, dan parser record A (dengan kompresi nama) pesan DNS (RFC 1035).
  - `AdTestDomains.kt`: Daftar domain iklan uji bersama (audit & pratinjau filter) dan `FilterVerdict`.
  - `HostnameValidator.kt`: Validasi ketat FQDN (RFC 1035 / RFC 1123) dan normalisasi IDN Punycode.
  - `DnsAuditor.kt`: Engine audit sinkhole real-time dengan domain kontrol konektivitas; resolver diabstraksi (`SystemResolver`) dan diuji di `DnsAuditorTest`.
- `widget/`:
  - `StatusWidget.kt`: widget layar utama (`res/layout/widget_status.xml`, `res/xml/widget_status_info.xml`).
- `service/`:
  - `AetherDnsTileService.kt`: `TileService` untuk Quick Settings panel.
- `automation/`:
  - `AutomationProtocol.kt` (parsing perintah + token), `AutomationSettings.kt` (sakelar komponen + token), `AutomationReceiver.kt` (eksekusi via `DnsRepository`).
- `onboarding/`:
  - `OnboardingStore.kt`: Penanda onboarding selesai.
- `shortcut/`:
  - `AppShortcuts.kt`, `ShortcutActivity.kt`: App Shortcuts dinamis dan activity transparan pelaksananya.
- `receiver/`:
  - `PauseResumeReceiver.kt`: Mengaktifkan kembali proteksi setelah jeda / boot (`goAsync`, tanpa service).
- `ui/`:
  - `MainViewModel.kt`: ViewModel penampung state UI, orkestrasi Shizuku, apply/disable/audit/jeda, benchmark latensi + filter (`BenchmarkState`), dan `Channel<UiEvent>` (berisi `UiText`) untuk Snackbar.
  - `UiText.kt`: Teks terlokalisasi tertunda + pemetaan semua tipe error domain ke string resource.
  - `AetherDnsApp.kt`: Scaffold, top bar, navigasi bawah 3 tab (Beranda / Resolver / Lainnya), state pilihan profil. Beranda punya pull-to-refresh; tombol utama tanpa izin menggulir ke asisten izin.
  - `SelectedResolverCard.kt`, `MoreScreen.kt`: ringkasan resolver di Beranda dan tab Lainnya (izin, otomasi, tautan, tentang).
  - `AetherLogo.kt`: logo (gradien brand + foreground ikon launcher) untuk top bar & onboarding; `Modifier.brandGradient()` untuk kartu status aktif.
  - `theme/`: warna status semantik (`StatusColors` via `LocalStatusColors`, versi terang & gelap). `PrimaryBlue`, `SuccessGreen`, `WarningOrange`, `ErrorRed`, dsb. adalah getter `@Composable` yang mengikuti tema aktif (termasuk Material You) — jangan menulis `Color(0x...)` langsung di komponen. Tinggi baris body memakai `em` agar tetap rapat saat `fontSize` di-override.
  - `NativeActivationSection.kt`: bagian aktivasi bawaan di `PermissionAssistantCard` (tombol notifikasi + kolom kode manual).
  - `StatusCard.kt` (hero beranimasi + haptic), `AuditCard.kt`, `PermissionAssistantCard.kt`, `PresetComponents.kt`, `ProfileComponents.kt`, `AutomationCard.kt`: Komponen per section.
  - `OnboardingScreen.kt`: Onboarding 3 langkah (memakai ulang `PermissionAssistantCard`, `PresetCard`, `AddTileButton`).
- `MainActivity.kt`: Entrypoint aktivitas dengan `enableEdgeToEdge()` dan injeksi `MainViewModel`.

---

## 3. Perintah Build & Kompilasi

```powershell
# Menjalankan Unit Tests (HostnameValidator, DnsApplier, PauseController, ProfileRepository, NextDnsHostname, DnsWireFormat, FilterVerdict, DnsAuditor, GrantOutputParser, BenchmarkState, ActivationModel)
.\gradlew.bat testDebugUnitTest

# Kompilasi Debug APK
.\gradlew.bat assembleDebug

# Kompilasi Release APK (dengan ProGuard/R8 shrinking & minification)
.\gradlew.bat assembleRelease

# Bersihkan build cache
.\gradlew.bat clean
```

> **CI**: `.github/workflows/android.yml` menjalankan `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, dan `lintDebug` pada setiap push ke `main` dan pull request.
>
> **Live DoT**: `.github/workflows/live-dot.yml` menjalankan `app/src/test/.../live/LiveDotProbeTest.kt` terhadap resolver DoT sungguhan (env `AETHERDNS_LIVE_DOT=1`; tanpa env itu test di-skip) dan menulis tabel latensi + filter per resolver ke ringkasan job (env `AETHERDNS_LIVE_REPORT`). Tidak memblokir di PR/push; berjalan mingguan (Senin 03:17 UTC) dan manual, dan gagal secara terlihat di sana agar perubahan hostname/blocklist preset cepat ketahuan.
>
> **Actions**: gunakan versi mayor berbasis Node 24 (`checkout@v7`, `setup-java@v6`, `upload-artifact@v7`, `setup-gradle@v6` dengan `cache-provider: basic`, `action-gh-release@v3`).
>
> **Screenshot UI (opsional)**: `AETHERDNS_SCREENSHOTS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'` merender layar utama (terang/gelap, onboarding) via Robolectric + Roborazzi ke `app/build/screenshots/`. Tanpa env itu test di-skip (CI tidak terpengaruh). Bila Maven Central membatasi unduhan android-all, set `ROBOLECTRIC_REPO_URL` ke mirror. Robolectric memakai `@ConscryptMode(OFF)` karena `conscrypt-android` tidak punya library native JVM.
>
> **Uji perangkat**: ikuti `docs/TESTING.md` sebelum membuat tag rilis.
>
> **Rilis**: panduan lengkap di `docs/RELEASE.md`. Setiap versi wajib punya bagian `## [X.Y.Z]` di `CHANGELOG.md` (dipakai sebagai catatan rilis). Push tag `vX.Y.Z` (harus sama dengan `versionName`) menjalankan `.github/workflows/release.yml`, yang membangun APK release bertanda tangan dari secrets `AETHERDNS_KEYSTORE_BASE64`, `AETHERDNS_KEYSTORE_PASSWORD`, `AETHERDNS_KEY_ALIAS`, `AETHERDNS_KEY_PASSWORD` dan melampirkannya ke GitHub Release. Tanpa env `AETHERDNS_KEYSTORE_PATH`, `assembleRelease` lokal tetap menghasilkan APK unsigned.
>
> **Catatan JDK**: Jangan menulis `org.gradle.java.home` di `gradle.properties` proyek (path mesin lokal merusak build di CI/mesin lain). Simpan di `~/.gradle/gradle.properties` milik pengguna.

> **Catatan Windows / OneDrive**: `build.gradle.kts` root telah mengalihkan `buildDirectory` ke direktori lokal temporer (`C:\Users\...\AppData\Local\Temp\gradle_build\AetherDNS\...`) untuk menghindari *file lock* oleh sinkronisasi cloud OneDrive.
