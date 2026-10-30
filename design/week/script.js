const IMG={gun:"{{GUN}}",fin:"{{FIN}}",thy:"{{THY}}",i1:"{{I1}}"};
const th=(k,st="")=>`<div class="thumb" ${st?`style="${st}"`:""}><img src="${IMG[k]}" alt=""></div>`;
const NOTES={fin:"Yeni Finans Ürün Planı",gun:"Gün 1- 14 Eylül",thy:"THY Rapor"};

/* ---- ortak gazete başlığı ---- */
const head=(title,sub,activeTab)=>`<div class="meta caps"><span>${sub}</span><span>AYARLAR</span></div>
 <div class="ttl">${title}</div><div class="dbl"></div>
 <div class="nav"><span class="${activeTab==0?"on":""}">Hafta</span><span class="${activeTab==1?"on":""}">Notlar</span><span class="${activeTab==2?"on":""}">★ Sabit</span></div>`;

/* ---- sabit notlar: 3 yer önerisi (üst kısım kesit) ---- */
function pinA(){return `<div class="screen pad">${head("Notlar","Salı, 27 Ekim 2026",1)}
  <div class="strip"><span class="caps k">Sabit</span>${["fin","gun","thy"].map(k=>`<div class="it">${th(k)}<div><b>${NOTES[k]}</b><i>${k=="fin"?"28 Eyl":k=="gun"?"14 Eyl":"12 Eki"}</i></div></div>`).join("")}</div>
  <div class="leadmini">${th("i1")}<div><span class="caps">Son not · 12 Eki</span><h2>THY Rapor</h2><p style="font-size:24px;font-style:italic">Senaryo ve senaryo aralıkları gereksiz.</p></div></div></div>`}
function pinB(){return `<div class="screen pad"><div class="meta caps"><span>Salı, 27 Ekim 2026</span><span>AYARLAR</span></div>
  <div class="ears" style="margin:14px 0 6px"><div class="ear">${th("fin")}<b>${NOTES.fin}</b></div><div class="ttl" style="margin:0;font-size:100px">Notlar</div><div class="ear">${th("gun")}<b>${NOTES.gun}</b></div></div>
  <div class="dbl"></div><div class="nav"><span>Hafta</span><span class="on">Notlar</span><span>★ Sabit</span></div>
  <div class="leadmini">${th("i1")}<div><span class="caps">Son not · 12 Eki</span><h2>THY Rapor</h2><p style="font-size:24px;font-style:italic">Senaryo ve senaryo aralıkları gereksiz.</p></div></div></div>`}
function pinC(){return `<div class="screen pad">${head("Sabit","3 not",2)}
  ${["fin","gun","thy"].map(k=>`<div style="display:grid;grid-template-columns:96px 1fr;gap:22px;padding:14px 0;border-bottom:1.5px solid var(--g2);align-items:center"><div class="thumb" style="height:128px;border:2px solid var(--ink)"><img src="${IMG[k]}"></div><div><b style="font-size:34px;letter-spacing:-.01em">${NOTES[k]}</b><div class="caps" style="margin-top:8px">${k=="fin"?"28 Eyl · 11:26":k=="gun"?"14 Eyl · 10:46":"12 Eki · 13:18"}</div></div></div>`).join("")}</div>`}

/* ---- hafta ---- */
const DAYS=[
 {w:"Pzt",n:26,past:1,ev:[],tk:[["Vekil hesapları: Can ile konuş",1],["Tradesoft sözleşmelerini güncelle",1],["Karar: Clemta aboneliği",0]]},
 {w:"Sal",n:27,today:1,ev:[["10:00","Rasyonet toplantısı"],["15:30","THY raporu gözden geçirme"]],tk:[["Windows için Rasyonet belgesi",0],["Mercury hesap açılışı",0],["Aragonite ana ekran",1]],carry:["Karar: Clemta aboneliği"]},
 {w:"Çar",n:28,ev:[["11:00","Innovance görüşmesi"]],tk:[["Pulse ekranını yap",0]]},
 {w:"Per",n:29,ev:[["14:00","Papara BIST haftalık"]],tk:[["Cronus'a güncelle, yatırımı koy",0]]},
 {w:"Cum",n:30,ev:[],tk:[["Haftalık değerlendirme",0]]},
 {w:"Cmt",n:31,ev:[],tk:[]},
 {w:"Paz",n:1,ev:[],tk:[]}];
const tk=(t,done,carry)=>`<div class="tk ${done?"done":""}"><i></i><span>${t}${carry?'<span class="c">↻ dünden</span>':""}</span></div>`;
const evh=(t,x)=>`<div class="ev"><span class="t">${t}</span><b>${x}</b></div>`;
function weekP(){
 const rows=DAYS.map(d=>{
  const carried=d.carry||[];
  const tasks=d.tk.filter(t=>!(d.today&&carried.includes(t[0]))).map(t=>tk(t[0],t[1],false)).join("")+(d.today?carried.map(c=>tk(c,0,true)).join(""):"");
  const body=(d.ev.map(e=>evh(e[0],e[1])).join("")+tasks)||'<div class="empty">Boş gün</div>';
  return `<div class="day ${d.today?"today":""}" ${d.past?'style="opacity:.78"':""}><div class="d"><div class="caps w">${d.w}</div><div class="n">${d.n}</div></div><div class="b">${d.today?body:body}</div></div>`}).join("");
 return `<div class="screen" style="padding:40px 56px 0">${head("Hafta","27 Ekim – 2 Kasım · 44. hafta",0)}
  <div class="strip" style="padding:10px 0"><span class="caps k" style="font-size:12px">Sabit</span>${["fin","gun","thy"].map(k=>`<div class="it">${th(k).replace('class="thumb"','class="thumb" style="width:44px;height:59px;border:1.5px solid #111;flex:none"')}<div><b style="font-size:18px">${NOTES[k]}</b></div></div>`).join("")}</div>
  ${rows}</div>`}
function weekL(){
 const cols=DAYS.map(d=>{
  const tasks=d.tk.filter(t=>!(d.today&&(d.carry||[]).includes(t[0]))).map(t=>tk(t[0],t[1],false)).join("")+(d.today?(d.carry||[]).map(c=>tk(c,0,true)).join(""):"");
  return `<div class="cl ${d.today?"today":""}"><div class="ch"><span class="n">${d.n}</span><span class="caps">${d.w}</span></div>${d.ev.map(e=>`<div class="ev"><span class="t">${e[0]}</span><b>${e[1]}</b></div>`).join("")}${tasks||(d.ev.length?"":'<div class="empty" style="font-size:18px">Boş</div>')}</div>`}).join("");
 return `<div class="screen" style="padding:28px 44px 0"><div class="meta caps"><span>27 Ekim – 2 Kasım · 44. hafta</span><span>AYARLAR</span></div>
  <div style="display:flex;align-items:flex-end;justify-content:space-between"><div class="ttl" style="margin:8px 0 6px;font-size:92px;text-align:left">Hafta</div>
   <div class="nav" style="border:0;padding:0 0 14px"><span class="on">Hafta</span><span>Notlar</span><span>★ Sabit</span></div></div><div class="dbl"></div>
  <div class="strip" style="padding:8px 0"><span class="caps k" style="font-size:12px">Sabit</span>${["fin","gun","thy"].map(k=>`<div class="it">${th(k).replace('class="thumb"','class="thumb" style="width:40px;height:54px;border:1.5px solid #111;flex:none"')}<div><b style="font-size:18px">${NOTES[k]}</b></div></div>`).join("")}</div>
  <div class="cols" style="height:610px">${cols}</div></div>`}

const frame=(html,o,label)=>`<div class="frame ${o}" data-o="${o}"><span class="lbl">${label}</span><div class="view">${html}</div></div>`;
const box=(a,b,c,d)=>`<div class="why">${[a,b,c,d].map(([x,y])=>`<div><b>${x}</b>${y}</div>`).join("")}</div>`;
document.getElementById("app").innerHTML=`
<h2 class="sec">1 · Sabit notlar nereye? <small>Üç yer, aynı kesitte (Notlar sayfasının üstü)</small></h2>
${box(["A · Şerit (önerim)","Sekmelerin altında tek satır. Her zaman görünür, hem Hafta hem Notlar sayfasında aynı yerde. En çok 3-4 not."],["B · Kulaklar","Gazete başlığının iki yanında 2 küçük kapak. Çok görsel ama yalnızca 2 not sığar."],["C · Sekme","Üstte ★ SABİT bölümü, tam liste. En az yer tutar, bir dokunuş daha ister."],["Karar","Hepsini birlikte de yapabiliriz: şerit her yerde, sekme tam liste için."])}
<div class="row">${frame(pinA(),"c","A · Şerit")}${frame(pinB(),"c","B · Kulaklar")}${frame(pinC(),"c","C · Sekme")}</div>
<h2 class="sec">2 · Haftalık görünüm <small>Uygulama artık buradan açılır, Notlar bir sekme olur</small></h2>
${box(["Ne gösterir","Pazartesi–Pazar. Bugün siyah dolgulu. Her günde önce takvim etkinlikleri (saatli), sonra görevler (kutucuklu)."],["Her gün güncellenir","Gece yarısı yeni güne geçer. Bitmeyen görevler bugüne «↻ dünden» etiketiyle taşınır, kaybolmaz."],["Görev eklemek","Alttaki + düğmesiyle yaz ya da kalemle yaz: el yazın metne çevrilir (notlardaki tanıma motoru). Kutucuğa dokununca biter."],["Takvim","Cihazın takvimi okunur. Şu an tabletinde takvim eşitlemesi kapalı, bir kez açman gerekir."])}
<div class="row">${frame(weekP(),"p","Dikey · 992 × 1323 dp")}${frame(weekL(),"l","Yatay · 1323 × 992 dp")}</div>`;
const ov=document.getElementById("ov");
document.addEventListener("click",e=>{const f=e.target.closest(".frame");
 if(ov.classList.contains("on")){ov.classList.remove("on");ov.innerHTML="";return}
 if(!f)return;const c=f.cloneNode(true);c.querySelector(".lbl").remove();
 const o=f.dataset.o,w=o=="l"?1323:992,h=o=="p"?1323:o=="l"?992:600;
 c.style.setProperty("--s",Math.min((innerWidth-80)/w,(innerHeight-80)/h));ov.innerHTML="";ov.appendChild(c);ov.classList.add("on")});
