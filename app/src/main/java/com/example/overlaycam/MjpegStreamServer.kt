package com.example.overlaycam

import android.graphics.Bitmap
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal class MjpegStreamServer {
    private val running = AtomicBoolean(false)
    private val latestFrame = AtomicReference<ByteArray?>(null)
    private val clients = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    var port: Int = 0
        private set

    fun start(port: Int) {
        if (running.get() && this.port == port) return
        stop()

        val socket = ServerSocket(port)
        serverSocket = socket
        this.port = socket.localPort
        running.set(true)
        acceptThread = thread(name = "overlaycam-mjpeg-accept", isDaemon = true) {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                clients.execute { serve(client) }
            }
        }
    }

    fun updateFrame(bitmap: Bitmap, quality: Int): Boolean {
        if (!running.get()) return false
        val bytes = ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 95), output)) {
                return false
            }
            output.toByteArray()
        }
        latestFrame.set(bytes)
        return true
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        latestFrame.set(null)
        port = 0
    }

    fun urlHint(): String {
        val host = firstLanIpv4Address() ?: "<device-ip>"
        val activePort = port.takeIf { it > 0 } ?: 8088
        return "http://$host:$activePort/stream.mjpeg"
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            client.tcpNoDelay = true
            client.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val request = runCatching { reader.readLine() }.getOrNull().orEmpty()
            while (true) {
                val line = runCatching { reader.readLine() }.getOrNull() ?: break
                if (line.isBlank()) break
            }

            val output = client.getOutputStream()
            if (!request.contains(" /stream.mjpeg ")) {
                val body = "Overlay Cam MJPEG stream: /stream.mjpeg\n"
                val bodySize = body.toByteArray(StandardCharsets.UTF_8).size
                output.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: $bodySize\r\nConnection: close\r\n\r\n$body")
                        .toByteArray(StandardCharsets.US_ASCII),
                )
                output.flush()
                return
            }

            output.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n")
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            output.flush()

            var lastFrame: ByteArray? = null
            while (running.get() && !client.isClosed) {
                val frame = latestFrame.get()
                if (frame == null || frame === lastFrame) {
                    Thread.sleep(8)
                    continue
                }
                lastFrame = frame
                runCatching {
                    output.write("--frame\r\n".toByteArray(StandardCharsets.US_ASCII))
                    output.write("Content-Type: image/jpeg\r\n".toByteArray(StandardCharsets.US_ASCII))
                    output.write("Content-Length: ${frame.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    output.write(frame)
                    output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
                    output.flush()
                }.onFailure { return }
            }
        }
    }

    private fun firstLanIpv4Address(): String? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
