/* Müşteri portalı — GERÇEK veri katmanı.
   Ana sayfa kartları + kota çubukları, kullanım geçmişi (tarih aralığı sorgusu),
   tarife değiştirme (gerçekten kotayı günceller), faturalar (detay + PDF),
   destek talepleri (gerçek POST) ve profil (gerçek düzenleme) buradan yönetilir.
   Simülasyon ekranları (esnek paket sliderı, mağaza) tasarım amaçlı korunur. */
(function(){
  const st = telcoxGetState();
  if(!st || !st.customerId) return;   // ops kullanıcısı / demo-offline: gerçek katman yok

  const CUR = { sub:null, tariff:null };   // aktif abonelik + tarife (paylaşımlı)
  const setTxt = (id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };
  const fmtMb = (v)=> v>=1024 ? (v/1024).toFixed(v%1024?1:0)+' GB' : v+' MB';
  const TRTYPE = { VOICE:'Ses', SMS:'SMS', DATA:'Data' };
  const TICKET_STATUS_TR = { OPEN:'Açık', IN_PROGRESS:'İşlemde', RESOLVED:'Çözüldü', CLOSED:'Kapandı' };

  /* ---------------------------------------------------------------- ANA SAYFA */
  async function home(){
    const subs = await api(`/api/v1/subscriptions/customers/${st.customerId}`);
    const sub = asList(subs.data)[0] || (subs.data && subs.data.id ? subs.data : null);
    if(sub){
      CUR.sub = sub;
      const t = await api(`/api/v1/tariffs/${sub.tariffCode}`);
      if(t.ok){ CUR.tariff = t.data; setTxt('active-plan-name', t.data.name); }
      const meta = document.querySelector('#view-home .card .meta');
      if(meta && sub.status) meta.textContent = (sub.status==='ACTIVE'?'Postpaid · Aktif':sub.status) + ' · ' + (sub.msisdn||'');
      const q = await api(`/api/v1/usage/subscriptions/${sub.id}/quota`);
      if(q.ok && t.ok){
        const Q=q.data, T=t.data;
        paintQuotaSet('#view-home', Q, T);
        paintQuotaSet('#view-usage', Q, T);
      }
    }
    const inv = await api(`/api/v1/invoices/customer/${st.customerId}`);
    if(inv.ok){
      const rows = asList(inv.data).sort((a,b)=> new Date(b.periodStart)-new Date(a.periodStart));
      const pending = rows.find(i=>i.status==='PENDING') || rows[0];
      if(pending){ setTxt('home-invoice-amount', fmtTL(pending.grandTotal));
        setTxt('home-invoice-meta', 'Son ödeme: '+fmtDate(pending.dueDate)); }
      paintInvoices(rows);
    }
    const tk = await api(`/api/v1/tickets?customerId=${st.customerId}&size=50`);
    if(tk.ok){
      const rows = asList(tk.data);
      const open = rows.filter(t=>t.status!=='RESOLVED'&&t.status!=='CLOSED').length;
      setTxt('home-ticket-count', open+' talep');
      setTxt('home-ticket-meta', open?'Yanıt bekleniyor':'Açık talebiniz yok');
      paintTickets(rows);
    }
  }

  function paintQuotaSet(scope, Q, T){
    paintQuota(scope, 0, Q.minutesRemaining, T.minutesIncluded, v=>v+' dk');
    paintQuota(scope, 1, Q.smsRemaining, T.smsIncluded, v=>v+' SMS');
    paintQuota(scope, 2, Q.mbRemaining, T.dataMbIncluded, fmtMb);
  }
  function paintQuota(scope, idx, rem, total, unit){
    const cards = document.querySelectorAll(scope+' .quota-card');
    const c = cards[idx]; if(!c || rem==null || total==null) return;
    const usedPct = total>0 ? Math.max(0, Math.min(100, Math.round(100*(1-rem/total)))) : 0;
    const val = c.querySelector('.value'); if(val) val.textContent = `${unit(rem)} / ${unit(total)}`;
    const fill = c.querySelector('.bar-fill'); if(fill) fill.style.width = usedPct+'%';
    c.classList.toggle('warn', usedPct>=80);
  }

  function paintInvoices(rows){
    const tbl = document.querySelector('#view-invoices table'); if(!tbl || !rows.length) return;
    const pill = s => s==='PAID'?'<span class="status-pill paid">Ödendi</span>'
      : s==='PENDING'?'<span class="status-pill pending">Bekliyor</span>'
      : `<span class="status-pill pending">${esc(s)}</span>`;
    tbl.innerHTML = '<tr><th>Dönem</th><th>Tutar</th><th>Son Ödeme</th><th>Durum</th><th></th></tr>' +
      rows.map(i=>`<tr>
        <td>${fmtDate(i.periodStart)} – ${fmtDate(i.periodEnd)}</td>
        <td>${fmtTL(i.grandTotal)}</td>
        <td>${fmtDate(i.dueDate)}</td>
        <td>${pill(i.status)}</td>
        <td style="text-align:right;white-space:nowrap;">
          <button class="btn ghost inv-detail" data-id="${i.id}">Detay</button>
          <button class="btn ghost inv-pdf" data-id="${i.id}">PDF</button>
        </td></tr>`).join('');
  }

  function paintTickets(rows){
    const list = document.getElementById('ticket-list'); if(!list) return;
    if(!rows.length){ list.innerHTML = '<div class="ticket"><div><div class="t-title">Henüz talebin yok</div><div class="t-meta">Yeni Talep ile ilk kaydını oluşturabilirsin.</div></div></div>'; return; }
    const pill = s => (s==='RESOLVED'||s==='CLOSED')
      ? '<span class="status-pill ok">'+esc(TICKET_STATUS_TR[s]||s)+'</span>'
      : '<span class="status-pill pending">'+esc(TICKET_STATUS_TR[s]||s)+'</span>';
    const sorted = rows.slice().sort((a,b)=> new Date(b.createdAt)-new Date(a.createdAt));
    list.innerHTML = sorted.map(t=>`<div class="ticket"><div>
      <div class="t-title">${esc(t.category)} talebi</div>
      <div class="t-meta">#${t.id.slice(0,8)} · ${esc(t.priority)} · ${fmtDT(t.createdAt)}${t.assignedTeam?' · '+esc(t.assignedTeam):''}</div>
      </div>${pill(t.status)}</div>`).join('');
  }

  /* -------------------------------------------------- TARİFE DEĞİŞTİR (gerçek) */
  async function buildTariffSwitcher(){
    const view = document.getElementById('view-builder'); if(!view) return;
    let host = document.getElementById('real-tariff-switch');
    if(!host){
      host = document.createElement('div'); host.id = 'real-tariff-switch';
      host.style.marginBottom = '30px';
      // esnek paket sliderının ÜSTÜne yerleştir
      const firstTop = view.querySelector('.topbar');
      if(firstTop && firstTop.nextSibling) view.insertBefore(host, firstTop.nextSibling);
      else view.insertBefore(host, view.firstChild);
    }
    host.innerHTML = '<div class="section-title">Tarifeni Değiştir — Gerçek Paketler</div>' +
      '<p style="color:var(--ink-soft);font-size:13.5px;margin:-6px 0 16px;">Bir pakete geçtiğinde aboneliğin ve kotan gerçekten güncellenir; Ana Sayfa\'daki kota çubukları yeni pakete göre yenilenir.</p>' +
      '<div id="rt-cards" class="market-grid"><div style="color:var(--ink-soft);font-size:13px;">Paketler yükleniyor…</div></div>';

    const r = await api('/api/v1/tariffs?status=ACTIVE&size=50');
    const cards = document.getElementById('rt-cards');
    const tariffs = asList(r.data);
    if(!r.ok || !tariffs.length){ cards.innerHTML = '<div style="color:var(--ink-soft);font-size:13px;">Paket kataloğu yüklenemedi (product-catalog-service çalışıyor mu?).</div>'; return; }
    const curCode = CUR.sub ? CUR.sub.tariffCode : null;
    cards.innerHTML = tariffs.map(t=>{
      const isCur = t.code===curCode;
      return `<div class="m-card${isCur?' owned':''}" style="cursor:default;">
        <div class="m-logo" style="background:${isCur?'var(--good)':'var(--primary)'}">${esc((t.code||'?').slice(-3))}</div>
        <div class="m-name">${esc(t.name)}</div>
        <div class="m-desc">${t.minutesIncluded||0} dk · ${t.smsIncluded||0} SMS · ${fmtMb(t.dataMbIncluded||0)}${t.type?' · '+esc(t.type):''}</div>
        <div class="m-foot">
          <span class="m-price">${fmtTL(t.monthlyFee)}/ay</span>
          <button class="btn ${isCur?'ghost':'primary'} rt-go" data-code="${esc(t.code)}" ${isCur?'disabled':''}>${isCur?'Mevcut paketin':'Bu pakete geç'}</button>
        </div></div>`;
    }).join('');
    cards.querySelectorAll('.rt-go').forEach(b=> b.addEventListener('click', ()=> switchTariff(b.dataset.code, b)));
  }

  async function switchTariff(code, btn){
    if(!CUR.sub){ toast('Aktif abonelik bulunamadı'); return; }
    if(CUR.sub.status && CUR.sub.status!=='ACTIVE'){ toast('Sadece aktif abonelikte paket değiştirilebilir'); return; }
    if(!confirm(`"${code}" paketine geçmek istediğine emin misin?\nAboneliğin ve kotan bu pakete göre güncellenecek.`)) return;
    const old = btn.textContent; btn.disabled = true; btn.textContent = 'Geçiliyor…';
    // 1) aboneliğin tarifesini değiştir
    const p = await api(`/api/v1/subscriptions/${CUR.sub.id}/tariff`,
      {method:'PATCH', body: JSON.stringify({tariffCode: code})});
    if(!p.ok){ btn.disabled=false; btn.textContent=old; toast('Tarife değiştirilemedi ('+p.status+')'); return; }
    // 2) yeni tarifenin dahil miktarlarını al
    const t = await api(`/api/v1/tariffs/${code}`);
    if(t.ok){
      // 3) kotayı yeni paketin dahil miktarlarına sıfırla (GERÇEK güncelleme)
      await api(`/api/v1/usage/subscriptions/${CUR.sub.id}/quota`, {method:'PUT', body: JSON.stringify({
        minutes: t.data.minutesIncluded||0, sms: t.data.smsIncluded||0, mb: t.data.dataMbIncluded||0 })});
    }
    toast('Paket değişti — kotan güncellendi: ' + (t.ok?t.data.name:code));
    if(typeof telcoxSetState==='function') telcoxSetState({activePlan: t.ok?t.data.name:code});
    await home();            // ana sayfa kartları + kota çubukları yenilenir
    await buildTariffSwitcher();   // "mevcut paket" rozeti güncellenir
    // kullanıcıyı ana sayfaya götür ki güncel kotayı görsün
    navTo('home');
  }

  /* ------------------------------------------------ KULLANIMIM (tarih aralığı) */
  function buildUsageSearch(){
    const view = document.getElementById('view-usage'); if(!view) return;
    if(document.getElementById('usage-search')) return;
    const today = new Date(); const past = new Date(); past.setDate(past.getDate()-30);
    const iso = d => d.toISOString().slice(0,10);
    const box = document.createElement('div');
    box.className = 'chart-card'; box.id = 'usage-search';
    box.style.marginTop = '18px';
    box.innerHTML = `
      <h4 style="margin-bottom:12px;">Kullanım Geçmişi Sorgula (gerçek kayıtlar)</h4>
      <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:flex-end;">
        <div class="field" style="margin:0;"><label>Başlangıç</label><input type="date" id="us-from" value="${iso(past)}"></div>
        <div class="field" style="margin:0;"><label>Bitiş</label><input type="date" id="us-to" value="${iso(today)}"></div>
        <button class="btn primary" id="us-go" style="height:42px;">Ara</button>
      </div>
      <div id="us-result" style="margin-top:14px;"></div>`;
    // "Kullanım Geçmişi" başlığından hemen önce ekle
    const histTitle = Array.from(view.querySelectorAll('.section-title')).find(s=>/Geçmiş/i.test(s.textContent));
    if(histTitle) view.insertBefore(box, histTitle); else view.appendChild(box);
    document.getElementById('us-go').addEventListener('click', runUsageSearch);
    runUsageSearch();
  }
  async function runUsageSearch(){
    if(!CUR.sub){ return; }
    const from = document.getElementById('us-from').value, to = document.getElementById('us-to').value;
    const out = document.getElementById('us-result');
    if(!from || !to){ out.innerHTML = '<span style="color:var(--ink-soft);font-size:13px;">Tarih aralığı seç.</span>'; return; }
    out.innerHTML = '<span style="color:var(--ink-soft);font-size:13px;">Sorgulanıyor…</span>';
    const r = await api(`/api/v1/usage/subscriptions/${CUR.sub.id}/history?from=${from}T00:00:00&to=${to}T23:59:59`);
    if(!r.ok){ out.innerHTML = '<span style="color:var(--warn);font-size:13px;">Kayıtlar alınamadı ('+r.status+').</span>'; return; }
    const rows = asList(r.data);
    if(!rows.length){ out.innerHTML = '<span style="color:var(--ink-soft);font-size:13px;">Bu aralıkta kullanım kaydı yok.</span>'; return; }
    const unit = (t,q)=> t==='DATA'?fmtMb(q):(t==='VOICE'?q+' dk':q+' SMS');
    const sorted = rows.slice().sort((a,b)=> new Date(b.recordedAt)-new Date(a.recordedAt));
    out.innerHTML = '<table><tr><th>Tarih</th><th>Tür</th><th>Miktar</th><th>CDR</th></tr>' +
      sorted.map(u=>`<tr><td>${fmtDT(u.recordedAt)}</td><td>${esc(TRTYPE[u.type]||u.type)}</td><td>${unit(u.type,u.quantity)}</td><td style="color:var(--ink-soft);font-size:12px;">${esc(u.cdrRef||'—')}</td></tr>`).join('') +
      '</table>';
  }

  /* ------------------------------------------------------ FATURA DETAY + PDF */
  function ensureInvoiceModal(){
    let m = document.getElementById('inv-backdrop');
    if(m) return m;
    m = document.createElement('div'); m.className = 'modal-backdrop'; m.id = 'inv-backdrop';
    m.innerHTML = `<div class="modal"><h2 id="inv-m-title">Fatura Detayı</h2>
      <p class="sub" id="inv-m-sub"></p>
      <div id="inv-m-body"></div>
      <div class="modal-actions"><button class="btn ghost" id="inv-m-close">Kapat</button>
        <button class="btn primary" id="inv-m-pdf">PDF İndir</button></div></div>`;
    document.body.appendChild(m);
    m.addEventListener('click', e=>{ if(e.target===m) m.classList.remove('open'); });
    document.getElementById('inv-m-close').addEventListener('click', ()=> m.classList.remove('open'));
    return m;
  }
  async function openInvoiceDetail(id){
    const m = ensureInvoiceModal(); m.classList.add('open');
    const body = document.getElementById('inv-m-body');
    document.getElementById('inv-m-sub').textContent = 'Fatura #' + id.slice(0,8);
    body.innerHTML = '<p style="color:var(--ink-soft);font-size:13px;">Yükleniyor…</p>';
    document.getElementById('inv-m-pdf').onclick = ()=> downloadPdf(id);
    const [head, lines] = await Promise.all([
      api(`/api/v1/invoices/${id}`), api(`/api/v1/invoices/${id}/lines`) ]);
    const inv = head.ok ? head.data : null;
    const rows = asList(lines.data);
    let html = '';
    if(rows.length){
      html += '<table><tr><th>Açıklama</th><th>Adet</th><th>Birim</th><th>Tutar</th></tr>' +
        rows.map(l=>`<tr><td>${esc(l.description)}</td><td>${l.quantity}</td><td>${fmtTL(l.unitPrice)}</td><td>${fmtTL(l.lineTotal)}</td></tr>`).join('') +
        '</table>';
    } else {
      html += '<p style="color:var(--ink-soft);font-size:13px;">Kalem bilgisi yok.</p>';
    }
    if(inv){
      html += `<div class="kv-card" style="margin-top:16px;max-width:100%;"><div class="kv" style="grid-template-columns:1fr auto;">
        <div>Ara Toplam</div><div style="text-align:right;">${fmtTL(inv.subTotal)}</div>
        <div>Vergiler (ÖİV + KDV)</div><div style="text-align:right;">${fmtTL(inv.tax)}</div>
        <div style="font-weight:700;">Genel Toplam</div><div style="text-align:right;font-weight:700;">${fmtTL(inv.grandTotal)}</div>
        <div>Durum</div><div style="text-align:right;">${esc(inv.status)}</div>
        <div>Son Ödeme</div><div style="text-align:right;">${fmtDate(inv.dueDate)}</div>
      </div></div>`;
    }
    body.innerHTML = html;
  }
  async function downloadPdf(id){
    try{
      const s = telcoxGetState();
      const res = await fetch(`/api/v1/invoices/${id}/pdf`, {headers: s.token?{'Authorization':'Bearer '+s.token}:{}});
      if(!res.ok){ toast('PDF alınamadı ('+res.status+')'); return; }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      setTimeout(()=>URL.revokeObjectURL(url), 60000);
    }catch(e){ toast('PDF indirilemedi'); }
  }

  /* ----------------------------------------------------- TALEPLERİM (gerçek) */
  const TICKET_CATS = {
    'FATURA':   {label:'Fatura', subs:['Yüksek fatura','Yanlış kalem','Ödeme sorunu']},
    'TEKNIK':   {label:'Teknik', subs:['İnternet yavaş','Çekim/sinyal yok','Arama sorunu']},
    'TARIFE':   {label:'Tarife / Paket', subs:['Paket değişikliği','Kota sorunu','Ek paket']},
    'ROAMING':  {label:'Yurt Dışı (Roaming)', subs:[]},
    'GENEL':    {label:'Genel', subs:[]},
  };
  function enhanceTicketForm(){
    const step = document.getElementById('modal-ticket-step'); if(!step) return;
    if(document.getElementById('rt-cat')) return;   // zaten kuruldu
    step.innerHTML = `
      <h2>Yeni Destek Talebi</h2>
      <p class="sub">Talep gerçek ticket-service üzerinde oluşturulur.</p>
      <div class="field"><label>Konu (kendi cümlenle yaz)</label>
        <input type="text" id="rt-subject" placeholder="Örn: Temmuz faturamdaki ek kalemi anlamadım"></div>
      <div style="display:flex;gap:10px;">
        <div class="field" style="flex:1;"><label>Ana Kategori</label>
          <select id="rt-cat" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);">
            ${Object.entries(TICKET_CATS).map(([k,v])=>`<option value="${k}">${v.label}</option>`).join('')}
          </select></div>
        <div class="field" style="flex:1;"><label>Alt Kategori</label>
          <select id="rt-sub" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);"></select></div>
      </div>
      <div class="field"><label>Öncelik</label>
        <select id="rt-prio" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);">
          <option value="LOW">Düşük</option><option value="MEDIUM" selected>Orta</option><option value="HIGH">Yüksek</option><option value="CRITICAL">Kritik</option>
        </select></div>
      <div class="field"><label>Açıklama</label>
        <textarea id="rt-desc" rows="3" placeholder="Sorununu detaylandır…" style="width:100%;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;background:oklch(99% 0.002 265);resize:vertical;"></textarea></div>
      <div class="modal-actions">
        <button class="btn ghost" id="rt-cancel">Vazgeç</button>
        <button class="btn primary" id="rt-submit">Talep Oluştur</button></div>`;
    const fillSubs = ()=>{
      const cat = document.getElementById('rt-cat').value;
      const subs = TICKET_CATS[cat].subs;
      document.getElementById('rt-sub').innerHTML = subs.length
        ? subs.map(s=>`<option value="${esc(s)}">${esc(s)}</option>`).join('')
        : '<option value="">— (alt kategori yok)</option>';
    };
    document.getElementById('rt-cat').addEventListener('change', fillSubs); fillSubs();
    document.getElementById('rt-cancel').addEventListener('click', ()=> document.getElementById('purchase-backdrop').classList.remove('open'));
    document.getElementById('rt-submit').addEventListener('click', submitTicket);
  }
  async function submitTicket(){
    const subject = (document.getElementById('rt-subject').value||'').trim();
    if(!subject){ toast('Lütfen bir konu yaz'); return; }
    const cat = document.getElementById('rt-cat').value;
    const sub = document.getElementById('rt-sub').value;
    const prio = document.getElementById('rt-prio').value;
    const desc = (document.getElementById('rt-desc').value||'').trim();
    const category = TICKET_CATS[cat].label + (sub? ' / '+sub : '');
    const description = `Konu: ${subject}` + (desc? `\n\n${desc}` : '');
    const btn = document.getElementById('rt-submit'); btn.disabled=true; btn.textContent='Oluşturuluyor…';
    const r = await api('/api/v1/tickets', {method:'POST', body: JSON.stringify({
      customerId: st.customerId, category, priority: prio, description })});
    btn.disabled=false; btn.textContent='Talep Oluştur';
    if(!r.ok){ toast('Talep oluşturulamadı ('+r.status+')'); return; }
    document.getElementById('purchase-backdrop').classList.remove('open');
    document.getElementById('rt-subject').value=''; document.getElementById('rt-desc').value='';
    toast('Talebin oluşturuldu — #'+ (r.data && r.data.id ? r.data.id.slice(0,8):''));
    home();
  }

  /* -------------------------------------------------------- PROFİLİM (gerçek) */
  async function buildProfile(){
    const view = document.getElementById('view-profile'); if(!view) return;
    const c = await api(`/api/v1/customers/${st.customerId}`);
    const [ad, ct] = await Promise.all([
      api(`/api/v1/customers/${st.customerId}/addresses`),
      api(`/api/v1/customers/${st.customerId}/contacts`) ]);
    const cust = c.ok ? c.data : {};
    const addrs = asList(ad.data), contacts = asList(ct.data);
    const origLogout = document.getElementById('logout-btn'); if(origLogout) origLogout.style.display='none';
    let host = document.getElementById('real-profile');
    if(!host){
      host = document.createElement('div'); host.id='real-profile';
      const kv = view.querySelector('.kv-card');
      if(kv) kv.replaceWith(host); else view.appendChild(host);
    }
    const fullName = ((cust.firstName||'')+' '+(cust.lastName||'')).trim() || st.name;
    host.innerHTML = `
      <div class="kv-card"><div class="kv">
        <div>Ad Soyad</div><div id="pf-name">${esc(fullName)}</div>
        <div>Müşteri Türü</div><div>${esc(cust.type||'Bireysel')}</div>
        <div>KYC Durumu</div><div>${cust.status==='APPROVED'||cust.status==='ACTIVE'?'<span class="status-pill ok">Onaylandı</span>':'<span class="status-pill pending">'+esc(cust.status||'—')+'</span>'}</div>
        <div>MSISDN</div><div>${esc((CUR.sub&&CUR.sub.msisdn)||st.msisdn||'—')}</div>
      </div></div>

      <div class="kv-card" style="margin-top:18px;"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
        <b>Adreslerim</b><button class="btn ghost" id="pf-add-addr">+ Adres Ekle</button></div>
        <div id="pf-addr-list">${addrs.length?addrs.map(a=>`<div style="padding:8px 0;border-bottom:1px solid var(--line);font-size:14px;">${esc(a.line1)}, ${esc(a.district)}/${esc(a.city)} ${esc(a.postalCode||'')} ${a.defaultAddress?'<span class="status-pill ok" style="margin-left:6px;">Varsayılan</span>':''}</div>`).join(''):'<div style="color:var(--ink-soft);font-size:13px;">Kayıtlı adres yok.</div>'}</div>
      </div>

      <div class="kv-card" style="margin-top:18px;"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
        <b>İletişim Bilgilerim</b><button class="btn ghost" id="pf-add-contact">+ İletişim Ekle</button></div>
        <div id="pf-contact-list">${contacts.length?contacts.map(k=>`<div style="padding:8px 0;border-bottom:1px solid var(--line);font-size:14px;">${esc(k.type)}: ${esc(k.value)} ${k.primaryContact?'<span class="status-pill ok" style="margin-left:6px;">Birincil</span>':''}</div>`).join(''):'<div style="color:var(--ink-soft);font-size:13px;">Kayıtlı iletişim bilgisi yok.</div>'}</div>
      </div>

      <div class="kv-card" style="margin-top:18px;"><b>Profili Düzenle</b>
        <div style="display:flex;gap:10px;margin-top:12px;">
          <div class="field" style="flex:1;margin:0;"><label>Ad</label><input type="text" id="pf-first" value="${esc(cust.firstName||'')}"></div>
          <div class="field" style="flex:1;margin:0;"><label>Soyad</label><input type="text" id="pf-last" value="${esc(cust.lastName||'')}"></div>
        </div>
        <button class="btn primary" id="pf-save" style="margin-top:12px;">Kaydet</button>
      </div>
      <button class="btn ghost" id="logout-btn2" style="margin-top:18px;">Çıkış Yap</button>`;

    document.getElementById('pf-save').addEventListener('click', async ()=>{
      const body = { firstName: document.getElementById('pf-first').value.trim(),
                     lastName: document.getElementById('pf-last').value.trim(),
                     companyName: cust.companyName||null, dateOfBirth: cust.dateOfBirth||null };
      const r = await api(`/api/v1/customers/${st.customerId}`, {method:'PUT', body: JSON.stringify(body)});
      if(r.ok){ toast('Profil güncellendi'); buildProfile(); } else toast('Güncellenemedi ('+r.status+')');
    });
    document.getElementById('pf-add-addr').addEventListener('click', async ()=>{
      const line1 = prompt('Adres satırı (cadde, no):'); if(!line1) return;
      const city = prompt('İl:')||''; const district = prompt('İlçe:')||'';
      const postalCode = prompt('Posta kodu (opsiyonel):')||'';
      const r = await api(`/api/v1/customers/${st.customerId}/addresses`, {method:'POST', body: JSON.stringify({
        line1, city, district, postalCode, defaultAddress: addrs.length===0 })});
      if(r.ok){ toast('Adres eklendi'); buildProfile(); } else toast('Adres eklenemedi ('+r.status+')');
    });
    document.getElementById('pf-add-contact').addEventListener('click', async ()=>{
      const type = (prompt('İletişim tipi (EMAIL / PHONE):','EMAIL')||'').toUpperCase();
      if(type!=='EMAIL' && type!=='PHONE'){ toast('Tip EMAIL veya PHONE olmalı'); return; }
      const value = prompt(type==='EMAIL'?'E-posta adresi:':'Telefon numarası:'); if(!value) return;
      const r = await api(`/api/v1/customers/${st.customerId}/contacts`, {method:'POST', body: JSON.stringify({
        type, value, primaryContact: contacts.length===0 })});
      if(r.ok){ toast('İletişim bilgisi eklendi'); buildProfile(); } else toast('Eklenemedi ('+r.status+')');
    });
    document.getElementById('logout-btn2').addEventListener('click', ()=>{ telcoxLogout(); window.location.href='TelcoX.html'; });
  }

  /* --------------------------------------------------------------- yardımcılar */
  function toast(msg){
    if(typeof showToast==='function'){ showToast(msg); return; }
    const t=document.getElementById('toast'); if(!t){ alert(msg); return; }
    setTxt('toast-msg', msg); t.classList.add('show'); setTimeout(()=>t.classList.remove('show'),3200);
  }
  function navTo(v){
    if(typeof telcoxNavTo==='function'){ telcoxNavTo(v); return; }
    document.querySelectorAll('.nav-item').forEach(n=>n.classList.toggle('active', n.getAttribute('data-view')===v));
    document.querySelectorAll('.view').forEach(s=>s.classList.toggle('active', s.id==='view-'+v));
    window.scrollTo(0,0);
  }

  // Fatura detay/PDF butonları (delegasyon)
  document.addEventListener('click', e=>{
    const d = e.target.closest('.inv-detail'); if(d){ openInvoiceDetail(d.dataset.id); return; }
    const p = e.target.closest('.inv-pdf'); if(p){ downloadPdf(p.dataset.id); return; }
  });

  async function init(){
    enhanceTicketForm();
    await home();            // CUR.sub / CUR.tariff dolar
    buildTariffSwitcher();   // CUR.sub.tariffCode -> "mevcut paket"
    buildUsageSearch();      // CUR.sub -> geçmiş sorgusu
    buildProfile();          // CUR.sub.msisdn
  }
  if(document.readyState!=='loading') init(); else document.addEventListener('DOMContentLoaded', init);
})();
