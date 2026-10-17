package com.example.spacetycoon

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "save")

data class GameState(
    val credits: Double = 0.0,
    val total: Double = 0.0,      // всего заработано за всё время
    val run: Double = 0.0,        // заработано в текущем забеге (для престижа)
    val counts: Map<Int, Int> = emptyMap(),
    val taps: Long = 0, val crits: Long = 0, val bestCombo: Int = 0,
    val dust: Int = 0, val prestiges: Int = 0,
    val playSeconds: Long = 0,
    val combo: Int = 0,
    val vibrate: Boolean = true,
    val lastCrit: Int = 0,        // множитель последнего крита (0 — нет)
    val offlineGain: Double = 0.0,
    val lastGain: Double = 0.0,   // последний доход с тапа (для всплывающих цифр)
    val meteors: Long = 0,        // поймано метеоритов
    val boostUntil: Long = 0,     // до какого времени действует x10 от метеорита
    val dailyDay: Long = 0,       // день, когда забрали ежедневный бонус
    val streak: Int = 0,          // серия дней подряд
    val loaded: Boolean = false,  // сохранение уже загружено
    val pet: Int = 0,             // уровень космо-питомца (+5% ко всему за уровень)
    val sound: Boolean = true,    // звуки включены
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val ds = app.dataStore
    private val _s = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _s.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()   // сообщения для баннера сверху
    private var lastTap = 0L
    private val saveMutex = Mutex()

    companion object {
        val CREDITS = doublePreferencesKey("credits"); val TOTAL = doublePreferencesKey("total")
        val RUN = doublePreferencesKey("run"); val COUNTS = stringPreferencesKey("counts")
        val TAPS = longPreferencesKey("taps"); val CRITS = longPreferencesKey("crits")
        val BEST = intPreferencesKey("best"); val DUST = intPreferencesKey("dust")
        val PRES = intPreferencesKey("pres"); val PLAY = longPreferencesKey("play")
        val MET = longPreferencesKey("met"); val DAILY = longPreferencesKey("daily"); val STREAK = intPreferencesKey("streak"); val VIB = booleanPreferencesKey("vib"); val LAST = longPreferencesKey("last"); val BOOST = longPreferencesKey("boost"); val PET = intPreferencesKey("pet"); val SND = booleanPreferencesKey("snd")
        const val MAX_OFFLINE_SEC = 30L   // оффлайн-доход не больше, чем за 30 секунд
    }

    // ---------- Формулы ----------
    fun mult(s: GameState): Double {
        var m = (1.0 + 0.05 * s.dust) * (1.0 + 0.01 * achCount(s)) * (1.0 + 0.05 * s.pet)   // пыль +5%, достижения +1%
        UPGRADES.filter { it.kind == Kind.MULT && (s.counts[it.id] ?: 0) > 0 }.forEach { m *= it.value }
        if (System.currentTimeMillis() < s.boostUntil) m *= 10
        return m
    }
    fun tapPower(s: GameState) =
        (1.0 + UPGRADES.filter { it.kind == Kind.TAP }.sumOf { (s.counts[it.id] ?: 0) * it.value }) * mult(s)
    fun passive(s: GameState) =
        UPGRADES.filter { it.kind == Kind.PASSIVE }.sumOf { (s.counts[it.id] ?: 0) * it.value } * mult(s)
    fun critChance(s: GameState) = 0.05 + 0.05 * (s.counts[18] ?: 0)
    fun comboMult(c: Int) = when { c >= 50 -> 3.0; c >= 25 -> 2.0; c >= 10 -> 1.5; else -> 1.0 }
    fun prestigeGain(s: GameState) = floor(sqrt(s.run / 1_000_000.0)).toInt()

    // ---------- Действия ----------
    fun tap() {
        val now = System.currentTimeMillis()
        _s.update { s ->
            val combo = if (now - lastTap < 2000) s.combo + 1 else 1
            lastTap = now
            val crit = Random.nextDouble() < critChance(s)
            val k = if (crit) Random.nextInt(5, 11) else 0
            val gain = tapPower(s) * comboMult(combo) * (if (crit) k else 1)
            s.copy(credits = s.credits + gain, total = s.total + gain, run = s.run + gain,
                taps = s.taps + 1, crits = s.crits + if (crit) 1 else 0,
                combo = combo, bestCombo = maxOf(s.bestCombo, combo), lastCrit = k, lastGain = gain)
        }
    }

    /** Покупка qty штук (qty < 0 — максимум на все кредиты) */
    fun buy(u: Upgrade, qty: Int): Boolean {
        val s = _s.value
        val n = s.counts[u.id] ?: 0
        val q = if (u.single()) 1 else if (qty < 0) affordable(u, n, s.credits) else qty
        if (q < 1) return false
        val p = bulkPrice(u, n, q)
        if (s.credits < p || (u.single() && n > 0)) return false
        _s.update { it.copy(credits = it.credits - p, counts = it.counts + (u.id to n + q)) }
        return true
    }

    fun dailyAvailable(s: GameState) = s.loaded && s.dailyDay != System.currentTimeMillis() / 86_400_000L

    /** Ежедневный бонус: чем длиннее серия, тем больше награда; каждый 7-й день — звёздная пыль */
    fun claimDaily() {
        val s = _s.value
        val today = System.currentTimeMillis() / 86_400_000L
        if (s.dailyDay == today) return
        val streak = if (s.dailyDay == today - 1) s.streak + 1 else 1
        val g = max(passive(s) * 600, 500.0) * (1 + 0.25 * minOf(streak - 1, 10))
        val dust = if (streak % 7 == 0) 1 else 0
        _s.update { it.copy(credits = it.credits + g, total = it.total + g, run = it.run + g, dust = it.dust + dust, dailyDay = today, streak = streak) }
        _events.tryEmit("Бонус дня: +${fmt(g)}" + (if (dust > 0) " и звёздная пыль" else "") + " · серия $streak")
        save()
    }

    /** Награда за уничтоженные астероиды шторма */
    fun stormReward(hits: Int): String {
        val s = _s.value
        val g = hits * max(max(tapPower(s) * 30, passive(s) * 20), 10.0)
        _s.update { it.copy(credits = it.credits + g, total = it.total + g, run = it.run + g) }
        return "+${fmt(g)}"
    }

    fun prestige() {
        val g = prestigeGain(_s.value)
        if (g < 1) return
        _s.update { it.copy(credits = 0.0, run = 0.0, counts = emptyMap(), dust = it.dust + g, prestiges = it.prestiges + 1) }
        save()
    }

    /** Пауза до следующего золотого метеорита: 30–90 сек, гипер-ускоритель сокращает до 70% */
    fun meteorDelayMs(): Long =
        (Random.nextLong(30_000, 90_001) * 0.9.pow(_s.value.counts[19] ?: 0).coerceAtLeast(0.3)).toLong()

    /** Поймали метеорит: мгновенные кредиты или x10 на 15 сек (магнит удваивает награду) */
    fun catchMeteor(): String {
        val s = _s.value
        val k = if ((s.counts[20] ?: 0) > 0) 2.0 else 1.0
        return if (Random.nextBoolean()) {
            val g = max(passive(s) * 120, tapPower(s) * 100) * k
            _s.update { it.copy(credits = it.credits + g, total = it.total + g, run = it.run + g, meteors = it.meteors + 1) }
            "+${fmt(g)}"
        } else {
            val sec = (15 * k).toInt()
            _s.update { it.copy(meteors = it.meteors + 1, boostUntil = System.currentTimeMillis() + sec * 1000L) }
            "x10 на $sec сек!"
        }
    }

    /** Космо-питомец: каждый уровень даёт +5% ко всему, кормёжка дорожает в 1.9 раза */
    fun petCost(level: Int) = 5_000.0 * 1.9.pow(level)
    fun feedPet(): Boolean {
        val s = _s.value
        val c = petCost(s.pet)
        if (s.credits < c) return false
        _s.update { it.copy(credits = it.credits - c, pet = it.pet + 1) }
        return true
    }
    fun setSound(v: Boolean) = _s.update { it.copy(sound = v) }

    fun setVibrate(v: Boolean) = _s.update { it.copy(vibrate = v) }
    fun dismissOffline() = _s.update { it.copy(offlineGain = 0.0) }
    fun reset() { _s.value = GameState(loaded = true); save() }

    // ---------- Переносимое сохранение (переживает удаление приложения) ----------
    fun exportSave(): String {
        val s = _s.value
        val json = JSONObject()
        json.put("format", "SpaceTycoonSave")
        json.put("version", 1)
        json.put("credits", s.credits); json.put("total", s.total); json.put("run", s.run)
        json.put("counts", JSONObject().apply { s.counts.forEach { (k, v) -> put(k.toString(), v) } })
        json.put("taps", s.taps); json.put("crits", s.crits); json.put("bestCombo", s.bestCombo)
        json.put("dust", s.dust); json.put("prestiges", s.prestiges); json.put("playSeconds", s.playSeconds)
        json.put("vibrate", s.vibrate); json.put("meteors", s.meteors); json.put("boostUntil", s.boostUntil)
        json.put("dailyDay", s.dailyDay); json.put("streak", s.streak); json.put("pet", s.pet); json.put("sound", s.sound)
        json.put("savedAt", System.currentTimeMillis())
        return json.toString(2)
    }

    suspend fun importSave(jsonText: String): Boolean {
        return try {
            val j = JSONObject(jsonText)
            if (j.optString("format") != "SpaceTycoonSave") return false
            val c = j.optJSONObject("counts") ?: JSONObject()
            val counts = mutableMapOf<Int, Int>()
            val keys = c.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = key.toIntOrNull() ?: continue
                val count = c.optInt(key, 0)
                if (id > 0 && count >= 0) counts[id] = count
            }
            val old = _s.value
            val restored = old.copy(
                credits = j.optDouble("credits", 0.0).coerceAtLeast(0.0),
                total = j.optDouble("total", 0.0).coerceAtLeast(0.0),
                run = j.optDouble("run", 0.0).coerceAtLeast(0.0),
                counts = counts,
                taps = j.optLong("taps", 0).coerceAtLeast(0),
                crits = j.optLong("crits", 0).coerceAtLeast(0),
                bestCombo = j.optInt("bestCombo", 0).coerceAtLeast(0),
                dust = j.optInt("dust", 0).coerceAtLeast(0),
                prestiges = j.optInt("prestiges", 0).coerceAtLeast(0),
                playSeconds = j.optLong("playSeconds", 0).coerceAtLeast(0),
                vibrate = j.optBoolean("vibrate", true),
                meteors = j.optLong("meteors", 0).coerceAtLeast(0),
                boostUntil = j.optLong("boostUntil", 0).coerceAtLeast(0),
                dailyDay = j.optLong("dailyDay", 0).coerceAtLeast(0),
                streak = j.optInt("streak", 0).coerceAtLeast(0),
                pet = j.optInt("pet", 0).coerceAtLeast(0), sound = j.optBoolean("sound", true),
                offlineGain = 0.0, loaded = true
            )
            _s.value = restored
            saveMutex.withLock {
                ds.edit {
                    it[CREDITS] = restored.credits; it[TOTAL] = restored.total; it[RUN] = restored.run
                    it[COUNTS] = restored.counts.entries.joinToString(",") { e -> "${e.key}=${e.value}" }
                    it[TAPS] = restored.taps; it[CRITS] = restored.crits; it[BEST] = restored.bestCombo
                    it[DUST] = restored.dust; it[PRES] = restored.prestiges; it[PLAY] = restored.playSeconds
                    it[MET] = restored.meteors; it[DAILY] = restored.dailyDay; it[STREAK] = restored.streak
                    it[VIB] = restored.vibrate; it[BOOST] = restored.boostUntil; it[PET] = restored.pet; it[SND] = restored.sound
                    it[LAST] = System.currentTimeMillis()
                }
            }
            true
        } catch (_: Exception) { false }
    }

    // ---------- Резервная копия вне данных приложения ----------
    // На Android 10+ файл лежит в общей папке Downloads и переживает удаление приложения.
    private fun readExternalBackup(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = getApplication<Application>().contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        resolver.query(
            collection, projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("SpaceTycoon_save.json"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                return resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
        }
        return null
    }

    /** Удаляет только старые дубликаты резервных копий этой игры в Downloads. */
    private fun cleanupExternalBackups() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = getApplication<Application>().contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        resolver.query(
            collection, projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf("SpaceTycoon_save%.json", "${Environment.DIRECTORY_DOWNLOADS}/"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idsToDelete = mutableListOf<Long>()
            val names = mutableListOf<Pair<Long, String>>()
            while (cursor.moveToNext()) {
                names += cursor.getLong(0) to cursor.getString(1)
            }
            // Оставляем канонический файл, а копии вида (1), (2) и т. п. удаляем.
            names.filter { it.second != "SpaceTycoon_save.json" }.forEach { idsToDelete += it.first }
            idsToDelete.forEach { id ->
                runCatching { resolver.delete(Uri.withAppendedPath(collection, id.toString()), null, null) }
            }
        }
    }

    private fun writeExternalBackup(json: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = getApplication<Application>().contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        var uri: Uri? = null
        resolver.query(
            collection, projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("SpaceTycoon_save.json"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                uri = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
            }
        }
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "SpaceTycoon_save.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            uri = resolver.insert(collection, values)
        }
        uri?.let { target ->
            resolver.openOutputStream(target, "wt")?.bufferedWriter()?.use { it.write(json) }
        }
    }

    // ---------- Сохранение / загрузка ----------
    fun save() {
        viewModelScope.launch {
            // Serialize saves and read the latest state when the write actually starts.
            // This prevents a slower, older save from overwriting a newer one.
            saveMutex.withLock {
                val s = _s.value
                ds.edit {
                    it[CREDITS] = s.credits; it[TOTAL] = s.total; it[RUN] = s.run
                    it[COUNTS] = s.counts.entries.joinToString(",") { e -> "${e.key}=${e.value}" }
                    it[TAPS] = s.taps; it[CRITS] = s.crits; it[BEST] = s.bestCombo
                    it[DUST] = s.dust; it[PRES] = s.prestiges; it[PLAY] = s.playSeconds
                    it[MET] = s.meteors; it[DAILY] = s.dailyDay; it[STREAK] = s.streak
                    it[VIB] = s.vibrate; it[BOOST] = s.boostUntil; it[PET] = s.pet; it[SND] = s.sound
                    it[LAST] = System.currentTimeMillis()
                }
                // Копия в Downloads не удаляется вместе с приложением.
                runCatching { writeExternalBackup(exportSave()) }
            }
        }
    }

    init {
        viewModelScope.launch {
            runCatching { cleanupExternalBackups() }
            val p = ds.data.first()
            val counts = (p[COUNTS] ?: "").split(",").filter { it.contains("=") }
                .associate { it.substringBefore("=").toInt() to it.substringAfter("=").toInt() }
            var s = GameState(p[CREDITS] ?: 0.0, p[TOTAL] ?: 0.0, p[RUN] ?: 0.0, counts,
                p[TAPS] ?: 0, p[CRITS] ?: 0, p[BEST] ?: 0, p[DUST] ?: 0, p[PRES] ?: 0,
                p[PLAY] ?: 0, 0, p[VIB] ?: true)
            s = s.copy(meteors = p[MET] ?: 0L, dailyDay = p[DAILY] ?: 0L, streak = p[STREAK] ?: 0, boostUntil = p[BOOST] ?: 0L, pet = p[PET] ?: 0, sound = p[SND] ?: true)
            // Оффлайн-доход (макс. 8 часов)
            val last = p[LAST] ?: 0L
            if (last > 0) {
                val sec = minOf((System.currentTimeMillis() - last) / 1000, MAX_OFFLINE_SEC)
                val gain = passive(s) * sec
                if (gain > 0) s = s.copy(credits = s.credits + gain, total = s.total + gain, run = s.run + gain, offlineGain = gain)
            }
            _s.value = s.copy(loaded = true)
            // После переустановки внутреннее хранилище пустое: поднимаем копию из Downloads.
            if (p[CREDITS] == null && p[TOTAL] == null && p[COUNTS] == null) {
                val backup = runCatching { readExternalBackup() }.getOrNull()
                if (!backup.isNullOrBlank()) importSave(backup)
            }
            // Игровой цикл: 10 тиков в секунду, автосохранение раз в 10 секунд
            var ticks = 0
            while (true) {
                delay(100)
                ticks++
                _s.update { c ->
                    val g = passive(c) / 10
                    val combo = if (c.combo > 0 && System.currentTimeMillis() - lastTap > 2000) 0 else c.combo
                    c.copy(credits = c.credits + g, total = c.total + g, run = c.run + g, combo = combo,
                        playSeconds = c.playSeconds + if (ticks % 10 == 0) 1 else 0)
                }
                if (ticks % 100 == 0) save()
            }
        }
    }
}
