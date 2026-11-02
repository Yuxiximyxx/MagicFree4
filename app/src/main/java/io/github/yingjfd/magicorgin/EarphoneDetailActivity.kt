package io.github.yingjfd.magicorgin

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yingjfd.magicorgin.ui.MagicOriGTheme
import io.github.yingjfd.magicorgin.utils.MagicOriGAction
import kotlinx.coroutines.delay

/**
 * 耳机详情页（设备中心「更多设置」落地页）。
 *
 * 视觉严格对齐《Honor MagicOS 设计规范》（深色模式）：
 *   背景 #111827 / 卡片 #1F2937 / 三级背景 #374151 / 分隔线 #4B5563
 *   主文字 #F3F4F6 / 次要文字 #8A919A / 主操作色 #4D94FF / 成功色 #5CAA7E
 *   字号：Headline3 20sp Medium、Title2 16sp、Body2 14sp、Caption1 12sp
 *   圆角：卡片 16dp、弹窗 20dp、次按钮 12dp、主按钮 20dp（胶囊）
 *   间距：8dp 栅格、水平 16dp、区块 24dp、列表行 56dp、触控最小 48dp
 *
 * 数据与下发全部走本模块自己的跨进程桥，不依赖系统设备表。
 */
class EarphoneDetailActivity : ComponentActivity() {

    // ── MagicOS 深色模式色板 ────────────────────────────────
    private object P {
        val bg = Color(0xFF111827)
        val card = Color(0xFF1F2937)
        val cardAlt = Color(0xFF374151)
        val line = Color(0xFF4B5563)
        val textPrimary = Color(0xFFF3F4F6)
        val textSecondary = Color(0xFF8A919A)
        val textTertiary = Color(0xFF6B7280)
        val accent = Color(0xFF4D94FF)
        val success = Color(0xFF5CAA7E)
        val danger = Color(0xFFE07065)
    }

    private val FEATURES = listOf(
        Triple(0x06, "低延迟模式", "与视频保持同步"),
        Triple(0x18, "高音质音频", "提高比特率与采样率"),
        Triple(0x1B, "3D 音效", "带头部追踪的空间音频"),
        Triple(0x04, "佩戴检测", "摘下耳机时自动暂停"),
        Triple(0x11, "双设备连接", "同时连接两台设备"),
    )

    private val EQ_PRESETS = listOf(
        26 to "至臻原音",
        28 to "纯享人声",
        29 to "澎湃低音",
        34 to "活力动感",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = 0xFF111827.toInt()
            window.navigationBarColor = 0xFF111827.toInt()
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        setContent {
            MagicOriGTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = P.bg) { DetailScreen() }
            }
        }
    }

    private fun g(key: String): String? = runCatching {
        Settings.Global.getString(contentResolver, key)
    }.getOrNull()

    private fun send(action: String, fill: Intent.() -> Unit = {}) {
        runCatching {
            sendBroadcast(Intent(action).apply {
                fill()
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    private fun openBt(mac: String) {
        runCatching {
            startActivity(
                Intent("com.hihonor.intent.action.DEVICE_PROFILES_SETTINGS").putExtra("DEVICE_ID", mac)
            )
        }
    }

    @Composable
    private fun DetailScreen() {
        var raw by remember { mutableStateOf("") }
        var ancRaw by remember { mutableStateOf(0) }
        var connected by remember { mutableStateOf(false) }
        var mac by remember { mutableStateOf("") }
        var features by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
        var firmware by remember { mutableStateOf("--") }
        var alertVol by remember { mutableStateOf(5) }
        var eq by remember { mutableStateOf(-1) }
        var ringing by remember { mutableStateOf(false) }
        var showEq by remember { mutableStateOf(false) }
        var showVol by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                raw = g("magicorig_battery") ?: ""
                ancRaw = g("magicorig_anc_raw")?.toIntOrNull() ?: 0
                connected = g("magicorig_connected") == "1"
                mac = g("magicorig_mac") ?: ""
                features = parseFeatures(g("magicorig_features"))
                firmware = g("magicorig_firmware") ?: "--"
                alertVol = g("magicorig_alert_vol")?.toIntOrNull() ?: 5
                eq = g("magicorig_eq")?.toIntOrNull() ?: -1
                delay(1000)
            }
        }

        val bat = parseBattery(raw)
        val ancBit = ancBitOf(ancRaw)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── 页头 ──
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    getString(R.string.detail_device_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = P.textPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier.size(6.dp)
                            .background(if (connected) P.success else P.textTertiary, CircleShape),
                    )
                    Text(
                        if (connected) "已连接 · $mac" else "未连接 · $mac",
                        fontSize = 12.sp,
                        color = P.textSecondary,
                    )
                }
            }

            // ── 电量卡片 ──
            Card16 {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatteryRing("左耳", bat[0])
                    BatteryRing("右耳", bat[1])
                    BatteryRing("充电仓", bat[2])
                }
            }

            // ── 噪声控制 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("噪声控制")
                Card16 {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModePill("关闭", ancBit == 0, connected, Modifier.weight(1f)) {
                            send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 1) }
                        }
                        ModePill("降噪", ancBit == 5, connected, Modifier.weight(1f)) {
                            send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 5) }
                        }
                        ModePill("自适应", ancBit == 11, connected, Modifier.weight(1f)) {
                            send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 7) }
                        }
                        ModePill("通透", ancBit == 2, connected, Modifier.weight(1f)) {
                            send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 2) }
                        }
                    }
                }
            }

            // ── 功能开关 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("功能")
                Card16(padding = 0.dp) {
                    Column {
                        for ((idx, f) in FEATURES.withIndex()) {
                            val (id, name, desc) = f
                            val value = features[id]
                            SwitchRow(
                                title = name,
                                subtitle = desc,
                                checked = value == 1,
                                enabled = connected && value != null,
                                onClick = {
                                    if (value != null) {
                                        send(MagicOriGAction.ACTION_FEATURE_SET) {
                                            putExtra("id", id)
                                            putExtra("on", value != 1)
                                        }
                                        features = features.toMutableMap().apply { put(id, if (value == 1) 0 else 1) }
                                    }
                                },
                            )
                            if (idx != FEATURES.lastIndex) Line(16.dp)
                        }
                    }
                }
            }

            // ── 设置入口 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("设置")
                Card16(padding = 0.dp) {
                    Column {
                        NavRow("均衡器", eqName(eq)) { showEq = true }
                        Line(16.dp)
                        NavRow("提示音音量", "$alertVol / 10") { showVol = true }
                        Line(16.dp)
                        NavRow(
                            "查找耳机",
                            if (ringing) "正在响铃，点击停止" else "让两只耳机响铃",
                        ) {
                            ringing = !ringing
                            send(MagicOriGAction.ACTION_FIND_BUDS) { putExtra("on", ringing) }
                        }
                        Line(16.dp)
                        NavRow("蓝牙连接设置", "") { openBt(mac) }
                        Line(16.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("固件版本", fontSize = 15.sp, color = P.textPrimary)
                            Text(firmware, fontSize = 14.sp, color = P.textSecondary)
                        }
                    }
                }
            }

            Text(
                "开关状态以耳机回报为准；灰色表示当前固件未报该项能力。",
                fontSize = 12.sp,
                color = P.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
        }

        if (showEq) {
            AlertDialog(
                onDismissRequest = { showEq = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = P.card,
                title = { Text("均衡器", color = P.textPrimary, fontSize = 20.sp) },
                text = {
                    Column {
                        for ((id, name) in EQ_PRESETS) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable {
                                        send(MagicOriGAction.ACTION_EQ_PRESET) { putExtra("id", id) }
                                        eq = id
                                        showEq = false
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    name,
                                    fontSize = 15.sp,
                                    color = if (id == eq) P.accent else P.textPrimary,
                                    fontWeight = if (id == eq) FontWeight.Medium else FontWeight.Normal,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEq = false }) { Text("关闭", color = P.accent) }
                },
            )
        }

        if (showVol) {
            AlertDialog(
                onDismissRequest = { showVol = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = P.card,
                title = { Text("提示音音量", color = P.textPrimary, fontSize = 20.sp) },
                text = {
                    Column {
                        Text("$alertVol / 10", fontSize = 18.sp, color = P.textPrimary)
                        Slider(
                            value = alertVol.toFloat(),
                            onValueChange = { alertVol = it.toInt().coerceIn(1, 10) },
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        send(MagicOriGAction.ACTION_ALERT_VOLUME) { putExtra("level", alertVol) }
                        showVol = false
                    }) { Text("保存", color = P.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showVol = false }) { Text("取消", color = P.textSecondary) }
                },
            )
        }
    }

    // ── 组件（按规范）────────────────────────────────────────

    @Composable
    private fun SectionTitle(text: String) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = P.textSecondary)
    }

    @Composable
    private fun Card16(padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(P.card, RoundedCornerShape(16.dp))
                .padding(padding),
            content = { content() },
        )
    }

    @Composable
    private fun Line(startPadding: androidx.compose.ui.unit.Dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPadding)
                .height(1.dp)
                .background(P.line.copy(alpha = 0.5f)),
        )
    }

    @Composable
    private fun BatteryRing(label: String, level: Int) {
        val pct = level.coerceIn(0, 100)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = P.cardAlt, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (level >= 0) {
                        drawArc(
                            color = if (pct <= 20) P.danger else P.success,
                            startAngle = -90f, sweepAngle = 360f * pct / 100f,
                            useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(
                    if (level >= 0) "$pct%" else "--",
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = P.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, color = P.textSecondary)
        }
    }

    @Composable
    private fun ModePill(
        label: String,
        active: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = modifier
                .heightIn(min = 48.dp)
                .height(56.dp)
                .background(
                    when {
                        !enabled -> P.cardAlt.copy(alpha = 0.4f)
                        active -> P.accent
                        else -> P.cardAlt
                    },
                    RoundedCornerShape(12.dp),
                )
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    !enabled -> P.textTertiary
                    active -> Color.White
                    else -> P.textPrimary
                },
            )
        }
    }

    @Composable
    private fun SwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    color = if (enabled) P.textPrimary else P.textTertiary,
                )
                Text(subtitle, fontSize = 12.sp, color = P.textSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = P.accent,
                    uncheckedThumbColor = P.textSecondary,
                    uncheckedTrackColor = P.cardAlt,
                ),
            )
        }
    }

    @Composable
    private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = P.textPrimary)
                if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = P.textSecondary)
            }
            Text("›", fontSize = 20.sp, color = P.textTertiary)
        }
    }

    // ── 数据 ───────────────────────────────────────────────

    private fun eqName(id: Int): String = EQ_PRESETS.firstOrNull { it.first == id }?.second ?: "未设置"

    private fun ancBitOf(raw: Int): Int = if (raw <= 0) 0 else Integer.numberOfTrailingZeros(raw)

    private fun parseFeatures(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(',').mapNotNull { part ->
            val k = part.substringBefore('=').trim().toIntOrNull() ?: return@mapNotNull null
            val v = part.substringAfter('=', "").trim().toIntOrNull() ?: return@mapNotNull null
            k to v
        }.toMap()
    }

    private fun parseBattery(raw: String): IntArray {
        val out = intArrayOf(-1, -1, -1)
        if (raw.isBlank()) return out
        raw.split(',').forEach { part ->
            val p = part.trim()
            if (p.length < 2) return@forEach
            val v = p.substring(1).toIntOrNull() ?: return@forEach
            when (p.first()) {
                'L' -> out[0] = v
                'R' -> out[1] = v
                'C' -> out[2] = v
            }
        }
        return out
    }
}
