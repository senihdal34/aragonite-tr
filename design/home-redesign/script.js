const IMG={gun:"{{GUN}}",fin:"{{FIN}}",thy:"{{THY}}",i1:"{{I1}}",i2:"{{I2}}"};
const N={
 thy:{t:"THY Rapor",d:"12 Eki 13:18",ex:"Senaryo ve senaryo aralıkları gereksiz.",img:IMG.thy},
 fin:{t:"Yeni Finans Ürün Planı",d:"28 Eyl 11:26",ex:"Rasyonetle anlaşma, rapor üretimi, custom dashboardlar, hazır değerleme modelleri…",img:IMG.fin},
 i1:{t:"İsimsiz Not",d:"16 Eyl 11:12",ex:"",img:IMG.i1},
 gun:{t:"Gün 1- 14 Eylül",d:"14 Eyl 10:46",ex:"Vekil hesapları, Rasyonet için belge, Cronus'a güncelleme…",img:IMG.gun},
 i2:{t:"İsimsiz Not",d:"13 Eyl 23:31",ex:"",img:IMG.i2}};
const T=[["papara",2],["laplace",1],["rasyonet",1]];
const th=(n)=>`<div class="thumb"><img src="${n.img}" alt=""></div>`;
const plus='<i class="ico plus"></i>',lines='<i class="ico lines"></i>',mag='<i class="ico mag"></i>';

/* ---------- A  Rams ---------- */
function A(land){
 const order=["thy","fin","i1","gun","i2"].slice(0,land?4:5);
 const rows=order.map((k,i)=>`<div class="li"><div class="n">${String(i+1).padStart(2,"0")}</div>${th(N[k])}<div class="t">${N[k].t}</div><div class="d">${N[k].d}</div></div>`).join("");
 const keys=T.map(([n,c])=>`<div class="key"><b>${c}</b><span>#${n}</span></div>`).join("")+`<div class="key add">${plus}</div>`;
 const ctl=`<div class="ctl"><div class="btn on"><i>${plus}</i>Yeni</div><div class="btn"><i>${lines}</i>Ayar</div></div>`;
 const title=`<div class="top"><div><div class="brand">aragonite</div><h1>Notlar</h1><div class="sub">5 not · 1 sabit · 3 konu</div></div>${land?"":ctl}</div><div class="rule"></div>`;
 const pin=`<h3><b>01</b>Sabitlenen</h3><div class="pin">${th(N.fin)}<div><h4>${N.fin.t}</h4><p>${N.fin.d} · 1 sayfa</p></div></div>`;
 const recent=`<h3><b>02</b>Son notlar</h3>${rows}`;
 const topics=`<h3><b>03</b>Konular</h3><div class="keys">${keys}</div>`;
 if(!land)return `<div class="screen A">${title}${pin}${recent}${topics}</div>`;
 return `<div class="screen A land"><div>${title}${topics}<div style="margin-top:40px">${ctl}</div></div><div>${pin}${recent}</div></div>`;
}

/* ---------- B  Apple ---------- */
function B(land){
 const card=(n)=>`<div class="card">${th(n)}<div class="t">${n.t}</div><div class="d">${n.d}</div></div>`;
 const chips=`<div class="chips"><div class="chip on">Tümü <small>5</small></div>${T.map(([n,c])=>`<div class="chip">#${n} <small>${c}</small></div>`).join("")}</div>`;
 const search=`<div class="search">${mag}<span>Notlarda ara</span></div>`;
 const icons=`<div class="bar"><div class="circ">${plus}</div><div class="circ">${lines}</div></div>`;
 const pill=`<div class="pill">${plus}Yeni Not</div>`;
 if(!land){
  return `<div class="screen B">${icons}<h1>Notlar</h1>${search}${chips}
   <h2>Sabitlenen <small>Tümü</small></h2><div class="car" style="gap:22px">${["fin","gun"].map(k=>`<div style="width:218px">${card(N[k])}</div>`).join("")}</div>
   <h2>Son Notlar</h2><div class="grid">${["thy","i1","i2"].map(k=>card(N[k])).join("")}</div>${pill}</div>`;
 }
 const item=(l,c,on)=>`<div class="it ${on?"on":""}">${l}<span>${c}</span></div>`;
 return `<div class="screen B land"><div class="side"><h1 style="font-size:46px;margin:0 8px 24px">Notlar</h1>
   ${item("Tüm notlar",5,1)}${item("Sabitlenen",1)}<h4>Konular</h4>${T.map(([n,c])=>item("#"+n,c)).join("")}</div>
  <div class="main"><div style="display:flex;gap:18px;align-items:center;margin-bottom:22px"><div class="search" style="flex:1">${mag}<span>Notlarda ara</span></div><div class="circ">${plus}</div><div class="circ">${lines}</div></div>
   <h2 style="margin-top:6px">Sabitlenen <small>1</small></h2><div class="car">${["fin"].map(k=>`<div style="width:190px">${card(N[k])}</div>`).join("")}</div>
   <h2>Son Notlar</h2><div class="grid">${["thy","i1","gun","i2"].map(k=>card(N[k])).join("")}</div>${pill}</div></div>`;
}

/* ---------- C  Gazete ---------- */
function C(land){
 const nav=`<div class="nav"><span class="on">Tümü</span>${T.map(([n])=>`<span>${n}</span>`).join("")}<span>+</span></div>`;
 const meta=`<div class="meta"><span>Salı, 27 Ekim 2026</span><span>5 not · 1 sabit</span></div>`;
 const lead=`<div class="lead">${th(N.thy)}<div><div class="kick">Son not · ${N.thy.d}</div><h2>${N.thy.t}</h2><p>${N.thy.ex}</p><div class="by">1 sayfa · el yazısı · metne çevrildi</div></div></div>`;
 const st=(k)=>`<div class="st">${th(N[k])}<h3>${N[k].t}</h3><div class="d">${N[k].d}</div></div>`;
 const box=`<div class="box"><div class="kick">Sabit</div>${th(N.fin).replace('class="thumb"','class="thumb" style="width:70px;height:94px;border:1.5px solid #111;flex:none"')}<div><h3>${N.fin.t}</h3><div class="kick" style="margin-top:6px">${N.fin.d}</div></div></div>`;
 const neu=`<div class="new"><b>＋ Yeni not yaz</b></div>`;
 if(!land)return `<div class="screen C">${meta}<h1>Notlar</h1><div class="dbl"></div>${nav}${lead}<div class="cols">${["fin","i1","gun"].map(st).join("")}</div>${box}${neu}</div>`;
 return `<div class="screen C land"><div class="head">${meta}<h1>Notlar</h1><div class="dbl"></div>${nav}</div>
  <div>${lead}<div class="cols">${["fin","i1"].map(st).join("")}</div></div>
  <div class="rail"><h4>Sabitlenen</h4>${box.replace('class="box"','class="box" style="border:0;padding:0 0 14px"')}<h4 style="margin-top:22px">Konular</h4>${T.map(([n,c])=>`<div class="tp">#${n}<span>${c}</span></div>`).join("")}<div class="new" style="margin-top:34px"><b style="padding:14px 26px">＋ Yeni not</b></div></div></div>`;
}

const OPTS=[
 {name:"A · Rams / Braun",sub:"“Az ama daha iyi”",fn:A,why:[
  ["İlke","Her öğe bir iş yapar. Süs yok: çizgi, sıra numarası ve büyük sayı var."],
  ["Referans","Braun ET 66 hesap makinesi, Vitsoe 606 raf sistemi."],
  ["E-ink","Yalnızca siyah ve beyaz çizgi, gri yok. En net basan ve en az yenileme yapan yön."],
  ["Uygulama","Orta: liste satırı, tuş ızgarası, dikey ve yatay iki düzen."]]},
 {name:"B · Apple",sub:"Büyük başlık, yuvarlak kartlar",fn:B,why:[
  ["İlke","Bol boşluk, büyük küçük resim, tek bakışta anlaşılan hiyerarşi."],
  ["Referans","Apple Notlar galeri görünümü, iPadOS kenar çubuğu, Apple Books."],
  ["E-ink","Gri alan fazla: arama kutusu ve düğmeler noktalı basabilir, istersek çizgiye çeviririm."],
  ["Uygulama","Büyük: kenar çubuğu, ızgara, konu çipleri. Arama için metin dizini de gerekir."]]},
 {name:"C · Gazete",sub:"Bir manşet, altında haberler, kenarda dizin",fn:C,why:[
  ["İlke","En son not manşet olur, yazısı alt başlık. Notlarına bir gazete gibi bakarsın."],
  ["Referans","Financial Times, Monocle, e-kitap okuyucuların serif sayfa düzeni."],
  ["E-ink","Serif harf ve ince çizgi e-ink'te en iyi basılan şeylerdir. Gri yok."],
  ["Uygulama","Orta: manşet kartı ve sütunlar. Alt başlık için dışa aktarılan metin kullanılır."]]}];
const frame=(html,o,label)=>`<div class="frame ${o}" data-o="${o}"><span class="lbl">${label}</span><div class="view">${html}</div></div>`;
document.getElementById("app").innerHTML=OPTS.map(o=>`<section class="opt"><h2>${o.name} <small>${o.sub}</small></h2>
 <div class="why">${o.why.map(([a,b])=>`<div><b>${a}</b>${b}</div>`).join("")}</div>
 <div class="row">${frame(o.fn(false),"p","Dikey · 992 × 1323 dp")}${frame(o.fn(true),"l","Yatay · 1323 × 992 dp")}</div></section>`).join("");
const ov=document.getElementById("ov");
document.addEventListener("click",e=>{
 const f=e.target.closest(".frame");
 if(ov.classList.contains("on")){ov.classList.remove("on");ov.innerHTML="";return}
 if(!f)return;
 const c=f.cloneNode(true);c.querySelector(".lbl").remove();
 const w=f.dataset.o==="p"?992:1323,h=f.dataset.o==="p"?1323:992;
 const s=Math.min((innerWidth-80)/w,(innerHeight-80)/h);
 c.style.setProperty("--s",s);ov.innerHTML="";ov.appendChild(c);ov.classList.add("on");
});
