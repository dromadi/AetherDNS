package com.security.aetherdns.shizuku;

interface IGrantService {
    void destroy() = 16777114;

    /** Menjalankan `pm grant`; mengembalikan "<exit code>\n<stdout+stderr>". */
    String grantSecureSettings() = 1;
}
