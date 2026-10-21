# Panduan Rilis AetherDNS

Rilis dibangun dan ditandatangani otomatis oleh `.github/workflows/release.yml` saat tag `vX.Y.Z` di-push.

## 1. Sekali saja: buat keystore

Jalankan di komputer Anda (butuh JDK; `keytool` ikut terpasang bersama Java):

```bash
keytool -genkeypair -v -keystore aetherdns-release.jks -alias aetherdns \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Simpan `aetherdns-release.jks` dan kata sandinya di tempat aman (mis. password manager + cadangan offline).
- **Jangan pernah kehilangan keystore ini.** APK yang ditandatangani kunci lain tidak bisa dipasang sebagai update;
  pengguna harus uninstall dulu dan kehilangan pengaturan.
- Jangan commit file `.jks` ke repositori.

## 2. Sekali saja: isi secrets GitHub

GitHub → repositori → **Settings → Secrets and variables → Actions → New repository secret**:

| Nama | Isi |
|---|---|
| `AETHERDNS_KEYSTORE_BASE64` | hasil `base64 -w0 aetherdns-release.jks` (macOS: `base64 -i aetherdns-release.jks`; Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("aetherdns-release.jks"))`) |
| `AETHERDNS_KEYSTORE_PASSWORD` | kata sandi keystore |
| `AETHERDNS_KEY_ALIAS` | `aetherdns` (alias di perintah keytool) |
| `AETHERDNS_KEY_PASSWORD` | kata sandi kunci (sama dengan keystore bila tidak dibedakan) |

## 3. Setiap rilis

1. Jalankan checklist [`docs/TESTING.md`](TESTING.md) di HP.
2. Pastikan `versionName`/`versionCode` di `app/build.gradle.kts` sudah dinaikkan dan `CHANGELOG.md` punya bagian `## [X.Y.Z]`.
3. Merge ke `main`, lalu:
   ```bash
   git checkout main && git pull
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
4. Tunggu workflow **Release** selesai. APK `AetherDNS-vX.Y.Z.apk` + checksum SHA-256 muncul di GitHub Releases,
   dengan catatan rilis dari `CHANGELOG.md`.

Workflow gagal dengan pesan jelas bila secrets belum diisi atau tag tidak sama dengan `versionName`.
