package com.mandala.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Interceptor
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class TestServer(
    val id: String,
    val name: String,
    val location: String,
    val host: String,
    val downloadUrl: String,
    val uploadUrl: String
)

val defaultTestServers = listOf(
    TestServer("auto", "Auto Select", "Best Server", "speed.cloudflare.com", "https://speed.cloudflare.com/__down?bytes=50000000", "https://speed.cloudflare.com/__up"),
    TestServer("cgk", "CepatNet", "Jakarta", "speed.cloudflare.com", "https://speed.cloudflare.com/__down?bytes=50000000", "https://speed.cloudflare.com/__up"),
    TestServer("sub", "Indosat Ooredoo", "Surabaya", "speed.cloudflare.com", "https://speed.cloudflare.com/__down?bytes=50000000", "https://speed.cloudflare.com/__up"),
    TestServer("sin", "Singtel", "Singapura", "speed.cloudflare.com", "https://speed.cloudflare.com/__down?bytes=50000000", "https://speed.cloudflare.com/__up")
)

sealed class SpeedTestState {
    object Idle : SpeedTestState()
    data class PingTest(val progress: Float, val currentPingMs: Long, val jitterMs: Long, val packetLoss: Float) : SpeedTestState()
    data class DownloadTest(val progress: Float, val currentSpeedMbps: Double, val pingMs: Long, val jitterMs: Long, val packetLoss: Float) : SpeedTestState()
    data class UploadTest(val progress: Float, val currentSpeedMbps: Double, val downloadSpeedMbps: Double, val pingMs: Long, val jitterMs: Long, val packetLoss: Float) : SpeedTestState()
    data class Completed(val pingMs: Long, val jitterMs: Long, val packetLoss: Float, val downloadSpeedMbps: Double, val uploadSpeedMbps: Double) : SpeedTestState()
    data class Error(val message: String) : SpeedTestState()
}

class SpeedTestManager {

    private val speedTestDispatcher = Executors.newFixedThreadPool(16) { r ->
        Thread(r).apply {
            name = "SpeedTest-IO"
            priority = Thread.MAX_PRIORITY
        }
    }.asCoroutineDispatcher()

    private fun createHttpClient(connectTimeoutSec: Long, readTimeoutSec: Long, writeTimeoutSec: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSec, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val pingClient = createHttpClient(3, 3, 3)
    private val client = createHttpClient(15, 15, 15)

    fun runSpeedTest(requestedServer: TestServer): Flow<SpeedTestState> = channelFlow {
        val activeDownloadCalls = java.util.Collections.synchronizedList(mutableListOf<okhttp3.Call>())
        val activeUploadCalls = java.util.Collections.synchronizedList(mutableListOf<okhttp3.Call>())

        try {
            send(SpeedTestState.PingTest(0.0f, 0L, 0L, 0f))
            
            val server = if (requestedServer.id == "auto") {
                var bestServer = defaultTestServers.getOrNull(1) ?: requestedServer
                var minPing = Long.MAX_VALUE
                for (candidate in defaultTestServers) {
                    if (candidate.id == "auto") continue
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                    try {
                        val start = System.currentTimeMillis()
                        val request = Request.Builder().url("https://${candidate.host}/").head().build()
                        pingClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val ping = System.currentTimeMillis() - start
                                if (ping < minPing) {
                                    minPing = ping
                                    bestServer = candidate
                                }
                            }
                        }
                    } catch (e: Exception) { }
                }
                bestServer
            } else {
                requestedServer
            }

            // 1. Latency (Ping) Test with Jitter and Packet Loss
            var totalPing = 0L
            val iterations = 10
            var successfulPings = 0
            var failedPings = 0
            val pings = mutableListOf<Long>()
            
            for (i in 1..iterations) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                val startTime = System.currentTimeMillis()
                val request = Request.Builder()
                    .url("https://${server.host}/")
                    .head()
                    .build()
                    
                try {
                    pingClient.newCall(request).execute().use { response ->
                        val duration = System.currentTimeMillis() - startTime
                        totalPing += duration
                        successfulPings++
                        pings.add(duration)
                    }
                } catch (e: Exception) {
                    failedPings++
                }
                
                val curPing = if (successfulPings > 0) totalPing / successfulPings else 0L
                val jitter = calculateJitter(pings)
                val pktLoss = (failedPings.toFloat() / i) * 100f
                send(SpeedTestState.PingTest(i.toFloat() / iterations, curPing, jitter, pktLoss))
                delay(50)
            }

            val finalPing = if (successfulPings > 0) totalPing / successfulPings else 0L
            val finalJitter = calculateJitter(pings)
            val finalPacketLoss = (failedPings.toFloat() / iterations) * 100f
            
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return@channelFlow

            // 2. Download Speed Test (Multi-threaded, Time-based)
            send(SpeedTestState.DownloadTest(0f, 0.0, finalPing, finalJitter, finalPacketLoss))
            
            val downloadThreads = 4
            val testDurationNanos = 10_000_000_000L // 10 seconds
            val bytesRead = AtomicLong(0L)
            var finalDownloadSpeed = 0.0
            val downloadStartTime = System.nanoTime()
            var downloadError: String? = null
            
            coroutineScope {
                val jobs = (1..downloadThreads).map {
                    async(speedTestDispatcher) {
                        val downloadRequest = Request.Builder()
                            .url(server.downloadUrl)
                            .build()
                        try {
                            if (!this@async.isActive) return@async
                            val call = client.newCall(downloadRequest)
                            synchronized(activeDownloadCalls) { activeDownloadCalls.add(call) }
                            call.execute().use { response ->
                                if (!response.isSuccessful) throw IOException("Failed to download: ${response.code}")
                                val body = response.body ?: throw IOException("Empty body")
                                val inputStream = body.byteStream()
                                val buffer = ByteArray(65536)
                                var read = inputStream.read(buffer)
                                while (read != -1 && this@async.isActive) {
                                    bytesRead.addAndGet(read.toLong())
                                    read = inputStream.read(buffer)
                                }
                            }
                        } catch (e: Exception) {
                            if (e.message != "Canceled" && e.message != "Socket closed" && e !is java.io.InterruptedIOException) {
                                downloadError = e.localizedMessage
                            }
                        }
                    }
                }
                
                // Progress monitor
                launch(Dispatchers.Default) {
                    var lastBytes = 0L
                    var lastTime = System.nanoTime()
                    var lastEmittedMbps = 0.0
                    
                    val ignoreStartNanos = 2_000_000_000L // 2 seconds
                    var bytesAtStartOfWindow = 0L
                    var timeAtStartOfWindow = 0L
                    
                    while (System.nanoTime() - downloadStartTime < testDurationNanos && downloadError == null && this@coroutineScope.isActive) {
                        delay(100)
                        val currentBytes = bytesRead.get()
                        val currentTime = System.nanoTime()
                        val elapsedTestNanos = currentTime - downloadStartTime
                        val elapsedNanos = currentTime - lastTime
                        
                        if (elapsedTestNanos > ignoreStartNanos && bytesAtStartOfWindow == 0L) {
                            bytesAtStartOfWindow = currentBytes
                            timeAtStartOfWindow = currentTime
                        }
                        
                        if (elapsedNanos > 0) {
                            val bytesDiff = currentBytes - lastBytes
                            val megabits = (bytesDiff * 8.0) / 1_000_000.0
                            val rawSpeedMbps = megabits / (elapsedNanos / 1_000_000_000.0)
                            
                            val speedMbps = if (lastEmittedMbps == 0.0) rawSpeedMbps else (0.2 * rawSpeedMbps + 0.8 * lastEmittedMbps)
                            lastEmittedMbps = speedMbps
                            
                            val progress = (elapsedTestNanos.toDouble() / testDurationNanos.toDouble()).coerceIn(0.0, 1.0).toFloat()
                            
                            send(SpeedTestState.DownloadTest(progress, speedMbps, finalPing, finalJitter, finalPacketLoss))
                        }
                        lastBytes = currentBytes
                        lastTime = currentTime
                    }
                    
                    synchronized(activeDownloadCalls) {
                        activeDownloadCalls.forEach { 
                            try { it.cancel() } catch(e: Exception) {}
                        }
                        activeDownloadCalls.clear()
                    }
                    
                    if (downloadError == null) {
                        if (timeAtStartOfWindow > 0 && lastTime > timeAtStartOfWindow) {
                            val validBytes = lastBytes - bytesAtStartOfWindow
                            val validTimeNanos = lastTime - timeAtStartOfWindow
                            finalDownloadSpeed = ((validBytes * 8.0) / 1_000_000.0) / (validTimeNanos / 1_000_000_000.0)
                        } else {
                            val totalTimeNanos = lastTime - downloadStartTime
                            if (totalTimeNanos > 0) {
                                finalDownloadSpeed = ((lastBytes * 8.0) / 1_000_000.0) / (totalTimeNanos / 1_000_000_000.0)
                            }
                        }
                    }
                }
                
                jobs.awaitAll()
            }

            if (downloadError != null) {
                send(SpeedTestState.Error("Download Gagal: $downloadError"))
                return@channelFlow
            }
            
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return@channelFlow

            // 3. Upload Speed Test (Multi-threaded, Time-based)
            send(SpeedTestState.UploadTest(0f, 0.0, finalDownloadSpeed, finalPing, finalJitter, finalPacketLoss))
            
            val uploadThreads = 3
            val bytesWritten = AtomicLong(0L)
            var finalUploadSpeed = 0.0
            val uploadStartTime = System.nanoTime()
            var uploadError: String? = null
            
            coroutineScope {
                val jobs = (1..uploadThreads).map {
                    async(speedTestDispatcher) {
                        while (System.nanoTime() - uploadStartTime < testDurationNanos && uploadError == null && this@async.isActive) {
                            val customRequestBody = object : RequestBody() {
                                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                                override fun contentLength() = 10_000_000L // 10MB per request
                                override fun writeTo(sink: BufferedSink) {
                                    val chunkSize = 65536
                                    val data = ByteArray(chunkSize)
                                    var written = 0L
                                    while (written < 10_000_000L && this@async.isActive) {
                                        val toWrite = Math.min(chunkSize.toLong(), 10_000_000L - written).toInt()
                                        try {
                                            sink.write(data, 0, toWrite)
                                            sink.flush()
                                            written += toWrite
                                            bytesWritten.addAndGet(toWrite.toLong())
                                        } catch (e: Exception) {
                                            break // Broken pipe or stream closed, stop this request
                                        }
                                    }
                                }
                            }
                            val uploadRequest = Request.Builder()
                                .url(server.uploadUrl)
                                .post(customRequestBody)
                                .build()
                                
                            try {
                                if (!this@async.isActive) break
                                val call = client.newCall(uploadRequest)
                                synchronized(activeUploadCalls) { activeUploadCalls.add(call) }
                                call.execute().use { response ->
                                    // Ignore response code
                                }
                            } catch (e: Exception) {
                                // Ignored, just retry to keep bytes pumping
                                delay(500)
                            }
                        }
                    }
                }
                
                // Progress monitor
                launch(Dispatchers.Default) {
                    var lastBytes = 0L
                    var lastTime = System.nanoTime()
                    var lastEmittedMbps = 0.0
                    
                    val ignoreStartNanos = 2_000_000_000L // 2 seconds
                    var bytesAtStartOfWindow = 0L
                    var timeAtStartOfWindow = 0L
                    
                    while (System.nanoTime() - uploadStartTime < testDurationNanos && uploadError == null && this@coroutineScope.isActive) {
                        delay(100)
                        val currentBytes = bytesWritten.get()
                        val currentTime = System.nanoTime()
                        val elapsedTestNanos = currentTime - uploadStartTime
                        val elapsedNanos = currentTime - lastTime
                        
                        if (elapsedTestNanos > ignoreStartNanos && bytesAtStartOfWindow == 0L) {
                            bytesAtStartOfWindow = currentBytes
                            timeAtStartOfWindow = currentTime
                        }
                        
                        if (elapsedNanos > 0) {
                            val bytesDiff = currentBytes - lastBytes
                            val megabits = (bytesDiff * 8.0) / 1_000_000.0
                            val rawSpeedMbps = megabits / (elapsedNanos / 1_000_000_000.0)
                            
                            val speedMbps = if (lastEmittedMbps == 0.0) rawSpeedMbps else (0.2 * rawSpeedMbps + 0.8 * lastEmittedMbps)
                            lastEmittedMbps = speedMbps
                            
                            val progress = (elapsedTestNanos.toDouble() / testDurationNanos.toDouble()).coerceIn(0.0, 1.0).toFloat()
                            
                            send(SpeedTestState.UploadTest(progress, speedMbps, finalDownloadSpeed, finalPing, finalJitter, finalPacketLoss))
                        }
                        lastBytes = currentBytes
                        lastTime = currentTime
                    }
                    
                    synchronized(activeUploadCalls) {
                        activeUploadCalls.forEach { 
                            try { it.cancel() } catch(e: Exception) {}
                        }
                        activeUploadCalls.clear()
                    }
                    
                    if (uploadError == null) {
                        if (timeAtStartOfWindow > 0 && lastTime > timeAtStartOfWindow) {
                            val validBytes = lastBytes - bytesAtStartOfWindow
                            val validTimeNanos = lastTime - timeAtStartOfWindow
                            finalUploadSpeed = ((validBytes * 8.0) / 1_000_000.0) / (validTimeNanos / 1_000_000_000.0)
                        } else {
                            val totalTimeNanos = lastTime - uploadStartTime
                            if (totalTimeNanos > 0) {
                                finalUploadSpeed = ((lastBytes * 8.0) / 1_000_000.0) / (totalTimeNanos / 1_000_000_000.0)
                            }
                        }
                    }
                }
                
                jobs.awaitAll()
            }
            
            if (uploadError != null) {
                send(SpeedTestState.Error("Upload Gagal: $uploadError"))
                return@channelFlow
            }
            
            send(SpeedTestState.Completed(finalPing, finalJitter, finalPacketLoss, finalDownloadSpeed, finalUploadSpeed))
        } finally {
            synchronized(activeDownloadCalls) {
                activeDownloadCalls.forEach { 
                    try { it.cancel() } catch (e: Exception) {}
                }
                activeDownloadCalls.clear()
            }
            synchronized(activeUploadCalls) {
                activeUploadCalls.forEach { 
                    try { it.cancel() } catch (e: Exception) {}
                }
                activeUploadCalls.clear()
            }
        }
    }.flowOn(speedTestDispatcher)
    
    private fun calculateJitter(pings: List<Long>): Long {
        if (pings.size < 2) return 0L
        var totalJitter = 0L
        for (i in 1 until pings.size) {
            totalJitter += abs(pings[i] - pings[i - 1])
        }
        return totalJitter / (pings.size - 1)
    }

    fun onDestroy() {
        try {
            speedTestDispatcher.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            pingClient.dispatcher.executorService.shutdown()
            pingClient.connectionPool.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
