# Changelog

Format per versi: `## [X.Y.Z] - YYYY-MM-DD`. Bagian versi yang cocok dengan tag rilis otomatis
dipakai sebagai catatan rilis GitHub oleh `.github/workflows/release.yml`.

## [1.20.0] - 2026-10-21

### Ditambahkan
- **Uji stabilitas koneksi** (Insight → Stabilitas koneksi) untuk keluhan internet "putus-nyambung": resolver aktif diukur 12 kali selama ±30 detik bersama resolver sekelompok dan Cloudflare sebagai pembanding. Hasilnya berupa kesimpulan: resolver aktif stabil, jaringan yang bermasalah, atau pindah ke resolver sekelompok yang stabil (dengan tombol Pindah).
- Kartu Kesehatan memperingatkan "DNS terenkripsi sering gagal di jaringan ini" bila ≥ 20% pengukuran ke resolver aktif gagal.

## [1.19.0] - 2026-10-21

### Diubah
- **Tampilan diseragamkan dengan warna brand**: tombol, tautan, navigasi, dan chip kini memakai biru yang sama dengan logo dan kartu status (sebelumnya mengikuti warna wallpaper / Material You sehingga tidak serasi). Skema terang & gelap lengkap; kartu putih bersih di atas latar kebiruan.
- **Tab Resolver**: judul dan penjelasan singkat, tombol "Uji Kecepatan & Filter" selebar layar, kartu preset lebih ringkas dengan penanda pilihan di kanan, hostname tidak lagi mencolok.
- Kartu izin tidak lagi berlatar oranye; bilah atas lebih bersih (versi ada di Tentang).

## [1.18.0] - 2026-10-21

### Ditambahkan
- **Saran resolver lebih cepat** di Insight: bila resolver aktif lambat di jaringan ini dan hasil "Uji Kecepatan" menunjukkan resolver sekelompok (jenis pemblokiran sama) minimal 2× lebih cepat, muncul tombol "Pindah ke …".
- Grafik kecepatan bisa dilihat untuk **7 atau 30 hari**.
- **Riwayat proteksi**: kapan proteksi diaktifkan, dimatikan, dijeda, dilanjutkan, atau resolver diganti, beserta asalnya (aplikasi, Quick Settings, widget/pintasan, otomasi, jeda selesai, atau di luar AetherDNS). Perubahan dari Settings HP dicatat saat aplikasi dibuka berikutnya.
- **Statistik NextDNS 7 hari** di Insight (untuk profil NextDNS dengan API key): grafik kueri & blokir harian, alasan blokir teratas, dan perangkat teratas.

## [1.17.0] - 2026-10-21

### Ditambahkan
- **Insight** (tab Lainnya, atau tombol "Lihat insight" di kartu Kesehatan): kecepatan resolver aktif per jaringan selama 7 hari (median, jumlah pengukuran, grafik harian, label Cepat/Normal/Lambat) dan **rapor jaringan** 30 hari (berapa kali DNS dibelokkan atau DNS terenkripsi diblokir per Wi-Fi / data seluler per operator).
- Kartu Kesehatan menampilkan peringatan "DNS lambat di jaringan ini" bila resolver konsisten lambat (minimal 3 pengukuran, median ≥ 400 ms); tombol "Bandingkan resolver" menjalankan uji kecepatan.
- Data terkumpul otomatis setiap kali aplikasi dibuka, hanya disimpan di HP (tidak ikut backup, maksimal 30 hari), tanpa nama Wi-Fi atau IP, dan bisa dihapus kapan saja.

## [1.16.1] - 2026-10-21

### Diperbaiki
- Tombol "Pasang tombol di Quick Settings" terlihat tidak bereaksi: jawaban sistem diabaikan. Kini muncul pesan bila tile berhasil ditambahkan, sudah terpasang, atau HP tidak menampilkan dialog (dengan langkah menambahkan manual).

## [1.16.0] - 2026-10-21

### Diubah
- **Beranda dirapikan**: kartu Cek jaringan dan Audit digabung menjadi satu kartu **Kesehatan proteksi** berisi beberapa baris berbahasa awam (terenkripsi? iklan diblokir? ISP membelokkan DNS? jaringan mendukung DNS terenkripsi?). Detail teknis (port 53, NXDOMAIN, IP) disembunyikan di "Detail teknis". Pemeriksaan berjalan otomatis.
- Kartu resolver dihapus dari Beranda; nama resolver dan tombol **Ganti resolver** ada di kartu status. Pil validasi OS dipindah ke kartu Kesehatan.
- **Tab Lainnya** menjadi daftar menu (Izin, Otomasi, Laporan diagnostik, Pengaturan DNS HP, Kode sumber, Tentang); setiap item membuka halamannya sendiri, tombol Back kembali ke menu.

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
