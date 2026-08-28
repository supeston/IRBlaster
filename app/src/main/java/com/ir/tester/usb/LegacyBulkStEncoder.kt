package com.ir.tester.usb

import kotlin.math.max
import kotlin.math.min

class LegacyBulkStEncoder : UsbIrEncoder {
    override val name: String = "legacy_bulk_st"
    override val strictHandshake: Boolean = false
    override val wantsBackgroundReader: Boolean = true
    override val interFrameDelayMs: Long = 0

    private var eVal = 1
    private var fVal = 0

    private fun nextE(): Byte {
        eVal = if (eVal < 0x0F) eVal + 1 else 0x01
        return eVal.toByte()
    }

    private fun nextF(): Byte {
        fVal = if (fVal < 0x7F) fVal + 1 else 0x01
        return fVal.toByte()
    }

    override fun handshakeFrames(): List<ByteArray> {
        return listOf(
            byteArrayOf(0x02, 0x09, nextE(), 0x01, 0x01, 0x53, 0x54, nextF(), 0x53, 0x45, 0x4E)
        )
    }

    override fun encode(freqHz: Int, pattern: IntArray): List<ByteArray>? {
        if (pattern.isEmpty()) return null
        val normalized = pattern.map { max(1, it) }.toMutableList()
        if (normalized.size % 2 == 0) {
            val tail = normalized.last()
            normalized[normalized.size - 1] = if (tail > 3000) tail - 3000 else 10
        }

        val payload = ArrayList<Byte>()
        payload.add(0x53.toByte())
        payload.add(0x54.toByte())
        payload.add(nextF())
        payload.add(0x44.toByte())
        payload.add(0x00.toByte())

        for (i in normalized.indices) {
            val duration = normalized[i]
            var units = duration / 16
            if (units <= 0) units = 1
            val isMark = (i % 2 == 0)
            while (units > 0) {
                val chunk = min(units, 0x7F)
                units -= chunk
                val byteVal = (chunk or (if (isMark) 0x80 else 0x00)).toByte()
                payload.add(byteVal)
            }
        }

        val maxPayloadPerFrame = 58
        val total = ((payload.size + maxPayloadPerFrame - 1) / maxPayloadPerFrame).toByte()
        val eValue = nextE()
        val frames = ArrayList<ByteArray>()
        var offset = 0
        var index = 1

        while (offset < payload.size) {
            val take = min(maxPayloadPerFrame, payload.size - offset)
            val frame = ByteArray(take + 5)
            frame[0] = 0x02
            frame[1] = (take + 3).toByte()
            frame[2] = eValue
            frame[3] = total
            frame[4] = index.toByte()
            for (j in 0 until take) {
                frame[5 + j] = payload[offset + j]
            }
            frames.add(frame)
            offset += take
            index++
        }
        return frames
    }

    override fun postTransmitDelayMs(pattern: IntArray): Long {
        var totalUs = 0L
        for (v in pattern) totalUs += max(0, v)
        return max(60L, (totalUs + 500) / 1000 + 20)
    }
}
