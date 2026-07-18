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

  // ---- 3) GERÇEK ticket'lar — TÜM müşteriler (portal talepleri dahil) ----
  const REAL = { customers:0, ordersToday:0, revenue:0, activeUsers:0, openTickets:0, upSvc:0, downSvc:0, custMap:{} };
  const setTxt = (id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };
  const sid = x => x ? String(x).slice(0,8) : '—';
  const custName = cid => { const c=REAL.custMap[cid]; return c ? (((c.firstName||'')+' '+(c.lastName||'')).trim()||c.companyName||sid(cid)) : sid(cid); };

  async function realTickets(){
    const r = await api('/api/v1/tickets/all?size=100');
    if(!r.ok) return;
    const rows = asList(r.data);
    REAL.openTickets = rows.filter(t=>t.status!=='RESOLVED'&&t.status!=='CLOSED').length;
    const tbl = document.getElementById('tickets-table');
    if(!tbl) return;
    const pc = {OPEN:'degraded', IN_PROGRESS:'degraded', RESOLVED:'up', CLOSED:'up'};
    tbl.dataset.static = '';
    tbl.innerHTML = '<tr><th>ID</th><th>Müşteri</th><th>Kategori</th><th>Öncelik</th><th>Atanan Ekip</th><th>Durum</th><th></th></tr>' +
      (rows.length ? rows.map(t=>`<tr><td class="mono">#${sid(t.id)}</td><td>${esc(custName(t.customerId))}</td><td>${esc(t.category)}</td><td>${esc(t.priority)}</td><td>${esc(t.assignedTeam||'—')}</td><td><span class="pill ${pc[t.status]||'degraded'}"><span class="d"></span>${esc(t.status)}</span></td>
        <td>${t.status==='OPEN'?`<button class="mini-btn" onclick="opsTicket('${t.id}','IP')">İşleme Al</button>`:t.status==='IN_PROGRESS'?`<button class="mini-btn" onclick="opsTicket('${t.id}','R')">Çöz</button>`:'—'}</td></tr>`).join('')
        : '<tr><td colspan="7" style="color:var(--ink-dim);">Henüz talep yok.</td></tr>');
  }
  window.opsTicket = async (id, op)=>{
    let r;
    if(op==='IP') r = await api(`/api/v1/tickets/${id}/status`, {method:'PATCH', body: JSON.stringify({status:'IN_PROGRESS'})});
    else r = await api(`/api/v1/tickets/${id}/resolve`, {method:'POST'});
    if(r.ok){ telcoxPushEvent('ticket-service', `Ticket ${sid(id)} güncellendi`, 'ok'); realTickets(); }
    else alert('İşlem başarısız: HTTP '+r.status);
  };

  // ---- 3b) GERÇEK müşteriler ----
  async function realCustomers(){
    const r = await api('/api/v1/customers');
    if(!r.ok) return;
    const rows = asList(r.data);
    REAL.customers = rows.length;
    REAL.custMap = {}; rows.forEach(c=> REAL.custMap[c.id]=c);
    const tbl = document.getElementById('customers-table');
    if(tbl) tbl.innerHTML = '<tr><th>Ad Soyad</th><th>Tip</th><th>Kimlik No</th><th>Durum</th><th>Kayıt</th></tr>' +
      rows.map(c=>{ const name=((c.firstName||'')+' '+(c.lastName||'')).trim()||c.companyName||'—';
        const ok = c.status==='ACTIVE'||c.status==='APPROVED';
        return `<tr><td>${esc(name)}</td><td>${esc(c.type||'—')}</td><td class="mono">${esc(c.identityNumber||'—')}</td><td><span class="pill ${ok?'up':'degraded'}"><span class="d"></span>${esc(c.status||'—')}</span></td><td class="mono">${fmtDate(c.createdAt)}</td></tr>`;
      }).join('');
  }

  // ---- 3c) GERÇEK siparişler (portal satın almaları) ----
  async function realOrders(){
    const r = await api('/api/v1/orders');
    if(!r.ok) return;
    const rows = asList(r.data);
    const today = new Date().toISOString().slice(0,10);
    REAL.ordersToday = rows.filter(o=>String(o.createdAt||'').slice(0,10)===today).length;
    const pc = s => s==='COMPLETED'||s==='ACTIVATED'?'up':(s==='CANCELLED'||s==='FAILED')?'down':'degraded';
    const html = '<tr><th>Sipariş</th><th>Müşteri</th><th>Tutar</th><th>Durum</th><th>Tarih</th></tr>' +
      (rows.length ? rows.slice(0,25).map(o=>`<tr><td class="mono">${sid(o.id)}</td><td>${esc(custName(o.customerId))}</td><td class="mono">${fmtTL(o.totalAmount)}</td><td><span class="pill ${pc(o.status)}"><span class="d"></span>${esc(o.status)}</span></td><td class="mono">${fmtDT(o.createdAt)}</td></tr>`).join('')
        : '<tr><td colspan="5" style="color:var(--ink-dim);">Henüz sipariş yok.</td></tr>');
    const ot=document.getElementById('orders-table'); if(ot) ot.innerHTML=html;
    const ro=document.getElementById('recent-orders'); if(ro) ro.innerHTML=html;
  }

  // ---- 3d) Aktif abone sayısı (monitoring: aktif kullanıcı) ----
  async function realSubs(){
    const r = await api('/api/v1/subscriptions/active');
    if(r.ok) REAL.activeUsers = asList(r.data).length;
  }

  // ---- 4) GERÇEK faturalar — tüm müşteriler ----
  async function realBilling(){
    const cids = Object.keys(REAL.custMap);
    if(!cids.length){ const c=await api('/api/v1/customers'); if(c.ok) asList(c.data).forEach(x=>REAL.custMap[x.id]=x); }
    let all = [];
    for(const cid of Object.keys(REAL.custMap).slice(0,15)){
      const r = await api(`/api/v1/invoices/customer/${cid}`);
      if(r.ok) asList(r.data).forEach(i=> all.push(Object.assign({_cid:cid}, i)));
    }
    all.sort((a,b)=> new Date(b.periodStart)-new Date(a.periodStart));
    REAL.revenue = all.filter(i=>i.status==='PAID').reduce((a,i)=>a+(+i.grandTotal||0),0);
    const pending = all.filter(i=>i.status==='PENDING').length;
    setTxt('bill-pending', pending);
    const tables = document.getElementById('view-billing').querySelectorAll('table');
    const tbl = tables[tables.length-1];
    if(tbl){
      const pc = {PENDING:'degraded', PAID:'up', OVERDUE:'down'};
      tbl.innerHTML = '<tr><th>Fatura</th><th>Müşteri</th><th>Dönem</th><th>Genel Toplam</th><th>Durum</th></tr>' +
        (all.length ? all.slice(0,30).map(i=>`<tr><td class="mono">${sid(i.id)}</td><td>${esc(custName(i._cid))}</td><td>${fmtDate(i.periodStart)}–${fmtDate(i.periodEnd)}</td><td class="mono">${fmtTL(i.grandTotal)}</td><td><span class="pill ${pc[i.status]||'degraded'}"><span class="d"></span>${esc(i.status)}</span></td></tr>`).join('')
          : '<tr><td colspan="5" style="color:var(--ink-dim);">Fatura yok.</td></tr>');
    }
  }

  // ---- 5) Dashboard istatistikleri + Monitoring (GERÇEK türetilmiş) ----
  function svcCounts(){
    let up=0,down=0,cpuSum=0,pods=0,maxPods=0;
    (typeof TELCOX_SERVICES!=='undefined'?TELCOX_SERVICES:[]).forEach(s=>{
      const r = (typeof runtime!=='undefined')?runtime[s.name]:null; if(!r) return;
      if(r.status==='down'){ down++; } else { up++; cpuSum+=(r.cpu||s.baseCpu||20); pods+=(r.replicas||s.minPods||1); }
      maxPods += (s.maxPods||1);
    });
    const n = (typeof TELCOX_SERVICES!=='undefined')?TELCOX_SERVICES.length:1;
    return { up, down, avgCpu: up? Math.round(cpuSum/up):0, pods, maxPods, total:n };
  }
  function renderStatsReal(){
    const s = svcCounts();
    REAL.upSvc=s.up; REAL.downSvc=s.down;
    // dashboard
    setTxt('st-customers', REAL.customers);
    setTxt('st-orders', REAL.ordersToday);
    setTxt('st-revenue', fmtTL(REAL.revenue));
    setTxt('st-tickets', REAL.openTickets);
    setTxt('st-services', s.up+'/'+s.total);
    setTxt('st-error', (s.down? (s.down+' down'):'%0.2'));
    // monitoring (gerçek: aktif abone + ayakta servis)
    setTxt('kube-active-users', REAL.activeUsers);
    setTxt('kube-total-pods', s.pods||s.up);
    setTxt('kube-total-max', s.maxPods);
    setTxt('kube-avg-cpu', '%'+s.avgCpu);
  }

  // ---- 6) Logs — gerçek servis sağlığı + son aktivite ----
  function realLogs(){
    const el = document.getElementById('log-stream'); if(!el) return;
    const svcF=(document.getElementById('log-svc-filter')||{}).value||'all';
    const lvlF=(document.getElementById('log-lvl-filter')||{}).value||'all';
    const now = new Date().toLocaleTimeString('tr-TR');
    let lines = [];
    (typeof TELCOX_SERVICES!=='undefined'?TELCOX_SERVICES:[]).forEach(sv=>{
      const r=(typeof runtime!=='undefined')?runtime[sv.name]:null; if(!r) return;
      if(r.status==='down') lines.push({lvl:'ERROR', service:sv.name, message:'health=DOWN — /actuator/health erişilemedi'});
      else lines.push({lvl:'INFO', service:sv.name, message:`health=UP · replicas=${r.replicas||1} · cpu=%${r.cpu||sv.baseCpu||20} · lat=${r.latency||30}ms`});
    });
    lines.push({lvl:'INFO', service:'ops-console', message:`${REAL.customers} müşteri · ${REAL.activeUsers} aktif abone · ${REAL.openTickets} açık talep · ${REAL.upSvc}/${(TELCOX_SERVICES||[]).length} servis UP`});
    if(svcF!=='all') lines=lines.filter(l=>l.service===svcF);
    if(lvlF!=='all') lines=lines.filter(l=>l.lvl===lvlF);
    el.innerHTML = lines.map(l=>`<div class="stream-line"><span class="lvl ${l.lvl}">${l.lvl}</span><span class="mono dim">${now}</span><span class="l-svc">${esc(l.service)}</span><span>${esc(l.message)}</span></div>`).join('') || '<div class="empty-note">Log yok.</div>';
  }

  // business sayılarını gerçek değerlere kilitle (sim rastgeleliğini etkisiz kılar)
  try {
    if(typeof biz==='object' && biz){
      Object.defineProperty(biz,'customers',{configurable:true,get:()=>REAL.customers||0,set:()=>{}});
      Object.defineProperty(biz,'ordersToday',{configurable:true,get:()=>REAL.ordersToday||0,set:()=>{}});
      Object.defineProperty(biz,'revenueToday',{configurable:true,get:()=>REAL.revenue||0,set:()=>{}});
      Object.defineProperty(biz,'activeUsers',{configurable:true,get:()=>REAL.activeUsers||0,set:()=>{}});
    }
  } catch(e){}

  // Sim'in renderStats/renderLogs'unu sarmalayıp GERÇEK değerlerle üzerine yaz
  // (sim alarm rozetlerini korur; st-* ve loglar bizim gerçek veriyle güncellenir).
  try {
    if(typeof renderStats==='function'){ const _rs=renderStats; renderStats=function(){ try{_rs();}catch(e){} renderStatsReal(); }; }
    if(typeof renderLogs==='function'){ renderLogs = realLogs; }
  } catch(e){}

  async function pollFast(){ await Promise.all([realTickets(), realOrders(), realSubs()]); renderStatsReal(); realLogs(); }
  async function pollSlow(){ await realCustomers(); await realOrders(); await realBilling(); renderStatsReal(); }

  const origGo = window.goView;
  window.goView = function(v){
    origGo(v);
    if(v==='products') realProducts();
    if(v==='tickets') realTickets();
    if(v==='customers') realCustomers();
    if(v==='orders') realOrders();
    if(v==='billing') realBilling();
    if(v==='logs') realLogs();
    if(v==='monitoring'||v==='dashboard') renderStatsReal();
  };

  // log filtreleri gerçek loglara bağlansın
  ['log-svc-filter','log-lvl-filter'].forEach(id=>{ const e=document.getElementById(id); if(e) e.addEventListener('change', realLogs); });

  // ilk yükleme + canlı poll
  (async ()=>{ await realCustomers(); await Promise.all([realProducts(), realTickets(), realOrders(), realSubs(), realBilling()]); renderStatsReal(); realLogs(); })();
  setInterval(pollFast, 8000);
  setInterval(pollSlow, 30000);
})();
