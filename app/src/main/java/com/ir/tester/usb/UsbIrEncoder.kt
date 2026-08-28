package com.ir.tester.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint

interface UsbIrEncoder {
    val name: String
    val strictHandshake: Boolean get() = false
    val wantsBackgroundReader: Boolean get() = true
    val interFrameDelayMs: Long get() = 0

    fun handshakeFrames(): List<ByteArray> = emptyList()

    fun openHandshake(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        writeChunk: (ByteArray, Int) -> Boolean,
        drainIn: (Int) -> Unit
    ): Boolean = true

    fun encode(freqHz: Int, pattern: IntArray): List<ByteArray>?

    fun postTransmitDelayMs(pattern: IntArray): Long

    fun drainAfterTransmit(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        drainIn: (Int) -> Unit
    ) {}
}
