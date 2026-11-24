package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Phase 7: Native Android Bluetooth Classic (SPP/RFCOMM) and Bluetooth Low Energy (BLE GATT)
 * OBD-II transport implementations.
 *
 * Strictly enforces runtime permissions, performs live `ATI` adapter probing, and handles
 * disconnect/timeout cleanly without ever claiming unverified compatibility.
 */
class ClassicSppObdTransport private constructor(
    override val adapterLabel: String,
    private val socket: BluetoothSocket,
    private val inputStream: InputStream,
    private val outputStream: OutputStream
) : ObdTransportStream {

    override val sourceType: ObdDataSourceType = ObdDataSourceType.BLUETOOTH_OBD
    override val method: ObdConnectionMethod = ObdConnectionMethod.BLUETOOTH
    override val bluetoothArchitecture: BluetoothArchitecture = BluetoothArchitecture.CLASSIC_SPP

    @Volatile
    private var open = true
    private val ioMutex = Mutex()

    override val isConnected: Boolean
        get() = open && socket.isConnected

    override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> {
        if (!isConnected) return Result.failure(IllegalStateException("Bluetooth SPP socket is disconnected."))
        val clean = ReadOnlyObdCommandGuard.requireReadOnly(command)
            .getOrElse { return Result.failure(it) }

        return ioMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    while (inputStream.available() > 0) {
                        inputStream.read()
                    }
                    outputStream.write("$clean\r".toByteArray(Charsets.US_ASCII))
                    outputStream.flush()

                    val deadline = System.currentTimeMillis() + timeoutMs
                    val sb = StringBuilder()
                    val buf = ByteArray(512)
                    var lastDataAt = 0L
                    while (System.currentTimeMillis() < deadline && open) {
                        val avail = inputStream.available()
                        if (avail > 0) {
                            val count = inputStream.read(buf)
                            if (count < 0) {
                                open = false
                                break
                            }
                            if (count > 0) {
                                sb.append(String(buf, 0, count, Charsets.US_ASCII))
                                lastDataAt = System.currentTimeMillis()
                                if (sb.contains('>')) break
                            }
                        } else {
                            // Some ELM327 clones omit '>' on ATI/AT replies but terminate with \r/\n.
                            val current = sb.toString()
                            if (lastDataAt > 0L &&
                                (current.endsWith("\r") || current.endsWith("\n")) &&
                                !current.contains("SEARCHING", ignoreCase = true) &&
                                clean.startsWith("AT") &&
                                (System.currentTimeMillis() - lastDataAt) >= 220L
                            ) {
                                val meaningful = cleanResponseText(current, clean)
                                if (meaningful.isNotBlank()) break
                            }
                            Thread.sleep(15L)
                        }
                    }
                    val raw = cleanResponseText(sb.toString(), clean)
                    if (raw.isEmpty()) {
                        Result.failure(IllegalStateException("Bluetooth SPP timeout after ${timeoutMs}ms for '$clean'."))
                    } else {
                        Result.success(raw)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    open = false
                    Result.failure(e)
                }
            }
        }
    }

    private fun cleanResponseText(raw: String, sentCommand: String): String {
        return raw.replace("\r", "\n")
            .replace(">", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(sentCommand, ignoreCase = true) }
            .joinToString("\n")
    }

    override suspend fun close() {
        open = false
        withContext(Dispatchers.IO) {
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
            runCatching { socket.close() }
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @SuppressLint("MissingPermission")
        suspend fun connectAndHandshake(
            context: Context,
            deviceAddress: String,
            displayName: String
        ): Result<ClassicSppObdTransport> = withContext(Dispatchers.IO) {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter unavailable."))
            val enabled = try {
                adapter.isEnabled
            } catch (e: SecurityException) {
                return@withContext Result.failure(e)
            }
            if (!enabled) {
                return@withContext Result.failure(IllegalStateException("System Bluetooth is disabled."))
            }

            runCatching { adapter.cancelDiscovery() }
            val device = try {
                adapter.getRemoteDevice(deviceAddress)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            var connectedSocket: BluetoothSocket? = null
            var lastConnectError: Throwable? = null

            val socketStrategies: List<() -> BluetoothSocket> = listOf(
                { device.createRfcommSocketToServiceRecord(SPP_UUID) },
                { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
                {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    method.invoke(device, 1) as BluetoothSocket
                }
            )

            for (factory in socketStrategies) {
                var candidate: BluetoothSocket? = null
                try {
                    runCatching { adapter.cancelDiscovery() }
                    candidate = factory()
                    candidate.connect()
                    connectedSocket = candidate
                    break
                } catch (e: CancellationException) {
                    runCatching { candidate?.close() }
                    throw e
                } catch (e: SecurityException) {
                    runCatching { candidate?.close() }
                    return@withContext Result.failure(e)
                } catch (e: Exception) {
                    lastConnectError = e
                    runCatching { candidate?.close() }
                }
            }

            val socket = connectedSocket ?: return@withContext Result.failure(
                IllegalStateException(
                    "Unable to open Bluetooth Classic (SPP) link to '$displayName': ${lastConnectError?.localizedMessage ?: "Socket error"}",
                    lastConnectError
                )
            )

            try {
                Thread.sleep(150L)
                val transport = ClassicSppObdTransport(
                    adapterLabel = displayName,
                    socket = socket,
                    inputStream = socket.inputStream,
                    outputStream = socket.outputStream
                )
                // First wake-up / probe via read-only ATI (fallback ATE0 -> ATI for sleepy ELM327 clones)
                var probe = transport.exchangeCommand("ATI", timeoutMs = 2_500L)
                if (probe.isFailure || probe.getOrNull().isNullOrBlank()) {
                    transport.exchangeCommand("ATE0", timeoutMs = 1_500L)
                    probe = transport.exchangeCommand("ATI", timeoutMs = 2_500L)
                }
                if (probe.isSuccess && !probe.getOrNull().isNullOrBlank()) {
                    Result.success(transport)
                } else {
                    transport.close()
                    Result.failure(
                        IllegalStateException(
                            "Connected RFCOMM socket to '$displayName', but adapter did not reply to ELM327 ATI handshake."
                        )
                    )
                }
            } catch (e: CancellationException) {
                runCatching { socket.close() }
                throw e
            } catch (e: Exception) {
                runCatching { socket.close() }
                Result.failure(
                    IllegalStateException(
                        "Unable to open Bluetooth Classic (SPP) link to '$displayName': ${e.localizedMessage ?: "Socket error"}",
                        e
                    )
                )
            }
        }
    }
}

class BleGattObdTransport private constructor(
    override val adapterLabel: String
) : ObdTransportStream {

    override val sourceType: ObdDataSourceType = ObdDataSourceType.BLUETOOTH_OBD
    override val method: ObdConnectionMethod = ObdConnectionMethod.BLUETOOTH
    override val bluetoothArchitecture: BluetoothArchitecture = BluetoothArchitecture.BLE_GATT

    @Volatile
    private var gatt: BluetoothGatt? = null
    @Volatile
    private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile
    private var connectedFlag = false

    private val ioMutex = Mutex()
    private val responseBuffer = StringBuilder()
    @Volatile
    private var pendingResponseSignal: CompletableDeferred<String>? = null
    @Volatile
    private var pendingWriteAck: CompletableDeferred<Int>? = null

    override val isConnected: Boolean
        get() = connectedFlag && gatt != null && writeChar != null

    @SuppressLint("MissingPermission")
    override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> {
        val activeGatt = gatt
        val targetWrite = writeChar
        if (!isConnected || activeGatt == null || targetWrite == null) {
            return Result.failure(IllegalStateException("BLE GATT OBD transport is disconnected."))
        }
        val clean = ReadOnlyObdCommandGuard.requireReadOnly(command)
            .getOrElse { return Result.failure(it) }

        return ioMutex.withLock {
            synchronized(responseBuffer) { responseBuffer.clear() }
            val signal = CompletableDeferred<String>()
            pendingResponseSignal = signal

            val supportsDefaultWrite = (targetWrite.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            val writeType = if (supportsDefaultWrite) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            val writeAck = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                CompletableDeferred<Int>().also { pendingWriteAck = it }
            } else {
                pendingWriteAck = null
                null
            }

            val payload = "$clean\r".toByteArray(Charsets.US_ASCII)
            val writeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activeGatt.writeCharacteristic(
                    targetWrite,
                    payload,
                    writeType
                ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    targetWrite.writeType = writeType
                    targetWrite.value = payload
                    activeGatt.writeCharacteristic(targetWrite)
                }
            }

            if (!writeOk) {
                pendingResponseSignal = null
                pendingWriteAck = null
                return@withLock Result.failure(IllegalStateException("BLE write failed for command '$clean'."))
            }

            if (writeAck != null) {
                withTimeoutOrNull(1_000L) { writeAck.await() }
                pendingWriteAck = null
            }

            val completed = withTimeoutOrNull(timeoutMs) { signal.await() }
            pendingResponseSignal = null
            val partial = synchronized(responseBuffer) { responseBuffer.toString() }
            val resolved = cleanBleResponseText(completed ?: partial, clean)
            if (resolved.isBlank()) {
                Result.failure(IllegalStateException("BLE GATT response timed out after ${timeoutMs}ms for '$clean'."))
            } else {
                Result.success(resolved)
            }
        }
    }

    private fun cleanBleResponseText(raw: String, sentCommand: String): String {
        return raw.replace("\r", "\n")
            .replace(">", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(sentCommand, ignoreCase = true) }
            .joinToString("\n")
    }

    @SuppressLint("MissingPermission")
    override suspend fun close() {
        connectedFlag = false
        pendingResponseSignal?.cancel()
        pendingWriteAck?.cancel()
        val current = gatt
        gatt = null
        writeChar = null
        withContext(Dispatchers.IO) {
            runCatching { current?.disconnect() }
            runCatching { current?.close() }
        }
    }

    companion object {
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val CANDIDATE_SERVICE_PREFIXES = setOf(
            "6e400001", // Nordic UART (OBDLink CX / STN BLE)
            "49535343", // Microchip/ISSC Transparent UART
            "0000fff0", // Common BLE ELM327 GATT (Vgate / Viecar / Konnwei)
            "0000ffe0", // HM-10 / JDY serial BLE
            "000018f0", // Veepeak / Carista / newer Vgate iCar Pro BLE
            "e7810a71", // Common BLE UART (Microchip/Elm BLE)
            "bef8d6c9"  // Common BLE SPP bridge
        )

        @SuppressLint("MissingPermission")
        suspend fun connectAndHandshake(
            context: Context,
            deviceAddress: String,
            displayName: String
        ): Result<BleGattObdTransport> = withContext(Dispatchers.IO) {
            val adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter unavailable."))
            val enabled = try {
                adapter.isEnabled
            } catch (e: SecurityException) {
                return@withContext Result.failure(e)
            }
            if (!enabled) {
                return@withContext Result.failure(IllegalStateException("System Bluetooth is disabled."))
            }

            val device = try {
                adapter.getRemoteDevice(deviceAddress)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            var lastFailure: Throwable? = null
            // Retry once on transient Android BLE GATT 133 error
            for (attempt in 0..1) {
                val attemptResult = connectSingleAttempt(context, device, displayName)
                if (attemptResult.isSuccess) {
                    return@withContext attemptResult
                }
                val err = attemptResult.exceptionOrNull()
                lastFailure = err
                if (err is SecurityException || err?.message?.contains("did not answer ELM327") == true) {
                    break
                }
                if (attempt == 0) {
                    kotlinx.coroutines.delay(300L)
                }
            }

            Result.failure(
                lastFailure ?: IllegalStateException("Unable to connect BLE GATT adapter '$displayName'.")
            )
        }

        @SuppressLint("MissingPermission")
        private suspend fun connectSingleAttempt(
            context: Context,
            device: android.bluetooth.BluetoothDevice,
            displayName: String
        ): Result<BleGattObdTransport> {
            val transport = BleGattObdTransport(displayName)
            val readyDeferred = CompletableDeferred<Result<Unit>>()

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        transport.connectedFlag = false
                        transport.pendingResponseSignal?.cancel()
                        if (!readyDeferred.isCompleted) {
                            readyDeferred.complete(
                                Result.failure(IllegalStateException("BLE GATT connection failed/disconnected (status=$status)."))
                            )
                        }
                        return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        val mtuRequested = runCatching { g.requestMtu(185) }.getOrDefault(false)
                        if (!mtuRequested) {
                            g.discoverServices()
                        }
                    }
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                    g.discoverServices()
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (!readyDeferred.isCompleted) {
                            readyDeferred.complete(Result.failure(IllegalStateException("BLE service discovery failed (status=$status).")))
                        }
                        return
                    }
                    val uartPair = findUartCharacteristics(g)
                    if (uartPair == null) {
                        if (!readyDeferred.isCompleted) {
                            readyDeferred.complete(
                                Result.failure(
                                    IllegalStateException(
                                        "No recognized OBD BLE UART GATT characteristics found on '$displayName'."
                                    )
                                )
                            )
                        }
                        return
                    }
                    val (wChar, nChar) = uartPair
                    transport.writeChar = wChar
                    g.setCharacteristicNotification(nChar, true)
                    val cccd = nChar.getDescriptor(CCCD_UUID)
                    if (cccd != null) {
                        val descriptorBytes = if ((nChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        } else {
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        }
                        val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(cccd, descriptorBytes) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            run {
                                cccd.value = descriptorBytes
                                g.writeDescriptor(cccd)
                            }
                        }
                        if (!writeStarted) {
                            transport.connectedFlag = true
                            if (!readyDeferred.isCompleted) readyDeferred.complete(Result.success(Unit))
                        }
                        // Wait for onDescriptorWrite before completing readyDeferred!
                    } else {
                        transport.connectedFlag = true
                        if (!readyDeferred.isCompleted) readyDeferred.complete(Result.success(Unit))
                    }
                }

                override fun onDescriptorWrite(
                    g: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int
                ) {
                    transport.connectedFlag = true
                    if (!readyDeferred.isCompleted) {
                        readyDeferred.complete(Result.success(Unit))
                    }
                }

                override fun onCharacteristicWrite(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    transport.pendingWriteAck?.complete(status)
                }

                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    handleIncomingBytes(value)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    handleIncomingBytes(characteristic.value ?: ByteArray(0))
                }

                private fun handleIncomingBytes(bytes: ByteArray) {
                    if (bytes.isEmpty()) return
                    val text = String(bytes, Charsets.US_ASCII)
                    synchronized(transport.responseBuffer) {
                        transport.responseBuffer.append(text)
                        if (transport.responseBuffer.contains('>')) {
                            val full = transport.responseBuffer.toString()
                            transport.pendingResponseSignal?.complete(full)
                        }
                    }
                }
            }

            val gattHandle = try {
                device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            } catch (e: Exception) {
                return Result.failure(e)
            } ?: return Result.failure(
                IllegalStateException("Android BLE stack returned null GATT client for '$displayName'.")
            )
            transport.gatt = gattHandle
            val initResult = try {
                withTimeoutOrNull(6_000L) { readyDeferred.await() }
                    ?: Result.failure(IllegalStateException("Timed out connecting to BLE GATT device '$displayName'."))
            } catch (e: CancellationException) {
                runCatching { transport.close() }
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (initResult.isFailure) {
                runCatching { transport.close() }
                return Result.failure(initResult.exceptionOrNull()!!)
            }

            var probe = try {
                transport.exchangeCommand("ATI", timeoutMs = 2_500L)
            } catch (e: CancellationException) {
                runCatching { transport.close() }
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (probe.isFailure || probe.getOrNull().isNullOrBlank()) {
                runCatching { transport.exchangeCommand("ATE0", timeoutMs = 1_500L) }
                probe = try {
                    transport.exchangeCommand("ATI", timeoutMs = 2_500L)
                } catch (e: CancellationException) {
                    runCatching { transport.close() }
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            return if (probe.isSuccess && !probe.getOrNull().isNullOrBlank()) {
                Result.success(transport)
            } else {
                runCatching { transport.close() }
                Result.failure(
                    IllegalStateException(
                        "BLE GATT UART found on '$displayName', but adapter did not answer ELM327 ATI handshake."
                    )
                )
            }
        }

        private fun findUartCharacteristics(
            gatt: BluetoothGatt
        ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
            val prioritizedServices = gatt.services.sortedByDescending { svc ->
                val prefix = svc.uuid.toString().lowercase().take(8)
                if (prefix in CANDIDATE_SERVICE_PREFIXES) 1 else 0
            }
            for (service in prioritizedServices) {
                val chars = service.characteristics
                val writer = chars.firstOrNull { c ->
                    (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                }
                val notifier = chars.firstOrNull { c ->
                    (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                }
                if (writer != null && notifier != null) {
                    return writer to notifier
                }
            }
            return null
        }
    }
}
