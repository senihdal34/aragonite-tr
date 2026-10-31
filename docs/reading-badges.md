# Okuma rozetleri (Next badge kutusu)

Branch: `feat/reading-badges`. Kod: `app/src/main/java/com/ethran/notable/reading/badges/*`, arayüz: `reading/board/BoardBadgeWidgets.kt`.

## Kısa sonuç

BookOrbit'in gerçek rozet kataloğu **bulundu**. BookOrbit açık kaynak, rozetlerin tamamı sunucu deposunda bir tohum (seed) dosyasında duruyor: 87 rozet, 5 kategori, 4 nadirlik. Bu uygulama sunucusuz çalıştığı için bunların NeoReader okuma günlüğünden hesaplanabilen **56 tanesini** aynı anahtar, ad, açıklama, simge, nadirlik ve eşikle taşıdı. Kalan 31 rozet için gereken veri (puan verme, tür, dil, yayın yılı, cihaz, bırakılan kitap, vurgu notu) bu uygulamada yok; listede "hayır" diye işaretli.

## Kaynaklar

Depo: https://github.com/bookorbit/bookorbit (AGPL-3.0, sayfa: https://bookorbit.app). README "50+ achievements across five categories" diyor; ayrıntı yalnız kodda. Okunan dosyalar (hepsi `main` dalında):

| Ne | Dosya |
|---|---|
| Katalog (87 rozet: anahtar, ad, açıklama, simge, nadirlik, eşik, gizli, sıra) | `server/src/modules/achievement/seed/achievement-seed.ts` |
| Veri şekli (`AchievementItem`: key, groupKey, tier, category, name, description, iconName, rarity, threshold, hidden, awardedAt, currentProgress) | `packages/types/src/achievement.ts` |
| İlerleme nasıl sayılır (`computeProgress`, `computeSingleBadgeProgress`, `getProgressForGroup`), kategori sırası, gizli rozet maskesi | `server/src/modules/achievement/achievement.service.ts` |
| Ne zaman kazanılır | `server/src/modules/achievement/evaluators/{reading,library,exploration,dedication,milestones,rating,devices}.evaluator.ts` |
| Pano kutusunun verisi: `achievements { earned, total, next, latest }`, **sıradaki rozet kuralı** | `server/src/modules/koreader/koreader-catalog.service.ts` (`buildAchievementSummary`) |
| İstemcide "en yakın rozet" (aynı kural, ilerleme sıfırsa dışarıda) | `client/src/features/achievements/lib/achievement-showcase.ts` (`closestUnlocks`) |
| Madalya şekli (nadirliğe göre kenar: düz, boncuklu, sekiz dilimli, güneş) | `client/src/features/achievements/lib/medal-geometry.ts` |
| Üç kovada gruplama (kazanılan, sürüyor, kilitli) | `client/src/features/achievements/lib/achievement-display.ts` |

Eklentinin kendi kopyası (elle okunan şartname): `~/Workspace/Kai-Sync/projects/koreader-board/localboard.koplugin/lb_board_data.lua` (`BoardData.badges`, `earnedLabel`, `etaLabel`) ve `lb_board_decor.lua` (`Decor.badge`, `medal`). Depodaki asıl eklenti: `koreader-plugin/bookorbit.koplugin/`.

Bulunamayan: bookorbit.app üzerinde rozetleri anlatan bir belge sayfası yok (`what-is-bookorbit` rozetlerden söz etmiyor). Gerek de kalmadı, katalog kodda.

## BookOrbit'in veri şekli

- Kategoriler (liste sırası): `reading`, `library`, `exploration`, `dedication`, `devices`.
- Nadirlik: `common`, `rare`, `epic`, `legendary`. Eklentide renkleri her renk şemasında aynı: common yeşil, rare mavi, epic mor, legendary altın, kurdele kırmızı (`lb_board_colour.lua` RARITY).
- Kademeli setler: `groupKey` + `tier` (1..4). `books_finished`, `pages_read`, `hours_read`, `long_book`, `library_builder`, `annotator`, `critic`, `genre_explorer`, `polyglot`, `streak`. Setin tüm kademeleri aynı ilerleme sayısını paylaşır.
- Tek rozetler: `groupKey` yok. Eşiği olanların ilerlemesi sayılır (marathoner, binge_reader, ...), olmayanlar sadece kazanılır ya da kazanılmaz (sprint, perfect_month, ...).
- Gizli rozetler: `hidden: true` (all_nighter, early_bird, reborn, new_year_reader, dnf_self_aware, tough_crowd). Kazanılana kadar sunucu adı "Secret Achievement", açıklamayı "Keep reading to reveal this achievement.", simgeyi `lock`, nadirliği `common` olarak yollar; eşik ve ilerleme gizlenir.
- Kazanma tarihi: sunucu `awardedAt` olarak saklar (kutudaki "Earned 12 Oct" satırı buradan gelir).
- "Achievements off" durumu: kullanıcı ayarlarda rozetleri kapatırsa pano verisinde `achievements = null` gelir, kutu "Achievements are turned off" der. Bu uygulamada kapatma ayarı yok, durum kullanılmıyor.

### Sıradaki rozet kuralı (`buildAchievementSummary`)

Kazanılmamış, gizli olmayan, eşiği ve ilerlemesi (`currentProgress`) olan rozetler arasından `ilerleme / eşik` oranı **en büyük olan**, oran 1'in altında olmak şartıyla. Eşitlikte katalogdaki ilk. İlerlemesi sıfır olan rozet de aday sayılır. "Son kazanılan" (`latest`): `awardedAt` en yeni olan; eşitlikte listede ilk gelen. Kutu, son kazanılan rozet son 24 saat içinde kazanıldıysa ve kutu küçükse onu "New badge" diye gösterir, büyük kutu sıradakini gösterip altına yenisini koyar (`Decor.badge`). Süre satırı (`etaLabel`) yalnız `hours_read`, `books_finished`, `pages_read` setlerinde, okuma hızından çıkar.

## Bu uygulamada nasıl çalışıyor

Hesap `ReadingData`'dan yapılır (`BadgeFacts.from`), kurallar `BadgeEngine`de, sayma işleri `BadgeMetrics`te (hepsi saf Kotlin, saat dışarıdan verilir).

- **Kazanma tarihi kalıcı.** Bir rozet ilk kez kazanılmış görüldüğünde `SharedPreferences` içinde (`reading_board` dosyası, `badges_earned` anahtarı, satır başına `anahtar|ms|backlog`) saklanır. Sonra veri küçülse ya da kitap silinse de rozet ve tarihi kalır. Room şeması değişmedi.
- **Tarih nereden.** Günlük tarihi veriyorsa o (n. kitabın bitiş günü, eşiğin aşıldığı okuma günü, seri uzunluğuna ulaşılan gün, ilk serili kitap, ilk uzun kitap), yoksa rozetin ilk görüldüğü an. Gelecek tarih "şimdi"ye kısaltılır.
- **İlk dolum.** Depo ilk kez doldurulurken zaten kazanılmış olanlar `backlog` diye işaretlenir ve "New badge" diye kutlanmaz; yoksa uzun geçmişi olan okur ilk açılışta onlarca "yeni" görürdü. `__init__` satırı bunun işaretidir. Depo, kitaplar **ve** okuma günlüğü okunabildiğinde doldurulur; yarım veriyle dolmasın diye.
- **Sunucudan farklar** (NeoReader günlüğü BookOrbit'in oturum günlüğünden kaba olduğu için):
  - `streak`: sunucu güncel seriyi sayar, burada en uzun seri (kazanılan rozet zaten kalıcı, ilerleme halkası geriye gitmesin diye).
  - `pages_read`: sayfa çevirme olayı sayısı. `hours_read`: sayfa başına en çok 3 dk, saatin altı kesilir.
  - `all_nighter` / `early_bird`: yalnız son 90 günün saat dağılımı bilindiği için o pencerede görülürse kazanılır (pano her açılışta çalışır, görüldüğü an saklanır). Oturum başlangıcı yerine okunan saat sayılır.
  - `binge_reader`, `monthly_reader_2`, `year_round_reader`, `seasonal_reader`: tüm geçmişteki en iyi pencere/ay/yıl (sunucu o anki pencereyi gösterir); kazanma kuralı aynı.
  - `comeback_kid`: iki okuma günü arasındaki boş gün sayısı 30 ya da daha fazla.
  - `curator`: "koleksiyon" yerine kitapların durduğu klasör sayısı. `annotator` / `bookmarked` / `deep_dive_session`: vurgular.
  - `consistent_reader`, `weekend_rhythm`, `weekend_warrior`: sunucuda ilerleme sayısı yok ya da boş; burada gerçek sayı gösterilir.
  - `category_sweeper`: yerel kataloğun dört kategorisinde (`devices` yok) birer rozet.
- **Kutu ve liste.** `BadgeWidget` (Decor sayfası; tablette büyük kutu sıradaki rozet + altında son kazanılan, geniş kutu madalya + yazı, küçük kutu madalya + ad) ve kutuya dokununca açılan `BadgeListScreen` (Earned, In progress, Locked). Madalya Canvas ile çizilir: ilerleyen rozetin halkası, kazanılan rozetin kurdelesi, nadirlik kenarı. Gölge, geçiş, animasyon yok.

## Yerel katalog (87 rozetin tamamı)

"Bu uygulamada: evet" olanlar `BadgeCatalogue.kt` içinde; ad, açıklama, simge, nadirlik ve eşik BookOrbit tohumundan olduğu gibi.

### Reading

| Anahtar | Ad | Nadirlik | Eşik | Gizli | Bu uygulamada |
|---|---|---|---|---|---|
| `books_finished_1` | Ink Initiate | common | 1 |  | evet: bitmiş kitap sayısı (NeoReader, %99 ve üstü ya da bitti işareti) |
| `books_finished_2` | Spine Scout | rare | 10 |  | evet: bitmiş kitap sayısı (NeoReader, %99 ve üstü ya da bitti işareti) |
| `books_finished_3` | Story Stalwart | epic | 35 |  | evet: bitmiş kitap sayısı (NeoReader, %99 ve üstü ya da bitti işareti) |
| `books_finished_4` | Grand Archivist | legendary | 100 |  | evet: bitmiş kitap sayısı (NeoReader, %99 ve üstü ya da bitti işareti) |
| `pages_read_1` | First Leaves | common | 500 |  | evet: çevrilen sayfa sayısı (okuma günlüğündeki sayfa olayları) |
| `pages_read_2` | Chapter Weaver | rare | 5000 |  | evet: çevrilen sayfa sayısı (okuma günlüğündeki sayfa olayları) |
| `pages_read_3` | Tome Tactician | epic | 25000 |  | evet: çevrilen sayfa sayısı (okuma günlüğündeki sayfa olayları) |
| `pages_read_4` | Grand Lexicon | legendary | 100000 |  | evet: çevrilen sayfa sayısı (okuma günlüğündeki sayfa olayları) |
| `hours_read_1` | Hour Initiate | common | 10 |  | evet: toplam okuma süresi / 60 (sayfa başına en çok 3 dk) |
| `hours_read_2` | Timebound Reader | rare | 100 |  | evet: toplam okuma süresi / 60 (sayfa başına en çok 3 dk) |
| `hours_read_3` | Chronicle Veteran | epic | 500 |  | evet: toplam okuma süresi / 60 (sayfa başına en çok 3 dk) |
| `hours_read_4` | Timeless Scholar | legendary | 1000 |  | evet: toplam okuma süresi / 60 (sayfa başına en çok 3 dk) |
| `marathoner` | Marathoner | rare | 90 |  | evet: en uzun oturum (10 dk boşluk oturumu böler) |
| `all_nighter` | All-nighter | epic | - | gizli | evet: son 90 günde 01:00-04:00 arası okuma var mı |
| `early_bird` | Early Bird | rare | - | gizli | evet: son 90 günde 05:00-07:00 arası okuma var mı |
| `long_book_1` | Story Stretch | common | 300 |  | evet: bitmiş en uzun kitabın sayfa sayısı |
| `page_turner` | Page Pathfinder | rare | 500 |  | evet: bitmiş en uzun kitabın sayfa sayısı |
| `long_book_3` | Epic Voyager | epic | 750 |  | evet: bitmiş en uzun kitabın sayfa sayısı |
| `tome_tamer` | Tome Sovereign | legendary | 1000 |  | evet: bitmiş en uzun kitabın sayfa sayısı |
| `sprint` | Sprint | epic | - |  | evet: bitmiş kitap yalnız bir günde okunmuş ve o gün bitmiş |
| `binge_reader` | Binge Reader | epic | 3 |  | evet: herhangi bir 7 günlük pencerede bitmiş kitap sayısı |
| `monthly_reader_2` | Monthly Machine | legendary | 10 |  | evet: bir takvim ayında bitmiş en çok kitap |
| `slow_burn` | Slow Burn | rare | - |  | evet: ilk okuma gününden bitişe 90 günden fazla |
| `one_sitting` | One Sitting | epic | - |  | hayır: oturumda kitabın %90ı ilerlemesi ölçülemiyor |
| `power_hour` | Power Hour | rare | 75 |  | evet: bir oturumda çevrilen en çok sayfa |
| `speed_reader` | Speed Reader | epic | 100 |  | evet: bir günde çevrilen en çok sayfa |
| `old_friend` | Old Friend | rare | - |  | hayır: tekrar okuma bilinmiyor |
| `dnf_self_aware` | It's Not You, It's Me | common | - | gizli | hayır: NeoReader bırakıldı işareti tutmuyor |

### Library

| Anahtar | Ad | Nadirlik | Eşik | Gizli | Bu uygulamada |
|---|---|---|---|---|---|
| `library_builder_1` | Shelf Seed | common | 50 |  | evet: Books klasöründeki kitap sayısı |
| `library_builder_2` | Collection Architect | rare | 250 |  | evet: Books klasöründeki kitap sayısı |
| `library_builder_3` | Stack Sovereign | epic | 1000 |  | evet: Books klasöründeki kitap sayısı |
| `library_builder_4` | Library of Alexandria | legendary | 5000 |  | evet: Books klasöründeki kitap sayısı |
| `annotator_1` | Note Taker | common | 10 |  | evet: vurgu (highlight) sayısı |
| `annotator_2` | Active Annotator | rare | 50 |  | evet: vurgu (highlight) sayısı |
| `annotator_3` | Prolific Annotator | epic | 200 |  | evet: vurgu (highlight) sayısı |
| `annotator_4` | Master Annotator | legendary | 500 |  | evet: vurgu (highlight) sayısı |
| `multi_format` | Multi-Format | rare | 3 |  | evet: dosya uzantısı çeşidi |
| `curator` | Curator | rare | 10 |  | evet: kitapların durduğu klasör sayısı (Collections kutusu) |
| `note_keeper` | Note Keeper | rare | 25 |  | hayır: vurgulara not yazılmıyor |
| `deep_dive_session` | Deep Dive | rare | 10 |  | evet: bir günde yapılan en çok vurgu |
| `bookmarked` | Bookmarked | common | - |  | evet: en az bir vurgu |
| `first_verdict` | First Verdict | common | - |  | hayır: puan verme yok |
| `critic_1` | Fledgling Critic | common | 10 |  | hayır: puan verme yok |
| `critic_2` | Seasoned Critic | rare | 50 |  | hayır: puan verme yok |
| `critic_3` | Esteemed Critic | epic | 200 |  | hayır: puan verme yok |
| `critic_4` | Definitive Critic | legendary | 500 |  | hayır: puan verme yok |
| `standing_ovation` | Standing Ovation | common | - |  | hayır: puan verme yok |
| `across_the_board` | Across the Board | rare | 5 |  | hayır: puan verme yok |
| `tough_crowd` | Tough Crowd | rare | - | gizli | hayır: puan verme yok |
| `wordsmith` | Wordsmith | rare | 280 |  | hayır: vurgulara not yazılmıyor |
| `box_of_crayons` | Box of Crayons | rare | 5 |  | hayır: vurgu rengi yok |

### Exploration

| Anahtar | Ad | Nadirlik | Eşik | Gizli | Bu uygulamada |
|---|---|---|---|---|---|
| `genre_explorer_1` | Genre Curious | common | 5 |  | hayır: tür bilgisi yok |
| `genre_explorer_2` | Genre Adventurer | rare | 10 |  | hayır: tür bilgisi yok |
| `genre_explorer_3` | Genre Connoisseur | epic | 15 |  | hayır: tür bilgisi yok |
| `genre_explorer_4` | Genre Master | legendary | 20 |  | hayır: tür bilgisi yok |
| `polyglot_1` | Bilingual | common | 2 |  | hayır: dil bilgisi yok |
| `polyglot_2` | Trilingual | rare | 3 |  | hayır: dil bilgisi yok |
| `polyglot_3` | Multilingual | epic | 5 |  | hayır: dil bilgisi yok |
| `polyglot_4` | Polyglot | legendary | 7 |  | hayır: dil bilgisi yok |
| `old_soul` | Old Soul | epic | - |  | hayır: yayın yılı yok |
| `new_release` | New Release | rare | - |  | hayır: yayın yılı yok |
| `century_span` | Century Span | epic | 3 |  | hayır: yayın yılı yok |
| `author_deep_dive` | Author Deep Dive | rare | 5 |  | evet: bitmiş kitaplarda aynı yazarın en çok kitabı |
| `decade_sampler` | Decade Sampler | epic | 5 |  | hayır: yayın yılı yok |
| `short_story_fan` | Short Story Fan | rare | 5 |  | evet: 100 sayfadan kısa bitmiş kitap sayısı |
| `thick_and_thin` | Thick and Thin | rare | - |  | evet: 100 sayfadan kısa ve 800 sayfadan uzun birer bitmiş kitap |
| `trilogy_master` | Trilogy Master | rare | - |  | evet: en az 3 kitaplık, hepsi bitmiş bir seri |
| `genre_devotee` | Genre Devotee | rare | 10 |  | hayır: tür bilgisi yok |
| `first_series` | Series Begun | common | - |  | evet: bitmiş, serisi olan bir kitap |

### Dedication

| Anahtar | Ad | Nadirlik | Eşik | Gizli | Bu uygulamada |
|---|---|---|---|---|---|
| `streak_1` | Getting Started | common | 7 |  | evet: en uzun üst üste okuma günü (BookOrbit güncel seriyi sayar) |
| `streak_2` | Habitual | rare | 30 |  | evet: en uzun üst üste okuma günü (BookOrbit güncel seriyi sayar) |
| `streak_3` | Unstoppable | epic | 100 |  | evet: en uzun üst üste okuma günü (BookOrbit güncel seriyi sayar) |
| `streak_4` | Year-Long Flame | legendary | 365 |  | evet: en uzun üst üste okuma günü (BookOrbit güncel seriyi sayar) |
| `year_round_reader` | Year-Round Reader | epic | 12 |  | evet: bir yılda okuma olan ay sayısı |
| `reborn` | Reborn | epic | - | gizli | hayır: bırakıldı işareti yok |
| `comeback_kid` | Comeback Kid | rare | 30 |  | evet: iki okuma günü arasındaki en uzun boşluk (gün) |
| `seasonal_reader` | Four Seasons | epic | 4 |  | evet: bir yılda okuma olan mevsim sayısı (Ara-Şub, Mar-May, Haz-Ağu, Eyl-Kas) |
| `perfect_month` | Perfect Month | legendary | - |  | evet: her günü okunmuş bir takvim ayı |
| `consistent_reader` | Consistency Is Key | rare | 14 |  | evet: günde en az 30 dk okunan en uzun gün dizisi |
| `weekend_rhythm` | Weekend Rhythm | epic | 8 |  | evet: cumartesi ya da pazar okunan en uzun art arda hafta dizisi |
| `weekend_warrior` | Weekend Warrior | rare | 5 |  | evet: bir hafta sonunda (cmt+paz) en çok saat |
| `yearly_finisher_12` | Yearly Closer | epic | 12 |  | evet: bir takvim yılında bitmiş en çok kitap |
| `new_year_reader` | New Year Reader | epic | - | gizli | evet: 1 Ocak günü okuma var mı |
| `category_sweeper` | Category Sweeper | legendary | 4 |  | evet: rozet kazanılan kategori sayısı (Reading, Library, Exploration, Dedication) |

### Devices

| Anahtar | Ad | Nadirlik | Eşik | Gizli | Bu uygulamada |
|---|---|---|---|---|---|
| `synced_up` | Synced Up | common | - |  | hayır: cihaz/kaynak bilgisi yok |
| `full_orbit` | Full Orbit | legendary | 3 |  | hayır: cihaz/kaynak bilgisi yok |
| `two_worlds` | Two Worlds | rare | 2 |  | hayır: cihaz/kaynak bilgisi yok |

## Yapılmayanlar

- Gerçek cihazda düzen denemesi: Boox'ta bakılmadı (bu çalışmada cihaza dokunulmadı); kutu varyantlarının seçimi sabit oranlara ve satır yüksekliklerine dayanıyor, `BoardGrid` ölçüleriyle denenmeli.
- "Achievements off" ayarı yok; kapatma düğmesi eklenirse `ReadingUi.badges` boş bırakılıp kutu ipucu gösterir.
- Eksik 31 rozet için veri kaynağı (puan, tür, dil, yayın yılı) eklenirse `BadgeCatalogue` ve `BadgeEngine.measure` içine eklenir; anahtarlar BookOrbit ile aynı olduğundan sonradan eklenenler eski tarihleri bozmaz.
