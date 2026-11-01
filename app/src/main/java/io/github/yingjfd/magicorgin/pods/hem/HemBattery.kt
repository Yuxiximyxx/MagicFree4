/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * 电量解析（OPPO Enco Free4 / 丹拿版）。
 *
 * 移植自 QuickBuds（GPL-3.0）protocol/Battery.kt:13-75。
 * 证据：PROTOCOL.md §电量章节；QuickBuds 代码注释给出了两条独立路径：
 *   查询回复 0x8106：payload = [status=00][count][idx, raw]...
 *   主动上报 0x0204 subType 0x01：payload = [01][count][idx, raw]...
 * 注意：主动上报**没有** status 字节，两条路径不能共用同一段解析。
 *
 * idx：1 = 左耳，2 = 右耳，3 = 仓
 * raw：level = raw & 0x7F；charging = (raw & 0x80) != 0；level 0 = 未知（按 null）
 */
package io.github.yingjfd.magicorgin.pods.hem

object HemBattery {

    data class Info(val level: Int, val isCharging: Boolean)

    data class Result(val left: Info?, val right: Info?, val case: Info?) {
        val any: Boolean get() = left != null || right != null || case != null
    }

    /** 解析 0x8106 查询回复。 */
    fun parseQuery(msg: HemWire.Message): Result? {
        if (msg.cmd != HemCommands.RSP_BATTERY) return null
        val p = msg.payload
        if (p.size < 2) return null
        if ((p[0].toInt() and 0xFF) != 0x00) return null
        return parsePairs(p, 1)
    }

    /** 解析 0x0204 subType 0x01 主动上报。 */
    fun parseActive(msg: HemWire.Message): Result? {
        if (msg.cmd != HemCommands.CMD_ACTIVE_REPORT) return null
        val p = msg.payload
        if (p.size < 3) return null
        if ((p[0].toInt() and 0xFF) != HemCommands.EVT_BATTERY) return null
        return parsePairs(p, 1)
    }

    /** [start] 指向 count 字节，其后是 (idx, raw) 对。 */
    private fun parsePairs(p: ByteArray, start: Int): Result? {
        if (start >= p.size) return null
        val count = p[start].toInt() and 0xFF
        if (count == 0 || p.size < start + 1 + count * 2) return null

        var left: Info? = null
        var right: Info? = null
        var case: Info? = null
        for (i in 0 until count) {
            val idxPos = start + 1 + i * 2
            val idx = p[idxPos].toInt() and 0xFF
            val raw = p[idxPos + 1].toInt() and 0xFF
            val level = raw and 0x7F
            val info = if (level == 0) null else Info(level, (raw and 0x80) != 0)
            when (idx) {
                1 -> left = info
                2 -> right = info
                3 -> case = info
            }
        }
        return Result(left, right, case)
    }

    /**
     * 解析降噪上报/回读的值。
     *
     *  - 0x0204 subType 0x03：payload `03 01 01 <value LE 1-4B>`；`03 02 01 <mask>` 是写入回声，必须拒绝。
     *  - 0x810C 查询回复：payload `00 <what> <arg> <value LE>`。
     */
    fun ancRawValue(msg: HemWire.Message): Int {
        val p = msg.payload
        return when (msg.cmd) {
            HemCommands.CMD_ACTIVE_REPORT -> {
                if (p.size < 5) return -1
                if ((p[0].toInt() and 0xFF) != HemCommands.EVT_ANC) return -1
                if ((p[1].toInt() and 0xFF) != 0x01 || (p[2].toInt() and 0xFF) != 0x01) return -1  // 回声帧
                leValue(p, 3)
            }
            HemCommands.RSP_ANC -> {
                if (p.size < 4) return -1
                leValue(p, 3)
            }
            else -> -1
        }
    }

    private fun leValue(p: ByteArray, from: Int): Int {
        var value = 0
        val end = minOf(p.size, from + 4)
        for (i in end - 1 downTo from) value = (value shl 8) or (p[i].toInt() and 0xFF)
        return value
    }
}
