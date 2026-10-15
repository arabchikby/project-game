package com.example.spacetycoon

import java.util.Locale
import kotlin.math.pow

/** Тип улучшения */
enum class Kind { TAP, PASSIVE, MULT, CRIT }

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
)

/** Цена = base * 1.15^count */
fun price(u: Upgrade, count: Int) = u.base * 1.15.pow(count)

/** Форматирование чисел: K, M, B, T, Qa, Qi */
fun fmt(v: Double): String {
    val s = listOf("", "K", "M", "B", "T", "Qa", "Qi")
    var x = v; var i = 0
    while (x >= 1000 && i < s.lastIndex) { x /= 1000; i++ }
    return if (i == 0) x.toLong().toString() else String.format(Locale.US, "%.1f%s", x, s[i])
}
