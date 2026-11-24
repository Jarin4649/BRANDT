package com.example.data.obd

import android.content.Context
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Real ELM327 / STN Wi-Fi OBD-II TCP Socket transport (typically 192.168.0.10:35000).
 * Strictly enforces [ReadOnlyObdCommandGuard] before transmitting any command bytes.
 */
class WifiObdTransport(
    private val host: String,
    private val port: Int,
    override val adapterLabel: String,
    private val socket: Socket,
    private val inputStream: InputStream,
    private val outputStream: OutputStream
) : ObdTransportStream {

    override val sourceType: ObdDataSourceType = ObdDataSourceType.WIFI_OBD
    override val method: ObdConnectionMethod = ObdConnectionMethod.WIFI
    override val bluetoothArchitecture: BluetoothArchitecture? = null

    private val mutex = Mutex()

    @Volatile
    private var closed = false

    override val isConnected: Boolean
        get() = !closed && socket.isConnected && !socket.isClosed

    override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> = mutex.withLock {
        val guardResult = ReadOnlyObdCommandGuard.requireReadOnly(command)
        if (guardResult.isFailure) {
            return Result.failure(guardResult.exceptionOrNull()!!)
        }
        val cleanCmd = guardResult.getOrThrow()
        if (!isConnected) {
            val msg = if (isThai()) {
                "การเชื่อมต่อ Wi-Fi OBD-II ($host:$port) ถูกตัดแล้ว"
            } else {
                "Wi-Fi OBD-II connection ($host:$port) has closed."
            }
            return Result.failure(IllegalStateException(msg))
        }

        return withContext(Dispatchers.IO) {
            try {
                socket.soTimeout = timeoutMs.coerceIn(500L, 12_000L).toInt()
                outputStream.write("$cleanCmd\r".toByteArray(Charsets.US_ASCII))
                outputStream.flush()
                val response = readUntilPrompt(inputStream, cleanCmd, timeoutMs)
                Result.success(response)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SocketTimeoutException) {
                val msg = if (isThai()) {
                    "ตัวสแกนเนอร์ Wi-Fi OBD-II ($host:$port) ไม่ตอบสนองคำสั่ง '$cleanCmd' ภายในเวลาที่กำหนด"
                } else {
                    "Wi-Fi OBD-II scanner ($host:$port) timed out responding to '$cleanCmd'."
                }
                Result.failure(IllegalStateException(msg))
            } catch (e: Exception) {
                val fallback = if (isThai()) {
                    "การสื่อสาร TCP ผ่าน Wi-Fi OBD-II ขัดข้องระหว่างส่งคำสั่ง $cleanCmd"
                } else {
                    "Wi-Fi OBD-II TCP communication error while sending '$cleanCmd'."
                }
                Result.failure(
                    IllegalStateException(e.localizedMessage ?: fallback, e)
                )
            }
        }
    }

    override suspend fun close() {
        closed = true
        withContext(Dispatchers.IO) {
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
            runCatching { socket.close() }
        }
    }

    private fun readUntilPrompt(input: InputStream, sentCmd: String, timeoutMs: Long): String {
        val buffer = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val next = try {
                input.read()
            } catch (_: SocketTimeoutException) {
                break
            }
            if (next < 0) break
            val ch = next.toChar()
            if (ch == '>') break
            buffer.append(ch)
        }
        return buffer.toString()
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(sentCmd, ignoreCase = true) }
            .joinToString("\n")
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4_000

        private fun isThai(): Boolean =
            LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI

        suspend fun openAndHandshake(
            @Suppress("UNUSED_PARAMETER") context: Context,
            host: String,
            port: Int,
            displayLabel: String
        ): Result<ObdTransportStream> = withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                val tcpSocket = Socket()
                socket = tcpSocket
                tcpSocket.tcpNoDelay = true
                tcpSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                tcpSocket.soTimeout = 2_500

                val input = tcpSocket.getInputStream()
                val output = tcpSocket.getOutputStream()

                // Safe read-only AT wake-up sequence
                val transport = WifiObdTransport(
                    host = host,
                    port = port,
                    adapterLabel = displayLabel,
                    socket = tcpSocket,
                    inputStream = input,
                    outputStream = output
                )
                listOf("ATE0", "ATL0", "ATS0").forEach { cmd ->
                    transport.exchangeCommand(cmd, 2_000L)
                }
                Result.success(transport)
            } catch (e: CancellationException) {
                runCatching { socket?.close() }
                throw e
            } catch (e: Exception) {
                runCatching { socket?.close() }
                val msg = if (isThai()) {
                    "ไม่สามารถเชื่อมต่อ Wi-Fi OBD-II ที่ $host:$port ได้ กรุณาตรวจสอบว่ามือถือเชื่อมต่อ Wi-Fi ของตัวสแกนเนอร์และบิดกุญแจรถไปที่ ON แล้ว"
                } else {
                    "Unable to reach Wi-Fi OBD-II adapter at $host:$port. Make sure your phone is joined to the scanner Wi-Fi hotspot and ignition is ON."
                }
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }
}
