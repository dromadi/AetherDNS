# Changelog

Format per versi: `## [X.Y.Z] - YYYY-MM-DD`. Bagian versi yang cocok dengan tag rilis otomatis
dipakai sebagai catatan rilis GitHub oleh `.github/workflows/release.yml`.

## [1.15.0] - 2026-10-21

### Ditambahkan
- **Laporan diagnostik** (tab Lainnya): memeriksa semua fitur sekaligus (izin, resolver aktif + probe DoT, cek jaringan, audit iklan, aktivasi, Shizuku, otomasi, NextDNS, widget, onboarding) dan menyusun teks yang bisa disalin atau dibagikan. Tidak memuat token, API key, nama Wi-Fi, atau IP perangkat.
- **Pencatat crash**: jejak crash terakhir disimpan di perangkat (tidak ikut backup) dan disertakan di laporan; bisa dihapus dari kartu laporan.

## [1.14.1] - 2026-10-21

### Diperbaiki
- Kartu audit menjadi sangat tinggi dan kosong bila sebuah domain di-resolve ke banyak IP (kolom nama terjepit). Chip kini dibatasi lebarnya dan menampilkan "IP pertama +N".
- Audit tidak lagi melaporkan "bocor" untuk domain yang masih berasal dari cache DNS HP sejak sebelum proteksi aktif: saat proteksi aktif, resolver ditanya langsung lewat DoT, dan domain yang ia blokir ditandai "Cache HP (segera terblokir)".

## [1.14.0] - 2026-10-21

### Ditambahkan
- **Statistik blokir NextDNS** di Beranda (24 jam terakhir: jumlah diblokir, persentase, domain paling sering diblokir) bila resolver aktif adalah profil NextDNS pribadi. Butuh API key NextDNS; key hanya disimpan di HP (tidak ikut backup) dan hanya dikirim ke api.nextdns.io.
- **Cek jaringan otomatis** saat aplikasi dibuka (paling sering tiap 10 menit) dan saat jaringan berganti selama aplikasi tampil. Bila DNS terenkripsi diblokir saat proteksi aktif, muncul dialog dengan tombol jeda 15 menit. Tidak ada kerja latar.

### Diperbaiki
- Crash saat membuka kolom isian berlabel (Hostname kustom, helper NextDNS, simpan profil) sejak v1.10.0, akibat tinggi baris tipografi berunit `em`.

## [1.13.0] - 2026-10-21

### Ditambahkan
- **Cek jaringan** di Beranda: mendeteksi penyadapan DNS biasa (port 53), jawaban DNS yang dipalsukan ISP (iklan/halaman pengalihan), dan apakah DNS terenkripsi (port 853) bisa dipakai. Bila DoT diblokir saat proteksi aktif, tersedia tombol jeda satu ketuk agar internet tidak putus.
- Preset **Cloudflare 1.1.1.1** (tanpa filter, tercepat) dan **Cloudflare 1.1.1.3 Families** (malware + konten dewasa), melengkapi Cloudflare 1.1.1.2 Security.
- Preset **Control D Free**, **Mullvad Extended**, **Mullvad All**, **Quad9**, **AdGuard Non-filtering**, dan **Google Public DNS**.
- Daftar resolver dikelompokkan per tujuan: Blokir iklan, Keluarga, Keamanan saja, Enkripsi saja. Onboarding hanya menampilkan grup Blokir iklan dan Keluarga.

## [1.12.0] - 2026-10-21

### Ditambahkan
- Widget layar utama "Status AetherDNS": status proteksi, resolver aktif, tombol aktif/nonaktif, dan jeda 15 menit. Diperbarui otomatis tanpa service latar.
- `CHANGELOG.md` dan panduan rilis `docs/RELEASE.md` (pembuatan keystore + secrets GitHub).

## [1.11.1] - 2026-10-21

### Diperbaiki
- Aktivasi bawaan: kolom kode di aplikasi hanya aktif di layar terpisah (dialog kode di Settings tertutup saat berpindah aplikasi); pesan galat mengarahkan ke notifikasi.
- Pencarian mDNS memakai alamat cadangan bila alamat yang diiklankan tidak cocok dengan antarmuka lokal.

## [1.11.0] - 2026-10-21

### Diubah
- Ikon aplikasi baru (adaptive icon + themed icon Android 13); ikon tile/notifikasi selaras.
- Kartu status bergradien brand, langkah aktivasi bernomor, opsi Shizuku/PC dilipat, animasi antartab.

## [1.10.0] - 2026-10-21

### Diubah
- Navigasi bawah (Beranda / Resolver / Lainnya), mode gelap diperbaiki, pull-to-refresh, konfirmasi hapus profil.

## [1.9.0] - 2026-10-20

### Ditambahkan
- Aktivasi bawaan tanpa Shizuku (Android 11+): pairing Wireless debugging dengan kode lewat notifikasi, lalu `pm grant` otomatis.

## [1.8.0] – [1.8.1]

### Ditambahkan
- Otomasi Tasker / MacroDroid dengan token rahasia, onboarding pertama kali, pintasan pengaturan Shizuku.

## [1.4.0] – [1.7.0]

### Ditambahkan
- Probe DoT dengan query DNS sungguhan, uji kecepatan & filter, profil kustom, helper NextDNS, jeda 15 menit, bahasa Inggris/Indonesia, App Shortcuts, workflow rilis bertanda tangan, uji DoT live mingguan.
