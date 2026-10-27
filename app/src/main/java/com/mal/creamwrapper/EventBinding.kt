/**
 * 逻辑层：数组预处理 + 映射表 + 事件分发。
 * 数组解析处、事件执行处只需要调用这里的 BindingTable / EventDispatcher，不用关心细节。
 */

/** 绑定变量取值 */
object BindingValue {
    const val NONE = "none"
    val VALID: Set<String> = setOf("a", "b", "c", "d", NONE)
}

/** 数组条目的角色：X1（触发项） / X2（事件项） */
enum class ItemKind { TRIGGER, EVENT }

/**
 * 默认按名称末位数字区分角色：以 1 结尾为 X1，以 2 结尾为 X2。
 * 如果你的命名规则不同，在 BindingTable.parse 里传入自己的判断函数即可。
 */
fun defaultKindOf(name: String): ItemKind? = when (name.lastOrNull()) {
    '1' -> ItemKind.TRIGGER
    '2' -> ItemKind.EVENT
    else -> null
}

/**
 * 预处理结果，暂存在内存中的映射表。
 *
 *  - triggerBinding：X1 名称 -> 绑定变量值
 *  - eventsByBinding：绑定变量值 -> 该值下的所有 X2 名称（保持原数组顺序）
 *
 * X2 的绑定值为 none 时不会入表（none 的 X1 只会调用空事件，永远用不到它）。
 */
class BindingTable private constructor(
    private val triggerBinding: Map<String, String>,
    private val eventsByBinding: Map<String, List<String>>,
    /** 格式不合法、绑定值不在 a/b/c/d/none 内、无法判断 X1/X2 的条目，便于排查数据问题 */
    val skipped: List<String>,
) {
    /** 某个 X1 的绑定变量值，X1 不存在时返回 null */
    fun bindingOf(x1: String): String? = triggerBinding[x1]

    /** 输入 X1，返回需要调用的 X2 名称列表；none、未知 X1、没有匹配的 X2 都返回空列表 */
    fun eventsOf(x1: String): List<String> {
        val binding = triggerBinding[x1] ?: return emptyList()
        if (binding == BindingValue.NONE) return emptyList()
        return eventsByBinding[binding].orEmpty()
    }

    companion object {
        // 形如 a1[a]：名称 + [绑定值]
        private val ITEM = Regex("""^([^\[\],\s]+)\[([^\[\],\s]+)]$""")

        /** 直接解析 "a1[a],b1[none],..." 这样的逗号分隔字符串 */
        fun parse(
            raw: String,
            kindOf: (String) -> ItemKind? = ::defaultKindOf,
        ): BindingTable = parse(raw.split(','), kindOf)

        /** 解析字符串集合（数组用 .toList() 转一下） */
        fun parse(
            items: Collection<String>,
            kindOf: (String) -> ItemKind? = ::defaultKindOf,
        ): BindingTable {
            val triggers = LinkedHashMap<String, String>()
            val events = LinkedHashMap<String, MutableList<String>>()
            val skipped = mutableListOf<String>()

            for (item in items.map { it.trim() }.filter { it.isNotEmpty() }) {
                val match = ITEM.matchEntire(item)
                if (match == null) {
                    skipped.add(item)
                    continue
                }
                val name = match.groupValues[1]
                val binding = match.groupValues[2].lowercase()
                if (binding !in BindingValue.VALID) {
                    skipped.add(item)
                    continue
                }
                when (kindOf(name)) {
                    ItemKind.TRIGGER -> triggers[name] = binding
                    ItemKind.EVENT ->
                        if (binding != BindingValue.NONE) {
                            events.getOrPut(binding) { mutableListOf() }.add(name)
                        }
                    null -> skipped.add(item)
                }
            }
            return BindingTable(triggers, events, skipped)
        }
    }
}

/**
 * 事件分发：输入 X1，执行绑定变量相同的所有 X2 事件。
 *
 * @param handlers X2 名称 -> 对应函数，例如 "a2" to ::a2
 * @param onMissing 映射表里有这个 X2，但 handlers 里没注册时的处理，默认打印警告
 */
class EventDispatcher(
    private val table: BindingTable,
    handlers: Map<String, () -> Unit>,
    private val onMissing: (String) -> Unit = { println("[WARN] 事件 $it 未注册，已跳过") },
) {
    private val handlers: Map<String, () -> Unit> = handlers.toMap()

    /** 返回本次实际匹配到的 X2 名称（空列表代表空事件） */
    fun trigger(x1: String): List<String> {
        val names = table.eventsOf(x1)
        for (name in names) {
            val action = handlers[name]
            if (action == null) onMissing(name) else action()
        }
        return names
    }
}
