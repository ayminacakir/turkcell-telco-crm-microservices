// Esnek Paket Olusturucu
(function(){
  const main = document.querySelector('.main');
  const sec = document.createElement('section');
  sec.className = 'view'; sec.id = 'view-builder';
  sec.setAttribute('data-screen-label','Esnek Paket Olusturucu');
  const extras = [
    {id:'x5g', label:'5G Öncelikli Erişim', price:40},
    {id:'xnf', label:'Netflix Standart', price:99},
    {id:'xyt', label:'YouTube Premium', price:60},
    {id:'xig', label:'Sosyal Medya Sınırsız', price:35},
    {id:'xcl', label:'Cloud 100GB', price:29},
    {id:'xes', label:'eSIM', price:0},
    {id:'xya', label:'Yurt Dışı 5GB', price:120}
  ];
  sec.innerHTML = `
  <div class="topbar"><div><h1>Paket Değiştir</h1><p>Yukarıdan gerçek bir pakete geçebilir, aşağıdaki hesaplayıcıyla kendi esnek paketini planlayabilirsin.</p></div></div>
  <div class="slider-row"><div class="s-head"><span>İnternet</span><span class="s-val" id="v-gb">40 GB</span></div>
    <input type="range" id="r-gb" min="1" max="100" value="40"><div class="bar-x"><span>1 GB</span><span>100 GB</span></div></div>
  <div class="slider-row"><div class="s-head"><span>Dakika</span><span class="s-val" id="v-dk">1000 DK</span></div>
    <input type="range" id="r-dk" min="0" max="10000" step="100" value="1000"><div class="bar-x"><span>0</span><span>10.000 DK</span></div></div>
  <div class="slider-row"><div class="s-head"><span>SMS</span><span class="s-val" id="v-sms">500 SMS</span></div>
    <input type="range" id="r-sms" min="0" max="10000" step="100" value="500"><div class="bar-x"><span>0</span><span>10.000 SMS</span></div></div>
  <div class="section-title" style="margin-top:22px;">Ekler</div>
  <div class="chip-grid">${extras.map(x=>`<button class="chip" data-x="${x.id}" data-p="${x.price}">${x.label}${x.price?' · ₺'+x.price:' · Ücretsiz'}</button>`).join('')}</div>
  <div class="ai-card"><div class="ai-ic">✦</div><div id="ai-builder-msg"></div></div>
  <div class="price-bar"><div><div style="font-size:12px;opacity:.7;">Aylık toplam</div><div class="amount" id="total-price">₺0</div></div>
    <button class="btn primary" id="buy-custom" style="padding:12px 28px;font-size:15px;">Satın Al</button></div>`;
  main.appendChild(sec);
  const sel = new Set(['xes']);
  sec.querySelector('[data-x=xes]').classList.add('on');
  function fmt(n){ return '₺' + n.toLocaleString('tr-TR'); }
  function calc(){
    const gb = +document.getElementById('r-gb').value;
    const dk = +document.getElementById('r-dk').value;
    const sms = +document.getElementById('r-sms').value;
    document.getElementById('v-gb').textContent = gb + ' GB';
    document.getElementById('v-dk').textContent = dk.toLocaleString('tr-TR') + ' DK';
    document.getElementById('v-sms').textContent = sms.toLocaleString('tr-TR') + ' SMS';
    let total = 79 + gb*5.5 + dk*0.035 + sms*0.02;
    sec.querySelectorAll('.chip.on').forEach(c=> total += +c.dataset.p);
    total = Math.round(total);
    document.getElementById('total-price').textContent = fmt(total);
    const avg = 31, ai = document.getElementById('ai-builder-msg');
    if(gb > avg + 8){
      const save = Math.round((gb-avg-4)*5.5);
      ai.innerHTML = 'Şu anda <b>'+gb+' GB</b> seçtin. Son 12 aya göre ortalama <b>'+avg+' GB</b> kullanıyorsun — <b>'+(avg+4)+' GB</b>\'a düşürsen aylık <b>'+fmt(save)+' tasarruf</b> edersin.';
    } else if(gb < avg - 4){
      ai.innerHTML = '<b>'+gb+' GB</b> geçmiş kullanımının (<b>'+avg+' GB</b>) altında — ay sonunda ek paket almak zorunda kalabilirsin. <b>'+(avg+4)+' GB</b> öneririz.';
    } else {
      ai.innerHTML = 'Harika seçim — <b>'+gb+' GB</b>, son 12 aylık ortalaman (<b>'+avg+' GB</b>) ile uyumlu. Ek paket ihtiyacın olmaz.';
    }
    return {gb,dk,sms,total};
  }
  sec.querySelectorAll('input[type=range]').forEach(r=>r.addEventListener('input', calc));
  sec.querySelectorAll('.chip').forEach(c=>c.addEventListener('click', ()=>{ c.classList.toggle('on'); calc(); }));
  calc();
  document.getElementById('buy-custom').addEventListener('click', ()=>{
    const p = calc();
    const name = 'Esnek ' + p.gb + 'GB';
    const price = fmt(p.total);
    runSagaInModal([
      {service:'product-catalog-service', message:'POST /api/v1/tariffs — custom tarife oluşturuldu (CUST-'+p.gb+'GB, '+price+')', label:'Özel tarife tanımlanıyor'},
      {service:'order-service', message:'POST /api/v1/orders — OrderCreated (productCode: CUST-'+p.gb+'GB, type: TARIFF) · outbox→Kafka', label:'Sipariş oluşturuluyor (Saga)'},
      {service:'payment-service', message:'POST /api/v1/payments/{id}/process — '+price+' tahsil, payment.completed', label:'Ödeme işleniyor'},
      {service:'subscription-service', message:'payment.completed tüketildi — subscription.activated (CUST-'+p.gb+'GB)', label:'Abonelik aktifleştiriliyor'},
      {service:'usage-service', message:'POST /api/v1/usage/quotas — '+p.gb+'GB / '+p.dk+'DK / '+p.sms+'SMS kotası tanımlandı', label:'Kota tanımlanıyor'},
      {service:'notification-service', message:'Hoş geldin SMS gönderildi — '+name, label:'Bildirim gönderiliyor'}
    ], 'Esnek paketin hazırlanıyor', async ()=>{
      // GERÇEK kota güncellemesi: seçilen GB/DK/SMS mobil kotaya yazılır
      if(window.telcoxReal && telcoxReal.getSub()){
        // ekler ayrı kalemler olacağı için tarife tabanı = toplam - ek fiyatları (çift sayım olmasın)
        const chips = Array.from(sec.querySelectorAll('.chip.on')).map(c=>extras.find(e=>e.id===c.dataset.x)).filter(Boolean);
        const chipSum = chips.reduce((s,e)=>s+e.price,0);
        const base = Math.max(0, p.total - chipSum);
        await telcoxReal.setQuota(p.dk, p.sms, p.gb*1024, name, base);
        // esnek paketi geçmiş + fatura tahmini için kaydet (tarife kalemi, taban ücret)
        const st0 = telcoxGetState(); const now=new Date(), nb=new Date(now.getFullYear(),now.getMonth()+1,1);
        const MON=['Ocak','Şubat','Mart','Nisan','Mayıs','Haziran','Temmuz','Ağustos','Eylül','Ekim','Kasım','Aralık'];
        telcoxSetState({purchases:[...(st0.purchases||[]),{id:Math.random().toString(36).slice(2,10),name:name+' (esnek tarife)',monthlyFee:base,kind:'tariff',cat:'Tarife',catTag:'Esnek Tarife',boughtAt:now.toISOString(),billingMonth:MON[nb.getMonth()]+' '+nb.getFullYear()}]});
        // seçilen dijital/ek servisleri ayrı ürün olarak kaydet (ücretli olanlar)
        chips.filter(e=>e.price>0).forEach(ex=> telcoxReal.recordPurchase({name: ex.label, monthlyFee: ex.price, catTag:'Ek Servis'}));
      } else {
        telcoxSetState({activePlan: name});
        document.getElementById('active-plan-name').textContent = name;
      }
      showToast(name + ' aktifleştirildi — kotan güncellendi (' + price + '/ay)');
    });
  });
})();
