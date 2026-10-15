package com.example.spacetycoon

import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Pink, tertiary = Gold)) { App(vm) }
        }
    }
    override fun onPause() { super.onPause(); vm.save() }
    override fun onStop() { super.onStop(); vm.save() }
}

// ---------- Палитра и стили ----------
val Cyan = Color(0xFF00E5FF); val Pink = Color(0xFFFF4081); val Gold = Color(0xFFFFD700)
private val Title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
private val Card12 = RoundedCornerShape(16.dp)

/** «Стеклянная» карточка с неоновой рамкой */
fun Modifier.glass(border: Color = Color(0x33FFFFFF)) =
    this.clip(Card12).background(Color(0x22FFFFFF)).border(1.dp, border, Card12)

/** Скин планеты зависит от всего заработанного */
fun skin(total: Double) = when {
    total < 1e4 -> listOf(Color(0xFFFFB74D), Color(0xFFD84315), Color(0xFF3E1A0A))
    total < 1e6 -> listOf(Color(0xFF80F0FF), Color(0xFF0288D1), Color(0xFF0A1B4D))
    total < 1e9 -> listOf(Color(0xFFF48FB1), Color(0xFF7B1FA2), Color(0xFF1A0B3D))
    else -> listOf(Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFF5D3A00))
}
fun skinName(total: Double) = when {
    total < 1e4 -> "Ржавый мир"; total < 1e6 -> "Океаническая"
    total < 1e9 -> "Неоновая туманность"; else -> "Золотая планета"
}

@Composable
fun App(vm: GameViewModel) {
    val s by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🪐" to "Игра", "🛒" to "Апгрейды", "📊" to "Статистика", "⚙️" to "Настройки")

    if (s.offlineGain > 0) AlertDialog(
        onDismissRequest = vm::dismissOffline, containerColor = Color(0xFF14103A),
        confirmButton = { TextButton(onClick = vm::dismissOffline) { Text("Забрать", color = Cyan) } },
        title = { Text("Пока вас не было...") },
        text = { Text("Заработано: 💎 ${fmt(s.offlineGain)}", color = Gold, fontSize = 20.sp) })

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A0A5E), Color(0xFF0D1B4D), Color(0xFF020208))))) {
        Starfield()
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Color(0xAA050212)) {
                tabs.forEachIndexed { i, (e, n) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(e, fontSize = 20.sp) }, label = { Text(n, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Cyan.copy(alpha = 0.25f), selectedTextColor = Cyan))
                }
            }
        }) { pad ->
            Crossfade(tab, Modifier.padding(pad).fillMaxSize(), label = "tab") {
                when (it) { 0 -> MainScreen(vm, s); 1 -> UpgradesScreen(vm, s); 2 -> StatsScreen(s); else -> SettingsScreen(vm, s) }
            }
        }
    }
}

/** Мерцающие звёзды на фоне */
@Composable
fun Starfield() {
    val stars = remember { List(90) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    val t by rememberInfiniteTransition(label = "stars").animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (x, y, p) ->
            val a = 0.25f + 0.75f * abs(sin((t + p) * 2 * PI)).toFloat()
            drawCircle(Color.White.copy(alpha = a), (0.6f + p * 1.8f) * density, Offset(x * size.width, y * size.height))
        }
    }
}

/** Эффект тапа: частицы + всплывающая цифра */
class Fx(val born: Long, val x: Float, val y: Float, val text: String, val crit: Boolean, val angles: List<Float>)

@Composable
fun PlanetView(vm: GameViewModel, s: GameState, modifier: Modifier = Modifier.size(300.dp)) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val fx = remember { mutableStateListOf<Fx>() }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { now = it / 1_000_000 }; fx.removeAll { f -> now - f.born > 900 } } }
    val pulse = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "a")
    val colors = skin(s.total)

    Box(modifier.offset { IntOffset(shake.value.roundToInt(), 0) }.pointerInput(Unit) {
        detectTapGestures(onPress = { p ->
            val c = Offset(size.width / 2f, size.height / 2f)
            if ((p - c).getDistance() <= size.width * 0.35f) {
                vm.tap()
                val st = vm.state.value
                if (st.vibrate) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val txt = (if (st.lastCrit > 0) "КРИТ! x${st.lastCrit}\n" else "") + "+" + fmt(st.lastGain)
                fx.add(Fx(now, p.x, p.y, txt, st.lastCrit > 0, List(10) { Random.nextFloat() * 6.283f }))
                scope.launch { pulse.snapTo(0.9f); pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
                if (st.lastCrit > 0) scope.launch {
                    repeat(6) { shake.animateTo(if (it % 2 == 0) 14f else -14f, tween(35)) }
                    shake.animateTo(0f, tween(35))
                }
            }
        })
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val r = size.width * 0.35f * pulse.value
            drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha = 0.55f), Color.Transparent), c, r * 1.6f), r * 1.6f, c) // свечение
            drawCircle(Brush.radialGradient(listOf(colors[0], colors[1], colors[2]), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.5f), r, c)
            rotate(-20f, c) {
                drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - r * 1.35f, c.y - r * 0.35f), Size(r * 2.7f, r * 0.7f), style = Stroke(3.dp.toPx()))
                val a = Math.toRadians(spin.toDouble()).toFloat()
                drawCircle(Gold, 5.dp.toPx(), Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 0.35f)) // челнок на орбите
            }
            fx.forEach { f ->
                val age = ((now - f.born) / 900f).coerceIn(0f, 1f)
                f.angles.forEach { ang ->
                    val d = age * (if (f.crit) 140f else 80f) * density
                    drawCircle((if (f.crit) Gold else Cyan).copy(alpha = 1f - age), (4f * (1f - age) + 1f) * density,
                        Offset(f.x + cos(ang) * d, f.y + sin(ang) * d))
                }
            }
        }
        fx.forEach { f ->
            val age = ((now - f.born) / 900f).coerceIn(0f, 1f)
            Text(f.text, Modifier.offset { IntOffset(f.x.roundToInt() - 60, (f.y - age * 160f * density).roundToInt() - 40) }.alpha(1f - age),
                color = if (f.crit) Gold else Color.White, textAlign = TextAlign.Center,
                style = Title.copy(fontSize = if (f.crit) 26.sp else 22.sp, shadow = Shadow(Cyan, blurRadius = 16f)))
        }
    }
}

@Composable
fun Chip(text: String, color: Color) = Box(
    Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
        .padding(horizontal = 12.dp, vertical = 6.dp)) { Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

@Composable
fun Hud(vm: GameViewModel, s: GameState) = Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("💎 ${fmt(s.credits)}", style = Title.copy(fontSize = 42.sp, color = Cyan, shadow = Shadow(Cyan, blurRadius = 28f)))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("⚡ +${fmt(vm.passive(s))}/сек", Cyan); Chip("👆 +${fmt(vm.tapPower(s))}", Pink)
        if (s.dust > 0) Chip("⭐ ${s.dust}", Gold)
    }
}

@Composable
fun ComboBar(vm: GameViewModel, s: GameState) {
    val p by animateFloatAsState((s.combo / 50f).coerceIn(0f, 1f), label = "combo")
    val col = when { s.combo >= 50 -> Gold; s.combo >= 25 -> Pink; else -> Cyan }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (s.combo > 0) "КОМБО x${vm.comboMult(s.combo)} · ${s.combo}" else "Тапай быстрее — копи комбо", color = col, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.width(240.dp).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Brush.horizontalGradient(listOf(Cyan, col))))
        }
    }
}

@Composable
fun PrestigeButton(vm: GameViewModel, s: GameState) {
    val gain = vm.prestigeGain(s)
    Button(onClick = vm::prestige, enabled = gain >= 1, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black,
            disabledContainerColor = Color(0x22FFFFFF), disabledContentColor = Color.Gray)) {
        Text(if (gain >= 1) "⭐ Престиж: +$gain пыли" else "⭐ Престиж с 1M за забег", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MainScreen(vm: GameViewModel, s: GameState) {
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (land) Row(Modifier.fillMaxSize().padding(16.dp), Arrangement.spacedBy(24.dp), Alignment.CenterVertically) {
        PlanetView(vm, s, Modifier.size(260.dp))
        Column(Modifier.weight(1f), Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
            Hud(vm, s); ComboBar(vm, s); PrestigeButton(vm, s)
        }
    } else Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
        Hud(vm, s)
        PlanetView(vm, s)
        Text(skinName(s.total), color = Color(0x99FFFFFF), fontSize = 12.sp, letterSpacing = 2.sp)
        ComboBar(vm, s); PrestigeButton(vm, s)
    }
}

@Composable
fun UpgradesScreen(vm: GameViewModel, s: GameState) {
    val ctx = LocalContext.current
    val sections = listOf("👆 ЗА ТАП" to Kind.TAP, "⚡ ПАССИВНЫЙ ДОХОД" to Kind.PASSIVE, "✨ МНОЖИТЕЛИ" to Kind.MULT, "🎯 УТИЛИТЫ" to Kind.CRIT)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("💎 ${fmt(s.credits)}", style = Title.copy(fontSize = 26.sp, color = Cyan, shadow = Shadow(Cyan, blurRadius = 20f))) }
        sections.forEach { (title, kind) ->
            item { Text(title, color = Color(0xAAFFFFFF), fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(UPGRADES.filter { it.kind == kind }) { u ->
                val n = s.counts[u.id] ?: 0
                val p = price(u, n)
                val owned = u.kind == Kind.MULT && n > 0
                val accent = if (u.kind == Kind.MULT) Gold else if (u.kind == Kind.TAP) Pink else Cyan
                val prog by animateFloatAsState((s.credits / p).toFloat().coerceIn(0f, 1f), label = "p")
                val desc = when (u.kind) {
                    Kind.TAP -> "+${fmt(u.value)} за тап"; Kind.PASSIVE -> "+${fmt(u.value)}/сек"
                    Kind.MULT -> "×${u.value} ко всему"; Kind.CRIT -> "+5% к шансу крита"
                }
                Row(Modifier.fillMaxWidth().glass(if (n > 0) accent.copy(alpha = 0.8f) else Color(0x33FFFFFF)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.4f), Color(0x22000000)))),
                        contentAlignment = Alignment.Center) { Text(u.emoji, fontSize = 28.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(u.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("$desc · ×$n", fontSize = 12.sp, color = Color(0xAAFFFFFF))
                        LinearProgressIndicator(progress = { prog }, color = if (prog >= 1f) accent else Color(0x66FFFFFF),
                            trackColor = Color(0x22FFFFFF), modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)))
                    }
                    Button(enabled = !owned && s.credits >= p, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        onClick = { if (vm.buy(u)) Toast.makeText(ctx, "Куплено: ${u.name}", Toast.LENGTH_SHORT).show() }) {
                        Text(if (owned) "✔" else fmt(p), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(s: GameState) {
    val rows = listOf(
        Triple("👆", "Тапов", s.taps.toString()), Triple("💎", "Заработано", fmt(s.total)),
        Triple("💥", "Критов", s.crits.toString()), Triple("🔥", "Лучшее комбо", s.bestCombo.toString()),
        Triple("⭐", "Звёздная пыль", s.dust.toString()), Triple("🔄", "Престижей", s.prestiges.toString()),
        Triple("⏱️", "Время в игре", "${s.playSeconds / 60} мин"))
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("📊 Статистика", style = Title.copy(fontSize = 26.sp))
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (e, k, v) ->
                    Column(Modifier.weight(1f).glass(Cyan.copy(alpha = 0.3f)).padding(14.dp)) {
                        Text("$e $k", fontSize = 12.sp, color = Color(0xAAFFFFFF))
                        Text(v, style = Title.copy(fontSize = 22.sp, color = Cyan))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel, s: GameState) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("⚙️ Настройки", style = Title.copy(fontSize = 26.sp))
        Row(Modifier.fillMaxWidth().glass().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("📳 Вибрация"); Switch(checked = s.vibrate, onCheckedChange = vm::setVibrate)
        }
        Button(onClick = { confirm = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))) { Text("🗑️ Сбросить прогресс") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color(0xFF14103A),
        title = { Text("Сбросить всё?") }, text = { Text("Прогресс будет удалён безвозвратно.") },
        confirmButton = { TextButton(onClick = { vm.reset(); confirm = false }) { Text("Да", color = Pink) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
}
