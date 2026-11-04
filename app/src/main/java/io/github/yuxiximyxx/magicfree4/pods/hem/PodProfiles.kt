/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * 欢律耳机档案。协议帧、电量、写入回声校验（payload[1]==payload[2]==0x01）两款耳机相同，
 * 差异只在「有没有自适应降噪」和「均衡器预设」。
 *
 * status→slot 与 bit→status 必须分成两张表：Air4 Pro 上 1/5/6/7 都落 slot 5，
 * 反向解不回原来的荣耀 status。
 *
 * Free4 是机主自己的耳机，默认档案与历史行为一致（含自适应 slot 11、四档 EQ、自定义 EQ）。
 */
package io.github.yuxiximyxx.magicfree4.pods.hem

data class EqPreset(val slot: Int, val name: String)

data class PodProfile(
    /** 写入 Settings.Global magicorig_model 的稳定 id。 */
    val id: String,
    val displayName: String,
    /** HEM Product ID，大写十六进制，不含 0x。丹拿版与普通版共用一份行为。 */
    val productIds: Set<String>,
    /** 蓝牙名片段，越长越优先；大小写不敏感。 */
    val nameTokens: List<String>,
    val supportsAdaptiveAnc: Boolean,
    val supportsCustomEq: Boolean,
    /**
     * 0x0406 写入的是 slot，不是 models.json 里的 mode。
     * Free4：slot 0/1/2/7 ↔ 至臻原音 / 纯享人声 / 澎湃低音 / 活力动感。
     * Air4 Pro 没有第四档，也没有 customEqualizer。
     */
    val eqPresets: List<EqPreset>,
    /** 荣耀 status → 欢律 SET slot。 */
    val statusToSlot: Map<Int, Int>,
    /** 上报值的最低有效位 → 荣耀 status。表里没有的位保持 null，不改 UI。 */
    val bitToStatus: Map<Int, Int>,
)

object PodProfiles {

    private val FREE4_STATUS_TO_SLOT = mapOf(
        1 to HemAnc.BIT_OFF,
        2 to HemAnc.BIT_TRANSPARENCY,
        3 to HemAnc.BIT_ANC_MEDIUM,
        4 to HemAnc.BIT_ANC_DEEP,
        5 to HemAnc.BIT_ANC_MEDIUM,
        6 to HemAnc.BIT_ANC_MEDIUM,
        7 to HemAnc.BIT_ADAPTIVE,
    )

    private val FREE4_BIT_TO_STATUS = mapOf(
        HemAnc.BIT_OFF to 1,
        HemAnc.RPT_OFF_CHILD to 1,
        HemAnc.BIT_TRANSPARENCY to 2,
        HemAnc.RPT_TRANSPARENCY_CHILD to 2,
        HemAnc.BIT_ANC_DEEP to 4,
        HemAnc.BIT_ADAPTIVE to 7,
        HemAnc.BIT_ANC_PARENT to 5,
        HemAnc.BIT_ANC_MEDIUM to 5,
        HemAnc.BIT_ANC_LIGHT to 5,
        HemAnc.BIT_ANC_SMART to 5,
    )

    /**
     * Air4 Pro 没有顶层自适应（slot 11）。荣耀「自适应」与上报 bit 11 都落到中档降噪。
     * 智能降噪（slot 7）仍是真实子档，但面板不单独露出，避免把没验证过的第 4 档当成自适应。
     */
    private val AIR4_STATUS_TO_SLOT = mapOf(
        1 to HemAnc.BIT_OFF,
        2 to HemAnc.BIT_TRANSPARENCY,
        3 to HemAnc.BIT_ANC_MEDIUM,
        4 to HemAnc.BIT_ANC_DEEP,
        5 to HemAnc.BIT_ANC_MEDIUM,
        6 to HemAnc.BIT_ANC_MEDIUM,
        7 to HemAnc.BIT_ANC_MEDIUM,
    )

    private val AIR4_BIT_TO_STATUS = mapOf(
        HemAnc.BIT_OFF to 1,
        HemAnc.RPT_OFF_CHILD to 1,
        HemAnc.BIT_TRANSPARENCY to 2,
        HemAnc.RPT_TRANSPARENCY_CHILD to 2,
        HemAnc.BIT_ANC_DEEP to 4,
        HemAnc.BIT_ADAPTIVE to 5,
        HemAnc.BIT_ANC_PARENT to 5,
        HemAnc.BIT_ANC_MEDIUM to 5,
        HemAnc.BIT_ANC_LIGHT to 5,
        HemAnc.BIT_ANC_SMART to 5,
    )

    val FREE4 = PodProfile(
        id = "free4",
        displayName = "OPPO Enco Free4",
        productIds = setOf("068C10", "06C010"),
        nameTokens = listOf("Enco Free4", "Free4"),
        supportsAdaptiveAnc = true,
        supportsCustomEq = true,
        eqPresets = listOf(
            EqPreset(0, "至臻原音"),
            EqPreset(1, "纯享人声"),
            EqPreset(2, "澎湃低音"),
            EqPreset(7, "活力动感"),
        ),
        statusToSlot = FREE4_STATUS_TO_SLOT,
        bitToStatus = FREE4_BIT_TO_STATUS,
    )

    val AIR4_PRO = PodProfile(
        id = "air4pro",
        displayName = "OPPO Enco Air4 Pro",
        productIds = setOf("067C10"),
        nameTokens = listOf("Enco Air4 Pro", "Air4"),
        supportsAdaptiveAnc = false,
        supportsCustomEq = false,
        eqPresets = listOf(
            EqPreset(0, "至臻原音"),
            EqPreset(1, "纯享人声"),
            EqPreset(2, "澎湃低音"),
        ),
        statusToSlot = AIR4_STATUS_TO_SLOT,
        bitToStatus = AIR4_BIT_TO_STATUS,
    )

    /** Air4 Pro 放前面只影响同长度 token 的平局；实际按 token 长度取最长命中。 */
    val ALL = listOf(AIR4_PRO, FREE4)

    private val KNOWN_PRODUCT_IDS = ALL.flatMap { it.productIds }.toSet()

    fun byId(id: String?): PodProfile? = ALL.firstOrNull { it.id == id }

    fun byProductId(productId: String?): PodProfile? {
        val key = productId?.trim()?.uppercase().orEmpty()
        if (key.isEmpty()) return null
        return ALL.firstOrNull { key in it.productIds }
    }

    /**
     * 蓝牙名命中。两个 token 都要：长的精确，短的兼容系统改名或加后缀。
     * 多条命中时取最长 token，避免短 token 抢走更具体的名字。
     */
    fun matchName(name: String?): PodProfile? {
        val text = name?.trim().orEmpty()
        if (text.isEmpty()) return null
        var best: PodProfile? = null
        var bestLen = -1
        for (profile in ALL) {
            for (token in profile.nameTokens) {
                if (token.length > bestLen && text.contains(token, ignoreCase = true)) {
                    best = profile
                    bestLen = token.length
                }
            }
        }
        return best
    }

    /**
     * 解析 0x8103 payload。
     *
     * 协议记载是 `00 <id 3B LE>`（QuickBuds Capabilities.kt）。实机字节序也可能是大端，
     * 或直接带 ASCII `067C10`。三种都认；认不出返回 null，不覆盖已经按名字判定的机型。
     */
    fun parseProductId(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val ascii = runCatching { String(payload, Charsets.US_ASCII).uppercase() }.getOrNull()
        if (ascii != null) {
            KNOWN_PRODUCT_IDS.firstOrNull { ascii.contains(it) }?.let { return it }
        }
        val windows = ArrayList<ByteArray>(3)
        if (payload.size >= 4 && (payload[0].toInt() and 0xFF) == 0x00) {
            windows += payload.copyOfRange(1, 4)
        }
        if (payload.size >= 3) windows += payload.copyOfRange(0, 3)
        if (payload.size > 3) windows += payload.copyOfRange(payload.size - 3, payload.size)
        for (window in windows) {
            val le = "%02X%02X%02X".format(
                window[2].toInt() and 0xFF,
                window[1].toInt() and 0xFF,
                window[0].toInt() and 0xFF,
            )
            val be = "%02X%02X%02X".format(
                window[0].toInt() and 0xFF,
                window[1].toInt() and 0xFF,
                window[2].toInt() and 0xFF,
            )
            if (le in KNOWN_PRODUCT_IDS) return le
            if (be in KNOWN_PRODUCT_IDS) return be
        }
        return null
    }
}
