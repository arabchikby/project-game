package com.example.spacetycoon

import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Cyan, secondary = Pink, tertiary = Gold,
                background = Color(0xFF050914), surface = Color(0xFF0D1728),
                onBackground = Color(0xFFF2F7FF), onSurface = Color(0xFFF2F7FF)
            )) { App(vm) }
        }
    }
    override fun onPause() { super.onPause(); vm.save() }
    override fun onStop() { super.onStop(); vm.save() }
}

// ---------- Палитра и стили ----------
val Cyan = Color(0xFF50F6FF); val Pink = Color(0xFFFF4D9D); val Gold = Color(0xFFFFD166)
private val SpaceBg = Color(0xFF050914)
private val Panel = Color(0xFF0D1728)
private val Muted = Color(0xFF91A4C2)
private val Title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
private val Card12 = RoundedCornerShape(16.dp)

/** «Стеклянная» карточка с неоновой рамкой */
fun Modifier.glass(border: Color = Color(0x33FFFFFF)) =
    this.clip(Card12).background(Color(0x22FFFFFF)).border(1.dp, border, Card12)

/** Иконка для каждого улучшения (единый стиль вместо эмодзи) */
fun iconFor(id: Int): ImageVector = when (id) {
    1 -> Icons.Filled.Construction; 2 -> Icons.Filled.Hardware; 3 -> Icons.Filled.FlashOn
    4 -> Icons.Filled.ContentCut; 5 -> Icons.Filled.AutoAwesome; 6 -> Icons.Filled.RocketLaunch
    7 -> Icons.Filled.Memory; 8 -> Icons.Filled.SatelliteAlt; 9 -> Icons.Filled.Factory
    10 -> Icons.Filled.Public; 11 -> Icons.Filled.Language; 12 -> Icons.Filled.TrackChanges
    13 -> Icons.Filled.WbSunny; 14 -> Icons.Filled.Share; 15 -> Icons.Filled.Diamond
    16 -> Icons.Filled.WorkspacePremium; 17 -> Icons.Filled.EmojiEvents; 18 -> Icons.Filled.Casino
    19 -> Icons.Filled.Speed; 20 -> Icons.Filled.GpsFixed; else -> Icons.Filled.Visibility
}
/** Иконки достижений в порядке списка ACHS */
val ACH_ICONS = listOf(Icons.Filled.TouchApp, Icons.Filled.PanTool, Icons.Filled.Bolt, Icons.Filled.Whatshot,
    Icons.Filled.Stars, Icons.Filled.ShoppingCart, Icons.Filled.Inventory2, Icons.Filled.FlashOn, Icons.Filled.GpsFixed,
    Icons.Filled.Cached, Icons.Filled.Speed, Icons.Filled.TrackChanges, Icons.Filled.AutoAwesome, Icons.Filled.Savings,
    Icons.Filled.AccountBalance, Icons.Filled.Diamond, Icons.Filled.Star, Icons.Filled.EmojiEvents)

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
    var seen by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    val ach = achCount(s)
    LaunchedEffect(Unit) { delay(1500); ready = true }
    LaunchedEffect(Unit) { vm.events.collect { banner = it; delay(2500); banner = null } }
    LaunchedEffect(ach) {
        if (ready && ach > seen) { banner = "Достижение получено! +1% к доходу"; delay(2500); banner = null }
        seen = ach
    }
    val tabs = listOf(Icons.Filled.Public to "Игра", Icons.Filled.ShoppingCart to "Апгрейды", Icons.Filled.EmojiEvents to "Награды", Icons.Filled.BarChart to "Статистика", Icons.Filled.Settings to "Настройки")

    if (s.offlineGain > 0) AlertDialog(
        onDismissRequest = vm::dismissOffline, containerColor = Color(0xFF14103A),
        confirmButton = { TextButton(onClick = vm::dismissOffline) { Text("Забрать", color = Cyan) } },
        title = { Text("Пока вас не было...") },
        text = { Text("Заработано: ${fmt(s.offlineGain)}", color = Gold, fontSize = 20.sp) })

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101D38), SpaceBg, Color(0xFF03050B))))) {
        Starfield()
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Color(0xF20A1020), tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, (e, n) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(e, null) }, label = { Text(n, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Cyan.copy(alpha = 0.18f), selectedTextColor = Cyan, unselectedTextColor = Muted))
                }
            }
        }) { pad ->
            Crossfade(tab, Modifier.padding(pad).fillMaxSize(), label = "tab") {
                when (it) { 0 -> MainScreen(vm, s); 1 -> UpgradesScreen(vm, s); 2 -> AchievementsScreen(s); 3 -> StatsScreen(s); else -> SettingsScreen(vm, s) }
            }
        }
        AnimatedVisibility(banner != null, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
            Text(banner ?: "", Modifier.glass(Gold).background(Color(0xCC14103A)).padding(horizontal = 16.dp, vertical = 10.dp),
                color = Gold, fontWeight = FontWeight.Bold)
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
            val ringTL = Offset(c.x - r * 1.35f, c.y - r * 0.35f); val ringSz = Size(r * 2.7f, r * 0.7f)
            val sa = Math.toRadians(spin.toDouble()).toFloat()
            rotate(-20f, c) {   // задняя половина кольца и челнок за планетой
                drawArc(Color.White.copy(alpha = 0.25f), 180f, 180f, false, ringTL, ringSz, style = Stroke(3.dp.toPx()))
                if (sin(sa) < 0) drawCircle(Gold.copy(alpha = 0.6f), 4.dp.toPx(), Offset(c.x + cos(sa) * r * 1.35f, c.y + sin(sa) * r * 0.35f))
            }
            drawCircle(Brush.radialGradient(listOf(colors[0], colors[1], colors[2]), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.5f), r, c)
            // Кратеры
            listOf(Triple(-0.35f, -0.1f, 0.2f), Triple(0.25f, 0.3f, 0.15f), Triple(0.3f, -0.4f, 0.1f), Triple(-0.1f, 0.5f, 0.12f))
                .forEach { (dx, dy, k) -> drawCircle(colors[2].copy(alpha = 0.35f), r * k, Offset(c.x + dx * r, c.y + dy * r)) }
            rotate(-20f, c) {   // передняя половина кольца и челнок перед планетой
                drawArc(Color.White.copy(alpha = 0.55f), 0f, 180f, false, ringTL, ringSz, style = Stroke(3.5f.dp.toPx()))
                if (sin(sa) >= 0) drawCircle(Gold, 5.dp.toPx(), Offset(c.x + cos(sa) * r * 1.35f, c.y + sin(sa) * r * 0.35f))
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
fun Balance(v: String, size: TextUnit = 42.sp) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(Icons.Filled.Diamond, null, tint = Cyan, modifier = Modifier.size((size.value * 0.9f).dp))
    Text(v, style = Title.copy(fontSize = size, color = Cyan, shadow = Shadow(Cyan, blurRadius = 28f)))
}

@Composable
fun Chip(icon: ImageVector, text: String, color: Color, onClick: (() -> Unit)? = null) = Box(
    Modifier.clip(RoundedCornerShape(50)).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
        .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Hud(vm: GameViewModel, s: GameState) = Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Balance(fmt(s.credits))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 3) {
        if (vm.dailyAvailable(s)) Chip(Icons.Filled.CardGiftcard, "Бонус дня", Gold) { vm.claimDaily() }
        Chip(Icons.Filled.Bolt, "+${fmt(vm.passive(s))}/сек", Cyan); Chip(Icons.Filled.TouchApp, "+${fmt(vm.tapPower(s))}", Pink)
        if (s.dust > 0) Chip(Icons.Filled.Star, "${s.dust}", Gold)
        val left = (s.boostUntil - System.currentTimeMillis()) / 1000
        if (left > 0) Chip(Icons.Filled.Whatshot, "x10 · ${left}с", Gold)
        if ((s.counts[21] ?: 0) > 0) Chip(Icons.Filled.Visibility, "10 мин: +${fmt(vm.passive(s) * 600)}", Pink)
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
        Text(if (gain >= 1) "Престиж: +$gain пыли" else "Престиж с 1M за забег", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MainScreen(vm: GameViewModel, s: GameState) = Box(Modifier.fillMaxSize()) {
    MainContent(vm, s)
    MeteorLayer(vm)
    StormLayer(vm)
}

/** Золотой метеорит: пролетает раз в 30–90 сек, тап даёт бонус */
@Composable
fun MeteorLayer(vm: GameViewModel) {
    val prog = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    var yFrac by remember { mutableFloatStateOf(0.2f) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(vm.meteorDelayMs())
            yFrac = 0.12f + Random.nextFloat() * 0.5f
            prog.snapTo(0f); visible = true
            prog.animateTo(1f, tween(5000, easing = LinearEasing))
            visible = false
        }
    }
    LaunchedEffect(msg) { if (msg != null) { delay(2200); msg = null } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        if (visible) Box(
            Modifier.offset { IntOffset((prog.value * (w + 200f) - 100f).roundToInt(), (yFrac * h + sin(prog.value * 9f) * 30f).roundToInt()) }
                .size(64.dp).background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.7f), Color.Transparent)), CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    msg = "Метеорит! " + vm.catchMeteor(); visible = false
                }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = center
                        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Gold), 0f, c.x), Offset(0f, c.y), c, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(Brush.radialGradient(listOf(Color.White, Gold), c, 14.dp.toPx()), 12.dp.toPx(), c)
                    }
                }
        msg?.let {
            Text(it, Modifier.align(Alignment.TopCenter).padding(top = 24.dp), color = Gold,
                style = Title.copy(fontSize = 18.sp, shadow = Shadow(Gold, blurRadius = 20f)))
        }
    }
}

@Composable
fun AchievementsScreen(s: GameState) {
    val done = achCount(s)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Достижения", style = Title.copy(fontSize = 26.sp))
        Text("$done / ${ACHS.size} · +$done% к доходу", color = Gold, fontWeight = FontWeight.Bold)
        LazyVerticalGrid(GridCells.Adaptive(104.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ACHS) { a ->
                val ok = a.check(s)
                Column(Modifier.glass(if (ok) Gold.copy(alpha = 0.8f) else Color(0x22FFFFFF)).padding(10.dp).alpha(if (ok) 1f else 0.4f),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ACH_ICONS[ACHS.indexOf(a)], null, tint = if (ok) Gold else Color.White, modifier = Modifier.size(32.dp))
                    Text(a.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(a.desc, fontSize = 10.sp, color = Color(0xAAFFFFFF), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
@Composable
fun GameHeader(compact: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(if (compact) 36.dp else 42.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Cyan.copy(alpha = 0.28f), Color(0xFF3541A5).copy(alpha = 0.25f)))),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.RocketLaunch, null, tint = Cyan, modifier = Modifier.size(if (compact) 21.dp else 25.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("SPACE TYCOON", style = Title.copy(fontSize = if (compact) 15.sp else 18.sp, color = Color.White, letterSpacing = 2.sp))
                Text("EXPAND YOUR UNIVERSE", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Cyan.copy(alpha = 0.12f))
                .border(1.dp, Cyan.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("ALPHA 3.0", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Text("BUILD 003", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun MainContent(vm: GameViewModel, s: GameState) {
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (land) Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), Arrangement.spacedBy(22.dp), Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameHeader(compact = true)
            PlanetView(vm, s, Modifier.size(245.dp))
            Text(skinName(s.total).uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        }
        Column(Modifier.weight(1f), Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
            Hud(vm, s); ComboBar(vm, s); PrestigeButton(vm, s)
        }
    } else Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
        GameHeader()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xAA14243D), Color(0x33202B48))))
            .border(1.dp, Color(0x334FEFFF), RoundedCornerShape(24.dp)).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Hud(vm, s)
        }
        PlanetView(vm, s, Modifier.size(270.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(skinName(s.total).uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            ComboBar(vm, s)
            PrestigeButton(vm, s)
        }
    }
}

@Composable
fun UpgradesScreen(vm: GameViewModel, s: GameState) {
    var qty by remember { mutableIntStateOf(1) }
    val sections = listOf("ЗА ТАП" to Kind.TAP, "ПАССИВНЫЙ ДОХОД" to Kind.PASSIVE, "МНОЖИТЕЛИ" to Kind.MULT, "УТИЛИТЫ" to Kind.CRIT)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { GameHeader(compact = true) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF142943), Color(0xFF171C3A))))
                .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("КАЗНА КОЛОНИИ", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Balance(fmt(s.credits), 30.sp)
                Text("Покупай технологии. Расширяй империю.", color = Color(0xFFB5C6DF), fontSize = 12.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 25, -1).forEach { v ->
                    val sel = qty == v
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) Cyan else Color(0x22FFFFFF)).clickable { qty = v }
                        .padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(if (v < 0) "MAX" else "x$v", color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        sections.forEach { (title, kind) ->
            item { Text(title, color = Color(0xAAFFFFFF), fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(UPGRADES.filter { it.kind == kind || (kind == Kind.CRIT && it.kind == Kind.UTIL) }) { u ->
                val n = s.counts[u.id] ?: 0
                val q = if (u.single()) 1 else if (qty < 0) maxOf(affordable(u, n, s.credits), 1) else qty
                val p = bulkPrice(u, n, q)
                val owned = u.single() && n > 0
                val accent = if (u.kind == Kind.MULT) Gold else if (u.kind == Kind.TAP) Pink else Cyan
                val prog by animateFloatAsState((s.credits / p).toFloat().coerceIn(0f, 1f), label = "p")
                val desc = when (u.kind) {
                    Kind.TAP -> "+${fmt(u.value)} за тап"; Kind.PASSIVE -> "+${fmt(u.value)}/сек"
                    Kind.MULT -> "×${u.value} ко всему"; Kind.CRIT -> "+5% к шансу крита"
                    Kind.UTIL -> when (u.id) { 19 -> "метеориты чаще"; 20 -> "награда метеоритов x2"; else -> "прогноз дохода" }
                }
                Row(Modifier.fillMaxWidth().glass(if (n > 0) accent.copy(alpha = 0.8f) else Color(0x33FFFFFF)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.4f), Color(0x22000000)))),
                        contentAlignment = Alignment.Center) { Icon(iconFor(u.id), null, tint = accent, modifier = Modifier.size(30.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(u.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("$desc · ×$n", fontSize = 12.sp, color = Color(0xAAFFFFFF))
                        LinearProgressIndicator(progress = { prog }, color = if (prog >= 1f) accent else Color(0x66FFFFFF),
                            trackColor = Color(0x22FFFFFF), modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)))
                    }
                    Button(enabled = !owned && s.credits >= p, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        onClick = { vm.buy(u, qty) }) {
                        Text(if (owned) "Есть" else (if (q > 1) "×$q · " else "") + fmt(p), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(s: GameState) {
    val rows = listOf(
        Triple(Icons.Filled.TouchApp, "Тапов", s.taps.toString()), Triple(Icons.Filled.Diamond, "Заработано", fmt(s.total)),
        Triple(Icons.Filled.FlashOn, "Критов", s.crits.toString()), Triple(Icons.Filled.Whatshot, "Лучшее комбо", s.bestCombo.toString()),
        Triple(Icons.Filled.Star, "Звёздная пыль", s.dust.toString()), Triple(Icons.Filled.Cached, "Престижей", s.prestiges.toString()),
        Triple(Icons.Filled.Timer, "Время в игре", "${s.playSeconds / 60} мин"), Triple(Icons.Filled.TrackChanges, "Метеоритов", s.meteors.toString()))
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Статистика", style = Title.copy(fontSize = 26.sp))
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (ic, k, v) ->
                    Column(Modifier.weight(1f).glass(Cyan.copy(alpha = 0.3f)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(ic, null, tint = Cyan, modifier = Modifier.size(16.dp))
                            Text(k, fontSize = 12.sp, color = Color(0xAAFFFFFF))
                        }
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
        GameHeader(compact = true)
        Text("Настройки", style = Title.copy(fontSize = 26.sp))
        Row(Modifier.fillMaxWidth().glass().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Вибрация"); Switch(checked = s.vibrate, onCheckedChange = vm::setVibrate)
        }
        Row(Modifier.fillMaxWidth().glass(Cyan.copy(alpha = 0.25f)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SPACE TYCOON", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Версия alpha 3.0 · тестовая сборка", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Filled.RocketLaunch, null, tint = Cyan, modifier = Modifier.size(24.dp))
        }
        Button(onClick = { confirm = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))) { Text("Сбросить прогресс") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color(0xFF14103A),
        title = { Text("Сбросить всё?") }, text = { Text("Прогресс будет удалён безвозвратно.") },
        confirmButton = { TextButton(onClick = { vm.reset(); confirm = false }) { Text("Да", color = Pink) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
}

class Rock(val id: Int, val x: Float, val born: Long)

/** Астероидный шторм: раз в 5 минут предлагает 10 секунд тапать по падающим астероидам */
@Composable
fun StormLayer(vm: GameViewModel) {
    var phase by remember { mutableIntStateOf(0) }   // 0 — ждём, 1 — предложение, 2 — игра
    val rocks = remember { mutableStateListOf<Rock>() }
    var now by remember { mutableLongStateOf(0L) }
    var hits by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(10) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(60_000)
        while (true) {
            phase = 1; delay(15_000)
            if (phase == 1) phase = 0
            delay(300_000)
        }
    }
    LaunchedEffect(phase) {
        if (phase == 2) {
            hits = 0; rocks.clear()
            for (i in 0 until 28) {
                rocks.add(Rock(i, Random.nextFloat(), System.currentTimeMillis()))
                left = 10 - i * 350 / 1000
                delay(350)
            }
            delay(2000)
            msg = "Шторм окончен! Уничтожено: $hits · " + vm.stormReward(hits)
            rocks.clear(); phase = 0
        }
    }
    LaunchedEffect(phase) { while (phase == 2) withFrameNanos { now = System.currentTimeMillis() } }
    LaunchedEffect(msg) { if (msg != null) { delay(3000); msg = null } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        if (phase == 1) Button(onClick = { phase = 2 }, Modifier.align(Alignment.TopCenter).padding(top = 150.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text("Астероидный шторм! Нажми", fontWeight = FontWeight.Bold) }
        if (phase == 2) {
            Text("Шторм: ${left}с · уничтожено $hits", Modifier.align(Alignment.TopCenter).padding(top = 150.dp),
                color = Pink, style = Title.copy(fontSize = 18.sp, shadow = Shadow(Pink, blurRadius = 20f)))
            rocks.forEach { r ->
                key(r.id) {
                    val p = (now - r.born) / 2200f
                    if (p in 0f..1f) Box(Modifier.offset { IntOffset((r.x * (w - 160f)).roundToInt(), (p * (h - 100f)).roundToInt()) }
                        .size(56.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { rocks.remove(r); hits++ }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val rad = size.minDimension / 2.2f
                            drawCircle(Brush.radialGradient(listOf(Color(0xFFBCAAA4), Color(0xFF4E342E)), center, rad), rad, center)
                            drawCircle(Color(0x55000000), rad * 0.25f, Offset(center.x - rad * 0.3f, center.y - rad * 0.2f))
                            drawCircle(Color(0x55000000), rad * 0.18f, Offset(center.x + rad * 0.35f, center.y + rad * 0.3f))
                        }
                    }
                }
            }
        }
        msg?.let {
            Text(it, Modifier.align(Alignment.TopCenter).padding(top = 150.dp), color = Pink, textAlign = TextAlign.Center,
                style = Title.copy(fontSize = 16.sp, shadow = Shadow(Pink, blurRadius = 20f)))
        }
    }
}
