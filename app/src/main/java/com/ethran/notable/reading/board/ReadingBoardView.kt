package com.ethran.notable.reading.board

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.ethran.notable.reading.RBook
import com.ethran.notable.reading.ReadingGoal
import com.ethran.notable.reading.ReadingInsights
import com.ethran.notable.reading.ReadingSearch
import com.ethran.notable.reading.ReadingText
import com.ethran.notable.reading.ReadingUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private enum class Sub(val title: String) { Library("Library"), Authors("Authors"), Series("Series"), OnDevice("On device"), Collections("Collections"), Finished("Finished"), InProgress("Also reading") }

/** The device's own state the header and some boxes show: Wi-Fi, battery, clock and storage. */
private class Device(val online: Boolean, val battery: Int, val clock: String, val weekday: String, val dayMonth: String, val dateLong: String, val storage: Storage?)

@Composable
private fun rememberDevice(refreshKey: Any): Device {
    val ctx = LocalContext.current
    val tick by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    return remember(tick, refreshKey) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val now = LocalDateTime.now()
        val storage = runCatching {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            Storage(st.availableBytes / 1e9, st.totalBytes / 1e9)
        }.getOrNull()
        Device(
            online, battery, now.format(DateTimeFormatter.ofPattern("HH:mm")), now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            "${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
            "${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}", storage
        )
    }
}

/** The colour family of the lead book's cover, worked out once per cover from a 24 x 36 thumbnail. */
@Composable
private fun rememberLeadFamily(book: RBook?): Family? {
    val family by produceState<Family?>(null, book?.cover) {
        val file = book?.cover
        value = if (file == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= 24 && o.outHeight / (sample * 2) >= 36) sample *= 2
                val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@runCatching null
                val small = android.graphics.Bitmap.createScaledBitmap(bmp, 24, 36, true)
                val px = IntArray(24 * 36)
                small.getPixels(px, 0, 24, 0, 0, 24, 36)
                BoardColour.familyFromPixels(IntArray(px.size) { px[it] and 0xFFFFFF })
            }.getOrNull()
        }
    }
    return family
}

/**
 * The Reading board, a port of the KOReader board: a header (greeting, date, device status, search and menu), a page of
 * boxes laid on a grid (Home, Insights, Library, Decor) and page dots at the bottom. Tap a book to open it in NeoReader,
 * hold it for its details. Everything is sized in the plugin's own dp (see [BoardMetrics]).
 */
@Composable
fun ReadingBoardView(ui: ReadingUi, onOpen: (RBook) -> Unit, modifier: Modifier = Modifier, onSetGoal: ((Int) -> Unit)? = null, onRefresh: () -> Unit = {}) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var goalOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf<Long?>(null) }
    val data = ui.data
    val today = remember(data) { ReadingInsights.today(data) }
    val bd = remember(data, today) { BoardData(data, today) }
    val device = rememberDevice(data)
    val lead = rememberLeadFamily(data.continueReading.firstOrNull())
    val dm = context.resources.displayMetrics
    val scale = remember(dm.widthPixels, dm.heightPixels, dm.densityDpi) { BoardMetrics.scale(dm.widthPixels, dm.heightPixels, dm.densityDpi.toFloat()) }
    val bx = remember(scale, density.density, density.fontScale) { Bx(scale, density.density, density.fontScale) }
    val detail = detailId?.let { id -> data.books.firstOrNull { it.id == id } }
    LaunchedEffect(data.now) { if (data.now > 0L) lastSync = data.now }

    val ctx = BoardCtx(
        d = bd, clock = device.clock, weekday = device.weekday, dayMonth = device.dayMonth, online = device.online, storage = device.storage,
        lastSyncMs = lastSync, nowMs = System.currentTimeMillis(), lead = lead, onOpen = onOpen, onInfo = { detailId = it.id },
        onLink = { kind ->
            when (kind) {
                "library" -> sub = Sub.Library; "authors" -> sub = Sub.Authors; "series" -> sub = Sub.Series; "device" -> sub = Sub.OnDevice
                "collections" -> sub = Sub.Collections; "scopes" -> sub = Sub.Finished; "search" -> searching = true
            }
        },
        onSync = onRefresh,
        onToggle = { which ->
            when (which) {
                "wifi" -> startSettings(context, Settings.ACTION_WIFI_SETTINGS)
                "light", "night" -> startSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
                "sync" -> onRefresh()
            }
        },
        onShuffle = onRefresh
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val widthDp = scale.layoutWidth(widthPx).toInt()
        val landscape = widthPx > heightPx
        val g = BoardGrid.geometryPx({ bx.px(it) }, widthPx, heightPx, widthDp)
        val pages = remember(g.cols, landscape) { BoardStore.starterPages(g.cols, hasLight = false, landscape = landscape) }
        val current = page.coerceIn(0, pages.lastIndex)
        CompositionLocalProvider(LocalBx provides bx) {
            Column(Modifier.size(bx.d(widthPx), bx.d(heightPx)).background(Ink.white).padding(top = bx.d(g.top), bottom = bx.d(g.bottom), start = bx.d(g.margin), end = bx.d(g.margin))) {
                BoardHeader(g.width, device, ctx, onSearch = { searching = true }, onMenu = { goalOpen = true })
                VGap(g.headerGap)
                when {
                    detail != null -> Scrolling(g, heightPx) {
                        BookDetailScreen(detail, data.highlights.filter { it.book?.id == detail.id }, ctx) { detailId = null }
                    }
                    goalOpen -> Scrolling(g, heightPx) { GoalScreen(data.goalBooks, bd.finishedYear, today.year, onSetGoal) { goalOpen = false } }
                    searching -> Scrolling(g, heightPx) { SearchScreen(query, { query = it }, data.books, ctx) { searching = false; query = "" } }
                    sub != null -> Scrolling(g, heightPx) { SubList(sub!!, bd, ctx) { sub = null } }
                    !ui.sourceAvailable && ui.loaded -> BText(
                        "NeoReader's library could not be read, so there is nothing to show yet.", 14.0, italic = true, serif = true, color = Ink.g4, lines = 3, w = g.width
                    )
                    else -> {
                        val tiles = pages[current].tiles
                        val packed = remember(g, tiles) { BoardGrid.fitPage(g, tiles.mapIndexed { i, t -> BoardGrid.Item("$i", t.cw, t.rh) }) }
                        Box(
                            Modifier.size(bx.d(g.width), bx.d(packed.gridHeight)).pointerInput(current, pages.size) {
                                var total = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragEnd = {
                                        if (total < -bx.px(80)) page = min(pages.lastIndex, current + 1) else if (total > bx.px(80)) page = max(0, current - 1)
                                    }
                                ) { _, dx -> total += dx }
                            }
                        ) {
                            for (p in packed.placed) {
                                val tile = tiles[p.item.id.toInt()]
                                val tw = BoardGrid.spanWidth(g, p.x, p.cw)
                                val th = p.item.rh * packed.rowHeight + (p.item.rh - 1) * g.gap
                                Box(Modifier.offset(x = bx.d(BoardGrid.cellX(g, p.x)), y = bx.d(p.y * (packed.rowHeight + g.gap)))) { TileView(ctx, tile, tw, th, g.cols) }
                            }
                        }
                        FlexMin(g.footerGap)
                        BoardFooter(g.width, g.footerHeight, pages.map { it.name }, current) { page = it }
                    }
                }
            }
        }
    }
}

private fun startSettings(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FlexMin(min: Int) = Box(Modifier.weight(1f).height(LocalBx.current.d(min)))

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Scrolling(g: BoardGrid.Geometry, heightPx: Int, content: @Composable () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { content() }
}

// ================================================================ tile dispatch
@Composable
private fun TileView(c: BoardCtx, tile: Tile, w: Int, h: Int, cols: Int) {
    val fill = if (tile.type == "hero") BoardColour.heroFill(c.lead) else null
    val tap = when (tile.type) {
        "link" -> { { c.onLink(tile.kind ?: "") } }
        "sync" -> c.onSync
        else -> null
    }
    BTile(w, h, tile.chrome, tile.key, fill, if (tap != null) Modifier.tapOrHold(tap) else Modifier) {
        when (tile.type) {
            "hero" -> HeroWidget(c, w, h)
            "streak" -> StreakWidget(c, w, h)
            "week" -> WeekWidget(c, w, h)
            "goalpace" -> GoalPaceWidget(c, w, h)
            "toggles" -> TogglesWidget(c, w, h)
            "also" -> AlsoWidget(c, w, h)
            "highlight" -> HighlightWidget(c, w, h)
            "shelf" -> ShelfWidget(c, w, h, tile, cols)
            "link" -> LinkWidget(c, w, h, tile)
            "sync" -> SyncWidget(c, w, h)
            "clock" -> ClockWidget(c, w, h)
            "calendar" -> CalendarWidget(c, w, h)
            "heatmap" -> HeatmapWidget(c, w, h)
            "forecast" -> ForecastWidget(c, w, h)
            "hours" -> HoursWidget(c, w, h)
            "records" -> RecordsWidget(c, w, h)
            "yearago" -> YearAgoWidget(c, w, h)
            "word" -> WordWidget(c, w, h)
            "buddy" -> BuddyWidget(c, w, h)
            "analog" -> AnalogWidget(c, w, h)
            "moon" -> MoonWidget(c, w, h)
            "odometer" -> OdometerWidget(c, w, h)
            "glyph" -> GlyphWidget(c, w, h)
            "rings" -> RingsWidget(c, w, h)
            "chapters" -> ChaptersWidget(c, w, h)
            "badge" -> BadgeWidget(c, w, h)
            "dust" -> DustWidget(c, w, h)
            else -> Unit
        }
    }
}

// ================================================================ header and footer
@Composable
private fun StatusCluster(d: Device) {
    val bx = LocalBx.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(bx.dd(7))) {
        Ic(if (d.online) "wifi" else "wifi-off", 15, if (d.online) Ink.black else Ink.g4, 2.25f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(bx.dd(4))) {
            val bw = bx.px(21); val bh = bx.px(11); val b = max(1, bx.px(1.25)); val inner = bx.px(1.5)
            val fill = max(1, floor((bw - 2 * b - 2 * inner) * d.battery / 100.0 + 0.5).toInt())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(bx.d(bw), bx.d(bh)).border(bx.d(b), Ink.black, RoundedCornerShape(bx.dd(2.5))).padding(start = bx.d(b + inner), top = bx.d(b + inner), bottom = bx.d(b + inner))) {
                    BRect(fill, bh - 2 * b - 2 * inner, Ink.black, bx.px(1))
                }
                BRect(bx.px(2), bx.px(4.5), Ink.black)
            }
            BText("${d.battery}%", 12.0, color = Ink.g4)
        }
        BText(d.clock, 12.0, bold = true)
    }
}

@Composable
private fun BoardHeader(w: Int, d: Device, c: BoardCtx, onSearch: () -> Unit, onMenu: () -> Unit) {
    val bx = LocalBx.current
    val measurer = rememberMeasurer()
    val box = bx.px(44)
    val tapPad = (box - bx.px(24)) / 2
    val greeting = ReadingText.greeting(java.time.LocalTime.now().hour, c.d.data.name)
    val statusW = bx.px(15) + bx.px(21) + bx.px(2) + bx.px(4) * 2 + 3 * bx.px(7) + bx.px(60) + bx.px(40)
    val tw = w - statusW - bx.px(6) - 2 * box + tapPad
    val greetS = fitSize(measurer, bx, greeting, true, false, tw, 22, 18)
    Row(Modifier.requiredWidth(bx.d(w + tapPad)).height(bx.d(bx.px(64))), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BText(greeting, greetS.toDouble(), bold = true, w = tw)
            BText(d.dateLong, 13.0, color = Ink.g4, w = tw)
        }
        StatusCluster(d)
        HGap(bx.px(6))
        Box(Modifier.size(bx.d(box)).tapOrHold(onSearch), contentAlignment = Alignment.Center) { Ic("search", 23, Ink.black, 2f) }
        Box(Modifier.size(bx.d(box)).tapOrHold(onMenu), contentAlignment = Alignment.Center) { Ic("menu", 23, Ink.black, 2f) }
    }
}

@Composable
private fun BoardFooter(w: Int, h: Int, names: List<String>, page: Int, onPage: (Int) -> Unit) {
    val bx = LocalBx.current
    @Composable fun chev(name: String, enabled: Boolean, to: Int) {
        Box(Modifier.size(bx.d(bx.px(44)), bx.d(h)).tapOrHold({ if (enabled) onPage(to) }), contentAlignment = Alignment.Center) { Ic(name, 20, if (enabled) Ink.black else Ink.gb, 2.5f) }
    }
    Row(Modifier.width(bx.d(w)).height(bx.d(h)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        chev("chevron-left", page > 0, page - 1)
        HGap(bx.px(6))
        Row(horizontalArrangement = Arrangement.spacedBy(bx.dd(6)), verticalAlignment = Alignment.CenterVertically) {
            names.indices.forEach { i -> Box(Modifier.tapOrHold({ onPage(i) })) { BDot(bx.px(7), i == page, Ink.black, Ink.g8) } }
        }
        HGap(bx.px(6) + bx.px(4) + bx.px(6))
        BText(names[page], 13.0, bold = true)
        HGap(bx.px(6))
        chev("chevron-right", page < names.lastIndex, page + 1)
    }
}

// ================================================================ screens behind the boxes
@Composable
private fun ScreenTitle(title: String, onBack: () -> Unit, right: (@Composable () -> Unit)? = null) {
    val bx = LocalBx.current
    Row(Modifier.fillMaxWidth().height(bx.dd(48)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(bx.dd(44)).tapOrHold(onBack), contentAlignment = Alignment.CenterStart) { Ic("chevron-left", 24, Ink.black, 2.5f) }
        Box(Modifier.weight(1f)) { BText(title, 22.0, bold = true) }
        right?.invoke()
    }
}

@Composable
private fun BookRow(b: RBook, c: BoardCtx) {
    val bx = LocalBx.current
    val coverH = bx.px(66); val coverW = (coverH * 2 / 3.0 + 0.5).toInt()
    Row(Modifier.fillMaxWidth().tapOrHold({ c.onOpen(b) }, { c.onInfo(b) }).padding(vertical = bx.dd(8)), verticalAlignment = Alignment.CenterVertically) {
        BCover(b, coverW, coverH, max(1, bx.px(0.75)))
        HGap(bx.px(12))
        Column(Modifier.weight(1f)) {
            BText(b.title, 15.0, bold = true, lines = 2)
            if (b.author.isNotBlank()) BText(b.author, 12.0, color = Ink.g4)
        }
        HGap(bx.px(10))
        BText(if (b.finishedAt != null) "Done" else if (b.progress > 0) BoardData.pctLabel(b.progress) else "", 12.0, bold = true)
    }
    BRect(1, max(1, bx.px(0.75)), Ink.gc, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SubList(sub: Sub, bd: BoardData, c: BoardCtx, back: () -> Unit) {
    val bx = LocalBx.current
    val books = bd.data.books
    val groups: List<Pair<String?, List<RBook>>> = when (sub) {
        Sub.Library, Sub.OnDevice -> listOf(null to books.sortedBy { it.title.lowercase() })
        Sub.Authors -> books.groupBy { it.author.ifBlank { "Unknown" } }.toList().sortedBy { it.first.lowercase() }
        Sub.Series -> books.filter { it.series != null }.groupBy { it.series!! }.toList().sortedBy { it.first.lowercase() }
        Sub.Collections -> books.groupBy { it.path.substringBeforeLast('/', "").substringAfterLast('/') }.toList().sortedBy { it.first.lowercase() }
        Sub.Finished -> listOf(null to books.filter { it.finishedAt != null }.sortedByDescending { it.finishedAt })
        Sub.InProgress -> listOf(null to bd.data.continueReading)
    }
    ScreenTitle(sub.title, back)
    if (groups.all { it.second.isEmpty() }) BText("Nothing here yet.", 14.0, color = Ink.g4)
    groups.forEach { (header, list) ->
        if (header != null) {
            Row(Modifier.padding(top = bx.dd(10), bottom = bx.dd(2))) { BText(header, 13.0, bold = true); HGap(bx.px(6)); BText("${list.size}", 13.0, color = Ink.g4) }
        }
        list.forEach { BookRow(it, c) }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, books: List<RBook>, c: BoardCtx, back: () -> Unit) {
    val bx = LocalBx.current
    ScreenTitle("Search", back)
    val shape = RoundedCornerShape(bx.dd(22))
    Row(
        Modifier.fillMaxWidth().height(bx.dd(44)).clip(shape).border(bx.d(max(1, bx.px(1.25))), Ink.g6, shape).padding(horizontal = bx.dd(12)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic("search", 16, Ink.g4, 2.5f)
        HGap(bx.px(8))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) BText("Title, author or series", 14.0, color = Ink.g4)
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true, textStyle = boardStyle(bx, 14.0),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink.black), modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) Box(Modifier.tapOrHold({ onQuery("") })) { Ic("x", 16, Ink.black, 2.5f) }
    }
    VGap(bx.px(8))
    val found = remember(books, query) { ReadingSearch.filter(books, query) }
    if (query.isNotBlank()) {
        BText("${found.size} ${if (found.size == 1) "result" else "results"}", 12.0, color = Ink.g4)
        if (found.isEmpty()) BText("No book matches “${query.trim()}”.", 14.0, italic = true, serif = true, color = Ink.g4)
        found.forEach { BookRow(it, c) }
    }
}

@Composable
private fun GoalScreen(goal: Int, done: Int, year: Int, onSet: ((Int) -> Unit)?, back: () -> Unit) {
    val bx = LocalBx.current
    ScreenTitle("$year goal", back)
    BText("$done of $goal books finished", 14.0, color = Ink.g4)
    VGap(bx.px(16))
    if (onSet != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(bx.dd(14))) {
            val enabledMinus = goal > ReadingGoal.MIN; val enabledPlus = goal < ReadingGoal.MAX
            BCircle(bx.px(44), bg = Ink.white, border = max(1, bx.px(1.5)), borderCol = if (enabledMinus) Ink.black else Ink.gb, modifier = Modifier.tapOrHold({ if (enabledMinus) onSet(ReadingGoal.step(goal, -1)) })) {
                Ic("minus", 22, if (enabledMinus) Ink.black else Ink.gb, 2.75f)
            }
            Box(Modifier.width(bx.dd(64)), contentAlignment = Alignment.Center) { BText("$goal", 28.0, bold = true) }
            BCircle(bx.px(44), bg = Ink.white, border = max(1, bx.px(1.5)), borderCol = if (enabledPlus) Ink.black else Ink.gb, modifier = Modifier.tapOrHold({ if (enabledPlus) onSet(ReadingGoal.step(goal, 1)) })) {
                Ic("plus", 22, if (enabledPlus) Ink.black else Ink.gb, 2.75f)
            }
            BText("books a year", 13.0, color = Ink.g4)
        }
    }
}

@Composable
private fun BookDetailScreen(book: RBook, highlights: List<com.ethran.notable.reading.RHighlight>, c: BoardCtx, back: () -> Unit) {
    val bx = LocalBx.current
    ScreenTitle("Book", back)
    val coverH = bx.px(260); val coverW = (coverH * 2 / 3.0 + 0.5).toInt()
    Row(Modifier.fillMaxWidth()) {
        BCover(book, coverW, coverH, max(1, bx.px(1)), Ink.g6)
        HGap(bx.px(16))
        Column(Modifier.weight(1f)) {
            BText(book.title, 22.0, bold = true, lines = 4)
            if (book.author.isNotBlank()) { VGap(bx.px(4)); BText(book.authors.joinToString(", "), 14.0, color = Ink.g4, lines = 2) }
            book.series?.let { VGap(bx.px(4)); BText(it, 12.0, color = Ink.g4) }
            VGap(bx.px(14))
            BBar(coverW * 2, bx.px(6), book.progress, Ink.gc, Ink.black, bx.px(3))
            VGap(bx.px(6))
            BText(if (book.finishedAt != null) "Finished" else "${BoardData.pctLabel(book.progress)} read", 13.0, color = Ink.g4)
            VGap(bx.px(12))
            Fact("Pages", if (book.pages > 0) "${book.pages}" else "–")
            Fact("Last read", ReadingText.lastRead(book.lastReadAt, c.d.data.now.takeIf { it > 0L } ?: System.currentTimeMillis()))
            Fact("Time left", book.minutesLeft?.let { BoardData.duration(it) } ?: if (book.finishedAt != null) "Done" else "–")
            Flex()
            Row { PillButton("Open in NeoReader", "book-open") { c.onOpen(book) } }
        }
    }
    VGap(bx.px(16))
    BText("Highlights · ${highlights.size}", 13.0, bold = true)
    VGap(bx.px(6))
    if (highlights.isEmpty()) BText("No highlights in this book yet.", 14.0, italic = true, serif = true, color = Ink.g4)
    highlights.forEach { h ->
        BText("“${h.quote}”", 15.0, italic = true, serif = true, color = Ink.g1, lines = 12, modifier = Modifier.padding(vertical = bx.dd(8)))
        BRect(1, max(1, bx.px(0.75)), Ink.gc, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = LocalBx.current.dd(3)), horizontalArrangement = Arrangement.SpaceBetween) {
        BText(label, 13.0, color = Ink.g4); BText(value, 14.0, bold = true)
    }
}
