package com.ir.tester.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

class ElkSmartBulkEncoder : UsbIrEncoder {
    override val name: String = "elksmart_bulk"
    override val strictHandshake: Boolean = true
    override val wantsBackgroundReader: Boolean = false
    override val interFrameDelayMs: Long = 2

    private var subtype: String? = null

    override fun openHandshake(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        outEndpoint: UsbEndpoint?,
        writeChunk: (ByteArray, Int) -> Boolean,
        drainIn: (Int) -> Unit
    ): Boolean {
        drainIn(80)
        val identify = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte())
        if (!writeChunk(identify, 200)) {
            return false
        }

        if (inEndpoint == null) return false
        val size = max(64, inEndpoint.maxPacketSize)
        val resp = ByteArray(size)
        val deadline = System.currentTimeMillis() + 450
        var got = -1

        while (System.currentTimeMillis() < deadline) {
            val n = connection.bulkTransfer(inEndpoint, resp, size, 150)
            if (n > 0) {
                got = n
                break
            }
        }

        val identifiedSubtype = identifySubtype(resp, got) ?: return false
        subtype = identifiedSubtype
        return true
    }

    private fun identifySubtype(resp: ByteArray, size: Int): String? {
        if (size < 6) return null
        val vals = IntArray(6) { resp[it].toInt() and 0xFF }
        for (i in 0 until 4) {
            if (vals[i] != 0xFC) return null
        }
        if (vals[4] == 0x70 && vals[5] == 0x01) return "D552"
        if (vals[4] == 0x02 && vals[5] == 0xAA) return "D226"
        return null
    }

    override fun encode(freqHz: Int, pattern: IntArray): List<ByteArray>? {
        if (pattern.isEmpty()) return null
        val pulses = toPulses(pattern)
        val raw = compressPulses(pulses)
        val payload = if ((subtype ?: "D552") == "D226") encodeD226Payload(raw) else raw

        val f = freqHz + 0x7FFFF
        val msg = ArrayList<Byte>()
        msg.add(0xFF.toByte())
        msg.add(0xFF.toByte())
        msg.add(0xFF.toByte())
        msg.add(0xFF.toByte())
        msg.add(mangleByte(f shr 8))
        msg.add(mangleByte(f shr 16))
        msg.add(mangleByte(f))
        msg.add(mangleByte(payload.size shr 8))
        msg.add(mangleByte(payload.size))
        for (b in payload) {
            msg.add(b)
        }

        val frames = ArrayList<ByteArray>()
        var offset = 0
        while (offset < msg.size) {
            val chunk = min(62, msg.size - offset)
            val frame = ByteArray(if (chunk == 62) 63 else chunk)
            for (j in 0 until chunk) {
                frame[j] = msg[offset + j]
            }
            if (chunk == 62) {
                frame[62] = checksum62(frame)
            }
            frames.add(frame)
            offset += chunk
        }
        return frames
    }

    override fun postTransmitDelayMs(pattern: IntArray): Long {
        var totalUs = 0L
        for (v in pattern) totalUs += max(0, v)
        return max(80L, (totalUs + 500) / 1000 + 25)
    }

    override fun drainAfterTransmit(
        connection: UsbDeviceConnection,
        inEndpoint: UsbEndpoint?,
        drainIn: (Int) -> Unit
    ) {
        drainIn(120)
    }

    private fun toPulses(pattern: IntArray): List<Pair<Int, Int>> {
        val pulses = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < pattern.size) {
            val onUs = max(0, pattern[i])
            val offUs = if (i + 1 < pattern.size) max(0, pattern[i + 1]) else 10000
            pulses.add(Pair(onUs, offUs))
            i += 2
        }
        return pulses
    }

    private fun compressPulses(pulses: List<Pair<Int, Int>>): ByteArray {
        if (pulses.isEmpty()) return ByteArray(0)
        val freqMap = HashMap<Pair<Int, Int>, Int>()
        for (p in pulses) {
            freqMap[p] = (freqMap[p] ?: 0) + 1
        }
        val sorted = freqMap.entries.sortedByDescending { it.value }
        val p1 = sorted[0].key
        val p2 = if (sorted.size > 1) sorted[1].key else p1

        val out = ArrayList<Byte>()
        compressValueUs(p2.first, out)
        compressValueUs(p2.second, out)
        compressValueUs(p1.first, out)
        compressValueUs(p1.second, out)
        out.add(0xFF.toByte())
        out.add(0xFF.toByte())
        out.add(0xFF.toByte())

        for (p in pulses) {
            if (p == p1) {
                out.add(0x00)
            } else if (p == p2) {
                out.add(0x01)
            } else {
                compressValueUs(p.first, out)
                compressValueUs(p.second, out)
            }
        }
        return out.toByteArray()
    }

    private class HuffmanNode(
        val weight: Int,
        val seq: Int,
        val symbol: Int?,
        val left: HuffmanNode?,
        val right: HuffmanNode?
    ) : Comparable<HuffmanNode> {
        override fun compareTo(other: HuffmanNode): Int {
            val cmp = weight.compareTo(other.weight)
            return if (cmp != 0) cmp else seq.compareTo(other.seq)
        }
    }

    private fun encodeD226Payload(raw: ByteArray): ByteArray {
        if (raw.isEmpty()) return raw
        val freq = IntArray(256)
        for (b in raw) {
            freq[b.toInt() and 0xFF]++
        }

        val heap = PriorityQueue<HuffmanNode>()
        var seq = 0
        for (symbol in 0 until 256) {
            if (freq[symbol] > 0) {
                heap.add(HuffmanNode(freq[symbol], seq++, symbol, null, null))
            }
        }
        if (heap.isEmpty()) return raw
        while (heap.size > 1) {
            val left = heap.poll()!!
            val right = heap.poll()!!
            heap.add(HuffmanNode(left.weight + right.weight, seq++, null, left, right))
        }

        val codes = ArrayList<Triple<Int, Int, String>>()
        buildCodes(heap.peek()!!, "", codes)
        codes.sortBy { it.first }
        val codeBySymbol = HashMap<Int, String>()
        for (c in codes) {
            codeBySymbol[c.first] = c.third
        }

        val out = ArrayList<Byte>()
        out.add(((codes.size shr 8) and 0xFF).toByte())
        out.add((codes.size and 0xFF).toByte())
        for (c in codes) {
            out.add((c.first and 0xFF).toByte())
            out.add(((c.second shr 8) and 0xFF).toByte())
            out.add((c.second and 0xFF).toByte())
        }

        val sb = StringBuilder()
        for (b in raw) {
            sb.append(codeBySymbol[b.toInt() and 0xFF] ?: "")
        }
        val tailBits = sb.length % 8
        if (tailBits > 0) {
            for (k in 0 until (8 - tailBits)) {
                sb.append('0')
            }
        }
        out.add((tailBits and 0xFF).toByte())

        var idx = 0
        while (idx < sb.length) {
            val chunkStr = sb.substring(idx, idx + 8)
            val byteVal = chunkStr.toInt(2)
            out.add((byteVal and 0xFF).toByte())
            idx += 8
        }

        return out.toByteArray()
    }

    private fun buildCodes(node: HuffmanNode, prefix: String, out: ArrayList<Triple<Int, Int, String>>) {
        if (node.symbol != null) {
            out.add(Triple(node.symbol, node.weight, if (prefix.isEmpty()) "0" else prefix))
            return
        }
        node.left?.let { buildCodes(it, prefix + "0", out) }
        node.right?.let { buildCodes(it, prefix + "1", out) }
    }

    private fun compressValueUs(valueUs: Int, out: ArrayList<Byte>) {
        if (valueUs <= 2032) {
            val q = if (valueUs == 0 || valueUs == 1) valueUs else (valueUs / 16.0 + 0.5).toInt()
            out.add((q and 0xFF).toByte())
            return
        }
        var v = valueUs
        while (true) {
            var b = v and 0x7F
            v = v shr 7
            if (v != 0) {
                b = b or 0x80
            }
            if ((b and 0xFF) == 0xFF) {
                b = 0xFE
            }
            out.add((b and 0xFF).toByte())
            if (v == 0) break
        }
    }

    private fun mangleByte(v: Int): Byte {
        var value = v and 0xFF
        var reversedBits = 0
        for (i in 0 until 8) {
            reversedBits = (reversedBits shl 1) or (value and 1)
            value = value shr 1
        }
        return ((reversedBits.inv()) and 0xFF).toByte()
    }

    private fun checksum62(buf: ByteArray): Byte {
        var total = 0
        for (i in 0 until 62) {
            total += buf[i].toInt() and 0xFF
        }
        val x = (total and 0xF0) or ((total shr 8) and 0x0F)
        return mangleByte(x)
    }
}
