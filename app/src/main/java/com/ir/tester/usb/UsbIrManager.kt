package com.ir.tester.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class UsbIrManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _dongleName = MutableStateFlow<String?>(null)
    val dongleName: StateFlow<String?> = _dongleName.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null
    private var currentInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var currentEncoder: UsbIrEncoder? = null

    private val isTransmitting = AtomicBoolean(false)
    private val permissionAction = "${context.packageName}.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    scanAndConnect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && currentDevice?.deviceName == device.deviceName) {
                        disconnect()
                    }
                }
                permissionAction -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            openDevice(device)
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        scanAndConnect()
    }

    fun scanAndConnect() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList ?: return

        if (currentDevice != null && _isConnected.value) {
            val stillAttached = deviceList.values.any { it.deviceName == currentDevice?.deviceName }
            if (!stillAttached) {
                disconnect()
            } else {
                return
            }
        }

        for (device in deviceList.values) {
            val profile = UsbProfiles.find(device.vendorId, device.productId) ?: continue
            if (manager.hasPermission(device)) {
                openDevice(device)
                return
            } else {
                requestPermission(device)
                return
            }
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        try {
            val intent = Intent(permissionAction).apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
            manager.requestPermission(device, pendingIntent)
        } catch (e: Exception) {
            Log.e("UsbIrManager", "Failed to request USB permission", e)
        }
    }

    private fun openDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        val profile = UsbProfiles.find(device.vendorId, device.productId) ?: return

        var claimedIntf: UsbInterface? = null
        var foundOut: UsbEndpoint? = null
        var foundIn: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            val endpoints = findEndpoints(intf)
            if (endpoints != null) {
                claimedIntf = intf
                foundOut = endpoints.first
                foundIn = endpoints.second
                break
            }
        }

        if (claimedIntf == null || foundOut == null) {
            return
        }

        val conn = manager.openDevice(device) ?: return
        if (!conn.claimInterface(claimedIntf, true)) {
            conn.close()
            return
        }

        val encoder = profile.encoderFactory()
        val writeChunk: (ByteArray, Int) -> Boolean = { chunk, timeoutMs ->
            val res = conn.bulkTransfer(foundOut, chunk, chunk.size, timeoutMs)
            res >= 0
        }
        val drainIn: (Int) -> Unit = { timeoutMs ->
            if (foundIn != null) {
                val buf = ByteArray(64)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val n = conn.bulkTransfer(foundIn, buf, buf.size, 20)
                    if (n <= 0) break
                }
            }
        }

        val handshakeOk = encoder.openHandshake(conn, foundIn, foundOut, writeChunk, drainIn)
        if (!handshakeOk && encoder.strictHandshake) {
            try {
                conn.releaseInterface(claimedIntf)
            } catch (_: Exception) {}
            conn.close()
            return
        }

        currentDevice = device
        currentConnection = conn
        currentInterface = claimedIntf
        outEndpoint = foundOut
        inEndpoint = foundIn
        currentEncoder = encoder

        _dongleName.value = profile.name
        _isConnected.value = true
    }

    private fun findEndpoints(intf: UsbInterface): Pair<UsbEndpoint, UsbEndpoint?>? {
        var bulkOut: UsbEndpoint? = null
        var bulkIn: UsbEndpoint? = null
        var intOut: UsbEndpoint? = null
        var intIn: UsbEndpoint? = null

        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT && bulkOut == null) bulkOut = ep
                if (ep.direction == UsbConstants.USB_DIR_IN && bulkIn == null) bulkIn = ep
            } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (ep.direction == UsbConstants.USB_DIR_OUT && intOut == null) intOut = ep
                if (ep.direction == UsbConstants.USB_DIR_IN && intIn == null) intIn = ep
            }
        }

        if (bulkOut != null) {
            return Pair(bulkOut, bulkIn)
        }
        if (intOut != null) {
            return Pair(intOut, intIn)
        }
        return null
    }

    fun transmit(frequency: Int, pattern: IntArray): Boolean {
        if (!_isConnected.value) return false
        val conn = currentConnection ?: return false
        val outEp = outEndpoint ?: return false
        val encoder = currentEncoder ?: return false

        synchronized(isTransmitting) {
            val frames = encoder.encode(frequency, pattern) ?: return false
            for (frame in frames) {
                val sent = conn.bulkTransfer(outEp, frame, frame.size, 200)
                if (sent < 0) {
                    disconnect()
                    return false
                }
                if (encoder.interFrameDelayMs > 0) {
                    try {
                        Thread.sleep(encoder.interFrameDelayMs)
                    } catch (_: Exception) {}
                }
            }

            val drainIn: (Int) -> Unit = { timeoutMs ->
                inEndpoint?.let { inEp ->
                    val buf = ByteArray(64)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        val n = conn.bulkTransfer(inEp, buf, buf.size, 20)
                        if (n <= 0) break
                    }
                }
            }
            encoder.drainAfterTransmit(conn, inEndpoint, drainIn)

            val delayMs = encoder.postTransmitDelayMs(pattern)
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs)
                } catch (_: Exception) {}
            }
            return true
        }
    }

    fun disconnect() {
        try {
            currentInterface?.let { currentConnection?.releaseInterface(it) }
            currentConnection?.close()
        } catch (_: Exception) {}

        currentDevice = null
        currentConnection = null
        currentInterface = null
        outEndpoint = null
        inEndpoint = null
        currentEncoder = null

        _dongleName.value = null
        _isConnected.value = false
    }

    fun destroy() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        disconnect()
    }
}
