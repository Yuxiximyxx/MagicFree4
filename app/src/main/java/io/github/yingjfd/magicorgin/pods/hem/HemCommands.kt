/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * 欢律（OPPO / HeyMelody）命令表。
 *
 * 移植自 QuickBuds（GPL-3.0）:
 *   https://github.com/spizganed/QuickBuds
 *   源文件：protocol/Protocol.kt、protocol/Commands.kt、protocol/Capabilities.kt
 * 命令号与 payload 形状均为逆向所得，证据见 QuickBuds/docs/PROTOCOL.md。
 *
 * 本项目只用其中最小集合：握手、产品 ID、降噪读写、电量、佩戴、事件订阅。
 */
package io.github.yingjfd.magicorgin.pods.hem

import java.util.concurrent.atomic.AtomicInteger

object HemCommands {

    // ── 命令号 ──────────────────────────────────────────────
    const val CMD_HANDSHAKE = 0x0100
    const val CMD_QUERY_PRODUCT_ID = 0x0103
    const val CMD_QUERY_BATTERY = 0x0106          // seq 固定 0xF0
    const val CMD_QUERY_WEARING = 0x0109          // seq 固定 0xF2
    const val CMD_QUERY_ANC = 0x010C
    const val CMD_ACTIVE_REPORT = 0x0204          // 主动上报，payload[0] = subType
    const val CMD_REGISTER_NOTIFY = 0x0205        // 订阅主动上报
    const val CMD_SET_ANC = 0x0404

    // ── 回复号 ──────────────────────────────────────────────
    const val RSP_HANDSHAKE = 0x8100
    const val RSP_PRODUCT_ID = 0x8103
    const val RSP_BATTERY = 0x8106
    const val RSP_WEARING = 0x8109
    const val RSP_ANC = 0x810C

    // ── 0x0204 上报子类型 ────────────────────────────────────
    const val EVT_BATTERY = 0x01
    const val EVT_WEARING = 0x02
    const val EVT_ANC = 0x03

    // ── 固定序号 ────────────────────────────────────────────
    const val SEQ_BATTERY = 0xF0
    const val SEQ_WEARING = 0xF2

    /** 通用序号：0x01..0xBF 循环（引擎专用区间 0xC0..0xDF 不用）。 */
    private val seqCounter = AtomicInteger(0x10)

    fun nextSeq(): Int {
        while (true) {
            val cur = seqCounter.get()
            val next = if (cur >= 0xBF) 0x01 else cur + 1
            if (seqCounter.compareAndSet(cur, next)) return next
        }
    }

    // ── 报文构造 ────────────────────────────────────────────

    fun handshake(): ByteArray = HemWire.build(CMD_HANDSHAKE, nextSeq())

    fun queryProductId(): ByteArray = HemWire.build(CMD_QUERY_PRODUCT_ID, nextSeq())

    /**
     * 订阅主动上报。payload = [count][id...]（QuickBuds 实测：count 在前，
     * 写成 01 01 02 02 会被当成「只订阅电量」，佩戴推送永远不来）。
     */
    fun registerNotifications(ids: List<Int>): ByteArray {
        val payload = ByteArray(1 + ids.size)
        payload[0] = ids.size.toByte()
        ids.forEachIndexed { i, id -> payload[1 + i] = id.toByte() }
        return HemWire.build(CMD_REGISTER_NOTIFY, nextSeq(), payload)
    }

    /** 读当前降噪档：payload 01 01 → 回复 0x810C。 */
    fun queryAnc(): ByteArray = HemWire.build(CMD_QUERY_ANC, nextSeq(), byteArrayOf(0x01, 0x01))

    fun queryBattery(): ByteArray = HemWire.build(CMD_QUERY_BATTERY, SEQ_BATTERY)

    fun queryWearing(): ByteArray = HemWire.build(CMD_QUERY_WEARING, SEQ_WEARING)

    /**
     * 写降噪档：payload 01 01 <bit mask>，mask 为单个 bit（bit 序号见 [HemAnc]）。
     * 注意：写入本身会让耳机回一帧 0x0204 subType 03 的「回声」，其 payload 是
     * `03 02 01 <mask>`，不是真事件 —— 解析时必须校验 [1]==[2]==0x01。
     */
    fun setAnc(bit: Int): ByteArray {
        val mask = ByteArray(2 + bit / 8 + 1)
        mask[0] = 0x01
        mask[1] = 0x01
        mask[2 + bit / 8] = (1 shl (bit % 8)).toByte()
        return HemWire.build(CMD_SET_ANC, nextSeq(), mask)
    }
}
