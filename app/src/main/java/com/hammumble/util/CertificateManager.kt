package com.hammumble.util

import android.content.Context
import android.net.Uri
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Manages client certificates for Mumble connections.
 * Handles importing, storing, and listing .p12 (PKCS#12) certificate files.
 */
object CertificateManager {
    private const val TAG = "CertificateManager"
    private const val CERT_DIR_NAME = "certificates"
    
    /**
     * Get the directory where certificates are stored.
     */
    private fun getCertificatesDir(context: Context): File {
        val dir = File(context.filesDir, CERT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Import a certificate file from a URI (e.g., from file picker).
     * Copies the certificate to app's private storage.
     * 
     * @param context Application context
     * @param uri URI of the certificate file to import
     * @param fileName Desired filename (without path)
     * @return The absolute path to the imported certificate, or null if failed
     */
    fun importCertificate(context: Context, uri: Uri, fileName: String): String? {
        try {
            val certDir = getCertificatesDir(context)
            val destFile = File(certDir, fileName)
            
            // Copy file from URI to app storage
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Certificate imported successfully: ${destFile.absolutePath}")
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import certificate", e)
            return null
        }
    }
    
    /**
     * Validate that a certificate file exists and is readable.
     * 
     * @param certificatePath Absolute path to the certificate file
     * @return true if the certificate is valid and accessible
     */
    fun validateCertificate(certificatePath: String?): Boolean {
        if (certificatePath == null) return false
        
        val file = File(certificatePath)
        return file.exists() && file.canRead()
    }
    
    /**
     * Validate that a certificate can be loaded with the given password.
     * 
     * @param certificatePath Absolute path to the certificate file
     * @param password Password for the certificate
     * @return true if the certificate can be loaded successfully
     */
    fun validateCertificateWithPassword(certificatePath: String?, password: String): Boolean {
        if (certificatePath == null) return false
        
        try {
            val file = File(certificatePath)
            if (!file.exists()) return false
            
            // Try to load the keystore to verify password
            val keyStore = KeyStore.getInstance("PKCS12")
            FileInputStream(file).use { fis ->
                keyStore.load(fis, password.toCharArray())
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Certificate validation failed", e)
            return false
        }
    }
    
    /**
     * Read certificate file as byte array.
     * 
     * @param certificatePath Absolute path to the certificate file
     * @return Certificate bytes, or null if failed
     */
    fun readCertificate(certificatePath: String?): ByteArray? {
        if (certificatePath == null) return null
        
        try {
            val file = File(certificatePath)
            if (!file.exists()) return null
            
            return file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read certificate", e)
            return null
        }
    }
    
    /**
     * List all certificates in the app's certificate directory.
     * 
     * @param context Application context
     * @return List of certificate file paths
     */
    fun listCertificates(context: Context): List<String> {
        val certDir = getCertificatesDir(context)
        return certDir.listFiles()
            ?.filter { it.isFile && (it.extension == "p12" || it.extension == "pfx") }
            ?.map { it.absolutePath }
            ?: emptyList()
    }
    
    /**
     * Delete a certificate file.
     * 
     * @param certificatePath Absolute path to the certificate file
     * @return true if deleted successfully
     */
    fun deleteCertificate(certificatePath: String?): Boolean {
        if (certificatePath == null) return false
        
        try {
            val file = File(certificatePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Certificate deleted: $certificatePath")
                }
                return deleted
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete certificate", e)
            return false
        }
    }
    
    /**
     * Get the filename from a certificate path.
     * 
     * @param certificatePath Absolute path to the certificate file
     * @return Filename only, or null if invalid
     */
    fun getFileName(certificatePath: String?): String? {
        if (certificatePath == null) return null
        return File(certificatePath).name
    }
    
    /**
     * Generate a new self-signed client certificate.
     * 
     * @param context Application context
     * @param commonName Common name (CN) for the certificate (usually username)
     * @param email Email address for the certificate (optional)
     * @param password Password to protect the certificate
     * @param validityDays How many days the certificate should be valid (default: 3650 = 10 years)
     * @return The absolute path to the generated certificate, or null if failed
     */
    fun generateCertificate(
        context: Context,
        commonName: String,
        email: String = "",
        password: String,
        validityDays: Int = 3650
    ): String? {
        try {
            // Ensure BouncyCastle is registered as a security provider
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
            
            Log.d(TAG, "Generating certificate for: $commonName")
            
            // Generate RSA key pair using default Android provider
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            
            // Build the X500 name (subject/issuer)
            val nameBuilder = StringBuilder("CN=$commonName")
            if (email.isNotBlank()) {
                nameBuilder.append(", E=$email")
            }
            nameBuilder.append(", O=HamMumble, OU=Mumble Client")
            
            val subject = X500Name(nameBuilder.toString())
            val issuer = subject // Self-signed, so issuer = subject
            
            // Certificate serial number
            val serialNumber = BigInteger.valueOf(System.currentTimeMillis())
            
            // Validity period
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + (validityDays * 24L * 60 * 60 * 1000))
            
            // Build the certificate
            val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
            val certificateBuilder = X509v3CertificateBuilder(
                issuer,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                subjectPublicKeyInfo
            )
            
            // Add basic constraints extension (not a CA)
            certificateBuilder.addExtension(
                Extension.basicConstraints,
                true,
                BasicConstraints(false)
            )
            
            // Sign the certificate
            // Note: Don't set provider explicitly for ContentSigner - use default
            val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.private)
            
            val certificateHolder = certificateBuilder.build(contentSigner)
            // Use default provider for certificate conversion (not BouncyCastle)
            val certificate: X509Certificate = JcaX509CertificateConverter()
                .getCertificate(certificateHolder)
            
            // Create PKCS#12 keystore
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(null, null)
            
            // Store the private key and certificate
            val chain = arrayOf(certificate)
            keyStore.setKeyEntry("mumble-client", keyPair.private, password.toCharArray(), chain)
            
            // Save to file
            val certDir = getCertificatesDir(context)
            val fileName = "${commonName.replace(Regex("[^a-zA-Z0-9]"), "_")}_${System.currentTimeMillis()}.p12"
            val certFile = File(certDir, fileName)
            
            FileOutputStream(certFile).use { fos ->
                keyStore.store(fos, password.toCharArray())
            }
            
            Log.d(TAG, "Certificate generated successfully: ${certFile.absolutePath}")
            return certFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate certificate", e)
            return null
        }
    }
}
