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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 * 版式参照 QuickBuds 的 tile 结构（连接态 pill + 分区卡片），
 * 数据与下发全部走本模块自己的跨进程桥，不依赖系统设备表：
 *   电量 / 当前档位 / 连接态  ← Settings.Global（蓝牙进程写）
 *   切档 / 响铃              → 广播（蓝牙进程执行）
 */
class EarphoneDetailActivity : ComponentActivity() {

    private val accent = Color(0xFF4A6DFF)
    private val accent2 = Color(0xFF27D0C4)
    private val cardBg = Color(0xFF1B1D2B)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏/导航栏跟随页面深色底，避免白底压在深色页面上
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
        var anc by remember { mutableStateOf(-1) }
        var connected by remember { mutableStateOf(false) }
        var mac by remember { mutableStateOf("") }
        var ringing by remember { mutableStateOf(false) }
        var hint by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            while (true) {
                raw = g("magicorig_battery") ?: ""
                anc = g("magicorig_current_anc")?.toIntOrNull() ?: -1
                connected = g("magicorig_connected") == "1"
                mac = g("magicorig_mac") ?: ""
                delay(1000)
            }
        }

        val bat = parseBattery(raw)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部：标题 + 连接态 pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        getString(R.string.detail_device_name),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(mac, fontSize = 11.sp, color = Color(0xFF8B93B0))
                }
                ConnPill(connected)
            }

            // 电量卡（渐变 hero）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(accent, accent2)),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(vertical = 20.dp),
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

            // 噪声控制
            Tile(title = "噪声控制") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeButton("降噪", anc == 5, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 5) }
                        hint = "已切到降噪（以回读为准）"
                    }
                    ModeButton("通透", anc == 2, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 2) }
                        hint = "已切到通透（以回读为准）"
                    }
                    ModeButton("关闭", anc == 1, connected, Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_ANC_SELECT) { putExtra("status", 1) }
                        hint = "已关闭降噪（以回读为准）"
                    }
                }
            }

            // 快捷操作
            Tile(title = "快捷操作") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        if (ringing) "停止响铃" else "查找耳机",
                        Modifier.weight(1f),
                    ) {
                        ringing = !ringing
                        send(MagicOriGAction.ACTION_FIND_BUDS) { putExtra("on", ringing) }
                        hint = if (ringing) "已请求耳机响铃" else "已请求停止响铃"
                    }
                    ActionButton("刷新状态", Modifier.weight(1f)) {
                        send(MagicOriGAction.ACTION_REFRESH_STATUS)
                        hint = "已请求刷新"
                    }
                }
                Spacer(Modifier.height(10.dp))
                ActionButton("蓝牙连接设置", Modifier.fillMaxWidth()) { openBt(mac) }
            }

            if (hint.isNotEmpty()) {
                Text(hint, fontSize = 12.sp, color = Color(0xFF9AA3C0))
            }
            Text(
                "档位以耳机回报为准：点按后模块会回读一次，高亮即回读结果。",
                fontSize = 11.sp,
                color = Color(0xFF6C7490),
            )
        }
    }

    @Composable
    private fun ConnPill(connected: Boolean) {
        Row(
            modifier = Modifier
                .background(
                    if (connected) Color(0xFF1E3A2A) else Color(0xFF2A2230),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (connected) Color(0xFF35D07F) else Color(0xFF8A8A8A), CircleShape),
            )
            Text(
                if (connected) "已连接" else "未连接",
                fontSize = 12.sp,
                color = if (connected) Color(0xFF9BE7BF) else Color(0xFFB9B9B9),
            )
        }
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
    private fun BatteryRing(label: String, level: Int) {
        val pct = level.coerceIn(0, 100)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Color(0x55FFFFFF),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (level >= 0) {
                        drawArc(
                            color = Color.White,
                            startAngle = -90f, sweepAngle = 360f * pct / 100f, useCenter = false,
                            topLeft = Offset(inset, inset), size = arcSize,
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
                .height(64.dp)
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
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (enabled) Color.White else Color(0xFF6A7086),
            )
        }
    }

    @Composable
    private fun ActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Box(
            modifier = modifier
                .height(48.dp)
                .background(Color(0xFF262A3D), RoundedCornerShape(14.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 14.sp, color = Color(0xFFDCE2F5), fontWeight = FontWeight.Medium)
        }
    }

    /** "L100,R80,C90" → [左, 右, 仓]，-1 = 未知。 */
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
