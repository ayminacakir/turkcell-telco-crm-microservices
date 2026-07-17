// Magaza: dijital servisler + cloud + ev interneti
(function(){
  const main = document.querySelector('.main');
  const items = [
    {n:'Netflix', d:'Standart plan — faturana yansır, tek tıkla başlar.', p:99, bg:'#B9090B', t:'N'},
    {n:'Disney+', d:'Tüm Disney, Marvel ve Star içerikleri.', p:89, bg:'#1B3B8C', t:'D+'},
    {n:'Spotify Premium', d:'Reklamsız müzik, offline dinleme.', p:79, bg:'#1DB954', t:'S'},
    {n:'YouTube Premium', d:'Reklamsız video + YouTube Music.', p:60, bg:'#FF0000', t:'YT'},
    {n:'Xbox Game Pass', d:'300+ oyun, bulut oyun desteği.', p:159, bg:'#107C10', t:'X'},
    {n:'Office 365', d:'Word, Excel, PowerPoint + 1TB OneDrive.', p:129, bg:'#D83B01', t:'O'},
    {n:'Google One 200GB', d:'Google hesabın için ek depolama.', p:49, bg:'#4285F4', t:'G1'},
    {n:'Cloud 100GB', d:'TelcoX Cloud — fotoğraf, video ve telefon yedeği.', p:29, bg:'#0057FF', t:'C'},
    {n:'Cloud 1TB', d:'Tüm arşivin için büyük depolama.', p:89, bg:'#0041BF', t:'C'}
  ];
  const sec = document.createElement('section');
  sec.className = 'view'; sec.id = 'view-market';
  sec.setAttribute('data-screen-label','Magaza');
  sec.innerHTML = `
  <div class="topbar"><div><h1>Mağaza</h1><p>Dijital servisler, bulut depolama ve ev interneti — hepsi faturana eklenir.</p></div></div>
  <div class="section-title">Dijital Servisler</div>
  <div class="market-grid">${items.map((x,i)=>`
    <div class="m-card" data-i="${i}">
      <div class="m-logo" style="background:${x.bg}">${x.t}</div>
      <div class="m-name">${x.n}</div><div class="m-desc">${x.d}</div>
      <div class="m-foot"><span class="m-price">₺${x.p}/ay</span><button class="btn primary m-buy">Ekle</button></div>
    </div>`).join('')}</div>
  <div class="section-title" style="margin-top:30px;">Ev İnterneti</div>
  <div class="chart-card" id="fiber-card">
    <h4 style="margin-bottom:4px;">Fiber Uygunluk Sorgula</h4>
    <div style="font-size:13px;color:var(--ink-soft);margin-bottom:14px;">Adresini yaz, altyapı uygunluğunu anında görelim.</div>
    <div style="display:flex;gap:10px;">
      <input type="text" id="fiber-addr" placeholder="Mahalle, sokak, no — örn: Caferaga Mah. Moda Cad. 12, Kadikoy" style="flex:1;padding:11px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:14px;">
      <button class="btn primary" id="fiber-check">Sorgula</button>
    </div>
    <div id="fiber-result" style="display:none;margin-top:16px;">
      <div class="ai-card" style="margin:0 0 14px;"><div class="ai-ic" style="background:var(--good);">✓</div><div>Adresinde <b>Fiber 1000 Mbps</b> altyapısı uygun. Kurulum en erken <b>2 iş günü</b> içinde yapılabilir.</div></div>
      <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center;">
        <select id="fiber-speed" style="padding:10px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:13.5px;">
          <option>100 Mbps — ₺399/ay</option><option selected>500 Mbps — ₺549/ay</option><option>1000 Mbps — ₺749/ay</option>
        </select>
        <select id="fiber-modem" style="padding:10px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:13.5px;">
          <option selected>WiFi 6 Modem — ücretsiz</option><option>WiFi 7 Mesh (2'li) — +₺39/ay</option>
        </select>
        <input type="date" id="fiber-date" value="2026-07-20" style="padding:10px 13px;border-radius:10px;border:1px solid var(--line);font-family:inherit;font-size:13.5px;">
        <button class="btn primary" id="fiber-order">Başvur</button>
      </div>
    </div>
  </div>`;
  main.appendChild(sec);
  sec.querySelectorAll('.m-buy').forEach(btn=>btn.addEventListener('click', ()=>{
    const card = btn.closest('.m-card'); const x = items[+card.dataset.i];
    runSagaInModal([
      {service:'order-service', message:'POST /api/v1/orders — OrderCreated (productCode: VAS-'+x.t+', type: ADDON)', label:'Sipariş oluşturuluyor'},
      {service:'payment-service', message:'POST /api/v1/payments/{id}/process — ₺'+x.p+' tahsil, payment.completed', label:'Ödeme işleniyor'},
      {service:'subscription-service', message:'VAS aboneliği eklendi — '+x.n, label:'Servis aktifleştiriliyor'},
      {service:'notification-service', message:'Aktivasyon SMS gönderildi — '+x.n, label:'Bildirim gönderiliyor'}
    ], x.n + ' ekleniyor', ()=>{
      card.classList.add('owned');
      btn.textContent = 'Aktif ✓'; btn.classList.remove('primary'); btn.classList.add('ghost'); btn.disabled = true;
      const st = telcoxGetState();
      telcoxSetState({purchases:[...(st.purchases||[]),{name:x.n,price:'₺'+x.p,ts:telcoxNow(),date:new Date().toLocaleDateString('tr-TR',{day:'numeric',month:'short',year:'numeric'})}]});
      showToast(x.n + ' aktifleştirildi');
    });
  }));
  document.getElementById('fiber-check').addEventListener('click', ()=>{
    if(!document.getElementById('fiber-addr').value.trim()){ showToast('Lütfen adresini yaz'); return; }
    telcoxPushEvent('product-catalog-service','GET /api/v1/coverage?address=... — FIBER_1000 uygun','ok');
    document.getElementById('fiber-result').style.display = 'block';
  });
  document.getElementById('fiber-order').addEventListener('click', ()=>{
    const speed = document.getElementById('fiber-speed').value.split(' — ')[0];
    runSagaInModal([
      {service:'order-service', message:'POST /api/v1/orders — OrderCreated (productCode: FIBER, type: TARIFF)', label:'Başvuru oluşturuluyor'},
      {service:'subscription-service', message:'Fiber aboneliği PENDING_INSTALL — kurulum randevusu ayarlandı', label:'Kurulum planlanıyor'},
      {service:'notification-service', message:'Randevu onayı SMS gönderildi — '+speed, label:'Bildirim gönderiliyor'}
    ], 'Fiber başvurun işleniyor', ()=>{
      showToast('Fiber başvurun alındı — kurulum: ' + document.getElementById('fiber-date').value);
    });
  });
})();
