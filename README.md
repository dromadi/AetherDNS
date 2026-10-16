# AetherDNS - Native Android Private DNS & Ad-Sinkhole Controller (v1.2.0)

AetherDNS adalah aplikasi Android modern (Clean Architecture + Jetpack Compose + Material 3) yang berfungsi sebagai pengendali DNS-over-TLS (DoT) bawaan sistem operasi Android untuk mitigasi dan penyaringan (*sinkholing*) iklan dan telemetri tanpa membebani baterai dan RAM perangkat.

---

## Arsitektur & Keunggulan Teknis

1. **Clean Architecture (DnsRepository + MainViewModel + StateFlow)**:
   - Memisahkan logika bisnis sepenuhnya dari lapisan tampilan Jetpack Compose.
   - Pendaftaran `ContentObserver` pada database `Settings.Global` memastikan status di UI selalu reaktif secara real-time tanpa *polling loop*.
2. **Zero Battery & Zero RAM Overhead (Native Level)**:
   - Tidak menggunakan `VpnService` lokal yang mem-parsing paket data di CPU.
   - Konfigurasi langsung disematkan pada Android OS Network Stack (`Settings.Global.PRIVATE_DNS_MODE` dan `Settings.Global.PRIVATE_DNS_SPECIFIER`).
   - Sistem operasi Android sendiri yang menjalankan koneksi DoT terenkripsi di port 853 melalui daemon kernel C++ (`netd` / `resolv`).
   - Setelah proteksi aktif, aplikasi dapat ditutup total dan proteksi tetap aktif 100%.
3. **Pre-flight DoT Handshake Probe & Hostname Matching (`DotProbe`)**:
   - Menjalankan uji koneksi TLS handshake ke server DoT port 853 dengan SNI dan verifikasi kesesuaian hostname terhadap sertifikat X.509 (`HttpsURLConnection.getDefaultHostnameVerifier()`).
   - Dilengkapi *self-healing fallback*: Jika resolver lama sedang mati sehingga DNS sistem lumpuh, repository secara sementara mengaktifkan mode `opportunistic` untuk menguji dan menghubungkan ke resolver sehat yang baru.
4. **Quick Settings Tile (Status Bar 1-Klik)**:
   - Menggunakan `AetherDnsTileService` dengan ikon monokrom `ic_tile_shield`.
   - `onStartListening()` terpanggil otomatis saat panel Quick Settings ditarik turun (tanpa polling background).
   - Mendukung proteksi lockscreen (`unlockAndRun`) dan otomatis sinkronisasi status aktif/nonaktif.
   - Jika izin belum diberikan, tile tetap dapat diklik untuk langsung membuka panduan ADB di `MainActivity`.
   - Pada Android 13+, terdapat pintasan langsung untuk menambahkan tile ke Status Bar.
5. **Verifikasi Status Kernel OS (`LinkProperties`)**:
   - Membaca status `LinkProperties.isPrivateDnsActive` (API 28+) untuk memverifikasi apakah Android OS telah berhasil menyelesaikan enkripsi DoT.
6. **Bebas Monopoli Slot VPN**:
   - Pengguna tetap dapat mengaktifkan VPN lain (WireGuard, OpenVPN, Cloudflare WARP) secara bersamaan.
7. **Validasi Ketat Hostname FQDN**:
   - Dilengkapi `HostnameValidator` untuk menolak format URL (`https://`), path/query, port/IPv6, dan raw IP address, serta menormalisasi IDN ke Punycode ASCII (RFC 1035 / RFC 1123).
8. **Live Diagnostic / Sinkhole Audit Terverifikasi**:
   - Memiliki engine audit real-time (`DnsAuditor`) dengan domain kontrol konektivitas (`connectivitycheck.gstatic.com`) untuk mencegah *false-positive* saat offline.
   - Menguji domain AdMob, DoubleClick, dan AppLovin untuk mendeteksi status sinkhole (0.0.0.0 / NXDOMAIN).

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

### 2. Aktivasi 1-Klik Otomatis (Izin WRITE_SECURE_SETTINGS)
Agar aplikasi dapat mengubah pengaturan DNS sistem secara otomatis tanpa perlu membuka menu pengaturan manual, berikan izin ini 1 kali melalui ADB:

**Untuk Build Debug (APK yang belum ditandatangani keystore release):**
```bash
adb shell pm grant com.security.aetherdns.debug android.permission.WRITE_SECURE_SETTINGS
```

**Untuk Build Release (APK production):**
```bash
adb shell pm grant com.security.aetherdns android.permission.WRITE_SECURE_SETTINGS
```

*(Catatan: Aplikasi AetherDNS secara otomatis mendeteksi nama paket yang terpasang dan menampilkan tombol "Salin Perintah ADB" yang langsung sesuai di layar).*

### 3. Penggunaan Tanpa ADB (Mode Bantuan / Manual)
Jika belum sempat menghubungkan ke PC:
- Pilih preset DNS yang diinginkan di dalam aplikasi.
- Klik tombol **"Buka DNS HP"**.
- Aplikasi otomatis menyalin hostname ke clipboard dan membuka menu pengaturan Private DNS bawaan HP untuk di-paste langsung.
