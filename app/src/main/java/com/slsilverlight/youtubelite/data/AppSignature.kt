package com.slsilverlight.youtubelite.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** SHA-1 of the app's signing certificate, as Google's X-Android-Cert header expects it. */
@Suppress("DEPRECATION")
fun signingCertSha1(context: Context): String? {
    val cert: Signature = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } ?: return null
    return MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
        .joinToString("") { "%02X".format(it) }
}
