package com.hammumble.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * Trust store manager for accepting server certificates.
 * Based on Mumla's MumlaTrustStore.
 */
object HamMumbleTrustStore {
    private const val TAG = "HamMumbleTrustStore"
    private const val STORE_FILE = "hammumble-trust-store.bks"
    private const val STORE_PASSWORD = "hammumble"
    private const val STORE_FORMAT = "BKS"

    /**
     * Gets the trust store file path.
     */
    fun getTrustStorePath(context: Context): String? {
        val file = File(context.filesDir, STORE_FILE)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Gets the trust store password.
     */
    fun getTrustStorePassword(): String = STORE_PASSWORD

    /**
     * Gets the trust store format.
     */
    fun getTrustStoreFormat(): String = STORE_FORMAT

    /**
     * Loads or creates the trust store.
     */
    fun getTrustStore(context: Context): KeyStore {
        val keyStore = KeyStore.getInstance(STORE_FORMAT)
        val file = File(context.filesDir, STORE_FILE)

        if (file.exists()) {
            try {
                FileInputStream(file).use { fis ->
                    keyStore.load(fis, STORE_PASSWORD.toCharArray())
                }
                Log.d(TAG, "Loaded existing trust store")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load trust store, creating new one", e)
                keyStore.load(null, STORE_PASSWORD.toCharArray())
            }
        } else {
            // Create new empty trust store
            keyStore.load(null, STORE_PASSWORD.toCharArray())
            Log.d(TAG, "Created new trust store")
        }

        return keyStore
    }

    /**
     * Saves the trust store to disk.
     */
    fun saveTrustStore(context: Context, keyStore: KeyStore) {
        val file = File(context.filesDir, STORE_FILE)
        try {
            FileOutputStream(file).use { fos ->
                keyStore.store(fos, STORE_PASSWORD.toCharArray())
            }
            Log.d(TAG, "Saved trust store with ${keyStore.size()} certificates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save trust store", e)
            throw e
        }
    }

    /**
     * Adds a certificate to the trust store.
     */
    fun addCertificate(context: Context, alias: String, certificate: X509Certificate) {
        try {
            val trustStore = getTrustStore(context)
            trustStore.setCertificateEntry(alias, certificate)
            saveTrustStore(context, trustStore)
            Log.i(TAG, "Added certificate with alias: $alias")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add certificate", e)
            throw e
        }
    }

    /**
     * Clears all certificates from the trust store.
     */
    fun clearTrustStore(context: Context) {
        val file = File(context.filesDir, STORE_FILE)
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "Cleared trust store")
        }
    }

    /**
     * Checks if a certificate is already trusted.
     */
    fun isCertificateTrusted(context: Context, alias: String): Boolean {
        return try {
            val trustStore = getTrustStore(context)
            trustStore.containsAlias(alias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check certificate", e)
            false
        }
    }
}
