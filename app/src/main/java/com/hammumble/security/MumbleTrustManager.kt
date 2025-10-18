package com.hammumble.security

import android.util.Log
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Custom trust manager for Mumble servers.
 * Accepts all server certificates (similar to Mumla's approach).
 * 
 * In production, you might want to:
 * - Store accepted certificates
 * - Prompt user to accept unknown certificates
 * - Implement certificate pinning
 */
class MumbleTrustManager : X509TrustManager {
    
    companion object {
        private const val TAG = "MumbleTrustManager"
    }
    
    private val acceptedIssuers = arrayOf<X509Certificate>()
    
    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // We don't validate client certificates
    }
    
    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Accept all server certificates
        // This is common for Mumble clients as servers often use self-signed certificates
        if (chain == null || chain.isEmpty()) {
            return
        }
        
        chain.forEachIndexed { _index, _cert ->
        }
        
        // In a production app, you would:
        // 1. Check if certificate is in trusted store
        // 2. If not, prompt user to accept
        // 3. Store accepted certificates
        // For now, we accept all certificates like Mumla does
    }
    
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return acceptedIssuers
    }
}
