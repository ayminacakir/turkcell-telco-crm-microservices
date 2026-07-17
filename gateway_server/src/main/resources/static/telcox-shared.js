/* TelcoX web arayuzu — ortak durum, kimlik ve API yardimcilari.
   Sayfalar gateway'den (localhost:8080) servis edilir; API cagrilari ayni origin'e
   gider (CORS yok). Token, Keycloak telco-crm realm'inden ROPC ile alinir. */

const TELCOX = {
  TOKEN_URL: 'http://localhost:8085/realms/telco-crm/protocol/openid-connect/token',
  CLIENT_ID: 'telcox-web',
  // scripts/seed-demo-data.sh ile ayni sabit kimlikler:
  USERS: {
    'elif.aydin': {
      name: 'Elif Aydın', msisdn: '0532 417 47 12',
      customerId: '11111111-0000-4000-8000-000000000001',
      subscriptionId: '22222222-0000-4000-8000-000000000001',
    },
    'ops': { name: 'Operasyon Ekibi', msisdn: null, customerId: null },
  },
};

function telcoxState() {
  try { return JSON.parse(sessionStorage.getItem('telcox') || '{}'); } catch { return {}; }
}
function telcoxSetState(patch) {
  sessionStorage.setItem('telcox', JSON.stringify({ ...telcoxState(), ...patch }));
}
function telcoxLogout() { sessionStorage.removeItem('telcox'); window.location.href = 'TelcoX.html'; }

/* Eski login sayfasi uyumlulugu */
function telcoxLogin(name, msisdn) { telcoxSetState({ name, msisdn, role: 'customer' }); }

/* Keycloak ROPC — basarisizsa demo modda (tokensiz) devam edilebilir. */
async function telcoxAuth(username, password, role) {
  const profile = TELCOX.USERS[username] || { name: username, customerId: null };
  try {
    const res = await fetch(TELCOX.TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password', client_id: TELCOX.CLIENT_ID, username, password,
      }),
    });
    if (!res.ok) return { ok: false, reason: 'Kullanıcı adı veya şifre hatalı (Keycloak ' + res.status + ')' };
    const tok = await res.json();
    telcoxSetState({
      token: tok.access_token, role, username,
      name: profile.name, msisdn: profile.msisdn,
      customerId: profile.customerId, subscriptionId: profile.subscriptionId,
    });
    return { ok: true };
  } catch (e) {
    return { ok: false, reason: 'Keycloak\'a ulaşılamadı (docker compose ayakta mı?)', offline: true };
  }
}

/* Girissiz sayfa acilirsa login'e dondur. */
function telcoxGuard(requiredRole) {
  const s = telcoxState();
  if (!s.name) { window.location.href = 'TelcoX.html'; return null; }
  if (requiredRole && s.role !== requiredRole) { window.location.href = 'TelcoX.html'; return null; }
  return s;
}

/* Ayni origin API cagrisi. Doner: {ok, status, data} — data JSON ya da metin. */
async function api(path, opts = {}) {
  const s = telcoxState();
  const headers = { ...(opts.headers || {}) };
  if (s.token) headers['Authorization'] = 'Bearer ' + s.token;
  if (opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  try {
    const res = await fetch(path, { ...opts, headers });
    const text = await res.text();
    let data; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { ok: res.ok, status: res.status, data };
  } catch (e) {
    return { ok: false, status: 0, data: String(e) };
  }
}

/* Sayfali (PageResponse) ya da duz liste cevaplarini normalize et. */
function asList(data) {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.content)) return data.content;
  return [];
}

const fmtTL = (v) => v == null ? '—' :
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(v);
const fmtDate = (v) => v ? new Date(v).toLocaleDateString('tr-TR') : '—';
const fmtDT = (v) => v ? new Date(v).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
