package com.mal.creamwrapper

import android.content.ComponentName
import android.os.Build

/**
 * 厂家判断 + 安装/卸载确认页 Activity 查询
 * 适配范围：Android 6.0 (API 23) ~ 最新版本
 *
 * 判断思路（优先级从高到低，逐级 fallback）：
 *   1) 厂家专属系统属性（最准，MIUI/EMUI/ColorOS/OriginOS 等只有对应 ROM 才会写入）
 *   2) Build.MANUFACTURER / Build.BRAND 关键词匹配（覆盖面最广，但部分换皮机/ODM 机型不准）
 *   3) Build.FINGERPRINT / Build.DISPLAY 兜底（个别设备 MANUFACTURER 为空或写成 unknown 时用）
 *
 * 系统属性读取两级兜底：
 *   a) 反射调用 android.os.SystemProperties（Android 9+ 非 SDK 接口限制下仍可读，多数厂商未拉黑读操作）
 *   b) 反射失败时执行 `getprop <key>`（无需权限，shell 层面始终可读）
 *
 * === 关于安装/卸载确认页 Activity 的联网核实结论（2026-10） ===
 * 查过 AOSP 源码树（android.googlesource.com/platform/frameworks/base 的
 * packages/PackageInstaller 目录）后确认一个容易踩坑的点：
 *   - com.android.packageinstaller.InstallStart 并不是用户看到的那个
 *     "是否安装此应用" 弹窗！它只是一个瞬时路由 Activity：校验调用方权限后，
 *     立刻 finish() 并把真正的确认界面转发给：
 *       · Session 安装 / package: URI  -> PackageInstallerActivity
 *       · content: URI                -> InstallStaging（再转给 PackageInstallerActivity）
 *       · 新版 "PIA V2" 流程（较新 Android 版本，按厂商/版本灰度）-> InstallLaunch
 *     所以无障碍服务真正能稳定抓到、且用户会停留点按 "安装/取消" 的类名，
 *     是 PackageInstallerActivity（旧/主流路径）或 InstallLaunch（PIA V2 路径），
 *     不是 InstallStart。卸载确认页则比较稳定，一直是 UninstallerActivity。
 *   - 公开资料里能验证到的、厂商在"安装"环节插入自定义界面的只有：
 *       · 小米/红米/POCO (MIUI/HyperOS)：新装应用会先跳安全中心做安全扫描确认页
 *         （com.miui.securitycenter / com.miui.permcenter.install.AppSecurityActivity），
 *         版本间类名有变动风险。
 *       · OPPO (ColorOS)：公开资料只证实了其在 **系统服务层**
 *         （com.android.server.pm.ColorPackageInstallInterceptManager）拦截
 *         ADB 侧载安装，这不是一个 Activity，无障碍服务抓不到它。
 *     三星、vivo、华为、荣耀、OnePlus、realme、传音系等厂商，公开渠道
 *     （AOSP 源码、开发者社区、第三方安装库如 SimpleInstaller）**查不到**
 *     被广泛验证过的自定义安装/卸载确认页类名资料——大概率是直接用 AOSP
 *     原版或仅改了包名的 fork，但无法仅凭联网搜索确认，必须实机抓取校验，
 *     我没有把猜测性的类名硬编码进表里。
 * - API 23~25（Android 6.0~7.1）系统还没有运行时权限弹窗插在安装流程里，
 *   但确认页 Activity 名本身与 API 26+ 一致，无需单独分支。
 *
 * 参考来源：
 * - AOSP PackageInstaller 源码目录: https://android.googlesource.com/platform/frameworks/base/+/ee03676afffd/packages/PackageInstaller/src/
 * - InstallStart.java 行为分析（来自对该源码文件的解读）
 * - OPPO ColorOS ADB 安装拦截: https://gist.github.com/LeadroyaL/cc1ec2fe8c0818da2436818f1e8dc122
 * - SimpleInstaller（跨厂商安装库，未发现 OEM 专属类名）: https://github.com/solrudev/SimpleInstaller
 */
object OemInstallerResolver {

    // ---------- 0. 系统属性读取（两级 fallback） ----------

    private fun getSystemProperty(key: String): String {
        // a) 反射 SystemProperties.get(key)
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val value = method.invoke(null, key) as? String
            if (!value.isNullOrBlank()) return value
        } catch (_: Throwable) {
            // 忽略，走 b) 方案
        }
        // b) shell getprop 兜底
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Throwable) {
            ""
        }
    }

    // ---------- 1. 设备信息快照 ----------

    data class DeviceSignature(
        val manufacturer: String = Build.MANUFACTURER,
        val brand: String = Build.BRAND,
        val product: String = Build.PRODUCT,
        val device: String = Build.DEVICE,
        val hardware: String = Build.HARDWARE,
        val fingerprint: String = Build.FINGERPRINT,
        val display: String = Build.DISPLAY,
        val sdkInt: Int = Build.VERSION.SDK_INT,
        val release: String = Build.VERSION.RELEASE
    ) {
        val combined: String by lazy {
            "$manufacturer $brand $product $device $hardware $fingerprint $display".lowercase()
        }
    }

    enum class Oem {
        AOSP_DEFAULT,
        SAMSUNG,
        XIAOMI,      // 含 Redmi / POCO (MIUI / HyperOS)
        OPPO,        // 含部分 OPLUS 统一系（ColorOS）
        ONEPLUS,     // OxygenOS / OPLUS 统一系
        VIVO,        // 含 iQOO (OriginOS / FuntouchOS)
        REALME,      // realme UI / OPLUS 统一系
        HUAWEI,      // EMUI（不含鸿蒙 NEXT，后者非 AOSP）
        HONOR,       // 2020 后独立，MagicOS/MagicUI
        TRANSSION,   // TECNO / Infinix / itel (HiOS / XOS)
        LENOVO,      // 含 Motorola
        GOOGLE,
        UNKNOWN
    }

    /**
     * 根据系统参数判断厂家。
     * 优先用厂商专属系统属性确认（更准），再用 Build 字段关键词兜底。
     */
    fun detectOem(sig: DeviceSignature = DeviceSignature()): Oem {
        // ---- 第一级：厂商专属系统属性，命中即高置信度返回 ----
        val propChecks: List<Pair<String, Oem>> = listOf(
            "ro.miui.ui.version.name" to Oem.XIAOMI,
            "ro.miui.ui.version.code" to Oem.XIAOMI,
            "ro.mi.os.version.name" to Oem.XIAOMI,          // HyperOS
            "ro.build.version.emui" to Oem.HUAWEI,
            "ro.build.version.magic" to Oem.HONOR,          // MagicOS/MagicUI
            "ro.build.hw_emui_api_level" to Oem.HUAWEI,
            "ro.vivo.os.version" to Oem.VIVO,
            "ro.vivo.os.build.display.id" to Oem.VIVO,
            "ro.build.version.opporom" to Oem.OPPO,
            "ro.oppo.theme.version" to Oem.OPPO,
            "ro.oneplus.version" to Oem.ONEPLUS,
            "ro.build.version.oplusrom" to Oem.OPPO,        // OPLUS 统一系，细分交给第二级
            "ro.build.version.realmeui" to Oem.REALME,
            "persist.sys.oplus.region" to Oem.OPPO,
            "ro.lenovomm.optimization" to Oem.LENOVO,
            "ro.build.version.samsung" to Oem.SAMSUNG,
            "ro.csc.sales_code" to Oem.SAMSUNG,             // 三星 CSC 码，仅三星设备有
            "ro.transsion.build.version" to Oem.TRANSSION,
            "ro.hios.version.name" to Oem.TRANSSION,        // TECNO HiOS
            "ro.xos.version.name" to Oem.TRANSSION          // Infinix XOS
        )
        for ((prop, oem) in propChecks) {
            if (getSystemProperty(prop).isNotBlank()) {
                // OPLUS 统一系细分：三个品牌共享部分属性，用 manufacturer/brand 再区分一次
                if (oem == Oem.OPPO) {
                    val c = sig.combined
                    if (c.contains("oneplus")) return Oem.ONEPLUS
                    if (c.contains("realme")) return Oem.REALME
                }
                return oem
            }
        }

        // ---- 第二级：Build.MANUFACTURER / BRAND 关键词匹配 ----
        val c = sig.combined
        return when {
            c.contains("samsung") -> Oem.SAMSUNG
            c.contains("xiaomi") || c.contains("redmi") || c.contains("poco") -> Oem.XIAOMI
            c.contains("oneplus") -> Oem.ONEPLUS
            c.contains("realme") -> Oem.REALME
            c.contains("oppo") -> Oem.OPPO
            c.contains("vivo") || c.contains("iqoo") -> Oem.VIVO
            c.contains("honor") -> Oem.HONOR
            c.contains("huawei") -> Oem.HUAWEI
            c.contains("transsion") || c.contains("tecno") ||
                c.contains("infinix") || c.contains("itel") -> Oem.TRANSSION
            c.contains("lenovo") || c.contains("motorola") || c.contains("moto") -> Oem.LENOVO
            c.contains("google") -> Oem.GOOGLE
            else -> Oem.UNKNOWN
        }
    }

    // ---------- 2. 厂家 -> 安装器 / 卸载确认页 Activity 映射 ----------

    /**
     * 一个厂家可能的安装器包名不止一个（有/无 GMS 情况不同），
     * 确认页类名也可能不止一个（legacy 路径 vs PIA V2 路径），
     * 所以用集合而不是单个 ComponentName，交给 match 函数做"包名+类名任一命中"判断。
     */
    data class InstallerActivities(
        val packageNames: Set<String>,
        val installConfirmClassNames: Set<String>,
        val uninstallConfirmClassNames: Set<String>
    )

    // AOSP 原生两种包名：有 GMS 用 com.google.android.packageinstaller，纯 AOSP/无 GMS 用 com.android.packageinstaller
    private val AOSP_PACKAGE_NAMES = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller"
    )
    // 注意：InstallStart 只是瞬时路由 Activity，真正用户会停留点按的是下面这些；
    // 仍把 InstallStart 留在集合里是为了兼容极端情况下来得及被无障碍事件捕获的边缘场景
    private val AOSP_INSTALL_CLASSES = setOf(
        "com.android.packageinstaller.PackageInstallerActivity", // legacy 主路径，目前仍是最常见的确认页
        "com.android.packageinstaller.InstallStaging",           // content: URI 安装时的中间页
        "com.android.packageinstaller.InstallLaunch",            // 较新 Android 版本灰度的 "PIA V2" 路径
        "com.android.packageinstaller.InstallStart"               // 路由页，通常一闪而过，不建议单独作为判断依据
    )
    private val AOSP_UNINSTALL_CLASSES = setOf(
        "com.android.packageinstaller.UninstallerActivity",
        "com.android.packageinstaller.UninstallUninstalling"
    )

    private val AOSP_DEFAULT_ACTIVITIES = InstallerActivities(
        packageNames = AOSP_PACKAGE_NAMES,
        installConfirmClassNames = AOSP_INSTALL_CLASSES,
        uninstallConfirmClassNames = AOSP_UNINSTALL_CLASSES
    )

    private val activityMap: Map<Oem, InstallerActivities> = mapOf(
        Oem.AOSP_DEFAULT to AOSP_DEFAULT_ACTIVITIES,
        Oem.GOOGLE to AOSP_DEFAULT_ACTIVITIES,
        Oem.XIAOMI to InstallerActivities(
            // MIUI/HyperOS：新装应用先过安全中心做安全扫描确认页，是在 AOSP 流程之外新插入的一层，
            // 卸载仍走 AOSP 原生流程；具体类名版本间有变动风险，务必实机校验
            packageNames = setOf("com.miui.securitycenter") + AOSP_PACKAGE_NAMES,
            installConfirmClassNames = setOf("com.miui.permcenter.install.AppSecurityActivity") + AOSP_INSTALL_CLASSES,
            uninstallConfirmClassNames = AOSP_UNINSTALL_CLASSES
        ),
        // 以下厂家：联网核实不到被广泛验证过的自定义类名资料，
        // 保守起见直接用 AOSP 默认集合（这些厂商大概率是 AOSP 原版或仅改包名的 fork），
        // 但没有实测验证，务必用 matchInstallOrUninstallActivity 在真机上抓日志核实
        Oem.SAMSUNG to AOSP_DEFAULT_ACTIVITIES,
        Oem.OPPO to AOSP_DEFAULT_ACTIVITIES,
        Oem.ONEPLUS to AOSP_DEFAULT_ACTIVITIES,
        Oem.VIVO to AOSP_DEFAULT_ACTIVITIES,
        Oem.REALME to AOSP_DEFAULT_ACTIVITIES,
        Oem.HUAWEI to AOSP_DEFAULT_ACTIVITIES,
        Oem.HONOR to AOSP_DEFAULT_ACTIVITIES,
        Oem.TRANSSION to AOSP_DEFAULT_ACTIVITIES,
        Oem.LENOVO to AOSP_DEFAULT_ACTIVITIES,
        Oem.UNKNOWN to AOSP_DEFAULT_ACTIVITIES
    )

    // ---------- 3. 对外可调用函数 ----------

    /** 获取当前设备厂家对应的安装/卸载确认页候选集合；未录入时回退 AOSP 默认值。 */
    fun getInstallerActivities(oem: Oem = detectOem()): InstallerActivities {
        return activityMap[oem] ?: AOSP_DEFAULT_ACTIVITIES
    }

    enum class MatchResult { INSTALL_CONFIRM, UNINSTALL_CONFIRM, NONE }

    /**
     * 供 AccessibilityService#onAccessibilityEvent 直接调用：
     * 传入事件的 packageName + className，判断是否命中安装/卸载确认页。
     * 判断规则：packageName 命中候选包名集合，且 className 命中对应确认页类名集合。
     */
    fun matchInstallOrUninstallActivity(
        packageName: String?,
        className: String?,
        oem: Oem = detectOem()
    ): MatchResult {
        if (packageName.isNullOrBlank() || className.isNullOrBlank()) return MatchResult.NONE
        val activities = getInstallerActivities(oem)
        if (packageName !in activities.packageNames) return MatchResult.NONE

        return when {
            className in activities.installConfirmClassNames -> MatchResult.INSTALL_CONFIRM
            className in activities.uninstallConfirmClassNames -> MatchResult.UNINSTALL_CONFIRM
            else -> MatchResult.NONE
        }
    }
}
