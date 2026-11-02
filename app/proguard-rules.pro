# Keep model classes
-keep class com.security.aetherdns.model.** { *; }

# Aktivasi bawaan (libadb-android): provider TLS Conscrypt dan Conscrypt.exportKeyingMaterial dicari lewat refleksi
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
# BouncyCastle dipakai lewat API langsung (tanpa provider JCA), jadi boleh di-shrink R8
-dontwarn org.bouncycastle.**
# SPAKE2 (JNI) dan libadb
-keep class io.github.muntashirakon.** { *; }
-dontwarn io.github.muntashirakon.**
