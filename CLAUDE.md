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
2. **Reaktivitas Tanpa Polling (`ContentObserver`)**:
   - `DnsRepository` mendaftarkan `ContentObserver` pada database `Settings.Global`. Setiap perubahan DNS dari Settings HP atau Quick Settings Tile langsung memicu pembaruan `StateFlow` secara instan.
3. **Pre-flight DoT Handshake Probe & Hostname Verifier**:
   - `DotProbe` menguji handshake TLS port 853 dengan SNI dan verifikasi kesesuaian hostname terhadap Subject Alternative Name (SAN) sertifikat X.509 sebelum menulis ke sistem.
   - Dilengkapi mekanisme *self-healing*: Jika probe gagal karena resolver lama yang aktif sedang mati/rusak (NXDOMAIN), repository sementara mengalihkan ke mode `opportunistic` agar resolver baru dapat di-resolve dan dipasang.
4. **Quick Settings Tile (Status Bar 1-Klik)**:
   - Menggunakan `AetherDnsTileService` dengan ikon monokrom `ic_tile_shield`.
   - `onStartListening()` terpanggil otomatis saat panel Quick Settings ditarik turun.
   - Mendukung proteksi lockscreen (`unlockAndRun`) dan otomatis sinkronisasi status aktif/nonaktif.
5. **Verifikasi Status Kernel OS Langsung**:
   - Menggunakan `LinkProperties.isPrivateDnsActive` (API 28+) untuk memverifikasi apakah Android OS telah berhasil menyelesaikan enkripsi DoT.
6. **Tidak Memonopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.

---

## 2. Struktur Modul & Tanggung Jawab Kode

Basis kode berlokasi di `app/src/main/java/com/security/aetherdns/`:

- `AetherDnsApplication.kt`: Application class singleton untuk container dependency injection manual (`dnsRepository`).
- `data/`:
  - `DnsState.kt`: Model data immutable untuk merepresentasikan status DoT sistem (`hasPermission`, `mode`, `specifier`, `isProtectionActive`, `isSystemDoTValidated`).
  - `DnsRepository.kt`: Single source of truth. Mengelola pembacaan/penulisan settings, `ContentObserver`, eksekusi `DotProbe`, dan pemulihan deadlock DNS (*self-healing*).
- `model/`:
  - `DnsProfile.kt`: Data class profil DoT resolver dan koleksi `DefaultProfiles` (AdGuard DNS, AdGuard Family, Mullvad Privacy, Cloudflare Security 1.1.1.2, NextDNS Anycast, dan Hostname Kustom).
- `util/`:
  - `DnsManager.kt`: Helper wrapper interaksi native Android (`Settings.Global`, `ConnectivityManager.getLinkProperties`).
  - `DotProbe.kt`: Pre-flight test koneksi port 853 DoT via SSLSocket + SNI + `HttpsURLConnection.getDefaultHostnameVerifier()`.
  - `HostnameValidator.kt`: Validasi ketat FQDN (RFC 1035 / RFC 1123) dan normalisasi IDN Punycode.
  - `DnsAuditor.kt`: Engine audit sinkhole real-time dengan domain kontrol konektivitas (`connectivitycheck.gstatic.com`).
- `service/`:
  - `AetherDnsTileService.kt`: `TileService` untuk Quick Settings panel.
- `ui/`:
  - `MainViewModel.kt`: ViewModel penampung state UI, orkestrasi apply/disable/audit, dan `Channel<UiEvent>` untuk Snackbar.
  - `AetherDnsApp.kt`: Tampilan deklaratif murni Jetpack Compose (Material 3).
- `MainActivity.kt`: Entrypoint aktivitas dengan `enableEdgeToEdge()` dan injeksi `MainViewModel`.

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
