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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.Brush
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
 * 耳机详情页 —— 系统设置风格（纯黑 + 卡片 + 顶部渐变模糊 + 圆形返回 + 大标题）。
 *
 * 三个页面共用一套壳：
 *   0 概览（电量 / 噪声控制 / 功能开关 / 设置入口）
 *   1 均衡器（预设列表）
 *   2 耳机设置（佩戴检测 / 查找耳机 / 提示音音量 / 固件版本 / 手势占位）
 */
class EarphoneDetailActivity : ComponentActivity() {

    private object P {
        val bg = Color(0xFF000000)
        val card = Color(0xFF1C1C1E)
        val cardAlt = Color(0xFF2C2C2E)
        val line = Color(0xFF38383A)
        val textPrimary = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF8E8E93)
        val textTertiary = Color(0xFF636366)
        val accent = Color(0xFF0A84FF)
        val success = Color(0xFF32D74B)
        val danger = Color(0xFFFF453A)
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
            window.statusBarColor = 0x00000000
            window.navigationBarColor = 0xFF000000.toInt()
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        setContent {
            MagicOriGTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = P.bg) { Root() }
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
    private fun Root() {
        var raw by remember { mutableStateOf("") }
        var ancRaw by remember { mutableStateOf(0) }
        var connected by remember { mutableStateOf(false) }
        var mac by remember { mutableStateOf("") }
        var features by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
        var firmware by remember { mutableStateOf("--") }
        var alertVol by remember { mutableStateOf(5) }
        var eq by remember { mutableStateOf(-1) }
        var ringing by remember { mutableStateOf(false) }
        var showVol by remember { mutableStateOf(false) }
        var page by remember { mutableStateOf(0) }

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

        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            // —— 可滚动内容 ——
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = statusBar + 56.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        getString(R.string.detail_device_name),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = P.textPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
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

                    when (page) {
                        0 -> OverviewPage(
                            raw = raw, ancRaw = ancRaw, connected = connected,
                            features = features, eq = eq, alertVol = alertVol, firmware = firmware,
                            onNavigate = { page = it },
                            onOpenBt = { openBt(mac) },
                            onEq = { id -> send(MagicOriGAction.ACTION_EQ_PRESET) { putExtra("id", id) }; eq = id },
                            onFeature = { id, value ->
                                send(MagicOriGAction.ACTION_FEATURE_SET) {
                                    putExtra("id", id); putExtra("on", value != 1)
                                }
                                features = features.toMutableMap().apply { put(id, if (value == 1) 0 else 1) }
                            },
                            onFind = {
                                ringing = !ringing
                                send(MagicOriGAction.ACTION_FIND_BUDS) { putExtra("on", ringing) }
                            },
                            onVolume = { showVol = true },
                        )
                        1 -> EqPage(eq = eq, onSelect = { id ->
                            send(MagicOriGAction.ACTION_EQ_PRESET) { putExtra("id", id) }
                            eq = id
                        })
                        else -> SettingsPage(
                            connected = connected, features = features, firmware = firmware,
                            alertVol = alertVol, ringing = ringing,
                            onFeature = { id, value ->
                                send(MagicOriGAction.ACTION_FEATURE_SET) {
                                    putExtra("id", id); putExtra("on", value != 1)
                                }
                                features = features.toMutableMap().apply { put(id, if (value == 1) 0 else 1) }
                            },
                            onFind = {
                                ringing = !ringing
                                send(MagicOriGAction.ACTION_FIND_BUDS) { putExtra("on", ringing) }
                            },
                            onVolume = { showVol = true },
                            onOpenBt = { openBt(mac) },
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }

            // —— 顶部渐变模糊遮罩（内容从标题下滚过时渐隐）——
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBar + 56.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to P.bg,
                            0.55f to P.bg.copy(alpha = 0.92f),
                            0.85f to P.bg.copy(alpha = 0.55f),
                            1.0f to Color.Transparent,
                        )
                    ),
            )

            // —— 顶部栏：圆形返回 + 标题 ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBar + 8.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(P.cardAlt, CircleShape)
                        .clickable { if (page == 0) finish() else page = 0 },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", fontSize = 24.sp, color = P.textPrimary)
                }
                Text(
                    when (page) {
                        0 -> getString(R.string.detail_device_name)
                        1 -> "均衡器"
                        else -> "耳机设置"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = P.textPrimary,
                )
            }
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

    // ── 页面 0：概览 ────────────────────────────────────────

    @Composable
    private fun OverviewPage(
        raw: String,
        ancRaw: Int,
        connected: Boolean,
        features: Map<Int, Int>,
        eq: Int,
        alertVol: Int,
        firmware: String,
        onNavigate: (Int) -> Unit,
        onOpenBt: () -> Unit,
        onEq: (Int) -> Unit,
        onFeature: (Int, Int) -> Unit,
        onFind: () -> Unit,
        onVolume: () -> Unit,
    ) {
        val bat = parseBattery(raw)
        val ancBit = ancBitOf(ancRaw)

        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Card20 {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatteryRing("左耳", bat[0])
                    BatteryRing("右耳", bat[1])
                    BatteryRing("充电仓", bat[2])
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("噪声控制")
                Card20 {
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("功能")
                Card20(padding = 0.dp) {
                    Column {
                        for ((idx, f) in FEATURES.withIndex()) {
                            val (id, name, desc) = f
                            val value = features[id]
                            SwitchRow(name, desc, value == 1, connected && value != null) {
                                if (value != null) onFeature(id, value)
                            }
                            if (idx != FEATURES.lastIndex) Line()
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("设置")
                Card20(padding = 0.dp) {
                    Column {
                        NavRow("均衡器", eqName(eq)) { onNavigate(1) }
                        Line()
                        NavRow("耳机设置", "手势、佩戴检测、提示音、固件") { onNavigate(2) }
                        Line()
                        NavRow("蓝牙连接设置", "") { onOpenBt() }
                    }
                }
            }

            Text(
                "开关状态以耳机回报为准；灰色表示当前固件未报该项能力。",
                fontSize = 12.sp,
                color = P.textTertiary,
            )
        }
    }

    // ── 页面 1：均衡器 ──────────────────────────────────────

    @Composable
    private fun EqPage(eq: Int, onSelect: (Int) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("推荐")
                Card20(padding = 0.dp) {
                    Column {
                        for ((idx, p) in EQ_PRESETS.withIndex()) {
                            val (id, name) = p
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { onSelect(id) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    name,
                                    fontSize = 15.sp,
                                    color = if (id == eq) P.accent else P.textPrimary,
                                )
                                if (id == eq) Text("✓", color = P.accent, fontSize = 16.sp)
                            }
                            if (idx != EQ_PRESETS.lastIndex) Line()
                        }
                    }
                }
            }
            Text("自定义均衡曲线需要额外协议支持，暂未开放。", fontSize = 12.sp, color = P.textTertiary)
        }
    }

    // ── 页面 2：耳机设置 ────────────────────────────────────

    @Composable
    private fun SettingsPage(
        connected: Boolean,
        features: Map<Int, Int>,
        firmware: String,
        alertVol: Int,
        ringing: Boolean,
        onFeature: (Int, Int) -> Unit,
        onFind: () -> Unit,
        onVolume: () -> Unit,
        onOpenBt: () -> Unit,
    ) {
        val wear = features[0x04]
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("功能")
                Card20(padding = 0.dp) {
                    Column {
                        SwitchRow("佩戴检测", "摘下耳机时自动暂停", wear == 1, connected && wear != null) {
                            if (wear != null) onFeature(0x04, wear)
                        }
                        Line()
                        NavRow("耳机手势", "设置点按和滑动（待实现）") { }
                        Line()
                        NavRow(
                            "查找耳机",
                            if (ringing) "正在响铃，点击停止" else "让两只耳机响铃",
                        ) { onFind() }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("声音")
                Card20(padding = 0.dp) {
                    Column {
                        NavRow("提示音音量", "$alertVol / 10") { onVolume() }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("关于耳机")
                Card20(padding = 0.dp) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("固件版本", fontSize = 15.sp, color = P.textPrimary)
                            Text(firmware, fontSize = 14.sp, color = P.textSecondary)
                        }
                        Line()
                        NavRow("蓝牙连接设置", "") { onOpenBt() }
                    }
                }
            }
        }
    }

    // ── 组件 ───────────────────────────────────────────────

    @Composable
    private fun SectionTitle(text: String) {
        Text(text, fontSize = 13.sp, color = P.textSecondary, modifier = Modifier.padding(start = 4.dp))
    }

    @Composable
    private fun Card20(padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(P.card, RoundedCornerShape(20.dp))
                .padding(padding),
            content = { content() },
        )
    }

    @Composable
    private fun Line() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(1.dp)
                .background(P.line),
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
                        color = P.cardAlt, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (level >= 0) {
                        drawArc(
                            color = if (pct <= 20) P.danger else P.success,
                            startAngle = -90f, sweepAngle = 360f * pct / 100f, useCenter = false,
                            topLeft = Offset(inset, inset), size = arcSize,
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
                .height(56.dp)
                .background(
                    when {
                        !enabled -> P.cardAlt.copy(alpha = 0.4f)
                        active -> P.accent
                        else -> P.cardAlt
                    },
                    RoundedCornerShape(14.dp),
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
                Text(title, fontSize = 15.sp, color = if (enabled) P.textPrimary else P.textTertiary)
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
