# AetherDNS - Native Android Private DNS & Ad-Sinkhole Controller (v1.7.0)

AetherDNS adalah aplikasi Android modern (Clean Architecture + Jetpack Compose + Material 3) yang berfungsi sebagai pengendali DNS-over-TLS (DoT) bawaan sistem operasi Android untuk mitigasi dan penyaringan (*sinkholing*) iklan dan telemetri tanpa membebani baterai dan RAM perangkat.

---

## Arsitektur & Keunggulan Teknis

1. **Aktivasi Tanpa PC via Shizuku (Wireless Debugging Android 11+)**:
   - Terintegrasi dengan Shizuku API v13 melalui AIDL UserService (`IGrantService.aidl`).
   - Memberikan izin `WRITE_SECURE_SETTINGS` langsung dari HP tanpa perlu laptop/kabel data.
   - Menggunakan `daemon(false)` sehingga proses shell langsung berhenti setelah izin diberikan (0% overhead).
   - Mendukung Sui (Shizuku via Magisk/KernelSU) untuk perangkat root.
2. **Clean Architecture (DnsRepository + MainViewModel + StateFlow)**:
   - Memisahkan logika bisnis sepenuhnya dari lapisan tampilan Jetpack Compose.
   - Pendaftaran `ContentObserver` pada database `Settings.Global` memastikan status di UI selalu reaktif secara real-time tanpa *polling loop*.
   - Mendaftarkan `ConnectivityManager.NetworkCallback` (`onLinkPropertiesChanged`) untuk pembaruan instan status validasi DoT kernel OS.
3. **Pre-flight DoT Probe: Handshake, Hostname Matching & Query DNS (`DotProbe`)**:
   - Menjalankan uji koneksi TLS handshake ke server DoT port 853 dengan SNI dan verifikasi kesesuaian hostname terhadap sertifikat X.509 (`HttpsURLConnection.getDefaultHostnameVerifier()`).
   - Mengirim satu query DNS sungguhan (RFC 7858) untuk memastikan server benar-benar melayani DNS, bukan sekadar menerima TLS di port 853.
   - Tombol **Uji Kecepatan & Filter** mengukur latensi DoT (handshake + query) setiap preset/profil, menandai resolver tercepat, dan menampilkan berapa domain iklan uji yang benar-benar diblokir resolver tersebut **sebelum** Anda menerapkannya.
   - Dilengkapi *self-healing fallback* aman: Jika resolver lama sedang mati sehingga DNS sistem lumpuh, repository secara sementara mengaktifkan mode `opportunistic` untuk menguji dan menghubungkan ke resolver sehat yang baru, dilindungi oleh `Mutex` dan blok `finally { NonCancellable }`.
4. **Jeda 15 Menit untuk Wi-Fi Publik (Captive Portal)**:
   - Wi-Fi hotel/bandara/kafe sering mewajibkan login yang terblokir mode strict. Tombol **Jeda 15 Menit** mematikan proteksi sementara dan mengaktifkannya kembali otomatis lewat satu alarm sistem (tanpa service background), termasuk bila HP di-reboot selama jeda.
5. **Profil Kustom Tersimpan & Helper NextDNS**:
   - Simpan hingga 20 hostname kustom (mis. "Rumah – Pi-hole", "Kantor") dan ganti dengan 1 ketukan.
   - Cukup masukkan Config ID NextDNS (`abc123`) atau URL `https://dns.nextdns.io/abc123`; nama perangkat opsional agar perangkat terpisah di analytics NextDNS.
6. **Quick Settings Tile (Status Bar 1-Klik)**:
   - Menggunakan `AetherDnsTileService` dengan ikon monokrom `ic_tile_shield`.
   - `onStartListening()` terpanggil otomatis saat panel Quick Settings ditarik turun.
   - Mendukung proteksi lockscreen (`unlockAndRun`) dan otomatis sinkronisasi status aktif/nonaktif.
   - Jika izin belum diberikan, tile tetap dapat diklik untuk langsung membuka panduan ADB / Shizuku di `MainActivity`.
   - Pada Android 13+, terdapat pintasan langsung untuk menambahkan tile ke Status Bar.
7. **Verifikasi Status Kernel OS (`LinkProperties`)**:
   - Membaca status `LinkProperties.isPrivateDnsActive` (API 28+) untuk memverifikasi apakah Android OS telah berhasil menyelesaikan enkripsi DoT.
8. **Bebas Monopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.
9. **Validasi Ketat Hostname FQDN**:
   - Dilengkapi `HostnameValidator` untuk menolak format URL (`https://`), path/query, port/IPv6, dan raw IP address, serta menormalisasi IDN ke Punycode ASCII (RFC 1035 / RFC 1123).
10. **Live Diagnostic / Sinkhole Audit Terverifikasi**:
   - Memiliki engine audit real-time (`DnsAuditor`) dengan domain kontrol konektivitas (`connectivitycheck.gstatic.com`) untuk mencegah *false-positive* saat offline.
   - Menguji domain AdMob, DoubleClick, dan AppLovin untuk mendeteksi status sinkhole (0.0.0.0 / NXDOMAIN).
11. **App Shortcuts**:
   - Tekan lama ikon aplikasi untuk **Aktifkan**, **Jeda 15 menit**, atau **Matikan** proteksi tanpa membuka aplikasi.
12. **Bahasa Inggris & Indonesia**:
   - Antarmuka mengikuti bahasa HP (English sebagai default, Bahasa Indonesia untuk perangkat berbahasa Indonesia). Di Android 13+, bahasa aplikasi dapat dipilih terpisah lewat Settings → Apps → AetherDNS → Language.

---

## Preset DNS Filtering Bawaan

| Profile | Hostname DoT | Kategori | Cakupan Proteksi |
| :--- | :--- | :--- | :--- |
| **AdGuard DNS (Rekomendasi)** | `dns.adguard-dns.com` | Anti-Iklan & Tracker | Banner, pop-up, rewarded ads di aplikasi & browser |
| **AdGuard Family** | `family.adguard-dns.com` | Keluarga & Anti-Iklan | Anti-iklan + proteksi konten dewasa + SafeSearch |
| **Mullvad Privacy** | `adblock.dns.mullvad.net` | Privasi & Anti-Iklan | Resolver zero-log resmi Mullvad dengan filter iklan aktif |
| **Cloudflare Security (1.1.1.2)** | `security.cloudflare-dns.com` | Anti-Malware | Blokir phishing, malware, dan ransomware |
| **NextDNS Anycast** | `dns.nextdns.io` | Resolver Bersih | Resolver Anycast berlatensi rendah (gunakan Hostname Kustom jika ingin filter ad NextDNS) |
| **Custom Hostname** | *(Bebas ditentukan)* | Kustomisasi | ID profil NextDNS pribadi (`xxxxxx.dns.nextdns.io`) atau server VPS sendiri (AdGuard Home / Pi-hole) |

---

## Petunjuk Penggunaan

### 1. Unduh & Pasang APK
Unduh build biner terbaru dari menu **[GitHub Releases](https://github.com/dromadi/AetherDNS/releases)**.

### 2. Cara Aktivasi Izin WRITE_SECURE_SETTINGS

#### Jalur A: Tanpa PC Menggunakan Shizuku (Rekomendasi)
1. Pasang dan aktifkan aplikasi **Shizuku** via Wireless Debugging di HP Anda (Android 11+).
2. Buka **AetherDNS**. Aplikasi akan otomatis mendeteksi status Shizuku.
3. Tekan tombol **"Berikan Izin Otomatis Sekarang"**. Izin akan aktif secara instan!
*(Catatan pengguna Xiaomi/HyperOS: Pastikan opsi "USB debugging (Security settings)" aktif di Opsi Pengembang).*

#### Jalur B: Menggunakan Kabel Data & ADB di PC (Fallback)
Jalankan perintah ini di Command Prompt / Terminal:

**Untuk Build Debug:**
```bash
adb shell pm grant com.security.aetherdns.debug android.permission.WRITE_SECURE_SETTINGS
```

**Untuk Build Release:**
```bash
adb shell pm grant com.security.aetherdns android.permission.WRITE_SECURE_SETTINGS
```

### 3. Penggunaan Tanpa ADB (Mode Bantuan / Manual)
Jika belum sempat mengaktifkan izin:
- Pilih preset DNS yang diinginkan di dalam aplikasi.
- Klik tombol **"Buka DNS HP"**.
- Aplikasi otomatis menyalin hostname ke clipboard dan membuka menu pengaturan Private DNS bawaan HP untuk di-paste langsung.

---

## Pengujian

- `./gradlew testDebugUnitTest` — unit test (logika apply, self-healing, jeda, parser DNS, validator, dsb.).
- `AETHERDNS_LIVE_DOT=1 ./gradlew testDebugUnitTest --tests 'com.security.aetherdns.live.*'` — uji DoT terhadap resolver sungguhan (butuh akses port 853). Dijalankan otomatis oleh job CI `live-dot`.
- [`docs/TESTING.md`](docs/TESTING.md) — checklist uji di HP sebelum rilis (Shizuku, jeda + reboot, tile, shortcut, bahasa).

## Rilis Otomatis (Maintainer)

Push tag `vX.Y.Z` yang sama dengan `versionName` di `app/build.gradle.kts` untuk menjalankan workflow `Release`. Workflow membangun APK release bertanda tangan dan melampirkannya (beserta checksum SHA-256) ke halaman GitHub Releases.

Isi secrets berikut sekali di **Settings → Secrets and variables → Actions**:

| Secret | Isi |
| :--- | :--- |
| `AETHERDNS_KEYSTORE_BASE64` | Keystore `.jks` dalam base64 (`base64 -w0 release.jks`) |
| `AETHERDNS_KEYSTORE_PASSWORD` | Password keystore |
| `AETHERDNS_KEY_ALIAS` | Alias kunci |
| `AETHERDNS_KEY_PASSWORD` | Password kunci |

> Gunakan keystore yang **sama** dengan rilis sebelumnya. Android menolak update yang ditandatangani kunci berbeda, sehingga pengguna harus uninstall terlebih dahulu.
