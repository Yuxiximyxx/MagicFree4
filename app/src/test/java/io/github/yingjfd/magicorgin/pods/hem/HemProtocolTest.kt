package io.github.yingjfd.magicorgin.pods.hem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 欢律协议层的纯 JVM 测试（不依赖 Android、不依赖真机）。
 *
 * 这些断言把「帧格式」「电量布局」「降噪位掩码与回声帧」三类最容易写错的地方钉死，
 * 依据见 docs/protocol-code-facts.md 与 QuickBuds 的 docs/PROTOCOL.md。
 */
class HemProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // ── 帧层 ────────────────────────────────────────────────

    @Test
    fun `handshake frame has 0xAA header and 7 byte body`() {
        val frame = HemWire.build(HemCommands.CMD_HANDSHAKE, seq = 0x2A)
        assertEquals(0xAA, frame[0].toInt() and 0xFF)
        assertEquals(7, frame[1].toInt() and 0xFF)        // TotalLen = 7 + payload(0)
        assertEquals(0, frame[2].toInt())                 // slice = 0
        assertEquals(0, frame[3].toInt())
        assertEquals(0x00, frame[4].toInt() and 0xFF)     // cmd 低字节 = 0x00
        assertEquals(0x01, frame[5].toInt() and 0xFF)     // cmd 高字节 = 0x01 → 0x0100
        assertEquals(0x2A, frame[6].toInt() and 0xFF)     // seq
        assertEquals(0, frame[7].toInt())                 // payLen 低
        assertEquals(0, frame[8].toInt())                 // payLen 高
        assertEquals(9, frame.size)
    }

    @Test
    fun `build and parse round trip`() {
        val payload = bytes(0x01, 0x01, 0x20)
        val frame = HemWire.build(HemCommands.CMD_SET_ANC, seq = 0x11, payload = payload)
        val msg = HemWire.parse(frame)
        assertNotNull(msg)
        assertEquals(HemCommands.CMD_SET_ANC, msg!!.cmd)
        assertEquals(0x11, msg.seq)
        assertTrue(payload.contentEquals(msg.payload))
    }

    @Test
    fun `framer reassembles split reads and skips noise`() {
        val frame = HemWire.build(HemCommands.CMD_QUERY_BATTERY, seq = 0xF0)
        val framer = HemWire.Framer()

        // 帧头前塞噪声，并故意把一帧切成两段
        val noisy = bytes(0x00, 0xFF) + frame.copyOfRange(0, 5)
        assertTrue(framer.append(noisy, noisy.size).isEmpty())

        val rest = frame.copyOfRange(5, frame.size)
        val frames = framer.append(rest, rest.size)
        assertEquals(1, frames.size)
        val msg = HemWire.parse(frames[0])
        assertEquals(HemCommands.CMD_QUERY_BATTERY, msg!!.cmd)
    }

    @Test
    fun `framer handles two frames in one read`() {
        val a = HemWire.build(HemCommands.CMD_HANDSHAKE, 0x01)
        val b = HemWire.build(HemCommands.CMD_QUERY_ANC, 0x02, bytes(0x01, 0x01))
        val both = a + b
        val frames = HemWire.Framer().append(both, both.size)
        assertEquals(2, frames.size)
        assertEquals(HemCommands.CMD_HANDSHAKE, HemWire.parse(frames[0])!!.cmd)
        assertEquals(HemCommands.CMD_QUERY_ANC, HemWire.parse(frames[1])!!.cmd)
    }

    // ── 电量 ────────────────────────────────────────────────

    @Test
    fun `battery query reply parses left right and case`() {
        // payload: 00(count前状态) 03(count) [01 64][02 50][03 4B]
        val msg = HemWire.parse(
            HemWire.build(HemCommands.RSP_BATTERY, 0xF0, bytes(0x00, 0x03, 0x01, 0x64, 0x02, 0x50, 0x03, 0x4B))
        )!!
        val r = HemBattery.parseQuery(msg)
        assertNotNull(r)
        assertEquals(100, r!!.left!!.level)
        assertEquals(80, r.right!!.level)
        assertEquals(75, r.case!!.level)
        assertEquals(false, r.left!!.isCharging)
    }

    @Test
    fun `charging bit and unknown level are handled`() {
        // 0xE1 = 充电 + 97%；0x00 = 未知（必须为 null，不能当 0%）
        val msg = HemWire.parse(
            HemWire.build(HemCommands.RSP_BATTERY, 0xF0, bytes(0x00, 0x02, 0x01, 0xE1, 0x02, 0x00))
        )!!
        val r = HemBattery.parseQuery(msg)!!
        assertEquals(97, r.left!!.level)
        assertTrue(r.left!!.isCharging)
        assertNull(r.right)
    }

    @Test
    fun `active battery report has no status byte`() {
        val msg = HemWire.parse(
            HemWire.build(HemCommands.CMD_ACTIVE_REPORT, 0x01, bytes(0x01, 0x02, 0x01, 0x5A, 0x02, 0x5A))
        )!!
        val r = HemBattery.parseActive(msg)
        assertNotNull(r)
        assertEquals(90, r!!.left!!.level)
        assertEquals(90, r.right!!.level)
    }

    @Test
    fun `malformed battery payload returns null`() {
        val msg = HemWire.parse(HemWire.build(HemCommands.RSP_BATTERY, 0xF0, bytes(0x00, 0x00)))!!
        assertNull(HemBattery.parseQuery(msg))
    }

    // ── 降噪 ────────────────────────────────────────────────

    @Test
    fun `anc set payload uses single bit at its byte`() {
        val frame = HemWire.build(HemCommands.CMD_SET_ANC, 0x10, HemCommands.setAnc(5).copyOfRange(9, 12))
        // 直接验 setAnc 的 payload 形状：01 01 20（bit5）
        val setFrame = HemCommands.setAnc(5)
        val payload = setFrame.copyOfRange(9, setFrame.size)
        assertEquals(0x01, payload[0].toInt())
        assertEquals(0x01, payload[1].toInt())
        assertEquals(0x20, payload[2].toInt())
        assertEquals(frame.size, setFrame.size)
    }

    @Test
    fun `anc event value is little endian and echo is rejected`() {
        // 真事件：03 01 01 20 00 → 0x0020
        val real = HemWire.parse(
            HemWire.build(HemCommands.CMD_ACTIVE_REPORT, 0x01, bytes(0x03, 0x01, 0x01, 0x20, 0x00))
        )!!
        assertEquals(0x0020, HemBattery.ancRawValue(real))

        // 写入回声：03 02 01 20 00 → 必须被拒（返回 -1），否则 mask 会被当成档位
        val echo = HemWire.parse(
            HemWire.build(HemCommands.CMD_ACTIVE_REPORT, 0x01, bytes(0x03, 0x02, 0x01, 0x20, 0x00))
        )!!
        assertEquals(-1, HemBattery.ancRawValue(echo))
    }

    @Test
    fun `anc status mapping covers the three device-center buttons`() {
        assertEquals(0, HemAnc.bitForStatus(1))    // 关闭 → Off
        assertEquals(2, HemAnc.bitForStatus(2))    // 通透 → Transparency
        assertEquals(5, HemAnc.bitForStatus(5))    // 降噪 → Medium
    }

    @Test
    fun `anc raw values map back to honor status`() {
        assertEquals(1, HemAnc.statusForRaw(0x0008))   // Off 上报
        assertEquals(1, HemAnc.statusForRaw(0x0001))   // Off SET 位
        assertEquals(2, HemAnc.statusForRaw(0x0004))   // Transparency
        assertEquals(5, HemAnc.statusForRaw(0x0020))   // Medium
        assertEquals(4, HemAnc.statusForRaw(0x0010))   // Deep
        assertNull(HemAnc.statusForRaw(0x0000))        // 无值 → null（不改状态）
    }

    // ── 命令表 ──────────────────────────────────────────────

    @Test
    fun `fixed sequence numbers match the protocol`() {
        val battery = HemCommands.queryBattery()
        assertEquals(0xF0, battery[6].toInt() and 0xFF)
        val wearing = HemCommands.queryWearing()
        assertEquals(0xF2, wearing[6].toInt() and 0xFF)
    }

    @Test
    fun `notify registration puts count first`() {
        val frame = HemCommands.registerNotifications(listOf(0x01, 0x02, 0x03))
        val payload = frame.copyOfRange(9, frame.size)
        assertEquals(3, payload[0].toInt())          // count 在前
        assertEquals(0x01, payload[1].toInt())
        assertEquals(0x02, payload[2].toInt())
        assertEquals(0x03, payload[3].toInt())
    }
}
