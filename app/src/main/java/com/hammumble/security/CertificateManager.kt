package com.hammumble.security

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*

/**
 * Manages client certificates for Mumble authentication.
 * Generates and stores self-signed certificates in app private storage (PKCS12 format).
 * 
 * NOTE: We use PKCS12 file storage instead of Android KeyStore because:
 * 1. Android KeyStore keys have purpose restrictions (SIGN/VERIFY)
 * 2. SSL/TLS needs keys that can encrypt during handshake
 * 3. Purpose-restricted keys cause "INCOMPATIBLE_PADDING_MODE" errors
 */
class CertificateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CertificateManager"
        private const val KEYSTORE_FILE = "hammumble.p12"
        private const val KEYSTORE_PASSWORD = "hammumble_cert_2025"
        const val CERT_ALIAS = "HamMumbleCert"
        private const val CERT_VALIDITY_YEARS = 10
        
        // Certificate subject details
        private const val CERT_SUBJECT = "CN=HamMumble User, O=HamMumble, C=US"
        
        init {
            // Register BouncyCastle provider if not already registered
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            } else {
            }
        }
    }
    
    private val keystoreFile: File by lazy {
        File(context.filesDir, KEYSTORE_FILE)
    }
    
    private fun loadKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12")
        
        if (keystoreFile.exists()) {
            FileInputStream(keystoreFile).use {
                keyStore.load(it, KEYSTORE_PASSWORD.toCharArray())
            }
        } else {
            keyStore.load(null, null)
        }
        
        return keyStore
    }
    
    private fun saveKeyStore(keyStore: KeyStore) {
        FileOutputStream(keystoreFile).use {
            keyStore.store(it, KEYSTORE_PASSWORD.toCharArray())
        }
    }
    
    /**
     * Check if a client certificate exists.
     */
    fun hasCertificate(): Boolean {
        return try {
            val keyStore = loadKeyStore()
            keyStore.containsAlias(CERT_ALIAS)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate a new self-signed certificate for Mumble authentication.
     * Uses PKCS12 file storage (NOT Android KeyStore).
     */
    fun generateCertificate(): Boolean {
        return try {
            
            val keyStore = loadKeyStore()
            
            // Delete existing certificate if present
            if (keyStore.containsAlias(CERT_ALIAS)) {
                keyStore.deleteEntry(CERT_ALIAS)
            }
            
            // Generate key pair using standard Java crypto (NOT Android KeyStore)
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Create self-signed certificate using BouncyCastle
            val startDate = Date()
            val endDate = Calendar.getInstance().apply {
                time = startDate
                add(Calendar.YEAR, CERT_VALIDITY_YEARS)
            }.time
            
            val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
            val subject = X500Name(CERT_SUBJECT)
            
            val certBuilder = X509v3CertificateBuilder(
                subject, // Issuer (self-signed)
                serialNumber,
                startDate,
                endDate,
                subject, // Subject
                SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
            )
            
            // Sign the certificate
            // Note: Don't explicitly set provider, let it use the registered BC provider
            val signer = JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.private)
            
            val certHolder = certBuilder.build(signer)
            val cert = JcaX509CertificateConverter()
                .getCertificate(certHolder)
            
            // Store in keystore
            keyStore.setKeyEntry(
                CERT_ALIAS,
                keyPair.private,
                KEYSTORE_PASSWORD.toCharArray(),
                arrayOf(cert)
            )
            
            saveKeyStore(keyStore)
            
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get the certificate chain in DER format for logging purposes.
     * Returns null if no certificate exists.
     */
    fun getCertificateChainDER(): ByteArray? {
        return try {
            if (!hasCertificate()) {
                if (!generateCertificate()) {
                    return null
                }
            }
            
            val keyStore = loadKeyStore()
            val certificateChain = keyStore.getCertificateChain(CERT_ALIAS)
            if (certificateChain == null || certificateChain.isEmpty()) {
                return null
            }
            
            // Encode entire chain to DER format
            val derBytes = certificateChain[0].encoded
            
            derBytes
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the certificate for display purposes.
     */
    fun getCertificate(): X509Certificate? {
        return try {
            if (!hasCertificate()) {
                return null
            }
            val keyStore = loadKeyStore()
            keyStore.getCertificate(CERT_ALIAS) as? X509Certificate
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get the KeyStore for use with SSL KeyManagers.
     */
    fun getKeyStore(): KeyStore {
        return loadKeyStore()
    }
    
    /**
     * Get the keystore password.
     */
    fun getKeystorePassword(): String = KEYSTORE_PASSWORD
    
    /**
     * Get certificate information as a readable string.
     */
    fun getCertificateInfo(): String? {
        val cert = getCertificate() ?: return null
        
        return buildString {
            appendLine("Certificate Information:")
            appendLine("Subject: ${cert.subjectDN}")
            appendLine("Issuer: ${cert.issuerDN}")
            appendLine("Valid From: ${cert.notBefore}")
            appendLine("Valid Until: ${cert.notAfter}")
            appendLine("Serial Number: ${cert.serialNumber}")
            appendLine("Signature Algorithm: ${cert.sigAlgName}")
            appendLine("Public Key Algorithm: ${cert.publicKey.algorithm}")
        }
    }
    
    /**
     * Delete the stored certificate.
     */
    fun deleteCertificate(): Boolean {
        return try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(CERT_ALIAS)) {
                keyStore.deleteEntry(CERT_ALIAS)
                saveKeyStore(keyStore)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get the private key for the certificate.
     */
    fun getPrivateKey(): PrivateKey? {
        return try {
            if (!hasCertificate()) {
                return null
            }
            val keyStore = loadKeyStore()
            keyStore.getKey(CERT_ALIAS, KEYSTORE_PASSWORD.toCharArray()) as? PrivateKey
        } catch (e: Exception) {
            null
        }
    }
}
