/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * 降噪档 ↔ 位掩码映射。
 *
 * 不带 profile 的两个函数保持 Free4（含丹拿版）的历史行为，这是默认路径。
 * Air4 Pro 没有自适应 slot 11，映射在 [PodProfiles] 里另表维护，调用时显式传入。
 * status→slot 与 bit→status 是两张表，不能互相反推。
 *
 * 依据：
 *  - Free4 的机型表：QuickBuds/app/src/main/assets/models.json:108（普通版 068C10）
 *    与 :120（丹拿版 06C010），两版 ancModes 完全相同。
 *  - 上报/写入双表：QuickBuds/protocol/AncModes.kt:55-93（SET 用 slot，上报按树查）。
 *  - 「ANC 打开时停在上次档位」实测：QuickBuds/protocol/Anc.kt:22-43
 *    （0x0010 = Deep bit4，0x0020 = Medium bit5）。
 *  - 写入回声仍由 [HemBattery.ancRawValue] 拒绝（0x0204 subType 0x03 且 payload[1]==payload[2]==0x01 才是真事件）。
 *
 * Free4 的 ANC 树（SET slot / 上报 bit）：
 *   Off            slot 0   （上报子位 3）
 *   Transparency   slot 2   （上报子位 8）
 *   ANC            slot 1   ← 父节点，上报 0x0002 时只表示「在降噪」
 *     ├ Deep       slot 4
 *     ├ Medium     slot 5
 *     ├ Light      slot 6
 *     └ Smart      slot 7
 *   Adaptive      slot 11
 *
 * 荣耀侧 status（MagicOriG 内部值，见 hook/ControlCenterNoiseHook.kt）：
 *   1 = 关闭, 2 = 通透, 3 = NORMAL, 4 = DEEP, 5 = EXPERIMENT（面板「降噪」）, 6 = 抗风噪
 */
package io.github.yuxiximyxx.magicfree4.pods.hem

object HemAnc {

    const val BIT_OFF = 0
    const val BIT_ANC_PARENT = 1
    const val BIT_TRANSPARENCY = 2
    const val BIT_ANC_DEEP = 4
    const val BIT_ANC_MEDIUM = 5
    const val BIT_ANC_LIGHT = 6
    const val BIT_ANC_SMART = 7
    const val BIT_ADAPTIVE = 11

    /** 上报专用的子位（不是额外的档位，只是同一档位的另一种上报值）。 */
    const val RPT_OFF_CHILD = 3
    const val RPT_TRANSPARENCY_CHILD = 8

    /**
     * 荣耀 status → 欢律 SET bit。默认走 Free4 表。
     *
     * 「降噪」走 Medium（bit 5）：QuickBuds 实测「ANC on」上报 0x0020 = Medium，
     * 是这套固件默认恢复到的那一档，听感上最接近荣耀面板的「降噪」。
     */
    fun bitForStatus(status: Int): Int? = bitForStatus(status, PodProfiles.FREE4)

    fun bitForStatus(status: Int, profile: PodProfile): Int? = profile.statusToSlot[status]

    /** 欢律上报值（位掩码 LE 解出的整数）→ 荣耀 status；未知值返回 null（不改状态）。 */
    fun statusForRaw(raw: Int): Int? = statusForRaw(raw, PodProfiles.FREE4)

    fun statusForRaw(raw: Int, profile: PodProfile): Int? {
        if (raw <= 0) return null
        val bit = Integer.numberOfTrailingZeros(raw)
        return profile.bitToStatus[bit]
    }

    /** 仅用于日志。 */
    fun nameOfStatus(status: Int): String = when (status) {
        1 -> "关闭"
        2 -> "通透"
        3 -> "普通"
        4 -> "深度降噪"
        5 -> "降噪"
        6 -> "抗风噪"
        7 -> "自适应"
        else -> "未知($status)"
    }
}
