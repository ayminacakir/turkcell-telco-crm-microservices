/* Müşteri portalı — GERÇEK veri overlay'i. Simülasyon ekranları (mağaza, esnek
   paket, kullanım analitiği) olduğu gibi kalır; aşağıdaki alanlar gerçek API'den
   doldurulur: ana sayfa kartları + kota çubukları, faturalar, talepler. */
(function(){
  const st = telcoxGetState();
  if(!st || !st.customerId) return;   // ops kullanıcısı ya da demo-offline: overlay yok

  const TPL_TR = {
    QUOTA_WARNING_80:'Paket kullanımınız %80\'e ulaştı', QUOTA_EXCEEDED:'Paket kotanız doldu',
    INVOICE_GENERATED:'Faturanız oluşturuldu', PAYMENT_RECEIVED:'Ödemeniz alındı',
    WELCOME_SMS:'Hattınız aktif edildi', TICKET_OPENED:'Talebiniz alındı', TICKET_RESOLVED:'Talebiniz çözüldü',
  };
  const setTxt = (id,v)=>{ const e=document.getElementById(id); if(e) e.textContent=v; };

  async function home(){
    const subs = await api(`/api/v1/subscriptions/customers/${st.customerId}`);
    const sub = asList(subs.data)[0];
    if(sub){
      const t = await api(`/api/v1/tariffs/${sub.tariffCode}`);
      if(t.ok) setTxt('active-plan-name', t.data.name);
      // Kota çubukları
      const q = await api(`/api/v1/usage/subscriptions/${sub.id}/quota`);
      if(q.ok && t.ok){
        const Q=q.data, T=t.data;
        paintQuota(0, 'Dakika', Q.minutesRemaining, T.minutesIncluded, v=>v+' dk');
        paintQuota(1, 'SMS', Q.smsRemaining, T.smsIncluded, v=>v);
        paintQuota(2, 'İnternet', Q.mbRemaining, T.dataMbIncluded, v=> v>=1024?(v/1024).toFixed(1)+' GB':v+' MB');
      }
    }
    const inv = await api(`/api/v1/invoices/customer/${st.customerId}`);
    if(inv.ok){
      const rows = asList(inv.data);
      const pending = rows.find(i=>i.status==='PENDING') || rows[0];
      if(pending){ setTxt('home-invoice-amount', fmtTL(pending.grandTotal));
        setTxt('home-invoice-meta', 'Son ödeme: '+fmtDate(pending.dueDate)); }
      paintInvoices(rows);
    }
    const tk = await api(`/api/v1/tickets?customerId=${st.customerId}&size=20`);
    if(tk.ok){
      const rows = asList(tk.data);
      const open = rows.filter(t=>t.status!=='RESOLVED'&&t.status!=='CLOSED').length;
      setTxt('home-ticket-count', open+' talep');
      setTxt('home-ticket-meta', open?'Yanıt bekleniyor':'Açık talebiniz yok');
      paintTickets(rows);
    }
    const nt = await api(`/api/v1/notifications?userId=${st.customerId}&size=6`);
    // (bildirim listesi profil ekranında yoksa atlanır)
  }

  function paintQuota(idx, label, rem, total, unit){
    const cards = document.querySelectorAll('#view-home .quota-card');
    const c = cards[idx]; if(!c || rem==null || !total) return;
    const usedPct = Math.max(0, Math.min(100, Math.round(100*(1-rem/total))));
    c.querySelector('.value').textContent = `${unit(rem)} / ${unit(total)} kaldı`;
    c.querySelector('.bar-fill').style.width = usedPct+'%';
    c.classList.toggle('warn', usedPct>=80);
  }

  function paintInvoices(rows){
    const tbl = document.querySelector('#view-invoices table'); if(!tbl || !rows.length) return;
    const pill = s => s==='PAID'?'<span class="status-pill paid">Ödendi</span>':s==='PENDING'?'<span class="status-pill pending">Bekliyor</span>':`<span class="status-pill pending">${esc(s)}</span>`;
    tbl.innerHTML = '<tr><th>Dönem</th><th>Tutar</th><th>Son Ödeme</th><th>Durum</th></tr>' +
      rows.map(i=>`<tr><td>${fmtDate(i.periodStart)} – ${fmtDate(i.periodEnd)}</td><td>${fmtTL(i.grandTotal)}</td><td>${fmtDate(i.dueDate)}</td><td>${pill(i.status)}</td></tr>`).join('');
  }

  function paintTickets(rows){
    const list = document.getElementById('ticket-list'); if(!list || !rows.length) return;
    const pill = s => (s==='RESOLVED'||s==='CLOSED')?'<span class="status-pill ok">Çözüldü</span>':'<span class="status-pill pending">'+esc(s)+'</span>';
    list.innerHTML = rows.map(t=>`<div class="ticket"><div><div class="t-title">${esc(t.category)} talebi</div>
      <div class="t-meta">#${t.id.slice(0,8)} · ${esc(t.priority)} · ${fmtDate(t.createdAt)}</div></div>${pill(t.status)}</div>`).join('');
  }

  // Yeni talep: gerçek POST /tickets (portalın simüle modalını by-pass eden basit prompt akışı)
  const nt = document.getElementById('open-ticket');
  if(nt) nt.addEventListener('click', async (e)=>{
    // Simülasyon modalı da açılabilir; gerçek kaydı arka planda oluşturmak için:
    setTimeout(async ()=>{
      const desc = (document.getElementById('ticket-desc')||{}).value;
      if(!desc) return;
      const r = await api('/api/v1/tickets', {method:'POST', body: JSON.stringify({
        customerId: st.customerId, category:'GENERAL', priority:'MEDIUM', description: desc })});
      if(r.ok) home();
    }, 300);
  }, true);

  if(document.readyState!=='loading') home(); else document.addEventListener('DOMContentLoaded', home);
})();
