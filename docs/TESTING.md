# Checklist Uji Perangkat AetherDNS

Unit test (`./gradlew testDebugUnitTest`) dan job CI `live-dot` menguji logika dan resolver DoT sungguhan, tetapi bagian berikut hanya bisa dibuktikan di HP. Jalankan sebelum membuat tag rilis.

Siapkan: satu HP Android 11+ (aktivasi bawaan / Shizuku via Wireless Debugging) dan, bila ada, satu HP Xiaomi/HyperOS. Pasang build release (`AetherDNS-vX.Y.Z.apk`) di atas versi sebelumnya untuk sekaligus menguji update.

## 1. Pemasangan & izin

Aktivasi bawaan (Android 11+, tanpa Shizuku) — uji dengan izin belum ada (`adb shell pm revoke <paket> android.permission.WRITE_SECURE_SETTINGS`):

- [ ] Tombol **Tampilkan kolom kode & buka Wireless debugging**: Android 13+ meminta izin notifikasi; notifikasi "Aktivasi AetherDNS" muncul dan Wireless debugging terbuka.
- [ ] Tolak izin notifikasi → Toast petunjuk layar terpisah; kolom kode di aplikasi tetap bisa dipakai.
- [ ] Buka dialog kode penyambungan → balas notifikasi dengan kode → progres (mencari → menyambungkan → memberi izin) → "Izin diberikan"; kartu asisten izin hilang setelah kembali ke aplikasi.
- [ ] Kode salah → "Kode ditolak"; notifikasi menawarkan **Coba lagi**.
- [ ] Balas notifikasi saat dialog kode sudah ditutup → "Layanan penyambungan tidak ditemukan".
- [ ] Layar terpisah (aplikasi + Settings): ketik kode di kolom aplikasi → berhasil.
- [ ] Layar penuh: kolom kode di aplikasi nonaktif dengan petunjuk memakai notifikasi / layar terpisah.
- [ ] "AetherDNS" muncul di daftar perangkat tersambung Wireless debugging dan bisa dilupakan; aktivasi berikutnya tetap bisa.
- [ ] Samsung (One UI): uji dengan Pemblokir Otomatis (Auto Blocker) aktif dan nonaktif; catat hasilnya.
- [ ] Android 9/10: bagian aktivasi bawaan tidak tampil; Shizuku/ADB tetap ada.

Shizuku:


- [ ] Update dari versi sebelumnya berhasil tanpa uninstall (kunci tanda tangan sama).
- [ ] Shizuku **belum terpasang** → status "Belum Terpasang / Not installed".
- [ ] Shizuku terpasang tapi **belum Start** → "Belum Berjalan / Not running".
- [ ] Saat "Belum Berjalan": tombol **Buka Wireless debugging** membuka Developer options (item Wireless debugging disorot bila ROM mendukung); bila Developer options belum aktif, membuka About phone + petunjuk Build number. Tombol **Buka Shizuku** membuka aplikasi Shizuku.
- [ ] Shizuku berjalan → "Perlu Otorisasi" → tombol izinkan → dialog Shizuku muncul → "Siap".
- [ ] "Berikan Izin Otomatis" → snackbar sukses, kartu asisten izin hilang, badge "Mode Native Aktif".
- [ ] Matikan Wireless Debugging saat dialog otorisasi Shizuku terbuka → aplikasi **tidak crash**, muncul pesan gagal.
- [ ] (Xiaomi) Tanpa "USB debugging (Security settings)" → pesan petunjuk MIUI/HyperOS muncul.
- [ ] Alternatif ADB: perintah yang ditampilkan memakai nama paket build yang terpasang (`.debug` untuk debug).

## 2. Apply, probe, dan pratinjau filter

- [ ] **Uji Kecepatan & Filter**: setiap preset menampilkan "xx ms · blokir X/Y"; AdGuard ≥ 3/4, Cloudflare Security 0/4; satu ditandai "Tercepat".
- [ ] Aktifkan AdGuard → snackbar menyebut latensi; kartu status "OS: DoT terenkripsi" muncul dalam beberapa detik.
- [ ] Hostname kustom `example.com` → **ditolak** sebelum diterapkan (bukan resolver DoT).
- [ ] Hostname kustom `https://dns.adguard-dns.com` → error format, tombol terapkan nonaktif.
- [ ] Set DoT ke host yang mati lewat Settings HP (mode strict, DNS putus) → pilih AdGuard di aplikasi → berhasil pulih (self-healing).
- [ ] **Uji DNS** (audit) saat AdGuard aktif → "terfilter (aman)"; saat mode pesawat → "Offline", bukan "aman".

## 2b. Cek jaringan

- [ ] Wi-Fi rumah / data seluler: **Periksa** → tiga baris hasil muncul dalam beberapa detik; catat hasilnya per penyedia (IndiHome, Telkomsel, dll.).
- [ ] Proteksi mati + jaringan yang membelokkan DNS → ringkasan oranye "Aktifkan proteksi…".
- [ ] Proteksi aktif + jaringan yang membelokkan DNS → ringkasan hijau "AetherDNS menjaga…".
- [ ] Jaringan yang memblokir port 853 (mis. Wi-Fi kantor/kampus) dengan proteksi aktif → ringkasan merah + tombol jeda berfungsi.
- [ ] Mode pesawat → baris DoT "Tidak bisa diperiksa", aplikasi tidak macet.

## 3. Jeda 15 menit

- [ ] Jeda → status "Dijeda — aktif kembali sekitar pukul HH:MM"; Private DNS di Settings HP = Off.
- [ ] Tunggu ±15 menit (boleh sampai beberapa menit lebih lambat) → proteksi aktif kembali sendiri.
- [ ] Jeda lalu **reboot** sebelum 15 menit → setelah waktu habis, proteksi tetap kembali.
- [ ] Jeda lalu reboot **setelah** waktu habis → proteksi langsung kembali setelah boot.
- [ ] Jeda lalu ubah DNS manual di Settings HP → saat waktu habis, pilihan manual **tidak** ditimpa.
- [ ] "Lanjutkan Proteksi Sekarang" saat dijeda → langsung aktif, alarm dibatalkan.

## 4. Quick Settings Tile

- [ ] Pasang tile lewat tombol di aplikasi (Android 13+) atau manual.
- [ ] Tanpa izin → klik tile membuka aplikasi.
- [ ] Toggle aktif/nonaktif dari tile; status di aplikasi ikut berubah tanpa refresh.
- [ ] Saat dijeda, subtitle tile "Dijeda s/d HH:MM"; klik → langsung aktif kembali.
- [ ] Dari lockscreen (dengan PIN) → diminta buka kunci sebelum toggle.
- [ ] Ubah DNS lewat Settings HP → tarik panel QS → status tile sesuai.

## 5a. Widget layar utama

- [ ] Tekan lama layar utama → Widget → "Status AetherDNS" bisa ditambahkan; pratinjau tampil.
- [ ] Widget mengikuti status: aktif (gradien biru + hostname), dijeda (waktu aktif lagi), mati.
- [ ] Tombol power / jeda bekerja tanpa membuka layar aplikasi (Toast singkat), widget langsung berubah.
- [ ] Ubah DNS dari aplikasi atau tile → widget ikut berubah.
- [ ] Tanpa izin → tombol membuka aplikasi.

## 5. App Shortcuts

- [ ] Tekan lama ikon aplikasi → muncul Aktifkan / Jeda 15 mnt / Matikan.
- [ ] Setiap shortcut menjalankan aksinya, menampilkan Toast, dan **tidak** membuka layar aplikasi.
- [ ] Tanpa izin → shortcut membuka aplikasi (asisten izin).
- [ ] Ganti bahasa HP → buka aplikasi sekali → label shortcut ikut berganti.

## 6. Profil & NextDNS

- [ ] Simpan profil kustom → muncul sebagai kartu; tutup paksa aplikasi lalu buka → masih ada.
- [ ] Simpan hostname yang sama dua kali → ditolak "sudah tersimpan".
- [ ] Helper NextDNS: `abc123` + "Pixel 8" → `pixel--8-abc123.dns.nextdns.io`; tempel URL `https://dns.nextdns.io/abc123` → ID terbaca.
- [ ] Profil tersimpan ikut diukur oleh Uji Kecepatan & Filter.

## 7. Tampilan & navigasi

- [ ] Tab Beranda / Resolver / Lainnya berpindah tanpa kehilangan pilihan resolver; titik merah di Beranda selama izin belum ada.
- [ ] Tanpa izin, tombol utama "Berikan izin terlebih dahulu" menggulir ke kartu aktivasi.
- [ ] Mode gelap: semua kartu (status, aktivasi, audit, resolver) terbaca, tidak ada blok putih/terang yang menyilaukan.
- [ ] Tarik ke bawah di Beranda memperbarui status; tombol aktif/nonaktif memberi getaran singkat.
- [ ] Hapus profil tersimpan meminta konfirmasi.
- [ ] Ikon launcher tampil penuh (bukan kecil di lingkaran putih); themed icon Android 13+ mengikuti warna wallpaper; ikon tile/notifikasi berupa perisai + gelombang.
- [ ] Kartu status aktif bergradien biru dengan teks putih (terang & gelap); "Cara lain: Shizuku atau PC" terlipat secara default.

## 8. Bahasa

- [ ] Bahasa HP English → seluruh UI, tile, shortcut, Toast, dan snackbar berbahasa Inggris.
- [ ] Bahasa HP Indonesia → seluruhnya berbahasa Indonesia.
- [ ] (Android 13+) Settings → Apps → AetherDNS → Language: ganti bahasa tanpa mengubah bahasa HP.
- [ ] Tidak ada teks terpotong pada layar kecil (cek kartu preset dan chip hasil uji).

## 9. Onboarding

- [ ] Instal bersih → onboarding 3 langkah muncul; "Lewati" di langkah 1 langsung ke layar utama dan tidak muncul lagi.
- [ ] Beri izin via Shizuku di langkah 2 → langkah 3 tombolnya "Aktifkan & Selesai" → resolver terpilih langsung aktif.
- [ ] Tanpa izin → langkah 3 "Selesai" → layar utama dengan asisten izin.
- [ ] Update dari versi sebelumnya yang sudah punya izin → onboarding **tidak** muncul.

## 10. Otomasi (Tasker / MacroDroid)

- [ ] Sakelar Otomasi mati → Send Intent dari Tasker tidak berpengaruh apa pun.
- [ ] Sakelar nyala, token benar: `.DISABLE`, `.ENABLE`, `.PAUSE` (minutes:5), `.APPLY` (profile:AdGuard DNS) semuanya bekerja.
- [ ] `.APPLY` dengan hostname:example.com → ditolak, resolver lama tetap aktif.
- [ ] Token salah / tanpa token → tidak ada perubahan.
- [ ] Buat token baru → perintah dengan token lama ditolak.
- [ ] Reboot → status sakelar otomasi tetap sama.

## 11. Backup

- [ ] (Opsional) `adb shell bmgr backupnow <paket>` lalu restore di perangkat lain → profil tersimpan kembali, status jeda dan token otomasi **tidak** ikut.
