package com.example.spacetycoon

import java.util.Locale
import kotlin.math.pow

/** Тип улучшения */
enum class Kind { TAP, PASSIVE, MULT, CRIT, UTIL }

data class Upgrade(val id: Int, val emoji: String, val name: String, val kind: Kind, val value: Double, val base: Double)

/** Каталог улучшений из ТЗ (1–18). Улучшения 19–21 (метеориты, оракул) пока не реализованы. */
val UPGRADES = listOf(
    Upgrade(1, "🛠️", "Кирка", Kind.TAP, 1.0, 50.0),
    Upgrade(2, "⚒️", "Лазерный бур", Kind.TAP, 5.0, 500.0),
    Upgrade(3, "🔫", "Плазменная пушка", Kind.TAP, 25.0, 5_000.0),
    Upgrade(4, "💥", "Квантовый резак", Kind.TAP, 150.0, 50_000.0),
    Upgrade(5, "🌟", "Сингулярный пресс", Kind.TAP, 1000.0, 500_000.0),
    Upgrade(6, "🚀", "Грузовой челнок", Kind.PASSIVE, 1.0, 100.0),
    Upgrade(7, "⛏️", "Дрон-шахтёр", Kind.PASSIVE, 5.0, 600.0),
    Upgrade(8, "🛰️", "Орбитальная станция", Kind.PASSIVE, 20.0, 3_000.0),
    Upgrade(9, "🏭", "Космическая фабрика", Kind.PASSIVE, 80.0, 15_000.0),
    Upgrade(10, "🌍", "Колония", Kind.PASSIVE, 350.0, 80_000.0),
    Upgrade(11, "🌌", "Межгалактический портал", Kind.PASSIVE, 1500.0, 400_000.0),
    Upgrade(12, "🕳️", "Чёрная дыра", Kind.PASSIVE, 8000.0, 2_000_000.0),
    Upgrade(13, "🌠", "Дайсон-сфера", Kind.PASSIVE, 40_000.0, 10_000_000.0),
    Upgrade(14, "📡", "Торговая сеть x1.5", Kind.MULT, 1.5, 100_000.0),
    Upgrade(15, "💠", "Кристалл власти x2", Kind.MULT, 2.0, 1_000_000.0),
    Upgrade(16, "👑", "Корона магната x3", Kind.MULT, 3.0, 10_000_000.0),
    Upgrade(17, "🌌", "Галактический трон x5", Kind.MULT, 5.0, 100_000_000.0),
    Upgrade(18, "🎁", "Бонусный генератор +5% крит", Kind.CRIT, 0.05, 25_000.0),
    Upgrade(19, "⚡", "Гипер-ускоритель (метеориты чаще)", Kind.UTIL, 0.1, 75_000.0),
    Upgrade(20, "🧲", "Магнит метеоритов (награда x2)", Kind.UTIL, 2.0, 300_000.0),
    Upgrade(21, "🔮", "Оракул (прогноз дохода)", Kind.UTIL, 1.0, 500_000.0),
)

/** Улучшения, которые можно купить только один раз */
fun Upgrade.single() = kind == Kind.MULT || id == 20 || id == 21

/** Достижение: условие считается из сохранённой статистики, +1% к доходу за каждое */
class Ach(val emoji: String, val name: String, val desc: String, val check: (GameState) -> Boolean)

private val BASE_ACH = listOf(
    Ach("👆", "Первый тап", "1 тап") { s -> s.taps >= 1 },
    Ach("💯", "Сотня", "100 тапов") { s -> s.taps >= 100 },
    Ach("🖐️", "Тысяча", "1 000 тапов") { s -> s.taps >= 1000 },
    Ach("🔥", "Десять тысяч", "10 000 тапов") { s -> s.taps >= 10_000 },
    Ach("⚡", "Сто тысяч", "100 000 тапов") { s -> s.taps >= 100_000 },
    Ach("🛒", "Первая покупка", "купи улучшение") { s -> s.counts.values.sum() >= 1 },
    Ach("🧰", "Полный арсенал", "купи все улучшения") { s -> UPGRADES.all { (s.counts[it.id] ?: 0) > 0 } },
    Ach("💥", "Критик", "первый крит") { s -> s.crits >= 1 },
    Ach("🎯", "Мастер крита", "100 критов") { s -> s.crits >= 100 },
    Ach("🌀", "Комбо x10", "10 подряд") { s -> s.bestCombo >= 10 },
    Ach("🌪️", "Комбо x50", "50 подряд") { s -> s.bestCombo >= 50 },
    Ach("☄️", "Охотник", "10 метеоритов") { s -> s.meteors >= 10 },
    Ach("⭐", "Перерождение", "первый престиж") { s -> s.prestiges >= 1 },
    Ach("💰", "Миллионер", "1M кредитов") { s -> s.total >= 1e6 },
    Ach("🏦", "Миллиардер", "1B кредитов") { s -> s.total >= 1e9 },
    Ach("🌌", "Триллионер", "1T кредитов") { s -> s.total >= 1e12 },
    Ach("✨", "Звёздный", "10 звёздной пыли") { s -> s.dust >= 10 },
)
val ACHS = BASE_ACH + Ach("🏆", "Легенда", "все достижения") { s -> BASE_ACH.all { it.check(s) } }
fun achCount(s: GameState) = ACHS.count { it.check(s) }

/** Цена = base * 1.15^count */
fun price(u: Upgrade, count: Int) = u.base * 1.15.pow(count)

/** Форматирование чисел: K, M, B, T, Qa, Qi */
fun fmt(v: Double): String {
    val s = listOf("", "K", "M", "B", "T", "Qa", "Qi")
    var x = v; var i = 0
    while (x >= 1000 && i < s.lastIndex) { x /= 1000; i++ }
    return if (i == 0) x.toLong().toString() else String.format(Locale.US, "%.1f%s", x, s[i])
}
