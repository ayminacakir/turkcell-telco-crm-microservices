/* Müşteri portalı — GERÇEK veri + tutarlı satın-alma katmanı.
   Kota tutarlılığı için kota durumu localStorage'da tutulur (state.quota):
   her paket alımında yeni pakete göre güncellenir, çubuklar HER ZAMAN buna göre
   çizilir; ayrıca gerçek usage-service'e de PUT edilir (best-effort).
   Faturalar, dijital ürünler, geçmiş, talepler, profil, kullanım sorgusu gerçek. */
(function(){
  const st = telcoxGetState();
  if(!st || !st.customerId) return;   // ops / demo-offline

  const CUR = { sub:null, tariff:null };
  let LAST_INVOICES = [];
  let TARIFFS = [];   // öneri + switcher için katalog cache
  const setTxt = (id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };
  const fmtMb = (v)=> v>=1024 ? (v/1024).toFixed(v%1024?1:0)+' GB' : v+' MB';
  const fmtGb = (v)=> (v>=1024? (v/1024).toFixed(1)+' TB' : v+' GB');
  const TRTYPE = { VOICE:'Ses', SMS:'SMS', DATA:'Data' };
  const TICKET_STATUS_TR = { OPEN:'Açık', IN_PROGRESS:'İşlemde', RESOLVED:'Çözüldü', CLOSED:'Kapandı' };
  const MONTHS = ['Ocak','Şubat','Mart','Nisan','Mayıs','Haziran','Temmuz','Ağustos','Eylül','Ekim','Kasım','Aralık'];
  const monthLabel = d => MONTHS[d.getMonth()]+' '+d.getFullYear();
  const rid = ()=> Math.random().toString(36).slice(2,10);
  const daysBetween = (a,b)=> Math.max(0, Math.round((b-a)/86400000));
  const simUsed = (total)=> total>0 ? Math.round(total*(0.10 + Math.random()*0.22)) : 0; // %10-32 kullanılmış

  const DIGITAL = {
    'Netflix':{cat:'Dijital Servis',kind:'data',totalGb:25,color:'#B9090B',icon:'N'},
    'Netflix Standart':{cat:'Dijital Servis',kind:'data',totalGb:25,color:'#B9090B',icon:'N'},
    'Disney+':{cat:'Dijital Servis',kind:'time',color:'#1B3B8C',icon:'D+'},
    'Spotify Premium':{cat:'Dijital Servis',kind:'time',color:'#1DB954',icon:'S'},
    'YouTube Premium':{cat:'Dijital Servis',kind:'time',color:'#FF0000',icon:'YT'},
    'Xbox Game Pass':{cat:'Dijital Servis',kind:'time',color:'#107C10',icon:'X'},
    'Office 365':{cat:'Dijital Servis',kind:'time',color:'#D83B01',icon:'O'},
    'Google One 200GB':{cat:'Bulut Depolama',kind:'storage',totalGb:200,color:'#4285F4',icon:'G1'},
    'Cloud 100GB':{cat:'Bulut Depolama',kind:'storage',totalGb:100,color:'#0057FF',icon:'C'},
    'Cloud 1TB':{cat:'Bulut Depolama',kind:'storage',totalGb:1024,color:'#0041BF',icon:'C'},
    '5G Öncelikli Erişim':{cat:'Ek Servis',kind:'flag',color:'#7C3AED',icon:'5G'},
    'Sosyal Medya Sınırsız':{cat:'Ek Servis',kind:'flag',color:'#E1306C',icon:'∞'},
    'Yurt Dışı 5GB':{cat:'Ek Kota',kind:'data',totalGb:5,color:'#0EA5E9',icon:'✈'},
    'eSIM':{cat:'Ek Servis',kind:'flag',color:'#64748B',icon:'e'},
  };
  const CAT_ORDER = ['Dijital Servis','Bulut Depolama','Ek Kota','Ev İnterneti','Ek Servis','Diğer'];

  /* ============================================================ KOTA (localStorage) */
  const readQuota = ()=> telcoxGetState().quota || null;
  const writeQuota = (q)=> telcoxSetState({quota:q});
  function seedQuotaFrom(real){
    const T = CUR.tariff;
    const mt = (real&&real.minutesTotal) ?? (T&&T.minutesIncluded) ?? (real&&real.minutesRemaining) ?? 1000;
    const stt = (real&&real.smsTotal) ?? (T&&T.smsIncluded) ?? (real&&real.smsRemaining) ?? 500;
    const bt = (real&&real.mbTotal) ?? (T&&T.dataMbIncluded) ?? (real&&real.mbRemaining) ?? 40960;
    const now = new Date(), end = new Date(now); end.setMonth(end.getMonth()+1);
    return {
      minutesTotal:mt, minutesRemaining: real? real.minutesRemaining : mt-simUsed(mt),
      smsTotal:stt, smsRemaining: real? real.smsRemaining : stt-simUsed(stt),
      mbTotal:bt, mbRemaining: real? real.mbRemaining : bt-simUsed(bt),
      planName:(T&&T.name)||st.activePlan||'Tarife', startDate:now.toISOString(), endDate:end.toISOString(),
    };
  }
  function paintQuotaAll(){
    const q = readQuota(); if(!q) return;
    ['#view-home','#view-usage'].forEach(scope=>{
      paintBar(scope,0,q.minutesRemaining,q.minutesTotal, v=>v+' dk');
      paintBar(scope,1,q.smsRemaining,q.smsTotal, v=>v+' SMS');
      paintBar(scope,2,q.mbRemaining,q.mbTotal, fmtMb);
    });
  }
  function paintBar(scope, idx, rem, total, unit){
    const cards = document.querySelectorAll(scope+' .quota-card');
    const c = cards[idx]; if(!c || rem==null || total==null) return;
    const usedPct = total>0 ? Math.max(0, Math.min(100, Math.round(100*(1-rem/total)))) : 0;
    const val = c.querySelector('.value'); if(val) val.textContent = `${unit(Math.max(0,rem))} / ${unit(total)}`;
    const fill = c.querySelector('.bar-fill'); if(fill) fill.style.width = usedPct+'%';
    c.classList.toggle('warn', usedPct>=80);
  }

  /* ============================================================== ANA SAYFA */
  async function home(){
    const subs = await api(`/api/v1/subscriptions/customers/${st.customerId}`);
    const sub = asList(subs.data)[0] || (subs.data && subs.data.id ? subs.data : null);
    if(sub){
      CUR.sub = sub;
      const t = await api(`/api/v1/tariffs/${sub.tariffCode}`);
      if(t.ok) CUR.tariff = t.data;
      // kota: yerel yoksa gerçekten/tarifeden seed'le (etiketten ÖNCE, tutarlı isim için)
      if(!readQuota()){
        const q = await api(`/api/v1/usage/subscriptions/${sub.id}/quota`);
        writeQuota(seedQuotaFrom(q.ok? q.data : null));
      }
      const state = telcoxGetState();
      setTxt('active-plan-name', (readQuota()&&readQuota().planName) || state.activePlan || (t.ok? t.data.name : sub.tariffCode));
      const meta = document.querySelector('#view-home .card .meta');
      if(meta && sub.status) meta.textContent = (sub.status==='ACTIVE'?'Postpaid · Aktif':sub.status) + ' · ' + (sub.msisdn||'');
      paintQuotaAll();
      checkLowUsage();
    }
    const inv = await api(`/api/v1/invoices/customer/${st.customerId}`);
    if(inv.ok){ LAST_INVOICES = asList(inv.data).sort((a,b)=> new Date(b.periodStart)-new Date(a.periodStart)); refreshInvoiceViews(); }
    const tk = await api(`/api/v1/tickets?customerId=${st.customerId}&size=50`);
    if(tk.ok){
      const rows = asList(tk.data);
      const open = rows.filter(t=>t.status!=='RESOLVED'&&t.status!=='CLOSED').length;
      setTxt('home-ticket-count', open+' talep'); setTxt('home-ticket-meta', open?'Yanıt bekleniyor':'Açık talebiniz yok');
      paintTickets(rows);
    }
    renderDigital(); renderHistory();
  }

  /* --------------------------------------- DÜŞÜK KOTA UYARISI + PAKET ÖNERİSİ */
  async function ensureTariffs(){
    if(TARIFFS.length) return TARIFFS;
    const r = await api('/api/v1/tariffs?status=ACTIVE&size=50');
    TARIFFS = asList(r.data); return TARIFFS;
  }
  function suggestFor(metric){ // 'minutes' | 'sms' | 'mb'
    const q = readQuota(); if(!q || !TARIFFS.length) return null;
    const field = metric==='minutes'?'minutesIncluded':metric==='sms'?'smsIncluded':'dataMbIncluded';
    const curTotal = metric==='minutes'?q.minutesTotal:metric==='sms'?q.smsTotal:q.mbTotal;
    const bigger = TARIFFS.filter(t=>(t[field]||0)>curTotal).sort((a,b)=>a.monthlyFee-b.monthlyFee);
    return bigger[0] || TARIFFS.slice().sort((a,b)=>(b[field]||0)-(a[field]||0))[0] || null;
  }
  async function checkLowUsage(){
    const q = readQuota(); if(!q) return;
    await ensureTariffs();
    const low = [];
    if(q.minutesTotal>0 && q.minutesRemaining/q.minutesTotal < 0.2) low.push({m:'minutes',label:'Dakika',rem:q.minutesRemaining+' dk'});
    if(q.smsTotal>0 && q.smsRemaining/q.smsTotal < 0.2) low.push({m:'sms',label:'SMS',rem:q.smsRemaining+' SMS'});
    if(q.mbTotal>0 && q.mbRemaining/q.mbTotal < 0.2) low.push({m:'mb',label:'İnternet',rem:fmtMb(Math.max(0,q.mbRemaining))});
    renderLowBanner(low);
  }
  function renderLowBanner(low){
    const view = document.getElementById('view-home'); if(!view) return;
    let host = document.getElementById('low-usage-banner');
    if(!low.length){ if(host) host.remove(); return; }
    if(!host){ host = document.createElement('div'); host.id='low-usage-banner';
      const topbar = view.querySelector('.topbar');
      if(topbar && topbar.parentNode===view && topbar.nextSibling) view.insertBefore(host, topbar.nextSibling); else view.insertBefore(host, view.firstChild);
    }
    const parts = low.map(l=>`<b>${l.label}</b> kotan %20 altında (${l.rem} kaldı)`).join(', ');
    const sug = suggestFor(low[0].m);
    const sugTxt = sug ? ` Kullanım alışkanlığına göre <b>${esc(sug.name)}</b> (${sug.minutesIncluded||0} dk · ${sug.smsIncluded||0} SMS · ${fmtMb(sug.dataMbIncluded||0)}, ${fmtTL(sug.monthlyFee)}/ay) daha avantajlı.` : '';
    host.innerHTML = `<div class="ai-card" style="border-color:oklch(80% 0.12 40);background:linear-gradient(120deg,#FFF4DB,#FFE8E0);">
      <div class="ai-ic" style="background:var(--warn);">!</div>
      <div>${parts}.${sugTxt} <a href="#" id="low-suggest-go" style="font-weight:700;">Paketi incele →</a></div></div>`;
    const go = document.getElementById('low-suggest-go');
    if(go) go.addEventListener('click', e=>{ e.preventDefault(); navTo('builder'); });
  }

  /* ------------------------------- DİJİTAL ÜRÜNLER & GEÇMİŞ (ana sayfa) */
  function homeHost(id, title, sub){
    const view = document.getElementById('view-home'); if(!view) return null;
    let sec = document.getElementById(id);
    if(!sec){ sec = document.createElement('div'); sec.id = id;
      sec.innerHTML = `<div class="section-title" style="margin-top:30px;">${title}${sub?`<span style="font-weight:400;color:var(--ink-soft);font-size:13px;">${sub}</span>`:''}</div><div class="${id}-body"></div>`;
      view.appendChild(sec); }
    return sec.querySelector('.'+id+'-body');
  }
  function activeDigitals(){
    const now = Date.now();
    return (telcoxGetState().purchases||[]).filter(p=> p.kind && p.kind!=='tariff' && p.kind!=='tariff-old' && (!p.endDate || new Date(p.endDate).getTime()>now));
  }
  function renderDigital(){
    const body = homeHost('home-digital','Dijital Ürünlerim & Ek Paketlerim'); if(!body) return;
    const items = activeDigitals();
    if(!items.length){ body.innerHTML = '<div style="color:var(--ink-soft);font-size:13.5px;">Henüz dijital servis / ek paket almadın. <a href="#" data-view="market" style="font-weight:700;">Mağaza\'ya git →</a></div>'; wireNav(body); return; }
    const byCat = {}; items.forEach(p=>{ (byCat[p.cat]=byCat[p.cat]||[]).push(p); });
    const now = Date.now(); let html = '';
    CAT_ORDER.filter(c=>byCat[c]).forEach(cat=>{
      html += `<div style="font-size:12.5px;font-weight:700;color:var(--ink-soft);margin:14px 0 8px;text-transform:uppercase;letter-spacing:.4px;">${esc(cat)}</div><div class="market-grid">`;
      byCat[cat].forEach(p=>{
        let bar='', foot='';
        if(p.kind==='time'){
          const s=new Date(p.startDate), e=new Date(p.endDate); const total=daysBetween(s,e)||30, left=daysBetween(new Date(now),e);
          const pct=Math.max(0,Math.min(100,Math.round(100*(total-left)/total)));
          bar=`<div class="bar-track" style="margin:8px 0 4px;"><div class="bar-fill" style="width:${pct}%"></div></div>`;
          foot=`<div style="font-size:12px;color:var(--ink-soft);">${fmtDate(p.startDate)} – ${fmtDate(p.endDate)} · <b style="color:var(--ink);">${left} gün kaldı</b></div>`;
        } else if(p.kind==='data'||p.kind==='storage'){
          const tot=p.totalGb||0, used=p.usedGb!=null?p.usedGb:0; const pct=tot>0?Math.max(0,Math.min(100,Math.round(100*used/tot))):0;
          bar=`<div class="bar-track" style="margin:8px 0 4px;"><div class="bar-fill" style="width:${pct}%"></div></div>`;
          foot=`<div style="font-size:12px;color:var(--ink-soft);">${p.kind==='storage'?'Kullanılan':'İzlenen'}: <b style="color:var(--ink);">${used} / ${fmtGb(tot)}</b></div>`;
        } else {
          foot=`<div style="font-size:12px;color:var(--ink-soft);">Aktif · ${fmtDate(p.startDate)}${p.endDate?' – '+fmtDate(p.endDate):''}</div>`;
        }
        html += `<div class="m-card"><div style="display:flex;align-items:center;gap:10px;">
          <div class="m-logo" style="background:${p.color||'#0057FF'};width:38px;height:38px;font-size:13px;">${esc(p.icon||p.name[0])}</div>
          <div><div class="m-name" style="margin:0;">${esc(p.name)}</div>
            <div style="font-size:11.5px;color:var(--ink-soft);">${p.monthlyFee?fmtTL(p.monthlyFee)+'/ay':'Ücretsiz'}</div></div>
        </div>${bar}${foot}</div>`;
      });
      html += '</div>';
    });
    body.innerHTML = html;
  }
  function renderHistory(){
    const body = homeHost('home-history','Satın Alma Geçmişi'); if(!body) return;
    const items = (telcoxGetState().purchases||[]).slice().sort((a,b)=> new Date(b.boughtAt)-new Date(a.boughtAt));
    if(!items.length){ body.innerHTML = '<div style="color:var(--ink-soft);font-size:13.5px;">Henüz satın alma yok.</div>'; return; }
    body.innerHTML = '<table><tr><th>Tarih</th><th>Ürün</th><th>Kategori</th><th>Aylık</th><th>Faturalanır</th></tr>' +
      items.map(p=>`<tr><td>${fmtDate(p.boughtAt)}</td><td>${esc(p.name)}</td>
        <td><span class="status-pill" style="background:var(--primary-soft);color:var(--primary-dark);">${esc(p.catTag||p.cat||'—')}</span></td>
        <td>${p.monthlyFee?fmtTL(p.monthlyFee):'—'}</td>
        <td style="color:var(--ink-soft);font-size:13px;">${esc(p.billingMonth||'—')}</td></tr>`).join('') + '</table>';
  }

  /* --------------------------------------------- FATURALAR (gerçek + biriken kalemler) */
  // Bu dönem eklenen kalemler (paket/dijital) — gerçek bekleyen faturaya EKLENİR
  function chargeLines(){
    return (telcoxGetState().purchases||[]).map(p=>({desc:p.name, amount:+p.monthlyFee||0})).filter(l=>l.amount>0);
  }
  function currentInvoice(){
    const base = LAST_INVOICES.find(i=>i.status==='PENDING') || LAST_INVOICES[0] || null;
    const now = new Date();
    const charges = chargeLines();
    const due = base? base.dueDate : new Date(now.getFullYear(),now.getMonth()+1,28).toISOString().slice(0,10);
    // Ek kalem yoksa: gerçek faturanın kendi değerleri (vergi dahil) aynen gösterilir
    if(!charges.length){
      const st0 = base? +base.subTotal : 0, tax0 = base? +base.tax : 0, oiv0 = +(st0*0.10).toFixed(2);
      return { base, label: monthLabel(now),
        lines: base? [{desc:'Aylık tarife & servisler ('+fmtDate(base.periodStart)+')', amount:st0}] : [],
        subTotal: st0, oiv: oiv0, kdv: +(tax0-oiv0>0? (tax0-oiv0):tax0).toFixed(2), tax: tax0,
        grandTotal: base? +base.grandTotal : 0, dueDate: due };
    }
    const lines = [];
    if(base) lines.push({desc:'Aylık tarife & servisler ('+fmtDate(base.periodStart)+')', amount:+base.subTotal});
    charges.forEach(l=>lines.push(l));
    const subTotal = +lines.reduce((s,l)=>s+l.amount,0).toFixed(2);
    const oiv = +(subTotal*0.10).toFixed(2);
    const kdv = +((subTotal+oiv)*0.20).toFixed(2);
    const grandTotal = +(subTotal+oiv+kdv).toFixed(2);
    return { base, label: monthLabel(now), lines, subTotal, oiv, kdv, tax:+(oiv+kdv).toFixed(2), grandTotal, dueDate:due };
  }
  function refreshInvoiceViews(){
    const ci = currentInvoice();
    setTxt('home-invoice-amount', fmtTL(ci.grandTotal));
    const extra = chargeLines().length;
    setTxt('home-invoice-meta', extra? (extra+' ek kalem dahil · son ödeme '+fmtDate(ci.dueDate)) : ('Son ödeme: '+fmtDate(ci.dueDate)));
    paintInvoices();
  }
  function paintInvoices(){
    const view = document.getElementById('view-invoices'); if(!view) return;
    const tbl = view.querySelector('table'); if(!tbl) return;
    const ci = currentInvoice();
    const past = LAST_INVOICES.filter(i=> !ci.base || i.id!==ci.base.id);
    const pill = s => s==='PAID'?'<span class="status-pill paid">Ödendi</span>':s==='PENDING'?'<span class="status-pill pending">Bekliyor</span>':`<span class="status-pill pending">${esc(s)}</span>`;
    let html = '<tr><th>Dönem</th><th>Tutar</th><th>Son Ödeme</th><th>Durum</th><th></th></tr>';
    html += `<tr style="background:var(--primary-soft);">
        <td><b>${ci.label}</b> <span style="font-size:11px;color:var(--ink-soft);">(güncel${chargeLines().length?' · '+chargeLines().length+' ek kalem':''})</span></td>
        <td><b>${fmtTL(ci.grandTotal)}</b></td><td>${fmtDate(ci.dueDate)}</td><td>${pill('PENDING')}</td>
        <td style="text-align:right;"><button class="btn ghost inv-current">Detay</button></td></tr>`;
    html += past.map(i=>`<tr>
        <td>${fmtDate(i.periodStart)} – ${fmtDate(i.periodEnd)}</td><td>${fmtTL(i.grandTotal)}</td>
        <td>${fmtDate(i.dueDate)}</td><td>${pill(i.status)}</td>
        <td style="text-align:right;white-space:nowrap;">
          <button class="btn ghost inv-detail" data-id="${i.id}">Detay</button>
          <button class="btn ghost inv-pdf" data-id="${i.id}">PDF</button></td></tr>`).join('');
    tbl.innerHTML = html;
    paintInvoiceChart(view, past, ci);
  }
  function paintInvoiceChart(view, past, ci){
    let card = document.getElementById('inv-chart');
    if(!card){ card=document.createElement('div'); card.className='chart-card'; card.id='inv-chart';
      const tbl0=view.querySelector('table'); if(tbl0 && tbl0.parentNode===view) view.insertBefore(card, tbl0); else view.insertBefore(card, view.firstChild); }
    const series = past.slice().reverse().map(i=>({label:MONTHS[new Date(i.periodStart).getMonth()].slice(0,3), val:+i.grandTotal, cur:false}));
    series.push({label:MONTHS[new Date().getMonth()].slice(0,3), val:+ci.grandTotal, cur:true});
    const max = Math.max.apply(null, series.map(s=>s.val))||1;
    card.innerHTML = `<h4>Aylık Ödeme Özeti (₺)</h4>
      <div class="bars" style="height:130px;">${series.map(s=>`<div class="b" title="${s.label}: ${fmtTL(s.val)}" style="height:${Math.round(s.val/max*100)}%;${s.cur?'background:var(--accent);':''}"></div>`).join('')}</div>
      <div class="bar-x">${series.map(s=>`<span>${s.label}${s.cur?' •':''}</span>`).join('')}</div>
      <div style="font-size:12px;color:var(--ink-soft);margin-top:8px;">• güncel ay · En yüksek: <b style="color:var(--ink);">${fmtTL(max)}</b></div>`;
  }

  function paintTickets(rows){
    const list = document.getElementById('ticket-list'); if(!list) return;
    if(!rows.length){ list.innerHTML = '<div class="ticket"><div><div class="t-title">Henüz talebin yok</div><div class="t-meta">Yeni Talep ile ilk kaydını oluşturabilirsin.</div></div></div>'; return; }
    const pill = s => (s==='RESOLVED'||s==='CLOSED')?'<span class="status-pill ok">'+esc(TICKET_STATUS_TR[s]||s)+'</span>':'<span class="status-pill pending">'+esc(TICKET_STATUS_TR[s]||s)+'</span>';
    list.innerHTML = rows.slice().sort((a,b)=> new Date(b.createdAt)-new Date(a.createdAt)).map(t=>`<div class="ticket"><div>
      <div class="t-title">${esc(t.category)} talebi</div>
      <div class="t-meta">#${t.id.slice(0,8)} · ${esc(t.priority)} · ${fmtDT(t.createdAt)}${t.assignedTeam?' · '+esc(t.assignedTeam):''}</div>
      </div>${pill(t.status)}</div>`).join('');
  }

  /* =============================================== PAYLAŞIMLI SATIN-ALMA API'si */
  window.telcoxReal = {
    getSub: ()=> CUR.sub,
    getQuota: ()=> readQuota(),
    // Paket/esnek: kotayı localStorage'da yeni pakete göre günceller (çubuklar hemen değişir) + gerçek PUT
    async setQuota(minutes, sms, mb, label, monthlyFee){
      const now=new Date(), end=new Date(now); end.setMonth(end.getMonth()+1);
      const q = { minutesTotal:minutes|0, minutesRemaining:(minutes|0)-simUsed(minutes|0),
                  smsTotal:sms|0, smsRemaining:(sms|0)-simUsed(sms|0),
                  mbTotal:mb|0, mbRemaining:(mb|0)-simUsed(mb|0),
                  planName: label||'Tarife', startDate:now.toISOString(), endDate:end.toISOString() };
      writeQuota(q);
      const patch={}; if(label)patch.activePlan=label; if(monthlyFee!=null)patch.activeMonthlyFee=monthlyFee;
      if(Object.keys(patch).length) telcoxSetState(patch);
      if(label) setTxt('active-plan-name', label);
      paintQuotaAll(); checkLowUsage();
      if(CUR.sub) api(`/api/v1/usage/subscriptions/${CUR.sub.id}/quota`, {method:'PUT', body:JSON.stringify({minutes:minutes|0,sms:sms|0,mb:mb|0})});
      return true;
    },
    recordPurchase(item){
      const meta = DIGITAL[item.name] || {cat:item.category||'Diğer', kind:item.kind||'flag', color:'#0057FF', icon:(item.name||'?')[0]};
      const now=new Date(), end=new Date(now); end.setMonth(end.getMonth()+1); const nb=new Date(now.getFullYear(),now.getMonth()+1,1);
      const p = Object.assign({ id:rid(), name:item.name, monthlyFee:item.monthlyFee!=null?+item.monthlyFee:0,
        cat:meta.cat, catTag:item.catTag||meta.cat, kind:meta.kind, color:meta.color, icon:meta.icon,
        boughtAt:now.toISOString(), startDate:now.toISOString(), endDate:end.toISOString(),
        totalGb:meta.totalGb||null, usedGb:meta.totalGb? +(Math.random()*meta.totalGb*0.35).toFixed(1):null,
        billingMonth:monthLabel(nb) }, item.extra||{});
      const s=telcoxGetState(); telcoxSetState({purchases:[...(s.purchases||[]), p]});
      renderDigital(); renderHistory(); refreshInvoiceViews();
    },
    // Tarife değişiminde önceki paketin ücretini de faturaya ekler
    recordCharge(name, monthlyFee, catTag){
      const now=new Date(), nb=new Date(now.getFullYear(),now.getMonth()+1,1);
      const s=telcoxGetState();
      telcoxSetState({purchases:[...(s.purchases||[]), {id:rid(), name, monthlyFee:+monthlyFee||0, kind:'tariff-old', cat:'Tarife', catTag:catTag||'Önceki Tarife', boughtAt:now.toISOString(), billingMonth:monthLabel(nb)}]});
      renderHistory(); refreshInvoiceViews();
    },
    refresh: ()=> home(),
  };

  /* -------------------------------------------------- PAKET DEĞİŞTİR (gerçek) */
  async function buildTariffSwitcher(){
    const view = document.getElementById('view-builder'); if(!view) return;
    let host = document.getElementById('real-tariff-switch');
    if(!host){ host=document.createElement('div'); host.id='real-tariff-switch'; host.style.marginBottom='10px';
      const ft=view.querySelector('.topbar'); if(ft&&ft.nextSibling) view.insertBefore(host, ft.nextSibling); else view.insertBefore(host, view.firstChild); }
    if(!document.getElementById('flex-divider')){
      const fs=view.querySelector('.slider-row');
      if(fs){ const d=document.createElement('div'); d.id='flex-divider';
        d.innerHTML='<div class="section-title" style="margin-top:34px;">Avantajlı Paketini Kendin Oluştur<span style="font-weight:400;color:var(--ink-soft);font-size:13px;">GB / dakika / SMS\'i kaydır, dijital ekleri seç — anında fiyatlanır</span></div>';
        fs.parentNode.insertBefore(d, fs); }
    }
    host.innerHTML = '<div class="section-title">Hazır Paketler — Tarifeni Değiştir</div>' +
      '<p style="color:var(--ink-soft);font-size:13.5px;margin:-6px 0 16px;">Bir pakete geçtiğinde aboneliğin ve kotan güncellenir; mevcut paketin son dönem ücreti de faturana yansır.</p>' +
      '<div id="rt-cards" class="market-grid"><div style="color:var(--ink-soft);font-size:13px;">Paketler yükleniyor…</div></div>';
    await ensureTariffs();
    const cards = document.getElementById('rt-cards');
    if(!TARIFFS.length){ cards.innerHTML='<div style="color:var(--ink-soft);font-size:13px;">Paket kataloğu yüklenemedi.</div>'; return; }
    const curCode = CUR.sub ? CUR.sub.tariffCode : null;
    cards.innerHTML = TARIFFS.map(t=>{
      const isCur = t.code===curCode;
      return `<div class="m-card${isCur?' owned':''}">
        <div class="m-logo" style="background:${isCur?'var(--good)':'var(--primary)'}">${esc((t.code||'?').slice(-3))}</div>
        <div class="m-name">${esc(t.name)}</div>
        <div class="m-desc">${t.minutesIncluded||0} dk · ${t.smsIncluded||0} SMS · ${fmtMb(t.dataMbIncluded||0)}${t.type?' · '+esc(t.type):''}</div>
        <div class="m-foot"><span class="m-price">${fmtTL(t.monthlyFee)}/ay</span>
          <button class="btn ${isCur?'ghost':'primary'} rt-go" data-code="${esc(t.code)}" data-name="${esc(t.name)}" data-fee="${t.monthlyFee}" data-min="${t.minutesIncluded||0}" data-sms="${t.smsIncluded||0}" data-mb="${t.dataMbIncluded||0}" ${isCur?'disabled':''}>${isCur?'Mevcut paketin':'Bu pakete geç'}</button>
        </div></div>`;
    }).join('');
    cards.querySelectorAll('.rt-go').forEach(b=> b.addEventListener('click', ()=> switchTariff(b.dataset)));
  }
  async function switchTariff(d){
    if(!CUR.sub){ toast('Aktif abonelik bulunamadı'); return; }
    if(CUR.sub.status && CUR.sub.status!=='ACTIVE'){ toast('Sadece aktif abonelikte paket değiştirilebilir'); return; }
    // Aktif paket uyarısı: kaç gün kaldı
    const q = readQuota(); let daysLeft = 30;
    if(q && q.endDate) daysLeft = daysBetween(new Date(), new Date(q.endDate));
    const warn = `Aktif paketin (${(q&&q.planName)||CUR.tariff&&CUR.tariff.name||'mevcut'}) devam ediyor — bitmesine ${daysLeft} gün kaldı.\n"${d.name}" paketine şimdi geçersen mevcut paketin son dönem ücreti faturana yansır.\n\nDevam edilsin mi?`;
    if(!confirm(warn)) return;
    // önceki paketin ücretini faturaya ekle
    const prevName = (CUR.tariff&&CUR.tariff.name) || (q&&q.planName) || 'Önceki paket';
    const prevFee = CUR.tariff? +CUR.tariff.monthlyFee : (telcoxGetState().activeMonthlyFee||0);
    if(prevFee>0) telcoxReal.recordCharge(prevName+' (önceki paket - son dönem)', prevFee, 'Önceki Tarife');
    const p = await api(`/api/v1/subscriptions/${CUR.sub.id}/tariff`, {method:'PATCH', body:JSON.stringify({tariffCode:d.code})});
    if(!p.ok){ toast('Tarife değiştirilemedi ('+p.status+')'); return; }
    CUR.sub.tariffCode = d.code;
    const t = await api(`/api/v1/tariffs/${d.code}`); if(t.ok) CUR.tariff=t.data;
    await telcoxReal.setQuota(+d.min, +d.sms, +d.mb, d.name, +d.fee);
    // yeni paketi geçmişe/faturaya ekle
    const now=new Date(), nb=new Date(now.getFullYear(),now.getMonth()+1,1); const s=telcoxGetState();
    telcoxSetState({purchases:[...(s.purchases||[]), {id:rid(), name:d.name+' (tarife)', monthlyFee:+d.fee||0, kind:'tariff', cat:'Tarife', catTag:'Tarife', boughtAt:now.toISOString(), billingMonth:monthLabel(nb)}]});
    renderHistory(); refreshInvoiceViews();
    toast('Paket değişti — kotan güncellendi: '+d.name);
    await buildTariffSwitcher(); navTo('home');
  }

  /* ------------------------------------------------ KULLANIMIM (tarih aralığı) */
  function buildUsageSearch(){
    const view = document.getElementById('view-usage'); if(!view || document.getElementById('usage-search')) return;
    const today=new Date(), past=new Date(); past.setDate(past.getDate()-30); const iso=d=>d.toISOString().slice(0,10);
    const box=document.createElement('div'); box.className='chart-card'; box.id='usage-search'; box.style.marginTop='18px';
    box.innerHTML=`<h4 style="margin-bottom:12px;">Kullanım Geçmişi Sorgula (gerçek kayıtlar)</h4>
      <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end;">
        <div class="field" style="margin:0;"><label>Başlangıç</label><input type="date" id="us-from" value="${iso(past)}"></div>
        <div class="field" style="margin:0;"><label>Bitiş</label><input type="date" id="us-to" value="${iso(today)}"></div>
        <button class="btn primary" id="us-go" style="height:42px;">Ara</button>
      </div><div id="us-result" style="margin-top:14px;"></div>`;
    const ht=Array.from(view.querySelectorAll('.section-title')).find(s=>/Geçmiş/i.test(s.textContent));
    // ht, view-usage'ın DOĞRUDAN çocuğu olmayabilir (portal-usage sarmalıyor) -> gerçek ebeveynine ekle
    if(ht && ht.parentNode) ht.parentNode.insertBefore(box, ht); else view.appendChild(box);
    document.getElementById('us-go').addEventListener('click', runUsageSearch); runUsageSearch();
  }
  async function runUsageSearch(){
    if(!CUR.sub) return;
    const from=document.getElementById('us-from').value, to=document.getElementById('us-to').value, out=document.getElementById('us-result');
    if(!from||!to){ out.innerHTML='<span style="color:var(--ink-soft);font-size:13px;">Tarih aralığı seç.</span>'; return; }
    out.innerHTML='<span style="color:var(--ink-soft);font-size:13px;">Sorgulanıyor…</span>';
    const r=await api(`/api/v1/usage/subscriptions/${CUR.sub.id}/history?from=${from}T00:00:00&to=${to}T23:59:59`);
    if(!r.ok){ out.innerHTML='<span style="color:var(--warn);font-size:13px;">Kayıtlar alınamadı ('+r.status+').</span>'; return; }
    const rows=asList(r.data);
    if(!rows.length){ out.innerHTML='<span style="color:var(--ink-soft);font-size:13px;">Bu aralıkta kullanım kaydı yok.</span>'; return; }
    const unit=(t,q)=> t==='DATA'?fmtMb(q):(t==='VOICE'?q+' dk':q+' SMS');
    out.innerHTML='<table><tr><th>Tarih</th><th>Tür</th><th>Miktar</th><th>CDR</th></tr>'+
      rows.slice().sort((a,b)=>new Date(b.recordedAt)-new Date(a.recordedAt)).map(u=>`<tr><td>${fmtDT(u.recordedAt)}</td><td>${esc(TRTYPE[u.type]||u.type)}</td><td>${unit(u.type,u.quantity)}</td><td style="color:var(--ink-soft);font-size:12px;">${esc(u.cdrRef||'—')}</td></tr>`).join('')+'</table>';
  }

  /* ------------------------------------------------------ FATURA DETAY + PDF */
  function ensureInvoiceModal(){
    let m=document.getElementById('inv-backdrop'); if(m) return m;
    m=document.createElement('div'); m.className='modal-backdrop'; m.id='inv-backdrop';
    m.innerHTML=`<div class="modal"><h2 id="inv-m-title">Fatura Detayı</h2><p class="sub" id="inv-m-sub"></p><div id="inv-m-body"></div>
      <div class="modal-actions"><button class="btn ghost" id="inv-m-close">Kapat</button><button class="btn primary" id="inv-m-pdf">PDF İndir</button></div></div>`;
    document.body.appendChild(m);
    m.addEventListener('click', e=>{ if(e.target===m) m.classList.remove('open'); });
    document.getElementById('inv-m-close').addEventListener('click', ()=> m.classList.remove('open'));
    return m;
  }
  function openCurrentDetail(){
    const ci = currentInvoice(); const m=ensureInvoiceModal(); m.classList.add('open');
    document.getElementById('inv-m-title').textContent = ci.label + ' — Güncel Fatura';
    document.getElementById('inv-m-sub').textContent = 'Aylık tarife + bu dönem aldıkların birlikte.';
    document.getElementById('inv-m-pdf').style.display='none';
    document.getElementById('inv-m-body').innerHTML =
      '<table><tr><th>Açıklama</th><th>Tutar</th></tr>'+ci.lines.map(l=>`<tr><td>${esc(l.desc)}</td><td>${fmtTL(l.amount)}</td></tr>`).join('')+'</table>'+
      `<div class="kv-card" style="margin-top:16px;max-width:100%;"><div class="kv" style="grid-template-columns:1fr auto;">
        <div>Ara Toplam</div><div style="text-align:right;">${fmtTL(ci.subTotal)}</div>
        <div>Özel İletişim Vergisi (%10)</div><div style="text-align:right;">${fmtTL(ci.oiv)}</div>
        <div>KDV (%20)</div><div style="text-align:right;">${fmtTL(ci.kdv)}</div>
        <div style="font-weight:700;">Genel Toplam</div><div style="text-align:right;font-weight:700;">${fmtTL(ci.grandTotal)}</div>
        <div>Son Ödeme</div><div style="text-align:right;">${fmtDate(ci.dueDate)}</div>
      </div></div>`;
  }
  async function openInvoiceDetail(id){
    const m=ensureInvoiceModal(); m.classList.add('open');
    document.getElementById('inv-m-title').textContent='Fatura Detayı';
    document.getElementById('inv-m-sub').textContent='Fatura #'+id.slice(0,8);
    const pdfBtn=document.getElementById('inv-m-pdf'); pdfBtn.style.display=''; pdfBtn.onclick=()=>downloadPdf(id);
    const body=document.getElementById('inv-m-body'); body.innerHTML='<p style="color:var(--ink-soft);font-size:13px;">Yükleniyor…</p>';
    const [head,lines]=await Promise.all([api(`/api/v1/invoices/${id}`), api(`/api/v1/invoices/${id}/lines`)]);
    const inv=head.ok?head.data:null; const rows=asList(lines.data);
    let html = rows.length? '<table><tr><th>Açıklama</th><th>Adet</th><th>Birim</th><th>Tutar</th></tr>'+rows.map(l=>`<tr><td>${esc(l.description)}</td><td>${l.quantity}</td><td>${fmtTL(l.unitPrice)}</td><td>${fmtTL(l.lineTotal)}</td></tr>`).join('')+'</table>' : '<p style="color:var(--ink-soft);font-size:13px;">Kalem bilgisi yok.</p>';
    if(inv){ const oiv=+(inv.subTotal*0.10).toFixed(2), kdv=+(inv.tax-oiv).toFixed(2);
      html += `<div class="kv-card" style="margin-top:16px;max-width:100%;"><div class="kv" style="grid-template-columns:1fr auto;">
        <div>Ara Toplam</div><div style="text-align:right;">${fmtTL(inv.subTotal)}</div>
        <div>Özel İletişim Vergisi (%10)</div><div style="text-align:right;">${fmtTL(oiv)}</div>
        <div>KDV</div><div style="text-align:right;">${fmtTL(kdv>0?kdv:inv.tax)}</div>
        <div style="font-weight:700;">Genel Toplam</div><div style="text-align:right;font-weight:700;">${fmtTL(inv.grandTotal)}</div>
        <div>Durum</div><div style="text-align:right;">${esc(inv.status)}</div><div>Son Ödeme</div><div style="text-align:right;">${fmtDate(inv.dueDate)}</div>
      </div></div>`; }
    body.innerHTML=html;
  }
  async function downloadPdf(id){
    try{ const s=telcoxGetState();
      const res=await fetch(`/api/v1/invoices/${id}/pdf`, {headers: s.token?{'Authorization':'Bearer '+s.token}:{}});
      if(!res.ok){ toast('PDF alınamadı ('+res.status+')'); return; }
      const url=URL.createObjectURL(await res.blob()); window.open(url,'_blank'); setTimeout(()=>URL.revokeObjectURL(url),60000);
    }catch(e){ toast('PDF indirilemedi'); }
  }

  /* ----------------------------------------------------- TALEPLERİM (gerçek) */
  const TICKET_CATS = {
    'FATURA':{label:'Fatura', subs:['Yüksek fatura','Yanlış kalem','Ödeme sorunu']},
    'TEKNIK':{label:'Teknik', subs:['İnternet yavaş','Çekim/sinyal yok','Arama sorunu']},
    'TARIFE':{label:'Tarife / Paket', subs:['Paket değişikliği','Kota sorunu','Ek paket']},
    'ROAMING':{label:'Yurt Dışı (Roaming)', subs:[]}, 'GENEL':{label:'Genel', subs:[]},
  };
  function enhanceTicketForm(){
    const step=document.getElementById('modal-ticket-step'); if(!step || document.getElementById('rt-cat')) return;
    step.innerHTML=`<h2>Yeni Destek Talebi</h2><p class="sub">Talep gerçek ticket-service üzerinde oluşturulur.</p>
      <div class="field"><label>Konu (kendi cümlenle yaz)</label><input type="text" id="rt-subject" placeholder="Örn: Temmuz faturamdaki ek kalemi anlamadım"></div>
      <div style="display:flex;gap:10px;">
        <div class="field" style="flex:1;"><label>Ana Kategori</label><select id="rt-cat" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);">${Object.entries(TICKET_CATS).map(([k,v])=>`<option value="${k}">${v.label}</option>`).join('')}</select></div>
        <div class="field" style="flex:1;"><label>Alt Kategori</label><select id="rt-sub" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);"></select></div></div>
      <div class="field"><label>Öncelik</label><select id="rt-prio" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);"><option value="LOW">Düşük</option><option value="MEDIUM" selected>Orta</option><option value="HIGH">Yüksek</option><option value="CRITICAL">Kritik</option></select></div>
      <div class="field"><label>Açıklama</label><textarea id="rt-desc" rows="3" placeholder="Sorununu detaylandır…" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);resize:vertical;"></textarea></div>
      <div class="modal-actions"><button class="btn ghost" id="rt-cancel">Vazgeç</button><button class="btn primary" id="rt-submit">Talep Oluştur</button></div>`;
    const fillSubs=()=>{ const subs=TICKET_CATS[document.getElementById('rt-cat').value].subs;
      document.getElementById('rt-sub').innerHTML = subs.length? subs.map(s=>`<option value="${esc(s)}">${esc(s)}</option>`).join('') : '<option value="">— (alt kategori yok)</option>'; };
    document.getElementById('rt-cat').addEventListener('change', fillSubs); fillSubs();
    document.getElementById('rt-cancel').addEventListener('click', ()=> document.getElementById('purchase-backdrop').classList.remove('open'));
    document.getElementById('rt-submit').addEventListener('click', submitTicket);
  }
  async function submitTicket(){
    const subject=(document.getElementById('rt-subject').value||'').trim(); if(!subject){ toast('Lütfen bir konu yaz'); return; }
    const cat=document.getElementById('rt-cat').value, sub=document.getElementById('rt-sub').value, prio=document.getElementById('rt-prio').value, desc=(document.getElementById('rt-desc').value||'').trim();
    const category=TICKET_CATS[cat].label+(sub?' / '+sub:''); const description=`Konu: ${subject}`+(desc?`\n\n${desc}`:'');
    const btn=document.getElementById('rt-submit'); btn.disabled=true; btn.textContent='Oluşturuluyor…';
    const r=await api('/api/v1/tickets', {method:'POST', body:JSON.stringify({customerId:st.customerId, category, priority:prio, description})});
    btn.disabled=false; btn.textContent='Talep Oluştur';
    if(!r.ok){ toast('Talep oluşturulamadı ('+r.status+')'); return; }
    document.getElementById('purchase-backdrop').classList.remove('open');
    document.getElementById('rt-subject').value=''; document.getElementById('rt-desc').value='';
    toast('Talebin oluşturuldu — #'+(r.data&&r.data.id?r.data.id.slice(0,8):'')); home();
  }

  /* -------------------------------------------------------- PROFİLİM (gerçek) */
  async function buildProfile(){
    const view=document.getElementById('view-profile'); if(!view) return;
    const c=await api(`/api/v1/customers/${st.customerId}`);
    const [ad,ct]=await Promise.all([api(`/api/v1/customers/${st.customerId}/addresses`), api(`/api/v1/customers/${st.customerId}/contacts`)]);
    const cust=c.ok?c.data:{}; const addrs=asList(ad.data), contacts=asList(ct.data);
    const ol=document.getElementById('logout-btn'); if(ol) ol.style.display='none';
    let host=document.getElementById('real-profile');
    if(!host){ host=document.createElement('div'); host.id='real-profile'; const kv=view.querySelector('.kv-card'); if(kv) kv.replaceWith(host); else view.appendChild(host); }
    const fullName=((cust.firstName||'')+' '+(cust.lastName||'')).trim()||st.name;
    host.innerHTML=`
      <div class="kv-card"><div class="kv">
        <div>Ad Soyad</div><div id="pf-name">${esc(fullName)}</div>
        <div>Müşteri Türü</div><div>${esc(cust.type||'Bireysel')}</div>
        <div>KYC Durumu</div><div>${cust.status==='APPROVED'||cust.status==='ACTIVE'?'<span class="status-pill ok">Onaylandı</span>':'<span class="status-pill pending">'+esc(cust.status||'—')+'</span>'}</div>
        <div>MSISDN</div><div>${esc((CUR.sub&&CUR.sub.msisdn)||st.msisdn||'—')}</div></div></div>
      <div class="kv-card" style="margin-top:18px;"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;"><b>Adreslerim</b><button class="btn ghost" id="pf-add-addr">+ Adres Ekle</button></div>
        <div id="pf-addr-list">${addrs.length?addrs.map(a=>`<div style="padding:8px 0;border-bottom:1px solid var(--line);font-size:14px;">${esc(a.line1)}, ${esc(a.district)}/${esc(a.city)} ${esc(a.postalCode||'')} ${a.defaultAddress?'<span class="status-pill ok" style="margin-left:6px;">Varsayılan</span>':''}</div>`).join(''):'<div style="color:var(--ink-soft);font-size:13px;">Kayıtlı adres yok.</div>'}</div></div>
      <div class="kv-card" style="margin-top:18px;"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;"><b>İletişim Bilgilerim</b><button class="btn ghost" id="pf-add-contact">+ İletişim Ekle</button></div>
        <div id="pf-contact-list">${contacts.length?contacts.map(k=>`<div style="padding:8px 0;border-bottom:1px solid var(--line);font-size:14px;">${esc(k.type)}: ${esc(k.value)} ${k.primaryContact?'<span class="status-pill ok" style="margin-left:6px;">Birincil</span>':''}</div>`).join(''):'<div style="color:var(--ink-soft);font-size:13px;">Kayıtlı iletişim bilgisi yok.</div>'}</div></div>
      <div class="kv-card" style="margin-top:18px;"><b>Profili Düzenle</b>
        <div style="display:flex;gap:10px;margin-top:12px;"><div class="field" style="flex:1;margin:0;"><label>Ad</label><input type="text" id="pf-first" value="${esc(cust.firstName||'')}"></div><div class="field" style="flex:1;margin:0;"><label>Soyad</label><input type="text" id="pf-last" value="${esc(cust.lastName||'')}"></div></div>
        <button class="btn primary" id="pf-save" style="margin-top:12px;">Kaydet</button></div>
      <button class="btn ghost" id="logout-btn2" style="margin-top:18px;">Çıkış Yap</button>`;
    document.getElementById('pf-save').addEventListener('click', async ()=>{
      const body={ firstName:document.getElementById('pf-first').value.trim(), lastName:document.getElementById('pf-last').value.trim(), companyName:cust.companyName||null, dateOfBirth:cust.dateOfBirth||null };
      const r=await api(`/api/v1/customers/${st.customerId}`, {method:'PUT', body:JSON.stringify(body)});
      if(r.ok){ toast('Profil güncellendi'); buildProfile(); } else toast('Güncellenemedi ('+r.status+')'); });
    document.getElementById('pf-add-addr').addEventListener('click', async ()=>{
      const line1=prompt('Adres satırı (cadde, no):'); if(!line1) return; const city=prompt('İl:')||'', district=prompt('İlçe:')||'', postalCode=prompt('Posta kodu (opsiyonel):')||'';
      const r=await api(`/api/v1/customers/${st.customerId}/addresses`, {method:'POST', body:JSON.stringify({line1,city,district,postalCode,defaultAddress:addrs.length===0})});
      if(r.ok){ toast('Adres eklendi'); buildProfile(); } else toast('Adres eklenemedi ('+r.status+')'); });
    document.getElementById('pf-add-contact').addEventListener('click', async ()=>{
      const type=(prompt('İletişim tipi (EMAIL / PHONE):','EMAIL')||'').toUpperCase(); if(type!=='EMAIL'&&type!=='PHONE'){ toast('Tip EMAIL veya PHONE olmalı'); return; }
      const value=prompt(type==='EMAIL'?'E-posta adresi:':'Telefon numarası:'); if(!value) return;
      const r=await api(`/api/v1/customers/${st.customerId}/contacts`, {method:'POST', body:JSON.stringify({type,value,primaryContact:contacts.length===0})});
      if(r.ok){ toast('İletişim bilgisi eklendi'); buildProfile(); } else toast('Eklenemedi ('+r.status+')'); });
    document.getElementById('logout-btn2').addEventListener('click', ()=>{ telcoxLogout(); window.location.href='TelcoX.html'; });
  }

  /* --------------------------------------------------------------- yardımcılar */
  function toast(msg){ if(typeof showToast==='function'){ showToast(msg); return; } const t=document.getElementById('toast'); if(!t){ alert(msg); return; } setTxt('toast-msg',msg); t.classList.add('show'); setTimeout(()=>t.classList.remove('show'),3200); }
  function navTo(v){ if(typeof telcoxNavTo==='function'){ telcoxNavTo(v); return; } document.querySelectorAll('.nav-item').forEach(n=>n.classList.toggle('active', n.getAttribute('data-view')===v)); document.querySelectorAll('.view').forEach(s=>s.classList.toggle('active', s.id==='view-'+v)); window.scrollTo(0,0); }
  function wireNav(scope){ scope.querySelectorAll('[data-view]').forEach(el=>el.addEventListener('click', e=>{ e.preventDefault(); navTo(el.getAttribute('data-view')); })); }
  // dışarıdan (portal-usage) kota erişimi için
  window.telcoxQuota = { get: readQuota, paint: paintQuotaAll, checkLow: checkLowUsage };

  document.addEventListener('click', e=>{
    if(e.target.closest('.inv-current')){ openCurrentDetail(); return; }
    const d=e.target.closest('.inv-detail'); if(d){ openInvoiceDetail(d.dataset.id); return; }
    const p=e.target.closest('.inv-pdf'); if(p){ downloadPdf(p.dataset.id); return; }
  });

  // Her adım bağımsız: biri hata verse bile diğerleri çalışır (cascade önlenir)
  async function step(name, fn){ try{ await fn(); }catch(e){ console.error('[portal-real] '+name+' hata:', e); } }
  // Ana sayfadaki "+ Paket / Addon Satın Al" butonu eski simülasyon modalını açıyordu
  // (kotayı gerçekten güncellemiyordu). Onu çalışan "Paket Değiştir" ekranına yönlendir.
  function redirectPurchaseBtn(){
    const op = document.getElementById('open-purchase'); if(!op) return;
    const clone = op.cloneNode(true);          // inline click-listener'ı kopyalanmaz -> eski modal açılmaz
    clone.textContent = 'Paket / Tarife Değiştir';
    op.replaceWith(clone);
    clone.addEventListener('click', ()=> navTo('builder'));
  }

  async function init(){
    await step('tickets', enhanceTicketForm);
    await step('purchase-btn', redirectPurchaseBtn);
    await step('home', home);
    await step('switcher', buildTariffSwitcher);
    await step('usage', buildUsageSearch);
    await step('profile', buildProfile);
  }
  if(document.readyState!=='loading') init(); else document.addEventListener('DOMContentLoaded', init);
})();
