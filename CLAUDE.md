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
3. **Reaktivitas Tanpa Polling (`ContentObserver` & `NetworkCallback`)**:
   - `DnsRepository` mendaftarkan `ContentObserver` pada database `Settings.Global`. Setiap perubahan DNS dari Settings HP atau Quick Settings Tile langsung memicu pembaruan `StateFlow` secara instan.
   - Mendaftarkan `ConnectivityManager.NetworkCallback` (`onLinkPropertiesChanged`) untuk memantau status validasi DoT kernel OS (`isPrivateDnsActive`) secara real-time.
4. **Pre-flight DoT Handshake Probe & Hostname Verifier**:
   - `DotProbe` menguji handshake TLS port 853 dengan SNI dan verifikasi kesesuaian hostname terhadap Subject Alternative Name (SAN) sertifikat X.509 (`HttpsURLConnection.getDefaultHostnameVerifier()`).
   - Setelah handshake, `DotProbe` mengirim satu query DNS sungguhan (A `example.com`, framing RFC 7858) dan memvalidasi ID, bit QR, serta RCODE respons. Server yang menerima TLS tetapi tidak melayani DNS ditolak (`DotProbeCause.DNS_QUERY`).
   - Hasil probe memuat latensi (`handshakeMs`, `queryMs`) yang dipakai fitur "Uji Kecepatan" pada daftar preset.
   - Dilengkapi *self-healing* aman (`DnsApplier`): Jika probe gagal karena resolusi DNS mati (`DotProbeCause.RESOLVE`) saat mode strict aktif, konfigurasi sementara beralih ke `opportunistic`, melakukan retry bertahap (3x percobaan), dan memulihkan setting semula di blok `finally { withContext(NonCancellable) { ... } }` bila resolver baru tidak berhasil **tertulis** (gagal, dibatalkan, atau ditolak sistem).
   - Dilindungi `Mutex` untuk mencegah *race condition* akibat penekanan preset ganda secara cepat.
5. **Quick Settings Tile (Status Bar 1-Klik)**:
   - Menggunakan `AetherDnsTileService` dengan ikon monokrom `ic_tile_shield`.
   - `onStartListening()` terpanggil otomatis saat panel Quick Settings ditarik turun.
   - Mendukung proteksi lockscreen (`unlockAndRun`) dan otomatis sinkronisasi status aktif/nonaktif.
6. **Tidak Memonopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.

---

## 2. Struktur Modul & Tanggung Jawab Kode

Basis kode berlokasi di `app/src/main/java/com/security/aetherdns/`:

- `AetherDnsApplication.kt`: Application class singleton untuk container dependency injection manual (`dnsRepository`, `permissionGranter`).
- `shizuku/`:
  - `IGrantService.aidl`: Interface AIDL transaksi Shizuku (`destroy() = 16777114`, `grantSecureSettings() = 1`).
  - `GrantService.kt`: Implementasi AIDL yang mengeksekusi `pm grant` di sisi proses ber-UID shell.
  - `PermissionGranter.kt`: Interface dan implementasi `ShizukuGranter` (`status()`, `requestPermission()`, `grant()`).
- `data/`:
  - `DnsState.kt`: Model data immutable untuk status DoT sistem.
  - `SecureSettings.kt`: Interface baca/tulis `Settings.Global` Private DNS + implementasi `AndroidSecureSettings`.
  - `DnsApplier.kt`: Pipeline apply murni (validasi → probe → self-healing → tulis) tanpa dependensi Android framework, dilindungi `Mutex`. Diuji penuh di `DnsApplierTest`.
  - `DnsRepository.kt`: Single source of truth state (`StateFlow`), `ContentObserver`, `NetworkCallback`; mendelegasikan apply ke `DnsApplier`.
- `model/`:
  - `DnsProfile.kt`: Data class profil DoT resolver dan koleksi `DefaultProfiles`.
- `util/`:
  - `DnsManager.kt`: Helper wrapper interaksi native Android (`Settings.Global`, `ConnectivityManager.getLinkProperties`).
  - `DotProbe.kt`: Pre-flight test DoT (SSLSocket + SNI + hostname verifier + query DNS RFC 7858) dengan typed `DotProbeCause` dan latensi; mengimplementasikan interface `DotProber`.
  - `DnsWireFormat.kt`: Encoder query / decoder header pesan DNS (RFC 1035) minimal untuk probe.
  - `HostnameValidator.kt`: Validasi ketat FQDN (RFC 1035 / RFC 1123) dan normalisasi IDN Punycode.
  - `DnsAuditor.kt`: Engine audit sinkhole real-time dengan domain kontrol konektivitas.
- `service/`:
  - `AetherDnsTileService.kt`: `TileService` untuk Quick Settings panel.
- `ui/`:
  - `MainViewModel.kt`: ViewModel penampung state UI, orkestrasi Shizuku, apply/disable/audit, benchmark latensi preset (`BenchmarkState`), dan `Channel<UiEvent>` untuk Snackbar.
  - `AetherDnsApp.kt`: Tampilan deklaratif Jetpack Compose (Material 3) dengan kartu status Shizuku multi-state.
- `MainActivity.kt`: Entrypoint aktivitas dengan `enableEdgeToEdge()` dan injeksi `MainViewModel`.

---

## 3. Perintah Build & Kompilasi

```powershell
# Menjalankan Unit Tests (HostnameValidator, DnsApplier, DnsWireFormat, GrantOutputParser, BenchmarkState)
.\gradlew.bat testDebugUnitTest

# Kompilasi Debug APK
.\gradlew.bat assembleDebug

# Kompilasi Release APK (dengan ProGuard/R8 shrinking & minification)
.\gradlew.bat assembleRelease

# Bersihkan build cache
.\gradlew.bat clean
```

> **CI**: `.github/workflows/android.yml` menjalankan `testDebugUnitTest`, `assembleDebug`, dan `assembleRelease` pada setiap push ke `main` dan pull request.
>
> **Catatan JDK**: Jangan menulis `org.gradle.java.home` di `gradle.properties` proyek (path mesin lokal merusak build di CI/mesin lain). Simpan di `~/.gradle/gradle.properties` milik pengguna.

> **Catatan Windows / OneDrive**: `build.gradle.kts` root telah mengalihkan `buildDirectory` ke direktori lokal temporer (`C:\Users\...\AppData\Local\Temp\gradle_build\AetherDNS\...`) untuk menghindari *file lock* oleh sinkronisasi cloud OneDrive.
