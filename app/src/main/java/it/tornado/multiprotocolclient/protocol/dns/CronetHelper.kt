package it.tornado.multiprotocolclient.protocol.dns

import android.content.Context
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.chromium.net.CronetException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper singleton for performing DNS over HTTPS requests using Cronet (HTTP/3 via QUIC).
 * Cronet is initialized with QUIC enabled to force HTTP/3 transport when available.
 */
object CronetHelper {

    private var cronetEngine: CronetEngine? = null
    private val executor = Executors.newCachedThreadPool()

    /**
     * Initialize the Cronet engine with QUIC/HTTP3 enabled.
     * Must be called with an Android Context before making requests.
     */
    fun initialize(context: Context) {
        if (cronetEngine != null) return
        try {
            // Install the Cronet provider from Google Play Services
            CronetProviderInstaller.installProvider(context)

            cronetEngine = CronetEngine.Builder(context)
                .enableHttp2(true)
                .enableQuic(true) // Enable QUIC for HTTP/3
                .build()
        } catch (e: Exception) {
            // Fallback: try building without Play Services provider
            cronetEngine = CronetEngine.Builder(context)
                .enableHttp2(true)
                .enableQuic(true)
                .build()
        }
    }

    /**
     * Perform a DNS over HTTPS POST request using HTTP/3 (Cronet).
     *
     * @param url The DoH endpoint URL (e.g., "https://cloudflare-dns.com/dns-query")
     * @param wireFormat The DNS query in binary wire format (RFC 8484)
     * @return The DNS response as a byte array
     */
    suspend fun performDoH3Request(url: String, wireFormat: ByteArray): ByteArray {
        val engine = cronetEngine
            ?: throw IllegalStateException("CronetEngine not initialized. Call CronetHelper.initialize(context) first.")

        return suspendCancellableCoroutine { continuation ->
            val responseBody = ByteArrayOutputStream()

            val request = engine.newUrlRequestBuilder(
                url,
                object : UrlRequest.Callback() {
                    override fun onRedirectReceived(
                        request: UrlRequest,
                        info: UrlResponseInfo,
                        newLocationUrl: String
                    ) {
                        request.followRedirect()
                    }

                    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                        val httpCode = info.httpStatusCode
                        if (httpCode != 200) {
                            continuation.resumeWithException(
                                Exception("DoH3 HTTP error: $httpCode")
                            )
                            return
                        }
                        // Allocate buffer and start reading
                        request.read(ByteBuffer.allocateDirect(16 * 1024))
                    }

                    override fun onReadCompleted(
                        request: UrlRequest,
                        info: UrlResponseInfo,
                        byteBuffer: ByteBuffer
                    ) {
                        byteBuffer.flip()
                        val bytes = ByteArray(byteBuffer.remaining())
                        byteBuffer.get(bytes)
                        responseBody.write(bytes)
                        byteBuffer.clear()
                        request.read(byteBuffer)
                    }

                    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                        continuation.resume(responseBody.toByteArray())
                    }

                    override fun onFailed(
                        request: UrlRequest,
                        info: UrlResponseInfo?,
                        error: CronetException
                    ) {
                        continuation.resumeWithException(
                            Exception("DoH3 request failed: ${error.message}", error)
                        )
                    }
                },
                executor
            )
                .setHttpMethod("POST")
                .addHeader("Content-Type", "application/dns-message")
                .addHeader("Accept", "application/dns-message")
                .setUploadDataProvider(UploadDataProviders.create(wireFormat), executor)
                .build()

            continuation.invokeOnCancellation {
                request.cancel()
            }

            request.start()
        }
    }

    /**
     * Get information about the protocol used in the last request.
     */
    fun getProtocolInfo(info: UrlResponseInfo?): String {
        return info?.negotiatedProtocol ?: "unknown"
    }
}
