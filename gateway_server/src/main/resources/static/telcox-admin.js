// TelcoX Operations Console logic (uses telcox-shared.js)
// ---- theme ----
const savedTheme = localStorage.getItem('telcox_theme') || 'light';
document.documentElement.setAttribute('data-theme', savedTheme);
function toggleTheme(){
  const cur = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', cur);
  localStorage.setItem('telcox_theme', cur);
  document.getElementById('theme-icon').textContent = cur === 'dark' ? '☀' : '☾';
}

// ---- runtime service state ----
const runtime = {};
TELCOX_SERVICES.forEach(s=>{
  let status='up', cpu=s.baseCpu;
  if(s.name==='usage-service'){ status='degraded'; cpu=82; }
  if(s.name==='notification-service'){ status='down'; cpu=4; }
  runtime[s.name] = {
    status, cpu, ram:+(0.8+Math.random()*2).toFixed(1),
    latency: status==='down'?null:(status==='degraded'?412:30+Math.round(Math.random()*40)),
    replicas: status==='down'?0:clampReplicas(s,cpu),
    uptime: status==='down'?97.1:(status==='degraded'?98.6:+(99.5+Math.random()*0.49).toFixed(2)),
    version:'1.0.'+(1+Math.floor(Math.random()*4)), restarts: status==='down'?3:Math.round(Math.random()*1.4),
    lag: s.name==='usage-service'?2140:Math.round(Math.random()*40),
    errRate: status==='down'?100:(status==='degraded'?4.2:+(Math.random()*0.5).toFixed(2)),
    lastHealthCheck: telcoxNow(), acked:false,
  };
});
function clampReplicas(svc,cpu){
  return Math.min(svc.maxPods, Math.max(svc.minPods, Math.round(svc.minPods + (cpu/100)*(svc.maxPods-svc.minPods))));
}
const statusLabel = {up:'Healthy', degraded:'Slow', down:'Down'};
const statusTr = {up:'Çalışıyor', degraded:'Yavaş', down:'Duruyor'};
function svcByName(n){ return TELCOX_SERVICES.find(s=>s.name===n); }

// ---- live business metrics ----
let biz = { customers:128402, ordersToday:412, revenueToday:184230, openTickets:64, activeUsers:3150 };
let revenueHist = Array.from({length:30},(_,i)=> 140000+Math.sin(i/4)*22000+Math.random()*14000);
let chartHistory = [];
let scaleEvents = [];
let incidents = [{id:'INC-000123', service:'notification-service', started: telcoxNow(), status:'Investigating', assigned:'DevOps Team', desc:'Connection refused — CrashLoopBackOff'}];
let synthetic = []; // synthetic log lines

// ---- view switching ----
function goView(v){
  document.querySelectorAll('.nav-item[data-view]').forEach(n=>n.classList.toggle('active', n.getAttribute('data-view')===v));
  document.querySelectorAll('.view').forEach(s=>s.classList.toggle('active', s.id==='view-'+v));
  document.getElementById('page-title').textContent = document.querySelector(`.nav-item[data-view="${v}"] span`).textContent;
  window.scrollTo(0,0);
}
document.querySelectorAll('.nav-item[data-view]').forEach(el=> el.addEventListener('click', ()=>goView(el.getAttribute('data-view'))));

// ---- dashboard stats ----
function fmt(n){ return n.toLocaleString('tr-TR'); }
function renderStats(){
  const counts = {up:0,degraded:0,down:0};
  TELCOX_SERVICES.forEach(s=>counts[runtime[s.name].status]++);
  const stTickets = 1 + (telcoxGetState().tickets||[]).length;
  document.getElementById('st-customers').textContent = fmt(biz.customers);
  document.getElementById('st-orders').textContent = fmt(biz.ordersToday);
  document.getElementById('st-revenue').textContent = '₺'+fmt(Math.round(biz.revenueToday));
  document.getElementById('st-tickets').textContent = biz.openTickets + stTickets;
  document.getElementById('st-services').textContent = counts.up + '/' + TELCOX_SERVICES.length;
  const err = (TELCOX_SERVICES.reduce((a,s)=>a+runtime[s.name].errRate,0)/TELCOX_SERVICES.length).toFixed(2);
  document.getElementById('st-error').textContent = '%'+err;
  const badge = document.getElementById('alarm-badge');
  const alarms = activeAlarms().length;
  badge.style.display = alarms? 'flex':'none'; badge.textContent = alarms;
  const nb = document.getElementById('nav-alarm-badge');
  if(nb){ nb.style.display = alarms? 'inline':'none'; nb.textContent = alarms; }
}

// ---- revenue chart ----
function drawArea(svgId, vals, color){
  const svg = document.getElementById(svgId); if(!svg) return;
  const w=600,h=140,p=6;
  const max=Math.max(...vals)*1.1, min=Math.min(...vals)*0.9;
  const step=(w-p*2)/(vals.length-1);
  const pts = vals.map((v,i)=>`${p+i*step},${h-p-((v-min)/(max-min))*(h-p*2)}`);
  svg.innerHTML = `<polygon points="${p},${h-p} ${pts.join(' ')} ${w-p},${h-p}" fill="${color}" opacity="0.12"></polygon><polyline points="${pts.join(' ')}" fill="none" stroke="${color}" stroke-width="2.5"></polyline>`;
}
function drawPods(){
  const svg=document.getElementById('pods-chart'); if(!svg||chartHistory.length<2) return;
  const w=600,h=140,p=6;
  const pods=chartHistory.map(c=>c.pods), users=chartHistory.map(c=>c.users);
  const step=(w-p*2)/(chartHistory.length-1);
  const path=(vals)=>{ const mx=Math.max(...vals)*1.2; return vals.map((v,i)=>`${i?'L':'M'} ${p+i*step} ${h-p-(v/mx)*(h-p*2)}`).join(' '); };
  svg.innerHTML=`<path d="${path(pods)}" fill="none" stroke="var(--primary)" stroke-width="2.5"></path><path d="${path(users)}" fill="none" stroke="var(--info)" stroke-width="2.5" stroke-dasharray="4 3"></path>`;
}

// ---- service status strip + monitoring grid ----
function renderServiceStrip(){
  const el = document.getElementById('svc-strip');
  el.innerHTML = TELCOX_SERVICES.map(s=>{
    const r=runtime[s.name];
    return `<button class="strip-item" data-svc="${s.name}"><span class="dot ${r.status}"></span>${s.name.replace('-service','').replace('-server','')}</button>`;
  }).join('');
  el.querySelectorAll('[data-svc]').forEach(b=>b.addEventListener('click', ()=>{ goView('monitoring'); openDrawer(svcByName(b.getAttribute('data-svc'))); }));
}
let currentFilter='all';
function renderGrid(){
  const grid=document.getElementById('svc-grid'); if(!grid) return;
  grid.innerHTML='';
  TELCOX_SERVICES.filter(s=>currentFilter==='all'||runtime[s.name].status===currentFilter).forEach(s=>{
    const r=runtime[s.name];
    const el=document.createElement('div');
    el.className='svc-card';
    el.innerHTML=`<div class="row1"><div><div class="name">${s.name}</div><div class="ctx">${s.ctx}</div></div>
      <div class="pill ${r.status}"><span class="d"></span>${statusLabel[r.status]}</div></div>
      <div class="mono dim">:${s.port} · v${r.version} · pod ${r.replicas}/${s.maxPods}</div>
      <div class="metrics"><span>cpu <b>${r.status==='down'?'—':r.cpu+'%'}</b></span><span>ram <b>${r.ram}GB</b></span><span>lat <b>${r.latency!==null?r.latency+'ms':'—'}</b></span><span>err <b>${r.errRate}%</b></span></div>`;
    el.addEventListener('click',()=>openDrawer(s));
    grid.appendChild(el);
  });
  const counts={up:0,degraded:0,down:0};
  TELCOX_SERVICES.forEach(s=>counts[runtime[s.name].status]++);
  document.getElementById('sum-up').textContent=counts.up+' Healthy';
  document.getElementById('sum-deg').textContent=counts.degraded+' Slow';
  document.getElementById('sum-down').textContent=counts.down+' Down';
}
document.querySelectorAll('.filter-btn').forEach(btn=>btn.addEventListener('click',()=>{
  document.querySelectorAll('.filter-btn').forEach(b=>b.classList.remove('active'));
  btn.classList.add('active'); currentFilter=btn.getAttribute('data-filter'); renderGrid();
}));

// ---- service detail drawer ----
let openService=null;
function openDrawer(s){ openService=s; refreshDrawer(); document.getElementById('drawer-backdrop').classList.add('open'); }
function refreshDrawer(){
  if(!openService) return;
  const s=openService, r=runtime[s.name];
  document.getElementById('d-name').textContent=s.name;
  document.getElementById('d-ctx').textContent=s.ctx+' · port '+s.port;
  document.getElementById('d-kv').innerHTML=`
    <div>Status</div><div>${statusTr[r.status]}</div>
    <div>Version</div><div>v${r.version}</div>
    <div>CPU</div><div>${r.status==='down'?'—':r.cpu+'%'}</div>
    <div>RAM</div><div>${r.ram} GB</div>
    <div>Pod</div><div>${r.replicas} / ${s.maxPods} (min ${s.minPods})</div>
    <div>Restart</div><div>${r.restarts}</div>
    <div>Latency</div><div>${r.latency!==null?r.latency+' ms':'—'}</div>
    <div>Error Rate</div><div>%${r.errRate}</div>
    <div>Kafka Lag</div><div>${r.lag}</div>
    <div>Uptime (30g)</div><div>%${r.uptime}</div>
    <div>Health</div><div>${s.health} · ${s.interval}</div>`;
  document.getElementById('d-cpu-bar').style.width=(r.status==='down'?0:r.cpu)+'%';
  document.getElementById('d-cpu-bar').style.background = r.cpu>80?'var(--danger)':(r.cpu>60?'var(--warning)':'var(--primary)');
  document.getElementById('d-api').innerHTML=(s.api||[]).map(a=>`<div class="log-line">${a}</div>`).join('');
  document.getElementById('d-runbook').innerHTML=s.runbook.map(rb=>`<div class="runbook-item"><div class="issue">⚠ ${rb.issue}</div><div class="fix">${rb.fix}</div></div>`).join('');
  const logs=[...telcoxGetEvents().filter(e=>e.service===s.name), ...synthetic.filter(e=>e.service===s.name)].slice(-8).reverse();
  document.getElementById('d-logs').innerHTML=logs.length?logs.map(l=>`<div class="log-line ${l.level==='err'?'err':l.level==='warn'?'warn':''}"><b>${l.ts}</b> ${l.message}</div>`).join(''):'<div class="log-line">Canlı log yok.</div>';
  document.getElementById('op-restart').disabled = r.status!=='down';
  document.getElementById('op-scale').disabled = r.replicas>=s.maxPods || r.status==='down';
}
document.getElementById('drawer-close').addEventListener('click',()=>document.getElementById('drawer-backdrop').classList.remove('open'));
document.getElementById('drawer-backdrop').addEventListener('click',e=>{ if(e.target.id==='drawer-backdrop') e.target.classList.remove('open'); });
document.getElementById('op-restart').addEventListener('click',()=>restartService(openService.name));
document.getElementById('op-scale').addEventListener('click',()=>{
  const s=openService,r=runtime[s.name];
  if(r.replicas>=s.maxPods) return;
  r.replicas++; telcoxPushEvent(s.name,`Manuel ölçekleme: pod ${r.replicas-1} → ${r.replicas}`,'ok');
  logScale(s.name,`${r.replicas-1} → ${r.replicas} pod`,'Manuel operasyon');
  renderAll();
});
document.getElementById('op-logs').addEventListener('click',()=>{ telcoxPushEvent(openService.name,'Log snapshot alındı (kubectl logs -f)','ok'); refreshDrawer(); });

function restartService(name){
  const s=svcByName(name), r=runtime[name];
  if(r.status!=='down') return;
  telcoxPushEvent(name,'Operasyon: rollout restart tetiklendi','warn');
  setTimeout(()=>{
    r.status='up'; r.replicas=s.minPods; r.cpu=s.baseCpu; r.latency=45; r.errRate=0.2; r.restarts++;
    telcoxPushEvent(name,'Pod yeniden başlatıldı — health check OK (200)','ok');
    const inc=incidents.find(i=>i.service===name && i.status!=='Resolved');
    if(inc){ inc.status='Resolved'; inc.resolved=telcoxNow(); }
    renderAll(); if(openService) refreshDrawer();
  },1500);
}

// ---- HPA scale events ----
function logScale(service,action,reason){
  scaleEvents.unshift({t:telcoxNow(),service,action,reason});
  scaleEvents=scaleEvents.slice(0,12);
  const tb=document.getElementById('scale-events-table');
  tb.innerHTML='<tr><th>Zaman</th><th>Servis</th><th>Aksiyon</th><th>Neden</th></tr>'+
    (scaleEvents.length?scaleEvents.map(e=>`<tr><td>${e.t}</td><td>${e.service}</td><td>${e.action}</td><td>${e.reason}</td></tr>`).join(''):'');
}

// ---- alarms ----
function activeAlarms(){
  const list=[];
  TELCOX_SERVICES.forEach(s=>{
    const r=runtime[s.name];
    if(r.status==='down') list.push({sev:'critical', service:s.name, msg:'Servis ayakta değil — CrashLoopBackOff', action:'restart'});
    else if(r.status==='degraded') list.push({sev:'warning', service:s.name, msg:`Yanıt süresi yüksek: ${r.latency}ms · Kafka lag ${r.lag}`, action:'scale'});
    else if(r.cpu>90) list.push({sev:'warning', service:s.name, msg:`CPU %${r.cpu} — HPA ölçekleme sınırında`, action:'scale'});
  });
  return list.filter(a=>!runtime[a.service].acked || a.sev==='critical');
}
function renderAlarms(){
  const el=document.getElementById('alarm-list'); if(!el) return;
  const alarms=activeAlarms();
  el.innerHTML = alarms.length? alarms.map(a=>`
    <div class="alarm ${a.sev}">
      <div class="a-head"><span class="a-sev">${a.sev==='critical'?'CRITICAL':'WARNING'}</span><b>${a.service}</b><span class="dim mono">${telcoxNow()}</span></div>
      <div class="a-msg">${a.msg}</div>
      <div class="a-actions">
        <button class="mini-btn" data-act="ack" data-svc="${a.service}">Acknowledge</button>
        <button class="mini-btn" data-act="restart" data-svc="${a.service}">Restart</button>
        <button class="mini-btn" data-act="incident" data-svc="${a.service}">Incident Oluştur</button>
      </div>
    </div>`).join('') : '<div class="empty-note">Aktif alarm yok — tüm sistemler normal. 🟢</div>';
  el.querySelectorAll('.mini-btn').forEach(b=>b.addEventListener('click',()=>{
    const svc=b.getAttribute('data-svc'), act=b.getAttribute('data-act');
    if(act==='ack'){ runtime[svc].acked=true; telcoxPushEvent(svc,'Alarm acknowledge edildi (operatör)','ok'); }
    if(act==='restart') restartService(svc);
    if(act==='incident'){ createIncident(svc,'Manuel incident (operatör)'); }
    renderAll();
  }));
  // dashboard alerts mini
  const mini=document.getElementById('alerts-mini');
  if(mini) mini.innerHTML = alarms.length? alarms.slice(0,3).map(a=>`<div class="mini-alert ${a.sev}"><b>${a.service}</b> ${a.msg}</div>`).join('') : '<div class="empty-note">Aktif alarm yok 🟢</div>';
}
function createIncident(service,desc){
  if(incidents.some(i=>i.service===service && i.status!=='Resolved')) return;
  const id='INC-'+String(123+incidents.length+1).padStart(6,'0');
  incidents.unshift({id, service, started:telcoxNow(), status:'Investigating', assigned:'DevOps Team', desc});
  telcoxPushEvent(service,`${id} incident açıldı — ${desc}`,'warn');
}
function renderIncidents(){
  const el=document.getElementById('incident-table'); if(!el) return;
  el.innerHTML='<tr><th>ID</th><th>Servis</th><th>Başlangıç</th><th>Durum</th><th>Atanan</th><th>Açıklama</th></tr>'+
    incidents.map(i=>`<tr><td class="mono">${i.id}</td><td>${i.service}</td><td>${i.started}</td><td><span class="pill ${i.status==='Resolved'?'up':'degraded'}"><span class="d"></span>${i.status}${i.resolved?' · '+i.resolved:''}</span></td><td>${i.assigned}</td><td>${i.desc}</td></tr>`).join('');
}

// ---- live logs view ----
const LOG_TEMPLATES=[
  {level:'INFO', msg:'Health check OK (200)'},
  {level:'INFO', msg:'Kafka event published'},
  {level:'INFO', msg:'DB connection pool: healthy'},
  {level:'WARN', msg:'Retry attempt 2/3'},
  {level:'INFO', msg:'Cache hit — Redis'},
];
function synthLog(){
  const s=TELCOX_SERVICES[Math.floor(Math.random()*TELCOX_SERVICES.length)];
  const r=runtime[s.name];
  let t;
  if(r.status==='down') t={level:'ERROR', msg:'Connection refused — health check failed'};
  else if(r.status==='degraded') t={level:'WARN', msg:`Response time ${r.latency}ms > 300ms threshold`};
  else t=LOG_TEMPLATES[Math.floor(Math.random()*LOG_TEMPLATES.length)];
  synthetic.push({ts:telcoxNow(), service:s.name, message:t.msg, lvl:t.level, level:t.level==='ERROR'?'err':(t.level==='WARN'?'warn':'info')});
  if(synthetic.length>120) synthetic.shift();
}
function renderLogs(){
  const el=document.getElementById('log-stream'); if(!el) return;
  const svcF=document.getElementById('log-svc-filter').value;
  const lvlF=document.getElementById('log-lvl-filter').value;
  const real=telcoxGetEvents().map(e=>({ts:e.ts,service:e.service,message:e.message,lvl:e.level==='err'?'ERROR':(e.level==='warn'?'WARN':'INFO')}));
  let all=[...synthetic,...real].slice(-80).reverse();
  if(svcF!=='all') all=all.filter(l=>l.service===svcF);
  if(lvlF!=='all') all=all.filter(l=>l.lvl===lvlF);
  el.innerHTML=all.slice(0,40).map(l=>`<div class="stream-line"><span class="lvl ${l.lvl}">${l.lvl}</span><span class="mono dim">${l.ts}</span><span class="l-svc">${l.service}</span><span>${l.message}</span></div>`).join('')||'<div class="empty-note">Filtreye uyan log yok.</div>';
}
['log-svc-filter','log-lvl-filter'].forEach(id=>document.getElementById(id).addEventListener('change',renderLogs));
document.getElementById('log-svc-filter').innerHTML='<option value="all">Tüm servisler</option>'+TELCOX_SERVICES.map(s=>`<option>${s.name}</option>`).join('');

// ---- realtime activity + orders ----
function renderActivity(){
  const el=document.getElementById('activity-feed'); if(!el) return;
  const events=telcoxGetEvents().slice(-14).reverse();
  el.innerHTML=events.length?events.map(e=>`<div class="event-row"><span class="e-t mono">${e.ts}</span><span class="e-svc mono">${e.service}</span><span class="e-msg">${e.message}</span></div>`).join(''):'<div class="empty-note">Müşteri portalında işlem yapıldığında burada canlı görünür.</div>';
}
function renderOrders(){
  const el=document.getElementById('orders-table'); if(!el) return;
  const st=telcoxGetState();
  const live=(st.purchases||[]).slice().reverse().map(p=>`<tr><td class="mono">ORD-${Math.abs(p.name.length*7919+p.ts.split(':').join(''))%100000}</td><td>Elif Aydın</td><td>${p.name}</td><td>${p.price}</td><td>${p.date} ${p.ts}</td><td><span class="pill up"><span class="d"></span>COMPLETED</span></td></tr>`).join('');
  const staticRows=`<tr><td class="mono">ORD-88121</td><td>Kerem Şahin</td><td>Ekonomik 20GB</td><td>₺249</td><td>14 Tem 09:12</td><td><span class="pill up"><span class="d"></span>COMPLETED</span></td></tr>
  <tr><td class="mono">ORD-88104</td><td>Zeynep Kaya</td><td>Süper 40GB</td><td>₺389</td><td>13 Tem 16:40</td><td><span class="pill degraded"><span class="d"></span>PENDING</span></td></tr>`;
  el.innerHTML='<tr><th>Sipariş</th><th>Müşteri</th><th>Ürün</th><th>Tutar</th><th>Zaman</th><th>Durum</th></tr>'+live+staticRows;
  const recent=document.getElementById('recent-orders');
  if(recent) recent.innerHTML=el.innerHTML;
}

// ---- customers ----
const CUSTOMERS={
  elif:{name:'Elif Aydın',msisdn:'0532 417 47 12',type:'Bireysel · Postpaid',kyc:'Onaylı',since:'Mar 2024',live:true},
  kerem:{name:'Kerem Şahin',msisdn:'0533 210 98 45',type:'Bireysel · Postpaid',kyc:'Onaylı',since:'Oca 2025',tariff:'Ekonomik 20GB'},
  zeynep:{name:'Zeynep Kaya',msisdn:'0541 887 12 03',type:'Bireysel · Postpaid',kyc:'Beklemede',since:'Haz 2026',tariff:'Süper 40GB'},
  burak:{name:'Burak Er',msisdn:'0555 302 44 91',type:'Bireysel · Postpaid',kyc:'Onaylı',since:'Eyl 2024',tariff:'Aile 60GB'},
};
const PORTAL_FLOW=['order-service','payment-service','subscription-service','usage-service','notification-service','billing-service','ticket-service','customer-service','keycloak','gateway-server'];
function openCustomer(key){
  const c=CUSTOMERS[key], st=telcoxGetState();
  const tariff=c.live?(st.activePlan||'Süper 40GB'):c.tariff;
  document.getElementById('c-name').textContent=c.name;
  document.getElementById('c-ctx').textContent=c.msisdn+' · '+c.type;
  document.getElementById('c-kv').innerHTML=`
    <div>Aktif Tarife</div><div>${tariff}</div>
    <div>KYC</div><div>${c.kyc}</div>
    <div>Müşteri Tarihi</div><div>${c.since}</div>
    <div>Fatura</div><div>${c.live&&st.invoicePaid?'Temmuz ödendi':'Temmuz bekliyor'}</div>`;
  const purchases=c.live?(st.purchases||[]):[];
  document.getElementById('c-purchases').innerHTML=purchases.length
    ?purchases.slice().reverse().map(p=>`<div class="runbook-item"><div class="issue" style="color:var(--success);">${p.name} — ${p.price}</div><div class="fix">${p.date} ${p.ts} · order→payment→subscription saga tamamlandı</div></div>`).join('')
    :'<div class="log-line">Portal üzerinden satın alma kaydı yok.</div>';
  const tickets=c.live?(telcoxGetState().tickets||[]):[];
  document.getElementById('c-tickets').innerHTML=tickets.length
    ?tickets.slice().reverse().map(t=>`<div class="runbook-item"><div class="issue">#TCK-${t.id} — ${t.subject}</div><div class="fix">${t.date} · ${t.category} · Yanıt bekleniyor</div></div>`).join('')
    :(c.live?'<div class="log-line">#TCK-2291 — Fatura kalemi anlaşılamadı (yanıt bekleniyor)</div>':'<div class="log-line">Açık talep yok.</div>');
  const logs=c.live?telcoxGetEvents().filter(e=>PORTAL_FLOW.includes(e.service)).slice(-12).reverse():[];
  document.getElementById('c-logs').innerHTML=logs.length
    ?logs.map(l=>`<div class="log-line ${l.level==='err'?'err':l.level==='warn'?'warn':''}"><b>${l.ts}</b> <span class="l-svc">${l.service}</span> ${l.message}</div>`).join('')
    :'<div class="log-line">Bu müşteri için canlı işlem logu yok.</div>';
  document.getElementById('cust-backdrop').classList.add('open');
}
document.querySelectorAll('#customers-table tr[data-customer]').forEach(r=>r.addEventListener('click',()=>openCustomer(r.getAttribute('data-customer'))));
document.getElementById('cust-close').addEventListener('click',()=>document.getElementById('cust-backdrop').classList.remove('open'));
document.getElementById('cust-backdrop').addEventListener('click',e=>{ if(e.target.id==='cust-backdrop') e.target.classList.remove('open'); });

// ---- AI assistant ----
const aiPanel=document.getElementById('ai-panel');
document.getElementById('ai-fab').addEventListener('click',()=>aiPanel.classList.toggle('open'));
document.getElementById('ai-close').addEventListener('click',()=>aiPanel.classList.remove('open'));
function aiAnswer(q){
  q=q.toLowerCase();
  const svc=TELCOX_SERVICES.find(s=>q.includes(s.name.split('-')[0]) || q.includes(s.name));
  if(svc){
    const r=runtime[svc.name];
    if(r.status==='down') return `${svc.name}\n\n🔴 Durum: DOWN — CrashLoopBackOff\nRestart sayısı: ${r.restarts}\nSon hata: Connection refused\n\nÖneri:\n• Alarm Merkezi'nden "Restart" tetikleyin\n• Pod loglarında OOM/exception arayın\n• Son deploy'u rollback etmeyi değerlendirin`;
    if(r.status==='degraded') return `${svc.name}\n\n🟡 CPU %${r.cpu} · Latency ${r.latency}ms\nKafka consumer lag: ${r.lag} mesaj\nSon 10 dakikada timeout arttı.\n\nÖneri:\n• Replica sayısını ${r.replicas} → ${Math.min(svc.maxPods,r.replicas+2)} çıkarın\n• Partition/consumer dengesini kontrol edin`;
    return `${svc.name}\n\n🟢 Sağlıklı\nCPU %${r.cpu} · RAM ${r.ram}GB · Latency ${r.latency}ms\nPod: ${r.replicas}/${svc.maxPods} · v${r.version}\nError rate: %${r.errRate}\n\nHer şey normal görünüyor.`;
  }
  if(q.includes('alarm')||q.includes('sorun')||q.includes('down')){
    const a=activeAlarms();
    return a.length?`Şu an ${a.length} aktif alarm var:\n\n`+a.map(x=>`• [${x.sev.toUpperCase()}] ${x.service}: ${x.msg}`).join('\n'):'Aktif alarm yok — tüm sistemler normal. 🟢';
  }
  if(q.includes('pod')||q.includes('scale')||q.includes('ölçek')){
    const total=TELCOX_SERVICES.reduce((a,s)=>a+runtime[s.name].replicas,0);
    return `Toplam ${total} pod çalışıyor.\nHPA hedefi: CPU %70.\nSon ölçekleme olayları Monitoring sekmesindeki tabloda.`;
  }
  if(q.includes('gelir')||q.includes('revenue')) return `Bugünkü gelir: ₺${fmt(Math.round(biz.revenueToday))}\nBugünkü sipariş: ${biz.ordersToday}\nDetay: Dashboard → Revenue grafiği.`;
  return 'Şunları sorabilirsin:\n• "payment service neden yavaş?"\n• "notification service durumu"\n• "aktif alarmlar neler?"\n• "kaç pod çalışıyor?"';
}
document.getElementById('ai-form').addEventListener('submit',e=>{
  e.preventDefault();
  const inp=document.getElementById('ai-input');
  const q=inp.value.trim(); if(!q) return;
  const msgs=document.getElementById('ai-msgs');
  const uDiv=document.createElement('div'); uDiv.className='ai-msg user'; uDiv.textContent=q; msgs.appendChild(uDiv);
  const aDiv=document.createElement('div'); aDiv.className='ai-msg bot'; aDiv.textContent='…'; msgs.appendChild(aDiv);
  inp.value='';
  setTimeout(()=>{ aDiv.textContent=aiAnswer(q); msgs.scrollTop=msgs.scrollHeight; },600);
  msgs.scrollTop=msgs.scrollHeight;
});

// ---- simulation tick ----
function tick(){
  const metrics=telcoxGetMetrics();
  const orderM=metrics['order-service'];
  const boost=(orderM&&Date.now()-orderM.lastHit<10000)?200:0;
  biz.activeUsers=Math.max(2000,Math.round(biz.activeUsers+(Math.random()-0.48)*40+boost*0.15));
  biz.customers+=Math.random()<0.4?1:0;
  biz.revenueToday+=Math.random()*120+(boost?400:0);
  if(Math.random()<0.25) biz.ordersToday++;
  revenueHist.push(revenueHist[revenueHist.length-1]+(Math.random()-0.45)*8000); revenueHist.shift();

  TELCOX_SERVICES.forEach(s=>{
    const r=runtime[s.name];
    if(r.status==='down') return;
    const m=metrics[s.name];
    const rBoost=(m&&Date.now()-m.lastHit<8000)?30:0;
    const target=Math.min(96,Math.max(6,s.baseCpu+rBoost+(Math.random()-0.5)*8));
    r.cpu=Math.round(r.cpu+(target-r.cpu)*0.35);
    r.latency=r.status==='degraded'?380+Math.round(Math.random()*60):Math.max(15,Math.round(20+r.cpu*0.6+(Math.random()-0.5)*15));
    r.lag=r.status==='degraded'?2000+Math.round(Math.random()*400):Math.max(0,Math.round(r.lag+(Math.random()-0.55)*20));
    r.lastHealthCheck=telcoxNow();
    const desired=clampReplicas(s,r.cpu);
    if(desired!==r.replicas){
      const old=r.replicas; r.replicas+=desired>r.replicas?1:-1;
      logScale(s.name,`${old} → ${r.replicas} pod`,`HPA: CPU %${r.cpu}`);
      if(r.cpu>90) telcoxPushEvent(s.name,`Auto Scaling başlatıldı — CPU %${r.cpu}, pod ${old}→${r.replicas}`,'warn');
    }
  });
  const totalPods=TELCOX_SERVICES.reduce((a,s)=>a+runtime[s.name].replicas,0);
  chartHistory.push({pods:totalPods,users:biz.activeUsers});
  if(chartHistory.length>30) chartHistory.shift();
  document.getElementById('kube-total-pods').textContent=totalPods;
  document.getElementById('kube-total-max').textContent=TELCOX_SERVICES.reduce((a,s)=>a+s.maxPods,0);
  document.getElementById('kube-active-users').textContent=fmt(biz.activeUsers);
  document.getElementById('kube-avg-cpu').textContent='%'+Math.round(TELCOX_SERVICES.reduce((a,s)=>a+(runtime[s.name].status==='down'?0:runtime[s.name].cpu),0)/TELCOX_SERVICES.length);
  document.getElementById('kube-scale-count').textContent=scaleEvents.length;
  synthLog();
  renderAll();
  if(openService) refreshDrawer();
}
function renderAll(){
  renderStats(); renderServiceStrip(); renderGrid(); renderAlarms(); renderIncidents();
  renderActivity(); renderOrders(); renderLogs();
  drawArea('revenue-chart',revenueHist,'#0057FF'); drawPods();
}
window.addEventListener('telcox-update',()=>{ renderActivity(); renderOrders(); syncCustomerCells(); });
function syncCustomerCells(){
  const st=telcoxGetState();
  if(st.activePlan) document.getElementById('elif-tariff').textContent=st.activePlan;
  if(st.invoicePaid) document.getElementById('elif-invoice-status').innerHTML='<span class="pill up"><span class="d"></span>PAID</span>';
  const tt=document.getElementById('tickets-table');
  const live=(st.tickets||[]).map(t=>`<tr><td class="mono">#TCK-${t.id}</td><td>Elif Aydın</td><td>${t.subject}</td><td>${t.category}</td><td><span class="pill degraded"><span class="d"></span>OPEN</span></td></tr>`).join('');
  if(!tt.dataset.static) tt.dataset.static=tt.innerHTML;
  tt.innerHTML=tt.dataset.static.replace('</tr>','</tr>'+live);
}
syncCustomerCells();
document.getElementById('theme-toggle').addEventListener('click',toggleTheme);
document.getElementById('theme-icon').textContent = savedTheme==='dark'?'☀':'☾';
document.getElementById('admin-logout').addEventListener('click',()=>{ telcoxLogout(); window.location.href='TelcoX.html'; });
logScale('usage-service','3 → 5 pod','HPA: CPU %82');
renderAll();
setInterval(tick,2200);

/* ═══════════════════════════════════════════════════════════════════════
   GERÇEK BACKEND KATMANI (simülasyonun üstüne biner)
   - Servis UP/DOWN: gateway /ops/health (gerçek /actuator/health aggregatoru)
   - Ürünler / Ticket / Faturalama: gerçek API (gateway route, JWT relay)
   CPU/RAM/pod/HPA/log görselleri Prometheus olmadığı için simülasyon kalır.
   ═══════════════════════════════════════════════════════════════════════ */
(function(){
  const st = (typeof telcoxGuard==='function') ? telcoxGuard('admin') : telcoxGetState();
  if(st && st.name){
    const chip = document.querySelector('.profile-chip span');
    if(chip) chip.textContent = st.name;
    const av = document.querySelector('.profile-chip .avatar');
    if(av) av.textContent = (st.name||'OP').split(' ').map(w=>w[0]).slice(0,2).join('').toUpperCase();
  }

  // ---- 1) GERÇEK sağlık: /ops/health → runtime[].status ----
  async function realHealth(){
    const r = await api('/ops/health');
    if(!r.ok || typeof r.data!=='object') return;
    Object.entries(r.data).forEach(([name, state])=>{
      if(!runtime[name]) return;
      const up = state==='UP';
      if(up && runtime[name].status==='down'){
        runtime[name].status='up'; runtime[name].replicas=svcByName(name).minPods;
        runtime[name].latency=40; runtime[name].errRate=0.2; runtime[name].cpu=svcByName(name).baseCpu;
      } else if(!up){
        runtime[name].status='down'; runtime[name].replicas=0; runtime[name].latency=null; runtime[name].errRate=100;
      }
    });
    renderAll(); if(openService) refreshDrawer();
  }
  realHealth();
  setInterval(realHealth, 12000);

  // ---- 2) GERÇEK ürün kataloğu ----
  async function realProducts(){
    const r = await api('/api/v1/tariffs?size=50&sort=code,asc');
    if(!r.ok) return;
    const rows = asList(r.data);
    const tbl = document.getElementById('view-products').querySelector('table');
    if(!tbl) return;
    tbl.innerHTML = '<tr><th>Kod</th><th>Ad</th><th>Tip</th><th>Segment</th><th>Aylık Ücret</th><th>Ver.</th><th>Durum</th></tr>' +
      rows.map(t=>`<tr><td class="mono">${esc(t.code)}</td><td>${esc(t.name)}</td><td>${esc(t.type)}</td><td>${esc(t.targetSegment||'—')}</td><td class="mono">${fmtTL(t.monthlyFee)}</td><td class="mono">v${t.version}</td><td><span class="pill up"><span class="d"></span>${esc(t.status)}</span></td></tr>`).join('');
  }

  // ---- 3) GERÇEK ticket'lar ----
  async function realTickets(){
    const cid = '11111111-0000-4000-8000-000000000001';
    const r = await api(`/api/v1/tickets?customerId=${cid}&size=20`);
    if(!r.ok) return;
    const rows = asList(r.data);
    const tbl = document.getElementById('tickets-table');
    if(!tbl || !rows.length) return;
    const pc = {OPEN:'degraded', IN_PROGRESS:'degraded', RESOLVED:'up', CLOSED:'up'};
    tbl.dataset.static = '';
    tbl.innerHTML = '<tr><th>ID</th><th>Müşteri</th><th>Kategori</th><th>Öncelik</th><th>Ekip</th><th>Durum</th><th></th></tr>' +
      rows.map(t=>`<tr><td class="mono">#${t.id.slice(0,8)}</td><td class="mono">${t.customerId.slice(0,8)}</td><td>${esc(t.category)}</td><td>${esc(t.priority)}</td><td>${esc(t.assignedTeam||'—')}</td><td><span class="pill ${pc[t.status]||'degraded'}"><span class="d"></span>${esc(t.status)}</span></td>
        <td>${t.status==='OPEN'?`<button class="mini-btn" onclick="opsTicket('${t.id}','IP')">İşleme Al</button>`:t.status==='IN_PROGRESS'?`<button class="mini-btn" onclick="opsTicket('${t.id}','R')">Çöz</button>`:'—'}</td></tr>`).join('');
  }
  window.opsTicket = async (id, op)=>{
    let r;
    if(op==='IP') r = await api(`/api/v1/tickets/${id}/status`, {method:'PATCH', body: JSON.stringify({status:'IN_PROGRESS'})});
    else r = await api(`/api/v1/tickets/${id}/resolve`, {method:'POST'});
    if(r.ok){ telcoxPushEvent('ticket-service', `Ticket ${id.slice(0,8)} güncellendi`, 'ok'); realTickets(); }
    else alert('İşlem başarısız: HTTP '+r.status);
  };

  // ---- 4) GERÇEK faturalar ----
  async function realBilling(){
    const cid = '11111111-0000-4000-8000-000000000001';
    const r = await api(`/api/v1/invoices/customer/${cid}`);
    if(!r.ok) return;
    const rows = asList(r.data);
    const tables = document.getElementById('view-billing').querySelectorAll('table');
    const tbl = tables[tables.length-1];
    if(!tbl || !rows.length) return;
    const pc = {PENDING:'degraded', PAID:'up', OVERDUE:'down'};
    tbl.innerHTML = '<tr><th>Fatura</th><th>Dönem</th><th>Ara Toplam</th><th>KDV</th><th>Genel Toplam</th><th>Durum</th></tr>' +
      rows.map(i=>`<tr><td class="mono">${i.id.slice(0,8)}</td><td>${fmtDate(i.periodStart)}–${fmtDate(i.periodEnd)}</td><td class="mono">${fmtTL(i.subTotal)}</td><td class="mono">${fmtTL(i.tax)}</td><td class="mono">${fmtTL(i.grandTotal)}</td><td><span class="pill ${pc[i.status]||'degraded'}"><span class="d"></span>${esc(i.status)}</span></td></tr>`).join('');
  }

  const origGo = window.goView;
  window.goView = function(v){
    origGo(v);
    if(v==='products') realProducts();
    if(v==='tickets') realTickets();
    if(v==='billing') realBilling();
  };
  realProducts(); realTickets(); realBilling();
})();
