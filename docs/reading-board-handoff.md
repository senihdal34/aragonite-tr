# Okuma panosu (Reading board) — devir belgesi

Branch: `feat/reading-board` (bu depoda, `feat/planner-pins-palm-guard` üzerinden açıldı). Uygulama adı: **A Deus Notebook** (kod deposunun adı `aragonite-tr`, paket `io.aragonite.notes`).

## 1. İstenen
Deus Notebook'a yeni bir **"Reading" sekmesi** eklenecek: sekme çubuğunda `Planner | Notes | ★ Favorites | Reading`. Sekmenin içeriği **BookOrbit Board** panosunun **birebir** tasarımı ve işlevi. Bir kitaba dokununca **NeoReader** (`com.onyx.kreader`) açılır. KOReader tamamen bırakıldı; veri **NeoReader'dan** gelir. Kullanıcı: Senih, yıllık hedef 15 kitap, Türkçe sade dil. Lisans uyarısı yapma (kullanıcı sadece kendi tabletinde kullanıyor). Sunucu/Docker yok, hiçbir şey VPS'e gitmez.

Referans görsel: BookOrbit Board ekranı (bir Boox üzerinde): selamlama + tarih + durum çubuğu, hero (Continue reading), Streak, kısayol düğmeleri (wifi/ışık/gece/yenile), This week çubukları, yıllık hedef, Also reading, kedi (buddy), günün vurgusu, Discover, Recently added, Library/Authors/Series/On device kutuları, altta sayfa noktaları `‹ ●○○○ Home ›`.

Tasarım referansı (kod olarak) `~/Workspace/Kai-Sync/projects/koreader-board/localboard.koplugin/lb_board_*.lua` (BookOrbit'in Lua kodunun kopyası, sadece okunacak şartname gibi kullan; Compose'a elle taşınacak). Önemli dosyalar:
- `lb_board_layout.lua` — ızgara: 2/4/6 sütun (genişlik dp'sine göre), kenar boşluğu, satır yüksekliği MIN 84 / COMFY 104 / MAX 150 dp, boşluk 10 dp, header 64, footer 40; `pack()` ilk-sığan yoğun yerleştirme; `fitPage()` sığmayanı düşürür, boşlukları ekstra kutuyla doldurur. Doğrudan Kotlin'e port edilecek, birim testli.
- `lb_board_store.lua` `starterPages()` — 4 sayfa (home, insights, library, decor) ve her birindeki kutu listesi/boyutu (S 1x1, M 2x1, L 2x2, W tam genişlik). Dikey 4 sütun düzeni `else` dalındaki (tablette bu görünüyor).
- `lb_board_widgets.lua` (95 KB) — kutuların çizimi: hero, streak, toggles, week, goalpace, also, highlight, shelf, link, heatmap, forecast, hours, records, yearago, word, sync, clock, light, calendar.
- `lb_board_decor.lua` — buddy (kedi), analog, moon, odometer, glyph, rings, chapters, badge, dust; `lb_board_art.lua` kedi pikselleri; `lb_board_icons.lua` çizgi ikonlar; `lb_board_colour.lua` renk/ink kuralları; `lb_board_ui.lua` yardımcılar; `lb_board_data.lua` veri biçimi; `lb_catalog_board.lua` ana ekran + sayfa çevirme + menü + düzenleme.
- Kutu krom kuralları: `BoardWidgets.chrome` (ink = siyah dolu, fill = gri dolu, line = ince çerçeve, köşe 12 dp).
- Kutuya dokununca ne olur: `boardItemAction` (hero→kitabı aç/devam, link→liste sayfası, shelf→kitap listesi…).

## 2. Hazır olan (bu branch'te)
- `reading/NeoReaderSource.kt`: NeoReader'ın üç veri kapısını `ContentResolver` ile okur (aşağıda). Manifest'e `<queries>` eklendi. **Deneme sonucu: Deus Notebook bu kapıları okuyabiliyor** (tablette log: `library=64 events=285 (pages=163) highlights=24`).
- `reading/ReadingModel.kt`: `RBook`, `ReadingData` (panonun çizeceği her şey).
- `reading/ReadingStats.kt`: ham veriden `ReadingData` hesabı (streak, hafta dakikaları/noktaları, yıl bitirilenleri, discover/recent, yazar/seri sayısı, günün vurgusu, kalan dakika tahmini). Test: `app/src/test/.../ReadingStatsTest.kt` (7 test, geçiyor).
- `NewHomeView.kt`'ta geçici bir `NeoReaderSource.probe()` çağrısı var (HomeView ON_RESUME). Kalıcı yapıda kaldır.

## 3. NeoReader veri kapıları (adb `content query` ile doğrulandı)
- `content://com.onyx.kreader.statistics.provider/OnyxStatisticsModel` — sayfa günlüğü. Sütunlar: `type, action(add/delete), eventTime(ms), docId, md5, sid, durationTime(ms), currPage, lastPage, readingProgress(%), orgText, position, path, title, author, hideRecord`. `type`: 0/6 = kitap açıldı (yalnız bu satırlarda `path`/`title` var, `docId` ile diğerlerine bağla), **1 = bir sayfa okundu** (`durationTime`, `currPage`), 2/4 = vurgu/alıntı, 5 = ilerleme anlık görüntüsü (`readingProgress`).
- `content://com.onyx.content.database.ContentProvider/Metadata` — kitap listesi: `name,title,authors,series,nativeAbsolutePath,progress("11/238"),lastAccess,createdAt,readingStatus,favorite,rating,tags,hashTag,type`. Taşınan/silinen dosyaların **eski satırları kalır** → dosyanın varlığına göre filtrele (kod yapıyor).
- `.../ContentProvider/Annotation` — vurgular (`quote`, `createdAt`, `docId`, `md5`). `.../Thumbnail` — `originContentPath` → küçük resim dosyası (`/storage/emulated/0/.thumbnails/com.onyx/*.png`).
- Kitaplar `/sdcard/Books` içinde (29 dosya: 22 PDF + 7 EPUB/MOBI). 24 rapor PDF'i `/sdcard/Documents/Raporlar`'da ve Boox Notlar'a aktarıldı; **panoya girmemeli** (`ReadingStats.build` yalnız `booksDir` içindekileri alır).
- Kitap açma: `Intent(ACTION_VIEW)` ile `com.onyx.kreader`'a dosya (içerik URI'si / FileProvider gerekebilir). **Henüz denenmedi (Deneme B)**. adb ile `am start -a android.intent.action.VIEW -d file://… -t application/pdf -p com.onyx.kreader` ile önce elle dene.

## 4. Yapılacaklar (sırayla)
1. **Sekme**: `MainTab.Reading`, `MainTabs`'a 4. sekme ("Reading"), `HomeSection.Reading` (`HomeViewModel.kt`), `NewHomeView.kt` `MainNav`/içerik dalı, `EditorView.kt`'taki planner sekme çubuğuna `onReading` (Home'a `HomeSection.Reading` ile git). Planner/Home'daki tab çubuğu yüksekliği (`PlannerLayout.NAV_H = 153f`) bozulmasın.
2. **Veri**: `ReadingViewModel` (Hilt): sağlayıcıları IO'da oku, `ReadingStats.build` ile `ReadingData` üret, önbellekle; sadece son okumadan sonraki olayları çek; sağlayıcı yoksa son veriyi göster.
3. **Izgara ve sayfa kabuğu**: `reading/board/BoardGrid.kt` (`lb_board_layout.lua` portu + test), `ReadingBoardView.kt`: üst başlık (selamlama "Good morning, Senih", tarih, wifi/pil/saat, arama, menü), ızgara, alt sayfa çevirici. Sayfalar: home, insights, library (+ decor). Yatay çevirme: kaydırma ve sayfa noktaları.
4. **Kutular** (her biri ayrı dosya): hero, streak, toggles, week, goalpace, also, buddy(kedi), highlight, shelf(discover/recent/finished/pickup/upnext/wtr), link(library/authors/series/device/collections/scopes/search), heatmap, forecast, hours, records, yearago, word, clock, light/calendar, decor kutuları. Sunucuya bağlı olanlar (sync, collections, scopes) aygıt karşılığıyla veya "yok" durumuyla.
5. **İkincil ekranlar**: Library / Authors / Series / On device listeleri, arama, kitap ayrıntısı, yıllık hedef değiştirme (menü). Kitaba dokunma → NeoReader.
6. **Kapaklar**: tabletteki hazır kapaklar `/sdcard/koreader/localboard-covers/<md5("/sdcard/Books/<dosya adı>")>.jpg` (29 kitap + eskiler). KOReader klasörüne bağımlı kalma: `Documents/ReadingCovers/` gibi sabit bir yere kopyala (adb push ile) ve `ReadingStats.build(coverDir=…)` oraya bak. Yedek: NeoReader küçük resimleri (`thumbnails` parametresi).
7. **Test**: JVM birim testleri (istatistik, ızgara yerleşimi, kenar durumlar); tablette ekran görüntüsüyle (`adb exec-out screencap`), dikey ve yatay.

## 5. Kenar durumlar (planlandı)
- Boşta açık sayfa: sayfa başına en fazla 3 dk (`PAGE_CAP_MS`). Gün 04:00'te başlar. `action=delete` ve gizli kayıtlar sayılmaz. Bitirme: ilerleme ≥ %99 ya da `readingStatus=2`; yeniden okuma ikinci kez sayılmaz. Hedef: yıllık 15.
- NeoReader güncellenip tablo değişirse çökme yok: boş liste + log, son veri gösterilir. Veri büyürse artımlı oku.
- Tablet dönebilir (otomatik döndürme açık): hem dikey hem yatay çalışsın.
- Kitap dosyası taşınırsa geçmiş kaybolmasın: eşleştirme `docId`/yol ile, taşınmış dosyalar için `md5`.

## 6. Tablete kurma ve test (Boox quirkleri)
- Derle ve yan yana kur: `./gradlew installDebug -PSIDE_BY_SIDE=true` → `io.aragonite.notes.debug` ("A Deus Notebook TEST"). Gerçek uygulamaya (`io.aragonite.notes`) **dokunma**; gerçek notlar için önce yedek (`Documents/notabledb`).
- İlk kurulumdan sonra uygulama Boox'ta "kullanılana kadar devre dışı" olur; **Uygulamalar** ekranından simgesine bir kez dokun (`am start` ancak ondan sonra çalışır). Dosya izni: `adb shell appops set io.aragonite.notes.debug MANAGE_EXTERNAL_STORAGE allow`.
- Log: `adb logcat -s NeoReaderSource:V`. Ekranı koordinatla değil **`uiautomator dump`** ile metne göre bul (tablet dönebiliyor). HOME tuşu kullanıcının Aragonite/Deus'unu açar; Boox başlatıcısı için `am start -f 0x20000 -n com.onyx/.reader.main.ui.MainActivity`.
- E-ink kuralları (`CLAUDE.md`): animasyon yok, gölge/gradyan yok, yüksek kontrast; gereksiz recomposition'dan kaçın.
- `CLAUDE.md` kodlama kuralları geçerli (Hilt, `!!` yok, Room'da değişiklik yok bu işte).

## 7. Kabul ölçütleri
- Sekmede 29 kitap (22 PDF + 7 EPUB/MOBI), kapakları var, raporlar yok.
- Streak, hafta çubukları, yıllık hedef, Also reading, Discover/Recent, vurgu NeoReader verisinden doğru.
- Bir kitaba dokununca NeoReader o kitabı açar; dönünce pano güncellenir.
- Dikey ve yatay çalışır; planner ve Notes ekranları bozulmaz; birim testleri geçer.
