/*
 * 当前连接的是哪一款耳机。蓝牙进程写入，设备中心 / SystemUI / 详情页读取。
 *
 * 名字在识别命中时就发布（不等 SPP）；0x8103 的 product id 后到，作为更硬的确认，
 * 不一致时覆盖名字判定。认不出 id 时不覆盖，避免一次坏包把 Free4 改成别的机型。
 *
 * 写入必须用主线程缓存的 Application，见 [DeviceIdentity]。
 */
package io.github.yuxiximyxx.magicfree4.utils

import android.content.Context
import io.github.yuxiximyxx.magicfree4.hook.Log
import io.github.yuxiximyxx.magicfree4.pods.hem.PodProfile
import io.github.yuxiximyxx.magicfree4.pods.hem.PodProfiles

object PodIdentity {

    const val MODEL_KEY = "magicorig_model"

    private const val TAG = "MagicOriG-Identity"

    @Volatile private var lastPublished: String? = null

    fun publish(profile: PodProfile, context: Context? = null) {
        if (profile.id == lastPublished) return
        val ctx = context ?: DeviceIdentity.currentApp()
        if (ctx == null) {
            Log.w(TAG, "发布机型 ${profile.id} 失败：没有可用的 Application")
            return
        }
        runCatching {
            android.provider.Settings.Global.putString(ctx.contentResolver, MODEL_KEY, profile.id)
            lastPublished = profile.id
            Log.i(TAG, "已发布 $MODEL_KEY=${profile.id}（${profile.displayName}）")
        }.onFailure {
            Log.w(TAG, "发布 $MODEL_KEY 失败: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    /** 读不到或尚未识别时回到 Free4，保证机主那副耳机的界面与今天一致。 */
    fun current(context: Context? = null): PodProfile {
        val ctx = context ?: DeviceIdentity.currentApp() ?: return PodProfiles.FREE4
        val id = runCatching {
            android.provider.Settings.Global.getString(ctx.contentResolver, MODEL_KEY)
        }.getOrNull()
        return PodProfiles.byId(id) ?: PodProfiles.FREE4
    }
}
