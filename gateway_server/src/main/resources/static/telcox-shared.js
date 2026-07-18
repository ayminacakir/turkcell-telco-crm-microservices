// Shared "backend simulation" for TelcoX prototype — aligned to
// github.com/ayminacakir/turkcell-telco-crm-microservices (real ports,
// endpoints, Kafka topics). localStorage acts as the fake event bus so
// portal + admin tabs share live state.

const TELCOX_SERVICES = [
  {name:'gateway-server', port:8080, kind:'infra', ctx:'Spring Cloud Gateway · JWT doğrulama (Keycloak)', health:'GET /actuator/health', interval:'10sn', minPods:2, maxPods:8, baseCpu:36,
    api:['Tüm /api/v1/** trafiği buradan geçer','OAuth2 resource server: realm telco-crm'],
    runbook:[
      {issue:'401/403 artışı', fix:'Keycloak (8085) ayakta mı ve issuer-uri http://localhost:8085/realms/telco-crm erişilebilir mi kontrol et.'},
      {issue:'Yüksek gecikme', fix:'Downstream servislerin health\'ine bak; Redis rate-limit bağlantısını (6379) doğrula.'}
    ]},
  {name:'eureka-server', port:8761, kind:'infra', ctx:'Service discovery (Eureka)', health:'GET /actuator/health', interval:'15sn', minPods:2, maxPods:3, baseCpu:18,
    api:['Dashboard: http://localhost:8761'],
    runbook:[
      {issue:'Instance kayboluyor', fix:'Self-preservation modunu ve ilgili servisin heartbeat\'ini kontrol et.'},
      {issue:'Servisler register olmuyor', fix:'defaultZone URL\'ini ve servislerin eureka.client konfigürasyonunu doğrula.'}
    ]},
  {name:'config-server', port:8888, kind:'infra', ctx:'Spring Cloud Config', health:'GET /actuator/health', interval:'15sn', minPods:2, maxPods:3, baseCpu:14,
    api:['Servisler: spring.config.import=configserver:http://localhost:8888','Kaynak: /configs klasörü'],
    runbook:[
      {issue:'Servis eski config okuyor', fix:'configs/ dizinindeki yaml\'ı doğrula, servise /actuator/refresh gönder.'},
      {issue:'Başlangıçta bağlanamıyor', fix:'"optional:" prefix\'i sayesinde servis yine kalkar; config-server\'ı başlatıp restart et.'}
    ]},
  {name:'customer-service', port:9002, ctx:'Müşteri, adres, KYC belgesi · customer_db (:5432)', health:'GET /actuator/health', interval:'10sn', minPods:2, maxPods:6, baseCpu:30,
    api:['POST/GET/PUT/DELETE /api/v1/customers','POST /api/v1/customers/{id}/kyc/approve|reject (ADMIN)','POST /api/v1/customers/{id}/addresses · /documents · /contacts'],
    runbook:[
      {issue:'KYC onayı 403 dönüyor', fix:'İsteği yapan token\'da ROLE_ADMIN var mı kontrol et (@PreAuthorize).'},
      {issue:'customer_db bağlantı hatası', fix:'customer-db container\'ı (5432) ayakta mı, connection pool doldu mu bak.'}
    ]},
  {name:'product-catalog-service', port:9003, ctx:'Tarife & addon kataloğu · product_catalog_db (:5433)', health:'GET /actuator/health', interval:'20sn', minPods:2, maxPods:5, baseCpu:18,
    api:['GET /api/v1/tariffs?status=ACTIVE','PATCH /api/v1/tariffs/{code}/price','GET /api/v1/addons?tariffCode=...','PUT /api/v1/addons/{addon}/tariffs/{tariff} (link)'],
    runbook:[
      {issue:'Stale fiyat verisi', fix:'PATCH /price sonrası cache invalidation\'ı kontrol et.'},
      {issue:'Addon-tarife bağlantı hatası', fix:'linkToTariff idempotent — 204 dönüyorsa ilişki zaten var, hata değil.'}
    ]},
  {name:'order-service', port:9004, ctx:'Sipariş & Saga (outbox) · order_db (:5434)', health:'GET /actuator/health', interval:'10sn', minPods:3, maxPods:10, baseCpu:40,
    api:['POST /api/v1/orders (productCode/productType ile)','GET /api/v1/orders/{id}','POST /api/v1/orders/{id}/cancel'],
    runbook:[
      {issue:'Saga adımı takıldı', fix:'SagaState tablosunu ve outbox event\'lerinin Kafka\'ya yazıldığını kontrol et.'},
      {issue:'Sipariş PENDING\'de kalıyor', fix:'payment.completed / subscription.activated consumer lag\'ine bak; ProductClient (Feign) product lookup hatası olabilir.'}
    ]},
  {name:'subscription-service', port:9005, ctx:'Abonelik, MSISDN havuzu, SIM · subscription_db (:5435)', health:'GET /actuator/health', interval:'10sn', minPods:2, maxPods:8, baseCpu:33,
    api:['POST /api/v1/subscriptions (aktivasyon)','POST /{id}/suspend · /reactivate · /terminate','PATCH /{id}/mnp-status','POST /api/v1/subscriptions/msisdns · /sim-cards'],
    runbook:[
      {issue:'MSISDN allocation hatası', fix:'MsisdnPool\'da FREE numara kaldı mı kontrol et, yeni blok ekle.'},
      {issue:'Aktivasyon gelmiyor', fix:'payment.completed event\'i tüketildi mi, Kafka consumer group offset\'ine bak.'}
    ]},
  {name:'usage-service', port:9006, ctx:'Kota & CDR kayıtları · usage_db (:5436)', health:'GET /actuator/health', interval:'5sn', minPods:3, maxPods:12, baseCpu:55,
    api:['POST /api/v1/usage/records (FR-17)','GET /api/v1/usage/subscriptions/{id}/quota (FR-18)','GET /api/v1/usage/subscriptions/{id}/history?from&to','POST /api/v1/usage/quotas'],
    runbook:[
      {issue:'Kafka consumer lag', fix:'CDR partition/consumer sayısını artır; quota.threshold.reached (%80) ve quota.exceeded (%100) event\'lerinin yayınlandığını doğrula.'},
      {issue:'Kota güncellenmiyor', fix:'UsageRecord idempotency\'sini ve aktif Quota dönemini kontrol et.'}
    ]},
  {name:'billing-service', port:9007, ctx:'Fatura & bill-run · billing_db (:5437)', health:'GET /actuator/health', interval:'20sn', minPods:2, maxPods:6, baseCpu:28,
    api:['POST /api/v1/invoices','GET /api/v1/invoices/customer/{customerId}','PATCH /api/v1/invoices/{id}/status?status=PAID','GET /api/v1/invoices/{id}/pdf','BillCycle & BillingRun controller\'ları'],
    runbook:[
      {issue:'Bill-run takıldı', fix:'BillingRunController job durumuna bak, kaldığı BillCycle\'dan devam ettir.'},
      {issue:'PDF üretim hatası', fix:'GET /{id}/pdf endpoint\'inin generatePdf çıktısını ve template\'i kontrol et.'}
    ]},
  {name:'payment-service', port:9008, ctx:'Ödeme & girişimler · payment_db (:5438)', health:'GET /actuator/health', interval:'10sn', minPods:2, maxPods:8, baseCpu:31,
    api:['POST /api/v1/payments (Idempotency-Key header)','POST /api/v1/payments/{id}/process','POST /api/v1/payments/{id}/refund','GET /api/v1/payments/{id}/attempts'],
    runbook:[
      {issue:'Mükerrer ödeme riski', fix:'Idempotency-Key header\'ının gönderildiğini doğrula; PaymentAttempt kayıtlarına bak.'},
      {issue:'process başarısız', fix:'payment.failed event\'i yayınlanır — retry politikasını ve attempt geçmişini (GET /attempts) incele.'}
    ]},
  {name:'notification-service', port:9009, ctx:'SMS / e-posta / push · notification_db (:5439)', health:'GET /actuator/health', interval:'15sn', minPods:2, maxPods:6, baseCpu:24,
    api:['POST /api/v1/notifications/send (manuel test)','Kafka consumer: payment.completed / payment.failed','Kafka consumer: quota.threshold.reached / quota.exceeded'],
    runbook:[
      {issue:'Bildirim gönderilmiyor', fix:'Kafka consumer\'ların (payment.*, quota.*) ayakta olduğunu Kafka UI (:9093) üzerinden kontrol et.'},
      {issue:'Pod CrashLoopBackOff', fix:'Son deploy\'u rollback et; pod loglarında OOM/exception ara (kubectl logs).'}
    ]},
  {name:'ticket-service', port:9010, ctx:'Talep & şikayet · ticket_db (:5440)', health:'GET /actuator/health', interval:'20sn', minPods:2, maxPods:5, baseCpu:22,
    api:['POST /api/v1/tickets','GET /api/v1/tickets?customerId=...','PATCH /api/v1/tickets/{id}/status','POST /api/v1/tickets/{id}/comments'],
    runbook:[
      {issue:'SLA ihlali artışı', fix:'Açık ticket dağılımına ve status update akışına bak.'},
      {issue:'Ticket oluşturulamıyor', fix:'customerId doğrulaması için customer-service health\'ini kontrol et.'}
    ]},
  {name:'keycloak', port:8085, kind:'infra', ctx:'Kimlik sağlayıcı · realm: telco-crm', health:'GET /health/ready', interval:'15sn', minPods:1, maxPods:2, baseCpu:26,
    api:['Token: /realms/telco-crm/protocol/openid-connect/token','Admin console: http://localhost:8085'],
    runbook:[
      {issue:'Login başarısız', fix:'Realm telco-crm ve client konfigürasyonunu kontrol et; admin/admin bootstrap hesabıyla console\'a gir.'},
      {issue:'JWT doğrulama hatası', fix:'Gateway\'in issuer-uri\'si ile realm URL\'inin birebir aynı olduğunu doğrula.'}
    ]},
  {name:'kafka', port:9092, kind:'infra', ctx:'Event bus (KRaft) · 3 partition, auto-create açık', health:'kafka-broker-api-versions.sh', interval:'10sn', minPods:1, maxPods:3, baseCpu:44,
    api:['Topic\'ler: payment.completed · payment.failed','quota.threshold.reached · quota.exceeded · subscription.activated','Kafka UI: http://localhost:9093'],
    runbook:[
      {issue:'Consumer lag birikiyor', fix:'Kafka UI\'dan (:9093) consumer group lag\'ini izle; partition sayısını artırmayı değerlendir.'},
      {issue:'Broker unhealthy', fix:'kafka-data volume doluluk ve controller quorum (1@kafka:9093) durumuna bak.'}
    ]},
  {name:'redis', port:6379, kind:'infra', ctx:'Cache & idempotency store', health:'PING', interval:'10sn', minPods:1, maxPods:2, baseCpu:12,
    api:['Gateway rate-limit ve payment idempotency anahtarları'],
    runbook:[
      {issue:'Bağlantı reddi', fix:'redis container\'ının (6379) ayakta olduğunu docker ps ile doğrula.'},
      {issue:'Bellek doluyor', fix:'maxmemory politikasını ve anahtar TTL\'lerini gözden geçir.'}
    ]},
  {name:'postgresql (9 DB)', port:5432, kind:'infra', ctx:'Database-per-service · postgres:17 (:5432–5440)', health:'pg_isready', interval:'15sn', minPods:1, maxPods:2, baseCpu:34,
    api:['customer_db 5432 · product_catalog_db 5433 · order_db 5434','subscription_db 5435 · usage_db 5436 · billing_db 5437','payment_db 5438 · notification_db 5439 · ticket_db 5440','pgAdmin: http://localhost:5050'],
    runbook:[
      {issue:'Bir servisin DB\'si erişilemiyor', fix:'İlgili container\'ı (docker ps) ve port eşlemesini kontrol et; volume bozulmuşsa yedekten dön.'},
      {issue:'Yavaş sorgular', fix:'pgAdmin (:5050) üzerinden pg_stat_statements ile uzun sorguları bul, index ekle.'}
    ]},
];

const EVENTS_KEY = 'telcox_events';
const METRICS_KEY = 'telcox_metrics';
const SESSION_KEY = 'telcox_session';
const STATE_KEY = 'telcox_state';

function telcoxNow(){
  return new Date().toLocaleTimeString('tr-TR', {hour:'2-digit', minute:'2-digit', second:'2-digit'});
}

function telcoxGetEvents(){
  try { return JSON.parse(localStorage.getItem(EVENTS_KEY) || '[]'); } catch(e){ return []; }
}

function telcoxPushEvent(service, message, level){
  level = level || 'info';
  const arr = telcoxGetEvents();
  arr.push({id: Date.now()+'-'+Math.random().toString(36).slice(2,7), ts: telcoxNow(), service, message, level});
  while(arr.length > 80) arr.shift();
  localStorage.setItem(EVENTS_KEY, JSON.stringify(arr));
  telcoxBumpMetric(service);
  window.dispatchEvent(new CustomEvent('telcox-update'));
}

function telcoxGetMetrics(){
  try { return JSON.parse(localStorage.getItem(METRICS_KEY) || '{}'); } catch(e){ return {}; }
}

function telcoxBumpMetric(service, weight){
  weight = weight || 1;
  const m = telcoxGetMetrics();
  if(!m[service]) m[service] = {requests:0, lastHit:0};
  m[service].requests += weight;
  m[service].lastHit = Date.now();
  localStorage.setItem(METRICS_KEY, JSON.stringify(m));
}

function telcoxGetSession(){
  try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); } catch(e){ return null; }
}

function telcoxLogin(name, msisdn){
  localStorage.setItem(SESSION_KEY, JSON.stringify({name, msisdn, loggedIn:true, since: telcoxNow()}));
  telcoxPushEvent('keycloak', `Token verildi (realm: telco-crm) — ${name}`, 'ok');
  telcoxPushEvent('gateway-server', 'JWT doğrulandı, oturum açıldı', 'ok');
}

function telcoxLogout(){
  localStorage.removeItem(SESSION_KEY);
}

function telcoxGetState(){
  try {
    return JSON.parse(localStorage.getItem(STATE_KEY) || '{}');
  } catch(e){ return {}; }
}

function telcoxSetState(patch){
  const s = Object.assign(telcoxGetState(), patch);
  localStorage.setItem(STATE_KEY, JSON.stringify(s));
  return s;
}

// Runs a saga: sequence of {service, message} steps, calling onStep(index, step) before
// each, and onDone() after the last. Each step is pushed as a live event.
function telcoxRunSaga(steps, onStep, onDone, stepDelay){
  stepDelay = stepDelay || 850;
  let i = 0;
  function next(){
    if(i >= steps.length){ onDone && onDone(); return; }
    const step = steps[i];
    onStep && onStep(i, step);
    setTimeout(()=>{
      telcoxPushEvent(step.service, step.message, step.level || 'ok');
      i++;
      setTimeout(next, 150);
    }, stepDelay);
  }
  next();
}

/* ═══════════════════════════════════════════════════════════════════════
   GERÇEK BACKEND KATMANI (simülasyonun üstüne)
   Bu bölüm gerçek servislere bağlanır: Keycloak login, JWT'li API çağrıları,
   gerçek /actuator/health yoklaması. Yukarıdaki simülasyon (CPU/pod/HPA/log)
   Prometheus olmadığı için görsel kalır; servis UP/DOWN durumu ise GERÇEKTİR.
   ═══════════════════════════════════════════════════════════════════════ */

const TELCOX = {
  TOKEN_URL: 'http://localhost:8085/realms/telco-crm/protocol/openid-connect/token',
  CLIENT_ID: 'telcox-web',
  // scripts/seed-demo-data.sh ile AYNI sabit kimlikler:
  USERS: {
    'elif.aydin': { name:'Elif Aydın', msisdn:'0532 417 47 12',
      customerId:'11111111-0000-4000-8000-000000000001',
      subscriptionId:'22222222-0000-4000-8000-000000000001' },
    'ops': { name:'Operasyon Ekibi', msisdn:null, customerId:null },
  },
};

/* Girilen değeri (kullanıcı adı / telefon / isim) Keycloak kullanıcı adına çevirir.
   Örn "0532 417 47 12", "05324174712", "Elif Aydın", "elif" → "elif.aydin". */
function telcoxResolveUsername(input){
  const raw = (input || '').trim();
  if (TELCOX.USERS[raw]) return raw;                 // zaten kullanıcı adı
  const norm = raw.toLocaleLowerCase('tr');
  const digits = raw.replace(/\D/g, '');
  for (const [uname, p] of Object.entries(TELCOX.USERS)) {
    if (p.name && p.name.toLocaleLowerCase('tr') === norm) return uname;         // tam isim
    if (p.name && p.name.toLocaleLowerCase('tr').split(' ')[0] === norm) return uname; // ilk ad
    if (p.msisdn && p.msisdn.replace(/\D/g, '') === digits && digits.length >= 10) return uname; // telefon
  }
  return raw;   // bulunamazsa girileni aynen dene (Keycloak karar versin)
}

/* Keycloak ROPC ile gerçek token. Başarısızsa {ok:false, offline?} döner. */
async function telcoxAuth(input, password, role){
  const username = telcoxResolveUsername(input);
  const profile = TELCOX.USERS[username] || { name: input, customerId: null };
  try {
    const res = await fetch(TELCOX.TOKEN_URL, {
      method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'},
      body: new URLSearchParams({ grant_type:'password', client_id:TELCOX.CLIENT_ID, username, password }),
    });
    if(!res.ok) return { ok:false, reason:'Kullanıcı adı veya şifre hatalı (Keycloak '+res.status+')' };
    const tok = await res.json();
    telcoxSetState({ token: tok.access_token, role, username, name: profile.name,
      msisdn: profile.msisdn, customerId: profile.customerId, subscriptionId: profile.subscriptionId });
    telcoxLogin(profile.name, profile.msisdn);   // simülasyon oturumu + canlı event
    return { ok:true };
  } catch(e){
    return { ok:false, offline:true, reason:'Keycloak\'a ulaşılamadı (docker compose ayakta mı?)' };
  }
}

function telcoxSession(){ try { return JSON.parse(localStorage.getItem(SESSION_KEY)||'null'); } catch { return null; } }

/* Girişsiz sayfayı login'e döndür. requiredRole verilirse rol de kontrol edilir. */
function telcoxGuard(requiredRole){
  const st = telcoxGetState();
  if(!st.name){ window.location.href = 'TelcoX.html'; return null; }
  if(requiredRole && st.role !== requiredRole){ window.location.href = 'TelcoX.html'; return null; }
  return st;
}

/* Aynı origin (gateway) API çağrısı. Token varsa Authorization ekler.
   Döner: {ok, status, data} — data JSON ya da metin. */
async function api(path, opts){
  opts = opts || {};
  const st = telcoxGetState();
  const headers = Object.assign({}, opts.headers||{});
  if(st.token) headers['Authorization'] = 'Bearer ' + st.token;
  if(opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  try {
    const res = await fetch(path, Object.assign({}, opts, {headers}));
    const text = await res.text();
    let data; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { ok: res.ok, status: res.status, data };
  } catch(e){ return { ok:false, status:0, data:String(e) }; }
}

/* GERÇEK sağlık yoklaması. TELCOX_SERVICES içindeki her HTTP servis için
   /actuator/health çağırır, callback'e (name, up|down|unknown) verir.
   CORS engeli olan servis 'unknown' döner (servis çalışıyor olabilir). */
async function telcoxHealthReal(onResult){
  const httpSvc = TELCOX_SERVICES.filter(s => s.port && s.port >= 8000 && s.kind !== undefined || (s.port>=9002 && s.port<=9010) || s.port===8080 || s.port===8761 || s.port===8888);
  await Promise.all(TELCOX_SERVICES.map(async s => {
    if(!s.port || s.port < 8000) { onResult(s.name, 'skip'); return; }
    const base = s.name==='gateway-server' ? '' : 'http://localhost:'+s.port;
    try {
      const res = await fetch(base + '/actuator/health', { signal: AbortSignal.timeout(4000) });
      onResult(s.name, res.ok ? 'up' : 'down');
    } catch(e){ onResult(s.name, 'unknown'); }
  }));
}

/* Sayfalı (PageResponse) ya da düz liste cevabını normalize et. */
function asList(data){
  if(Array.isArray(data)) return data;
  if(data && Array.isArray(data.content)) return data.content;
  return [];
}
const fmtTL = (v)=> v==null ? '—' : new Intl.NumberFormat('tr-TR',{style:'currency',currency:'TRY'}).format(v);
const fmtDate = (v)=> v ? new Date(v).toLocaleDateString('tr-TR') : '—';
const fmtDT = (v)=> v ? new Date(v).toLocaleString('tr-TR',{dateStyle:'short',timeStyle:'short'}) : '—';
const esc = (s)=> String(s ?? '').replace(/[&<>"]/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
