// Kullanimim ekranini analitikle genisletir
(function(){
  const view = document.getElementById('view-usage');
  const table = view.querySelector('table');
  const daily = [2.3,1.5,0.8,1.2,2.1,0.6,1.9,3.4,1.1,0.9,2.7,1.4,0.5,1.8,2.2,5.0,1.3,0.7,1.6,2.9,1.0,1.2,3.1,0.8,1.5,2.4,1.1,0.9,1.7,1.2];
  const apps = [['YouTube',38,'#FF0000'],['Instagram',15,'#E1306C'],['Netflix',12,'#B9090B'],['TikTok',8,'#16202E'],['Spotify',6,'#1DB954'],['Diğer',21,'#94A3B8']];
  const hourly = [0.05,0.02,0.01,0.01,0.02,0.05,0.15,0.3,0.2,0.4,0.6,0.9,1.1,0.8,0.6,0.5,0.7,0.9,1.4,1.8,2.4,3.5,2.1,0.8];
  const max = Math.max.apply(null,daily), hmax = Math.max.apply(null,hourly);
  const wrap = document.createElement('div');
  wrap.innerHTML = `
  <div class="ai-card"><div class="ai-ic">✦</div><div>Son 3 ayın incelendi: ortalama <b>35 GB</b> kullanıyorsun ve ay sonunda ek paket alıyorsun. Paketini <b>40 GB</b> olarak kurarsan aylık <b>₺240 tasarruf</b> edersin. <a href="#" data-view="builder" style="font-weight:700;">Paketini kur →</a></div></div>
  <div class="chart-card"><h4>Son 30 Gün — Günlük Data (GB)</h4>
    <div class="bars">${daily.map((v,i)=>`<div class="b" style="height:${(v/max*100).toFixed(0)}%" title="${i+1}. gün — ${v} GB"></div>`).join('')}</div>
    <div class="bar-x"><span>16 Haz</span><span>1 Tem</span><span>15 Tem</span></div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:18px;">
    <div class="chart-card" style="margin:0;"><h4>Uygulama Dağılımı</h4>
      ${apps.map(a=>`<div class="app-row"><span>${a[0]}</span><div class="bar-track"><div class="bar-fill" style="width:${a[1]}%;background:${a[2]}"></div></div><b>%${a[1]}</b></div>`).join('')}</div>
    <div class="chart-card" style="margin:0;"><h4>Saatlik Kullanım (bugün)</h4>
      <div class="bars hourly">${hourly.map((v,i)=>`<div class="b" style="height:${(v/hmax*100).toFixed(0)}%" title="${String(i).padStart(2,'0')}:00 — ${v} GB"></div>`).join('')}</div>
      <div class="bar-x"><span>00</span><span>06</span><span>12</span><span>18</span><span>23</span></div>
      <div style="font-size:12.5px;color:var(--ink-soft);margin-top:8px;">Zirve: <b style="color:var(--ink);">22:00 · 3.5 GB</b></div></div>
  </div>
  <div class="chart-card" style="display:flex;justify-content:space-between;align-items:center;margin-top:18px;">
    <div><h4 style="margin:0 0 4px;">Kullanım Uyarısı</h4><div style="font-size:13px;color:var(--ink-soft);">Kotanın %80'ine ulaştığında SMS + push bildirimi al (quota.threshold.reached)</div></div>
    <button class="btn ghost" id="quota-alert-btn">Uyarı Ayarla</button></div>
  <div class="section-title">Kullanım Geçmişi</div>`;
  view.querySelector('.section-title').remove();
  table.parentNode.insertBefore(wrap, table);
  document.getElementById('quota-alert-btn').addEventListener('click', function(){
    telcoxPushEvent('usage-service','Kota eşiği aboneliği güncellendi — threshold %80 (SMS+push)','ok');
    this.textContent = 'Uyarı Aktif ✓'; this.classList.add('primary'); this.classList.remove('ghost');
    showToast('%80 kota uyarısı ayarlandı');
  });
  window.telcoxNavTo = function(v){
    document.querySelectorAll('.nav-item').forEach(n=>n.classList.toggle('active', n.getAttribute('data-view')===v));
    document.querySelectorAll('.view').forEach(s=>s.classList.toggle('active', s.id==='view-'+v));
    window.scrollTo(0,0);
  };
  wrap.querySelectorAll('[data-view]').forEach(el=>el.addEventListener('click',(e)=>{e.preventDefault();telcoxNavTo(el.getAttribute('data-view'));}));
})();
