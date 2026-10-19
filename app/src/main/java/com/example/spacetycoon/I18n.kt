package com.example.spacetycoon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Языки игры. Флаг — эмодзи страны языка. */
enum class Lang(val code: String, val flag: String, val title: String) {
    RU("ru", "\uD83C\uDDF7\uD83C\uDDFA", "Русский"),
    BE("be", "\uD83C\uDDE7\uD83C\uDDFE", "Беларуская"),
    UK("uk", "\uD83C\uDDFA\uD83C\uDDE6", "Українська"),
    EN("en", "\uD83C\uDDEC\uD83C\uDDE7", "English"),
    DE("de", "\uD83C\uDDE9\uD83C\uDDEA", "Deutsch");

    companion object {
        fun fromCode(c: String?) = values().firstOrNull { it.code == c }
        /** Язык телефона; если такого перевода нет — английский */
        fun detect() = fromCode(Locale.getDefault().language) ?: EN
    }
}

/** Текущий язык хранится как Compose-состояние: при смене весь интерфейс перерисуется сам */
object I18n { var lang by mutableStateOf(Lang.RU) }

// Строки: русский оригинал | белорусский | украинский | английский | немецкий.
// Пустая часть — перевод совпадает с русским.
private val DICT: Map<String, List<String>> by lazy {
    RAW.lines().filter { it.isNotBlank() }.associate { line ->
        val p = line.split("|")
        p[0] to p.drop(1)
    }
}

/** Перевод строки по русскому оригиналу. Параметры подставляются в {0}, {1}... */
fun t(ru: String, vararg args: Any): String {
    val l = I18n.lang
    var s = if (l == Lang.RU) ru else DICT[ru]?.getOrNull(l.ordinal - 1)?.takeIf { it.isNotEmpty() } ?: ru
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

private const val RAW = """Ржавый мир|Іржавы свет|Іржавий світ|Rusty World|Rostige Welt
Океаническая|Акіянічная|Океанічна|Oceanic|Ozeanisch
Неоновая туманность|Неонавая туманнасць|Неонова туманність|Neon Nebula|Neonnebel
Золотая планета|Залатая планета|Золота планета|Golden Planet|Goldener Planet
Игра|Гульня|Гра|Game|Spiel
Апгрейды|Паляпшэнні|Покращення|Upgrades|Upgrades
Награды|Узнагароды|Нагороди|Awards|Erfolge
Статистика|Статыстыка||Stats|Statistik
Настройки|Налады|Налаштування|Settings|Einstellungen
Забрать|Забраць|Забрати|Collect|Abholen
Пока вас не было...|Пакуль вас не было...|Поки вас не було...|While you were away...|Während du weg warst...
Заработано: {0}|Зарабілі: {0}|Зароблено: {0}|Earned: {0}|Verdient: {0}
Достижение получено! +1% к доходу|Дасягненне атрымана! +1% да даходу|Досягнення отримано! +1% до доходу|Achievement unlocked! +1% income|Erfolg freigeschaltet! +1% Einkommen
КРИТ! x{0}|КРЫТ! x{0}||CRIT! x{0}|KRIT! x{0}
Бонус дня|||Daily bonus|Tagesbonus
+{0}/сек|||+{0}/s|+{0}/Sek.
x10 · {0}с|||x10 · {0}s|x10 · {0}s
10 мин: +{0}|10 хв: +{0}|10 хв: +{0}|10 min: +{0}|10 Min.: +{0}
КОМБО x{0} · {1}|КАМБА x{0} · {1}||COMBO x{0} · {1}|KOMBO x{0} · {1}
Тапай быстрее — копи комбо|Тапай хутчэй — збірай камба|Тапай швидше — збирай комбо|Tap faster to build a combo|Tippe schneller für Kombos
Престиж: +{0} пыли|Прэстыж: +{0} пылу|Престиж: +{0} пилу|Prestige: +{0} dust|Prestige: +{0} Staub
Престиж с 1M за забег|Прэстыж ад 1M за забег|Престиж від 1M за забіг|Prestige from 1M per run|Prestige ab 1M pro Run
Метеорит!|Метэарыт!|Метеорит!|Meteor!|Meteor!
Достижения|Дасягненні|Досягнення|Achievements|Erfolge
{0} / {1} · +{0}% к доходу|{0} / {1} · +{0}% да даходу|{0} / {1} · +{0}% до доходу|{0} / {1} · +{0}% income|{0} / {1} · +{0}% Einkommen
ЗА ТАП|||PER TAP|PRO TAP
ПАССИВНЫЙ ДОХОД|ПАСІЎНЫ ДАХОД|ПАСИВНИЙ ДОХІД|PASSIVE INCOME|PASSIVES EINKOMMEN
МНОЖИТЕЛИ|МНАЖНІКІ|МНОЖНИКИ|MULTIPLIERS|MULTIPLIKATOREN
УТИЛИТЫ|УТЫЛІТЫ|УТИЛІТИ|UTILITIES|HILFSMITTEL
+{0} за тап|||+{0} per tap|+{0} pro Tap
×{0} ко всему|×{0} да ўсяго|×{0} до всього|×{0} to everything|×{0} auf alles
+5% к шансу крита|+5% да шанцу крыта|+5% до шансу крита|+5% crit chance|+5% Krit-Chance
метеориты чаще|метэарыты часцей|метеорити частіше|meteors more often|Meteoren häufiger
награда метеоритов x2|узнагарода метэарытаў x2|нагорода метеоритів x2|meteor reward x2|Meteoren-Belohnung x2
прогноз дохода|прагноз даходу|прогноз доходу|income forecast|Einkommensprognose
ЕСТЬ|ЁСЦЬ|Є|OWNED|GEKAUFT
Тапов|Тапаў|Тапів|Taps|Taps
Заработано|Зарабіла|Зароблено|Earned|Verdient
Критов|Крытаў|Критів|Crits|Krits
Лучшее комбо|Лепшае камба|Найкраще комбо|Best combo|Beste Kombo
Звёздная пыль|Зорны пыл|Зоряний пил|Stardust|Sternenstaub
Престижей|Прэстыжаў|Престижів|Prestiges|Prestiges
Время в игре|Час у гульні|Час у грі|Playtime|Spielzeit
{0} мин|{0} хв|{0} хв|{0} min|{0} Min.
Метеоритов|Метэарытаў|Метеоритів|Meteors|Meteoren
Рейтинг магнатов|Рэйтынг магнатаў|Рейтинг магнатів|Tycoon Ranking|Magnaten-Rangliste
Звёздный Волк|Зорны Воўк|Зоряний Вовк|Star Wolf|Sternwolf
Капитан Нова|Капітан Нова|Капітан Нова|Captain Nova|Kapitän Nova
Лорд Квазар|||Lord Quasar|Lord Quasar
Мадам Орбита|Мадам Арбіта|Мадам Орбіта|Madame Orbit|Madame Orbit
Тёмный Барон|Цёмны Барон|Темний Барон|Dark Baron|Dunkler Baron
Император Зет|Імператар Зет|Імператор Зет|Emperor Zed|Imperator Zet
Вы|Вы|Ви|You|Du
Вибрация|Вібрацыя|Вібрація|Vibration|Vibration
Звуки|Гукі||Sounds|Töne
Резервная копия|Рэзервовая копія|Резервна копія|Backup|Sicherung
Чтобы прогресс пережил удаление игры, сохрани файл в папку «Загрузки» или облако. После установки выбери этот файл для восстановления.|Каб прагрэс перажыў выдаленне гульні, захавай файл у папку «Загрузкі» або ў воблака. Пасля ўстаноўкі абяры гэты файл для аднаўлення.|Щоб прогрес пережив видалення гри, збережи файл у папку «Завантаження» або в хмару. Після встановлення обери цей файл для відновлення.|To keep your progress after uninstalling, save the file to Downloads or the cloud. After installing, pick this file to restore.|Damit dein Fortschritt eine Deinstallation übersteht, speichere die Datei in Downloads oder in der Cloud. Wähle sie nach der Installation zur Wiederherstellung aus.
Сохранить резервную копию|Захаваць рэзервовую копію|Зберегти резервну копію|Save backup|Sicherung speichern
Восстановить сохранение|Аднавіць захаванне|Відновити збереження|Restore save|Spielstand wiederherstellen
Сбросить прогресс|Скінуць прагрэс|Скинути прогрес|Reset progress|Fortschritt zurücksetzen
ОК|||OK|OK
Сбросить всё?|Скінуць усё?|Скинути все?|Reset everything?|Alles zurücksetzen?
Прогресс будет удалён безвозвратно.|Прагрэс будзе выдалены назаўсёды.|Прогрес буде видалено назавжди.|Progress will be deleted permanently.|Der Fortschritt wird endgültig gelöscht.
Да|Так|Так|Yes|Ja
Отмена|Скасаваць|Скасувати|Cancel|Abbrechen
Резервная копия сохранена. Не удаляй этот файл — он понадобится после переустановки.|Рэзервовая копія захаваная. Не выдаляй гэты файл — ён спатрэбіцца пасля пераўстаноўкі.|Резервну копію збережено. Не видаляй цей файл — він знадобиться після перевстановлення.|Backup saved. Don't delete this file — you'll need it after reinstalling.|Sicherung gespeichert. Lösche die Datei nicht — du brauchst sie nach der Neuinstallation.
Не удалось сохранить резервную копию.|Не ўдалося захаваць рэзервовую копію.|Не вдалося зберегти резервну копію.|Couldn't save the backup.|Sicherung konnte nicht gespeichert werden.
Сохранение восстановлено!|Захаванне адноўлена!|Збереження відновлено!|Save restored!|Spielstand wiederhergestellt!
Файл не похож на сохранение SpaceTycoon.|Файл не падобны на захаванне SpaceTycoon.|Файл не схожий на збереження SpaceTycoon.|This file doesn't look like a SpaceTycoon save.|Die Datei sieht nicht nach einem SpaceTycoon-Spielstand aus.
Не удалось прочитать резервную копию.|Не ўдалося прачытаць рэзервовую копію.|Не вдалося прочитати резервну копію.|Couldn't read the backup.|Sicherung konnte nicht gelesen werden.
Космический Магнат · {0}|Касмічны Магнат · {0}|Космічний Магнат · {0}|Space Tycoon · {0}|Weltraum-Magnat · {0}
Шторм окончен! Уничтожено: {0}|Шторм скончаны! Знішчана: {0}|Шторм завершено! Знищено: {0}|Storm over! Destroyed: {0}|Sturm vorbei! Zerstört: {0}
Астероидный шторм! Нажми|Астэроідны шторм! Націсні|Астероїдний шторм! Натисни|Asteroid storm! Tap here|Asteroidensturm! Tippen
Шторм: {0}с · уничтожено {1}|Шторм: {0}с · знішчана {1}|Шторм: {0}с · знищено {1}|Storm: {0}s · destroyed {1}|Sturm: {0}s · zerstört {1}
Космо-питомец|Касма-гадаванец|Космо-улюбленець|Space Pet|Weltraum-Haustier
Яйцо|Яйка|Яйце|Egg|Ei
Ур. {0}|Узр. {0}|Рів. {0}|Lv. {0}|St. {0}
Сейчас +{0}% ко всему · каждый уровень +5%|Цяпер +{0}% да ўсяго · кожны ўзровень +5%|Зараз +{0}% до всього · кожен рівень +5%|Now +{0}% to everything · +5% per level|Aktuell +{0}% auf alles · +5% pro Stufe
Вылупить|Вывесці|Висидіти|Hatch|Schlüpfen
Кормить|Карміць|Годувати|Feed|Füttern
Бонус дня: +{0}|||Daily bonus: +{0}|Tagesbonus: +{0}
и звёздная пыль|і зорны пыл|і зоряний пил|and stardust|und Sternenstaub
серия {0}|серыя {0}|серія {0}|streak {0}|Serie {0}
x10 на {0} сек!|||x10 for {0}s!|x10 für {0} Sek.!
Торговый караван! x6 на 30 сек|Гандлёвы караван! x6 на 30 сек|Торговий караван! x6 на 30 с|Trade caravan! x6 for 30s|Handelskarawane! x6 für 30 Sek.
Караван x6 · {0}с|||Caravan x6 · {0}s|Karawane x6 · {0}s
Кирка|Кірка||Pickaxe|Spitzhacke
Лазерный бур|Лазерны бур|Лазерний бур|Laser Drill|Laserbohrer
Плазменная пушка|Плазменная гармата|Плазмова гармата|Plasma Cannon|Plasmakanone
Квантовый резак|Квантавы рэзак|Квантовий різак|Quantum Cutter|Quantenschneider
Сингулярный пресс|Сінгулярны прэс|Сингулярний прес|Singularity Press|Singularitätspresse
Грузовой челнок|Грузавы чаўнок|Вантажний човник|Cargo Shuttle|Frachtshuttle
Дрон-шахтёр|Дрон-шахцёр|Дрон-шахтар|Mining Drone|Bergbau-Drohne
Орбитальная станция|Арбітальная станцыя|Орбітальна станція|Orbital Station|Orbitalstation
Космическая фабрика|Касмічная фабрыка|Космічна фабрика|Space Factory|Weltraumfabrik
Колония|Калонія|Колонія|Colony|Kolonie
Межгалактический портал|Міжгалактычны парталь|Міжгалактичний портал|Intergalactic Portal|Intergalaktisches Portal
Чёрная дыра|Чорная дзірка|Чорна діра|Black Hole|Schwarzes Loch
Дайсон-сфера|Сфера Дайсана|Сфера Дайсона|Dyson Sphere|Dyson-Sphäre
Торговая сеть x1.5|Гандлёвая сетка x1.5|Торгова мережа x1.5|Trade Network x1.5|Handelsnetz x1.5
Кристалл власти x2|Крышталь улады x2|Кристал влади x2|Crystal of Power x2|Machtkristall x2
Корона магната x3|Карона магната x3||Tycoon's Crown x3|Magnatenkrone x3
Галактический трон x5|Галактычны трон x5|Галактичний трон x5|Galactic Throne x5|Galaktischer Thron x5
Бонусный генератор +5% крит|Бонусны генератар +5% крыт|Бонусний генератор +5% крит|Bonus Generator +5% crit|Bonus-Generator +5% Krit
Гипер-ускоритель (метеориты чаще)|Гіпер-паскаральнік (метэарыты часцей)|Гіпер-прискорювач (метеорити частіше)|Hyper Accelerator (more meteors)|Hyperbeschleuniger (mehr Meteoren)
Магнит метеоритов (награда x2)|Магніт метэарытаў (узнагарода x2)|Магніт метеоритів (нагорода x2)|Meteor Magnet (reward x2)|Meteoren-Magnet (Belohnung x2)
Оракул (прогноз дохода)|Аракул (прагноз даходу)|Оракул (прогноз доходу)|Oracle (income forecast)|Orakel (Einkommensprognose)
Первый тап|Першы тап|Перший тап|First Tap|Erster Tap
1 тап|||1 tap|1 Tap
Сотня|||Hundred|Hundert
100 тапов|100 тапаў|100 тапів|100 taps|100 Taps
Тысяча||Тисяча|Thousand|Tausend
1 000 тапов|1 000 тапаў|1 000 тапів|1,000 taps|1.000 Taps
Десять тысяч|Дзесяць тысяч|Десять тисяч|Ten Thousand|Zehntausend
10 000 тапов|10 000 тапаў|10 000 тапів|10,000 taps|10.000 Taps
Сто тысяч|Сто тысяч|Сто тисяч|Hundred Thousand|Hunderttausend
100 000 тапов|100 000 тапаў|100 000 тапів|100,000 taps|100.000 Taps
Первая покупка|Першая пакупка|Перша покупка|First Purchase|Erster Kauf
купи улучшение|купі паляпшэнне|купи покращення|buy an upgrade|kaufe ein Upgrade
Полный арсенал|Поўны арсенал|Повний арсенал|Full Arsenal|Volles Arsenal
купи все улучшения|купі ўсе паляпшэнні|купи всі покращення|buy every upgrade|kaufe alle Upgrades
Критик|Крытык||Critic|Kritiker
первый крит|першы крыт|перший крит|first crit|erster Krit
Мастер крита|Майстар крыта|Майстер крита|Crit Master|Krit-Meister
100 критов|100 крытаў|100 критів|100 crits|100 Krits
Комбо x10|Камба x10||Combo x10|Kombo x10
10 подряд|10 запар|10 поспіль|10 in a row|10 am Stück
Комбо x50|Камба x50||Combo x50|Kombo x50
50 подряд|50 запар|50 поспіль|50 in a row|50 am Stück
Охотник|Паляўнічы|Мисливець|Hunter|Jäger
10 метеоритов|10 метэарытаў|10 метеоритів|10 meteors|10 Meteoren
Перерождение|Перараджэнне|Переродження|Rebirth|Wiedergeburt
первый престиж|першы прэстыж|перший престиж|first prestige|erstes Prestige
Миллионер|Мільянер|Мільйонер|Millionaire|Millionär
1M кредитов|1M крэдытаў|1M кредитів|1M credits|1M Credits
Миллиардер|Мільярдэр|Мільярдер|Billionaire|Milliardär
1B кредитов|1B крэдытаў|1B кредитів|1B credits|1B Credits
Триллионер|Трыльянер|Трильйонер|Trillionaire|Billionär
1T кредитов|1T крэдытаў|1T кредитів|1T credits|1T Credits
Звёздный|Зорны|Зоряний|Starry|Sternenhaft
10 звёздной пыли|10 зорнага пылу|10 зоряного пилу|10 stardust|10 Sternenstaub
Легенда|||Legend|Legende
все достижения|усе дасягненні|усі досягнення|all achievements|alle Erfolge"""
