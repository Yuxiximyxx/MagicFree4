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
 * 耳机详情页（「更多设置」落地页）。
 *
 * 版式对齐 QuickBuds 主界面：电量三环 + 四档噪声控制 + 功能开关列表 + 设置入口。
 * 数据与下发全部走本模块自己的跨进程桥，不依赖系统设备表：
 *   电量 / 档位 / 连接态 / 功能开关 / 固件 / 音量 / EQ  ← Settings.Global（蓝牙进程写）
 *   各类操作                                          → 广播（蓝牙进程执行）
 */
class EarphoneDetailActivity : ComponentActivity() {

    private val accent = Color(0xFF4A6DFF)
    private val accent2 = Color(0xFF27D0C4)
    private val cardBg = Color(0xFF1B1D2B)
    private val subText = Color(0xFF8B93B0)

    // 功能开关 id（欢律 Feature 常量）
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
            window.statusBarColor = 0xFF101223.toInt()
            window.navigationBarColor = 0xFF101223.toInt()
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        setContent {
            MagicOriGTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101223)) {
                    DetailScreen()
                }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        getString(R.string.detail_device_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(mac.ifEmpty { "—" }, fontSize = 10.sp, color = subText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnPill(connected)
                    RoundIcon("⟳") { send(MagicOriGAction.ACTION_REFRESH_STATUS) }
                    RoundIcon("✕") { finish() }
                }
            }

            // 电量卡
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(accent, accent2)), RoundedCornerShape(24.dp))
                    .padding(vertical = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatteryRing("左耳", bat[0])
                    BatteryRing("右耳", bat[1])
                    BatteryRing("充电仓", bat[2])
                }
            }

            // 噪声控制（四档）
            Tile("噪声控制") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("关闭", ancBit == 0, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 1) }
                    }
                    ModeButton("降噪", ancBit == 5, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 5) }
                    }
                    ModeButton("自适应", ancBit == 11, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 7) }
                    }
                    ModeButton("通透", ancBit == 2, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 2) }
                    }
                }
            }

            // 功能开关
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(22.dp)),
            ) {
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
                                // 乐观更新，耳机回报后会覆盖
                                features = features.toMutableMap().apply { put(id, if (value == 1) 0 else 1) }
                            }
                        },
                    )
                    if (idx != FEATURES.lastIndex) Divider()
                }
            }

            // 设置入口
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(22.dp)),
            ) {
                NavRow("均衡器", eqName(eq)) { showEq = true }
                Divider()
                NavRow("提示音音量", "当前 $alertVol") { showVol = true }
                Divider()
                NavRow("查找耳机", if (ringing) "正在响铃，点击停止" else "让两只耳机响铃") {
                    ringing = !ringing
                    send(MagicOriGAction.ACTION_FIND_BUDS) { putExtra("on", ringing) }
                }
                Divider()
                NavRow("蓝牙连接设置", "") { openBt(mac) }
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("固件版本", color = Color(0xFFDCE2F5), fontSize = 14.sp)
                    Text(firmware, color = subText, fontSize = 13.sp)
                }
            }

            Text(
                "开关状态以耳机回报为准；若某项一直显示灰色，说明这副耳机的固件不支持该功能。",
                fontSize = 11.sp,
                color = Color(0xFF6C7490),
            )
            Spacer(Modifier.height(24.dp))
        }

        if (showEq) {
            AlertDialog(
                onDismissRequest = { showEq = false },
                title = { Text("均衡器") },
                text = {
                    Column {
                        for ((id, name) in EQ_PRESETS) {
                            Text(
                                (if (id == eq) "✓ " else "   ") + name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        send(MagicOriGAction.ACTION_EQ_PRESET) { putExtra("id", id) }
                                        eq = id
                                        showEq = false
                                    }
                                    .padding(vertical = 12.dp),
                                fontSize = 16.sp,
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showEq = false }) { Text("关闭") } },
            )
        }

        if (showVol) {
            AlertDialog(
                onDismissRequest = { showVol = false },
                title = { Text("提示音音量") },
                text = {
                    Column {
                        Text("$alertVol / 10", fontSize = 18.sp)
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
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { showVol = false }) { Text("取消") } },
            )
        }
    }

    // ── 小组件 ─────────────────────────────────────────────

    @Composable
    private fun ConnPill(connected: Boolean) {
        Row(
            modifier = Modifier
                .background(if (connected) Color(0xFF1E3A2A) else Color(0xFF2A2230), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier.size(7.dp)
                    .background(if (connected) Color(0xFF35D07F) else Color(0xFF8A8A8A), CircleShape),
            )
            Text(
                if (connected) "已连接" else "未连接",
                fontSize = 11.sp,
                color = if (connected) Color(0xFF9BE7BF) else Color(0xFFB9B9B9),
            )
        }
    }

    @Composable
    private fun RoundIcon(icon: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(0xFF262A3D), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(icon, color = Color(0xFFDCE2F5), fontSize = 15.sp) }
    }

    @Composable
    private fun Tile(title: String, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB9C2E0))
            content()
        }
    }

    @Composable
    private fun Divider() {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF23273A)))
    }

    @Composable
    private fun BatteryRing(label: String, level: Int) {
        val pct = level.coerceIn(0, 100)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Color(0x55FFFFFF), startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (level >= 0) {
                        drawArc(
                            color = Color.White, startAngle = -90f, sweepAngle = 360f * pct / 100f,
                            useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(
                    if (level >= 0) "$pct%" else "--",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = Color(0xCCFFFFFF))
        }
    }

    @Composable
    private fun ModeButton(
        label: String,
        active: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = modifier
                .height(60.dp)
                .background(
                    when {
                        !enabled -> Color(0xFF262A3D)
                        active -> accent
                        else -> Color(0xFF262A3D)
                    },
                    RoundedCornerShape(16.dp),
                )
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (enabled) Color.White else Color(0xFF6A7086),
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
                .clickable(enabled = enabled) { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) Color(0xFFDCE2F5) else Color(0xFF6A7086),
                )
                Text(subtitle, fontSize = 11.sp, color = subText)
            }
            Switch(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        }
    }

    @Composable
    private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDCE2F5))
                if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = subText)
            }
            Text("›", fontSize = 20.sp, color = subText)
        }
    }

    // ── 数据 ───────────────────────────────────────────────

    private fun eqName(id: Int): String = EQ_PRESETS.firstOrNull { it.first == id }?.second ?: "未设置"

    /** 原始位掩码 → 档位标识（0/2/5/11） */
    private fun ancBitOf(raw: Int): Int = when {
        raw <= 0 -> 0
        else -> Integer.numberOfTrailingZeros(raw)
    }

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
