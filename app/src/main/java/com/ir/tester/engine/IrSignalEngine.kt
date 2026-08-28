package com.ir.tester.engine

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.ir.tester.usb.UsbIrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class ModulationMode(val title: String, val description: String) {
    BASIC(
        "BASIC",
        "Симметричный меандр (пачки по 20 импульсов, фиксированная пауза ~20 мс)."
    ),
    ENHANCED_BASIC(
        "ENHANCED_BASIC",
        "Раздельные тайминги mark/space с множителем плотности сигнала (1..20)."
    ),
    SWEEP(
        "SWEEP",
        "Непрерывный свип длительности импульса от 80 до 700 мкс с регулировкой скорости."
    ),
    RANDOM(
        "RANDOM",
        "Псевдослучайная длина импульсов (50–1000 мкс) для стресс-тестирования декодера."
    ),
    EMPTY(
        "EMPTY",
        "Пакеты коротких преамбул для проверки отклика детектора и фронта сигнала."
    )
}

data class GeneratorMetrics(
    val packetsSent: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val packetsPerSecond: Double = 0.0,
    val isRunning: Boolean = false,
    val lastError: String? = null,
    val currentSweepPulseUs: Int = 0
) {
    val formattedTime: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

class IrSignalEngine(private val context: Context) {

    private val irManager: ConsumerIrManager? by lazy {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }

    val usbIrManager = UsbIrManager(context)

    val hasEmitter: Boolean
        get() = try {
            (irManager?.hasIrEmitter() == true) || usbIrManager.isConnected.value
        } catch (e: Exception) {
            usbIrManager.isConnected.value
        }

    private val _metrics = MutableStateFlow(GeneratorMetrics())
    val metrics: StateFlow<GeneratorMetrics> = _metrics.asStateFlow()

    private var engineJob: Job? = null
    private var timerJob: Job? = null

    var carrierFrequency: Int = 38000
    var modulationMode: ModulationMode = ModulationMode.BASIC
    var signalDensity: Int = 5
    var sweepSpeed: Int = 5
    private var sweepCurrentUs = 80
    private var sweepDirection = 1

    fun transmitSingle(frequency: Int, pattern: IntArray): Boolean {
        if (usbIrManager.isConnected.value) {
            return usbIrManager.transmit(frequency, pattern)
        }
        return try {
            irManager?.transmit(frequency, pattern)
            true
        } catch (e: Exception) {
            Log.e("IrSignalEngine", "Failed to transmit IR signal", e)
            false
        }
    }

    fun start(scope: CoroutineScope) {
        if (engineJob?.isActive == true) return
        sweepCurrentUs = 80
        sweepDirection = 1
        val startTime = System.currentTimeMillis()
        var packetCounter = 0L
        var lastRateCalcTime = System.currentTimeMillis()
        var lastRateCalcPackets = 0L
        var currentPps = 0.0

        _metrics.value = GeneratorMetrics(isRunning = true)

        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val now = System.currentTimeMillis()
                val deltaSec = (now - lastRateCalcTime) / 1000.0
                if (deltaSec >= 0.5) {
                    val deltaPackets = packetCounter - lastRateCalcPackets
                    currentPps = if (deltaSec > 0) deltaPackets / deltaSec else 0.0
                    lastRateCalcTime = now
                    lastRateCalcPackets = packetCounter
                }
                _metrics.value = _metrics.value.copy(
                    elapsedSeconds = elapsed,
                    packetsSent = packetCounter,
                    packetsPerSecond = currentPps,
                    currentSweepPulseUs = sweepCurrentUs
                )
                delay(200)
            }
        }

        engineJob = scope.launch(Dispatchers.Default) {
            try {
                while (isActive) {
                    val pattern = generatePattern(modulationMode)
                    val freq = if (modulationMode == ModulationMode.RANDOM && Random.nextBoolean()) {
                        val delta = (carrierFrequency * 0.05 * (Random.nextDouble() - 0.5)).toInt()
                        (carrierFrequency + delta).coerceIn(30000, 56000)
                    } else {
                        carrierFrequency
                    }

                    val success = if (usbIrManager.isConnected.value) {
                        usbIrManager.transmit(freq, pattern)
                    } else {
                        try {
                            irManager?.transmit(freq, pattern)
                            true
                        } catch (e: Exception) {
                            Log.e("IrSignalEngine", "Transmit error in loop", e)
                            _metrics.value = _metrics.value.copy(lastError = e.localizedMessage)
                            false
                        }
                    }

                    if (success || (irManager == null && !usbIrManager.isConnected.value)) {
                        packetCounter++
                    }

                    val packetIntervalMs = when (modulationMode) {
                        ModulationMode.BASIC -> 20L
                        ModulationMode.ENHANCED_BASIC -> (25 - signalDensity).toLong().coerceAtLeast(3L)
                        ModulationMode.SWEEP -> (15 - sweepSpeed).toLong().coerceAtLeast(2L)
                        ModulationMode.RANDOM -> Random.nextLong(10L, 40L)
                        ModulationMode.EMPTY -> 30L
                    }
                    delay(packetIntervalMs)
                }
            } finally {
                _metrics.value = _metrics.value.copy(isRunning = false)
            }
        }
    }

    fun stop() {
        engineJob?.cancel()
        timerJob?.cancel()
        engineJob = null
        timerJob = null
        _metrics.value = _metrics.value.copy(isRunning = false)
    }

    private fun generatePattern(mode: ModulationMode): IntArray {
        return when (mode) {
            ModulationMode.BASIC -> {
                val count = 20 * 2
                val arr = IntArray(count)
                for (i in 0 until count) {
                    arr[i] = 400
                }
                arr
            }
            ModulationMode.ENHANCED_BASIC -> {
                val pulses = 10 + (signalDensity * 2)
                val markUs = 300 + (signalDensity * 20)
                val spaceUs = 500 - (signalDensity * 15).coerceAtMost(350)
                val arr = IntArray(pulses * 2)
                for (i in 0 until pulses) {
                    arr[i * 2] = markUs
                    arr[i * 2 + 1] = spaceUs
                }
                arr
            }
            ModulationMode.SWEEP -> {
                val step = sweepSpeed * 15
                sweepCurrentUs += sweepDirection * step
                if (sweepCurrentUs >= 700) {
                    sweepCurrentUs = 700
                    sweepDirection = -1
                } else if (sweepCurrentUs <= 80) {
                    sweepCurrentUs = 80
                    sweepDirection = 1
                }
                val pulseWidth = sweepCurrentUs
                val pulses = 16
                val arr = IntArray(pulses * 2)
                for (i in 0 until pulses) {
                    arr[i * 2] = pulseWidth
                    arr[i * 2 + 1] = 600 - (pulseWidth / 2)
                }
                arr
            }
            ModulationMode.RANDOM -> {
                val pulses = Random.nextInt(8, 20)
                val arr = IntArray(pulses * 2)
                for (i in 0 until pulses) {
                    arr[i * 2] = Random.nextInt(50, 1000)
                    arr[i * 2 + 1] = Random.nextInt(50, 1000)
                }
                arr
            }
            ModulationMode.EMPTY -> {
                intArrayOf(200, 200, 200, 400, 200, 200, 200, 800, 300, 300)
            }
        }
    }
}
