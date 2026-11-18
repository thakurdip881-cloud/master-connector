/* =====================================================================
   DIP Master AI — 👷 वर्कर हायरिंग + 📢 कमांड बोर्ड
   एक ही फ़ाइल दो जगह चलती है:
     • admin.html (Control Panel)  → mount(el,'admin') : आदेश भेजें, हायरिंग लिंक बनाएँ, टीम देखें
     • index.html (ऐप बोर्ड)       → mount(el,'worker'): नया वर्कर लिंक से जुड़े, login, आदेश देखे, "हो गया"
   डेटा Firebase (Firestore) में रहता है — सुरक्षा नियम: master-connector/firebase/firestore.rules
   Firebase की जानकारी mc-firebase.js में भरी जाती है। खाली हो तो "अभी चालू नहीं" दिखता है।
   ===================================================================== */
const V = '11.0.2', CDN = 'https://www.gstatic.com/firebasejs/' + V + '/';
const MOB_DOMAIN = 'worker.masterconnector.app';
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const mobDigits = x => { let d = String(x || '').replace(/[\s\-+]/g, ''); if (/^91\d{10}$/.test(d)) d = d.slice(2); if (/^0\d{10}$/.test(d)) d = d.slice(1); return /^[6-9]\d{9}$/.test(d) ? d : ''; };
const loginId = x => { const d = mobDigits(x); return d ? d + '@' + MOB_DOMAIN : String(x || '').trim(); };
const showId = e => String(e || '').endsWith('@' + MOB_DOMAIN) ? '📱 ' + e.split('@')[0] : e;
const roleName = r => ({ admin: 'Admin (मालिक)', worker: 'Worker', trainee: 'Trainee' }[r] || 'सिर्फ़ देखने वाला');
const fmtDT = t => t && t.toDate ? t.toDate().toLocaleString('en-IN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—';
const fmtD = t => t && t.toDate ? t.toDate().toLocaleDateString('en-IN') : '—';
const wa = t => 'https://wa.me/?text=' + encodeURIComponent(t);
const KINDS = [['msg', '💬 मैसेज', ''], ['duty', '▶ ड्यूटी शुरू करो', 'सब अपनी ड्यूटी शुरू करें'], ['dutyEnd', '■ ड्यूटी खत्म करो', 'सब अपनी ड्यूटी खत्म करें'], ['link', '🔗 लिंक भेजो', '']];
const kindIcon = k => ({ duty: '⏱', link: '🔗' }[k] || '💬');
// ऐप बोर्ड का पता (admin.html और index.html एक ही फ़ोल्डर में हैं)
const BOARD = new URL('./', location.href).href.split('?')[0].split('#')[0];
export const joinLink = code => BOARD + '?join=' + code;
export const workerLoginLink = () => BOARD + '?worker=1';

function herr(e) {
  const m = (e && (e.code || e.message)) || '';
  if (/permission/i.test(m)) return 'अनुमति नहीं है';
  if (/invalid-credential|wrong-password|invalid-login|user-not-found/i.test(m)) return 'मोबाइल/ईमेल या पासवर्ड गलत है';
  if (/email-already-in-use/.test(m)) return 'यह मोबाइल/ईमेल पहले से रजिस्टर है — Login करें';
  if (/weak-password/.test(m)) return 'पासवर्ड कम से कम 6 अक्षर का हो';
  if (/invalid-email/.test(m)) return 'ईमेल सही नहीं है';
  if (/network|unavailable/i.test(m)) return 'इंटरनेट नहीं है, फिर कोशिश करें';
  return m || 'कुछ गड़बड़ हुई';
}

const CSS = `.dw{font:15px/1.45 system-ui,"Noto Sans Devanagari",sans-serif;color:var(--txt,#e4e6e9)}
.dw .c{background:var(--card,#202328);border:1px solid var(--line,#363b43);border-radius:12px;padding:12px;margin:8px 0}
.dw h3{color:#e7b84a;font-size:17px;margin:12px 0 6px}
.dw input,.dw textarea,.dw select{width:100%;box-sizing:border-box;background:var(--inp,#262a30);color:var(--txt,#e4e6e9);border:1px solid var(--inpline,#4a515c);border-radius:8px;padding:10px;font:inherit;margin:4px 0}
.dw .b{background:#e7b84a;color:#1a1a1a;border:0;border-radius:8px;padding:9px 14px;font:inherit;font-weight:700;cursor:pointer}
.dw .g{background:#4caf7a;color:#fff}.dw .w{width:100%;margin-top:6px}
.dw .l{background:none;border:0;color:#e7b84a;font:inherit;padding:6px 8px;cursor:pointer;text-decoration:none}
.dw .r{color:#e57373}.dw .ok{color:#4caf7a}.dw .bad{color:#e57373}
.dw .m{color:var(--mut,#9aa1ab);font-size:13px}
.dw .row{display:flex;gap:6px;flex-wrap:wrap;align-items:center}
.dw .chip{border:1px solid var(--line,#363b43);border-radius:16px;padding:5px 11px;background:none;color:inherit;font:inherit;font-size:13px;cursor:pointer}
.dw .chip.on{background:#e7b84a;color:#1a1a1a;border-color:#e7b84a}
.dw .lnk{border:1px dashed #e7b84a;border-radius:8px;padding:8px;word-break:break-all;color:#e7b84a;font-size:13px;margin:6px 0}
.dw .new{border:2px solid #e7b84a}.dw button:disabled{opacity:.5}
.dw .sep{border-top:1px solid var(--line,#363b43);padding-top:6px;margin-top:6px}`;

let FB = null;   // {A,AU,F,app,auth,db}
async function fb() {
  if (FB) return FB;
  const CFG = window.MC_FIREBASE;
  if (!CFG || !CFG.apiKey) return null;
  const A = await import(CDN + 'firebase-app.js'), AU = await import(CDN + 'firebase-auth.js'), F = await import(CDN + 'firebase-firestore.js');
  const app = A.getApps().find(a => a.name === 'dipw') || A.initializeApp(CFG, 'dipw');
  const auth = AU.getAuth(app), db = F.getFirestore(app);
  if (CFG.emulator) { AU.connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true }); F.connectFirestoreEmulator(db, '127.0.0.1', 8080); }
  FB = { A, AU, F, app, auth, db, CFG };
  return FB;
}

/** el में पूरा हिस्सा बनाता है। mode: 'admin' | 'worker'. onBadge(n): नए आदेशों की गिनती (बोर्ड का बटन) */
export async function mount(el, mode, onBadge = () => { }) {
  if (!document.getElementById('dwcss')) { const s = document.createElement('style'); s.id = 'dwcss'; s.textContent = CSS; document.head.appendChild(s); }
  el.classList.add('dw');
  el.innerHTML = '<div class="c m">लोड हो रहा है…</div>';
  let X;
  try { X = await fb(); } catch (e) { el.innerHTML = '<div class="c bad">इंटरनेट नहीं है — फिर खोलें</div>'; return; }
  if (!X) { el.innerHTML = `<div class="c"><h3>👷 वर्कर हायरिंग अभी चालू नहीं</h3><p class="m">इसके लिए Firebase प्रोजेक्ट जोड़ना है। Firebase की फ़ाइल Claude को भेजते ही यह हिस्सा अपने-आप चालू हो जाएगा।</p></div>`; return; }
  const { AU, F, auth, db } = X;
  const { doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection, onSnapshot, query, where, orderBy, limit, writeBatch, serverTimestamp, Timestamp } = F;
  const $ = id => el.querySelector('#' + id);
  const val = id => ($(id)?.value || '').trim();
  let toastT; const toast = m => { let t = document.getElementById('dwToast'); if (!t) { t = document.createElement('div'); t.id = 'dwToast'; t.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#000d;color:#fff;padding:10px 16px;border-radius:10px;z-index:99999;max-width:90%;font:14px system-ui'; document.body.appendChild(t); } t.textContent = m; t.style.display = 'block'; clearTimeout(toastT); toastT = setTimeout(() => t.style.display = 'none', 3500); };
  const copy = t => (navigator.clipboard ? navigator.clipboard.writeText(t) : Promise.reject()).then(() => toast('✓ कॉपी हो गया')).catch(() => prompt('कॉपी करें:', t));

  let subs = [], ME = null, D = {}, joining = false;
  const unsub = () => { subs.forEach(u => u()); subs = []; D = {}; };
  const live = (k, q, map) => subs.push(onSnapshot(q, s => { D[k] = map(s); draw(); }, e => console.warn('dw', k, e.code || e)));
  const typing = () => { const a = document.activeElement; return a && el.contains(a) && /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName); };
  let later = false; el.addEventListener('focusout', () => setTimeout(() => { if (later && !typing()) { later = false; draw(); } }, 150));
  let q = 0; const draw = () => { if (!ME) return; if (typing()) { later = true; badge(); return; } if (q) return; q = requestAnimationFrame(() => { q = 0; mode === 'admin' ? drawAdmin() : drawWorker(); }); };

  let JOIN = mode === 'worker' ? new URLSearchParams(location.search).get('join') : null;
  const cleanUrl = () => { const u = new URL(location.href); u.searchParams.delete('join'); u.searchParams.delete('worker'); history.replaceState(null, '', u.pathname + u.search + u.hash); };

  /* ---------- Login / पहली बार setup / Join ---------- */
  async function showLogin() {
    onBadge(0);
    // मालिक का अकाउंट MASTER CONNECTOR ऐप में बना है — यहाँ उसी ईमेल/पासवर्ड से Login करें
    if (false) {
      el.innerHTML = `<div class="c"><h3>🔐 पहली बार: Admin अकाउंट बनाइए</h3><p class="m">यह सिर्फ़ एक बार होता है। इसके बाद कोई और Admin नहीं बन सकता।</p>
        <input id="n" placeholder="आपका नाम"><input id="e" placeholder="मोबाइल नंबर या ईमेल"><input id="p" type="password" placeholder="पासवर्ड (कम से कम 6)"><input id="p2" type="password" placeholder="पासवर्ड दोबारा">
        <button class="b w" id="go">Admin अकाउंट बनाएँ</button></div>`;
      $('go').onclick = async () => { const n = val('n'), e = loginId(val('e')), p = $('p').value;
        if (!n || !val('e')) return toast('नाम और मोबाइल/ईमेल भरें'); if (p.length < 6) return toast('पासवर्ड कम से कम 6 अक्षर'); if (p !== $('p2').value) return toast('दोनों पासवर्ड एक जैसे नहीं');
        $('go').disabled = true;
        try { const u = (await AU.createUserWithEmailAndPassword(auth, e, p)).user; const b = writeBatch(db);
          b.set(doc(db, 'users/' + u.uid), { name: n, email: e, role: 'admin', canEdit: true, active: true, createdAt: serverTimestamp() });
          b.set(doc(db, 'meta/owner'), { uid: u.uid }); await b.commit(); onUser(u);
        } catch (x) { toast(herr(x)); await AU.signOut(auth); $('go').disabled = false; } };
      return;
    }
    el.innerHTML = `<div class="c"><h3>${mode === 'admin' ? '🔐 Admin Login (वर्कर हायरिंग)' : '👷 वर्कर Login'}</h3>
      <input id="e" placeholder="मोबाइल नंबर या ईमेल"><input id="p" type="password" placeholder="पासवर्ड">
      <button class="b w" id="go">Login</button>${mode === 'worker' ? '<p class="m">नए हैं? Admin से हायरिंग लिंक माँगें।</p>' : '<p class="m">MASTER CONNECTOR वाला ही ईमेल और पासवर्ड डालें।</p>'}</div>`;
    $('go').onclick = async () => { if (!val('e') || !$('p').value) return toast('मोबाइल/ईमेल और पासवर्ड भरें'); $('go').disabled = true;
      try { await AU.signInWithEmailAndPassword(auth, loginId(val('e')), $('p').value); } catch (x) { toast(herr(x)); $('go').disabled = false; } };
    $('p').onkeydown = e => { if (e.key === 'Enter') $('go').click(); };
  }

  async function showJoin() {
    el.innerHTML = '<div class="c m">लिंक जाँच रहे हैं…</div>';
    let inv = null; try { const s = await getDoc(doc(db, 'invites/' + JOIN)); inv = s.exists() ? s.data() : null; } catch (e) { }
    const bad = t => { el.innerHTML = `<div class="c"><h3>${t}</h3><p class="m">Admin से नया लिंक माँगें। अकाउंट पहले से बना है तो Login करें।</p><button class="b" id="tl">Login पर जाएँ</button></div>`;
      $('tl').onclick = () => { JOIN = null; cleanUrl(); showLogin(); }; };
    if (!inv) return bad('यह लिंक सही नहीं है');
    if (inv.used) return bad('यह लिंक पहले ही इस्तेमाल हो चुका है');
    if (inv.exp && inv.exp.toMillis() < Date.now()) return bad('इस लिंक का समय खत्म हो गया');
    el.innerHTML = `<div class="c"><h3>🙏 DIP Master AI टीम में स्वागत है</h3><p class="m">आपको <b>${esc(roleName(inv.role))}</b> के रूप में जोड़ा जा रहा है। अपना अकाउंट बनाइए।</p>
      <input id="jn" placeholder="आपका नाम" value="${esc(inv.name)}"><input id="jm" inputmode="tel" maxlength="13" placeholder="मोबाइल नंबर (10 अंक)">
      <input id="jp" type="password" placeholder="नया पासवर्ड (कम से कम 6)"><input id="jp2" type="password" placeholder="पासवर्ड दोबारा">
      <button class="b g w" id="jgo">✅ अकाउंट बनाएँ</button><p class="m">आगे से इसी मोबाइल नंबर और पासवर्ड से Login करें — यहाँ भी और MASTER CONNECTOR ऐप में भी (ड्यूटी वहीं लगती है)।</p></div>`;
    $('jgo').onclick = async () => { const n = val('jn'), m = mobDigits(val('jm')), p = $('jp').value;
      if (!n) return toast('नाम भरें'); if (!m) return toast('सही 10 अंक का मोबाइल नंबर भरें'); if (p.length < 6) return toast('पासवर्ड कम से कम 6 अक्षर'); if (p !== $('jp2').value) return toast('दोनों पासवर्ड एक जैसे नहीं');
      $('jgo').disabled = true; $('jgo').textContent = 'बन रहा है…'; joining = true; let u = null;
      try { u = (await AU.createUserWithEmailAndPassword(auth, m + '@' + MOB_DOMAIN, p)).user;
        const b = writeBatch(db);
        b.set(doc(db, 'users/' + u.uid), { name: n, email: m + '@' + MOB_DOMAIN, mobile: m, role: inv.role, canEdit: false, active: true, shift: 'morning', invite: JOIN, createdAt: serverTimestamp() });
        b.update(doc(db, 'invites/' + JOIN), { used: true, usedBy: u.uid, usedAt: serverTimestamp() });
        await b.commit(); JOIN = null; cleanUrl(); joining = false; toast('✅ अकाउंट बन गया'); onUser(u);
      } catch (x) { joining = false; toast(herr(x)); if (u) { try { await AU.deleteUser(u); } catch (e) { await AU.signOut(auth); } } $('jgo').disabled = false; $('jgo').textContent = '✅ अकाउंट बनाएँ'; } };
  }

  let profU = null;
  function onUser(u) {
    unsub(); if (profU) { profU(); profU = null; } ME = null;
    if (!u) { try { if (mode === 'worker') localStorage.removeItem('dipw_on'); } catch (e) { } return JOIN ? showJoin() : showLogin(); }
    if (joining) return;
    profU = onSnapshot(doc(db, 'users/' + u.uid), { includeMetadataChanges: true }, s => {
      if (s.metadata.hasPendingWrites) return;
      const p = s.exists() ? { uid: u.uid, ...s.data() } : null;
      const changed = !ME || !p || ME.role !== p.role || ME.active !== p.active; ME = p;
      if (!p || p.active === false) { unsub(); onBadge(0); el.innerHTML = '<div class="c">आपका अकाउंट अभी चालू नहीं है। Admin से संपर्क करें।<br><button class="l" id="lo">Logout</button></div>'; $('lo').onclick = () => AU.signOut(auth); return; }
      if (mode === 'admin' && p.role !== 'admin' && p.canEdit !== true) { unsub(); el.innerHTML = '<div class="c">यह हिस्सा सिर्फ़ Admin के लिए है।<br><button class="l" id="lo">Logout</button></div>'; $('lo').onclick = () => AU.signOut(auth); return; }
      if (mode === 'worker') { try { localStorage.setItem('dipw_on', '1'); } catch (e) { } }
      if (changed) start();
    }, e => toast(herr(e)));
  }
  AU.onAuthStateChanged(auth, u => onUser(u));

  function start() {
    unsub();
    const cm = s => s.docs.map(d => ({ id: d.id, ...d.data() }));
    if (mode === 'admin') {
      live('cmds', query(collection(db, 'commands'), orderBy('createdAt', 'desc'), limit(60)), cm);
      live('acks', query(collection(db, 'acks'), orderBy('at', 'desc'), limit(1500)), s => s.docs.map(d => d.data()));
      live('invites', query(collection(db, 'invites'), orderBy('createdAt', 'desc'), limit(100)), cm);
      live('users', query(collection(db, 'users'), orderBy('name')), s => s.docs.map(d => ({ uid: d.id, ...d.data() })));
    } else {
      live('cmdAll', query(collection(db, 'commands'), where('to', '==', 'all'), limit(100)), cm);
      live('cmdMe', query(collection(db, 'commands'), where('to', '==', ME.uid), limit(100)), cm);
      live('acks', query(collection(db, 'acks'), where('uid', '==', ME.uid), limit(500)), s => s.docs.map(d => d.data()));
    }
    draw();
  }

  const myAck = id => (D.acks || []).find(a => a.cmd === id && a.uid === ME.uid);
  const myCmds = () => { const m = new Map(); [...(D.cmdAll || []), ...(D.cmdMe || [])].forEach(c => m.set(c.id, c)); return [...m.values()].sort((a, b) => (b.createdAt?.seconds ?? 9e12) - (a.createdAt?.seconds ?? 9e12)); };
  const ack = (c, status) => setDoc(doc(db, 'acks/' + c.id + '_' + ME.uid), { cmd: c.id, uid: ME.uid, name: ME.name, status, at: serverTimestamp() });
  let lastBuzz = '';
  function badge() { if (mode !== 'worker' || !ME) return; const f = myCmds().filter(c => !myAck(c.id)); onBadge(f.length);
    if (f.length && lastBuzz !== f[0].id) { lastBuzz = f[0].id; try { navigator.vibrate && navigator.vibrate([200, 100, 200]); } catch (e) { } } }
  const linkBtn = c => c.kind === 'link' && /^https:\/\//.test(c.ref || '') ? `<a class="l" target="_blank" rel="noopener noreferrer" href="${esc(c.ref)}">🔗 लिंक खोलें</a>` : '';

  /* ---------- वर्कर: आदेश देखें ---------- */
  const seen = new Set();
  function drawWorker() {
    badge();
    // खुला हुआ पैनल दिख रहा है तो नए आदेश "देख लिया" हो जाते हैं
    const visible = el.offsetParent !== null;
    const L = myCmds();
    if (visible) L.filter(c => !myAck(c.id) && !seen.has(c.id)).forEach(c => { seen.add(c.id); ack(c, 'seen').catch(() => { }); });
    el.innerHTML = `<div class="row"><b style="flex:1">👷 ${esc(ME.name)} · ${esc(roleName(ME.role))}</b><button class="l" id="lo">Logout</button></div><h3>📢 आपके लिए आदेश</h3>` +
      (L.length ? L.map(c => { const a = myAck(c.id), done = a && a.status === 'done';
        return `<div class="c ${a ? '' : 'new'}"><div style="white-space:pre-wrap"><b>${kindIcon(c.kind)} ${esc(c.text)}</b></div>
          <div class="m">${esc(c.byName || 'Admin')} · ${fmtDT(c.createdAt)} · ${c.to === 'all' ? 'सबके लिए' : 'सिर्फ़ आपके लिए'}</div>
          <div class="row">${linkBtn(c)}<span style="flex:1"></span>${done ? '<span class="ok">✅ हो गया</span>' : `<button class="b g" data-done="${c.id}">✅ हो गया</button>`}</div></div>`; }).join('')
        : '<p class="m">अभी कोई आदेश नहीं। Admin जब आदेश भेजेंगे, यहाँ तुरंत दिखेगा।</p>');
    $('lo').onclick = () => { if (confirm('Logout करना है?')) AU.signOut(auth); };
    el.querySelectorAll('[data-done]').forEach(b => b.onclick = async () => { b.disabled = true;
      try { await ack(L.find(x => x.id === b.dataset.done), 'done'); toast('✅ Admin को बता दिया'); } catch (e) { toast(herr(e)); b.disabled = false; } });
  }
  // पैनल खुलने पर ताज़ा करें (ताकि "देख लिया" लिखा जाए)
  el.dwRefresh = () => draw();

  /* ---------- Admin: कमांड बोर्ड + हायरिंग ---------- */
  const C = { kind: 'msg', to: 'all', ref: '', text: '', invRole: 'worker', invDays: 7, invName: '', lastInv: '' };
  const invText = i => 'नमस्ते ' + i.name + ' 🙏\nDIP Master AI टीम में जुड़ने के लिए यह लिंक खोलें और अपना अकाउंट बनाएँ:\n' + joinLink(i.id) + '\n\nयह लिंक सिर्फ़ आपके लिए है और ' + fmtD(i.exp) + ' तक चलेगा।';
  function newCode() { const b = new Uint8Array(15); crypto.getRandomValues(b); return btoa(String.fromCharCode(...b)).replace(/\+/g, '-').replace(/\//g, '_'); }
  function drawAdmin() {
    const U = (D.users || []).filter(u => u.role !== 'admin'), act = U.filter(u => u.active !== false), CM = D.cmds || [], IV = D.invites || [], AK = D.acks || [], now = Date.now();
    if (C.to !== 'all' && !U.some(u => u.uid === C.to)) C.to = 'all';
    const last = IV.find(i => i.id === C.lastInv);
    let h = `<div class="row"><span class="m" style="flex:1">Admin: ${esc(ME.name)}</span><button class="l" id="lo">Logout</button></div>
    <h3>📢 कमांड बोर्ड</h3><div class="c">
      <div class="row">${KINDS.map(([k, l]) => `<button class="chip ${k === C.kind ? 'on' : ''}" data-k="${k}">${l}</button>`).join('')}</div>
      ${C.kind === 'link' ? `<input id="cRef" placeholder="https://…" value="${esc(C.ref || 'https://')}">` : ''}
      <textarea id="cText" rows="3" maxlength="1000" placeholder="आदेश लिखें, जैसे: आज 9 बजे सब काम पर पहुँचें">${esc(C.text)}</textarea>
      <div class="m">किसको भेजें</div>
      <select id="cTo"><option value="all">👥 सबको (${act.length})</option>${U.map(u => `<option value="${u.uid}" ${u.uid === C.to ? 'selected' : ''}>${esc(u.name)} — ${esc(roleName(u.role))}${u.active ? '' : ' (बंद)'}</option>`).join('')}</select>
      <button class="b w" id="cSend">📤 आदेश भेजें</button>
      <p class="m">आदेश उसी पल वर्कर्स के फ़ोन पर (ऐप बोर्ड के 👷 बटन में और MASTER CONNECTOR ऐप में) दिखेगा। नीचे दिखेगा किसने देखा और किसने "हो गया" दबाया।</p></div>
    <h3>➕ नया वर्कर — हायरिंग लिंक</h3><div class="c">
      <input id="iName" placeholder="नए वर्कर का नाम" value="${esc(C.invName)}">
      <div class="row">${[['worker', 'Worker'], ['trainee', 'Trainee']].map(([k, l]) => `<button class="chip ${k === C.invRole ? 'on' : ''}" data-ir="${k}">${l}</button>`).join('')}
        <span class="m" style="margin-left:6px">लिंक चलेगा:</span>${[1, 7, 30].map(d => `<button class="chip ${d === C.invDays ? 'on' : ''}" data-days="${d}">${d} दिन</button>`).join('')}</div>
      <button class="b g w" id="iMake">🔗 हायरिंग लिंक बनाएँ</button>
      ${last && !last.used ? `<div class="lnk">${esc(joinLink(last.id))}</div><div class="row"><button class="b" data-icp="${last.id}">कॉपी करें</button><a target="_blank" rel="noopener" href="${wa(invText(last))}"><button class="b g" type="button">WhatsApp पर भेजें</button></a></div>` : ''}
      <p class="m">नया वर्कर लिंक खोलकर नाम, मोबाइल नंबर और पासवर्ड डालेगा और सीधे टीम में जुड़ जाएगा — उसकी जानकारी यहाँ अपने-आप आ जाएगी। हर लिंक सिर्फ़ एक बार चलता है।</p>
      <p class="m">पुराने वर्कर का लॉगिन लिंक (नए फ़ोन के लिए): <button class="l" id="wl">कॉपी करें</button></p></div>`;
    if (IV.length) h += `<div class="c"><b style="color:#e7b84a">भेजे गए हायरिंग लिंक</b>${IV.map(i => { const ex = !i.used && i.exp && i.exp.toMillis() < now;
      return `<div class="row sep"><span style="flex:1">${esc(i.name)} · ${esc(roleName(i.role))}<br>${i.used ? '<span class="ok">✅ जुड़ गया</span> <span class="m">' + fmtDT(i.usedAt) + '</span>' : ex ? '<span class="bad">⌛ समय खत्म</span>' : '<span class="m">⏳ इंतज़ार में · ' + fmtD(i.exp) + ' तक</span>'}</span>
        ${!i.used && !ex ? `<button class="l" data-icp="${i.id}">कॉपी</button><a class="l" target="_blank" rel="noopener" href="${wa(invText(i))}">WhatsApp</a>` : ''}<button class="l r" data-idel="${i.id}">हटाएँ</button></div>`; }).join('')}</div>`;
    h += `<h3>👥 टीम (${U.length})</h3>` + (U.length ? `<div class="c">${U.map(u => `<div class="row sep"><span style="flex:1"><b>${esc(u.name)}</b> · ${esc(roleName(u.role))}<br><span class="m">${esc(u.mobile ? '📱 ' + u.mobile : showId(u.email))} · जुड़ा ${fmtD(u.createdAt)}</span></span>
      <span class="${u.active ? 'ok' : 'bad'}">${u.active ? 'चालू' : 'बंद'}</span><button class="l" data-act="${u.uid}" data-v="${u.active ? 0 : 1}">${u.active ? 'बंद करें' : 'चालू करें'}</button></div>`).join('')}</div>` : '<p class="m">अभी टीम में कोई नहीं। ऊपर से हायरिंग लिंक भेजें।</p>');
    h += `<h3>भेजे गए आदेश (${CM.length})</h3>` + (CM.length ? CM.map(c => { const A = AK.filter(a => a.cmd === c.id), dn = A.filter(a => a.status === 'done'), sn = A.filter(a => a.status === 'seen'), tot = c.to === 'all' ? act.length : 1;
      return `<div class="c"><div style="white-space:pre-wrap"><b>${kindIcon(c.kind)} ${esc(c.text)}</b></div><div class="m">${c.to === 'all' ? '👥 सबको' : '👤 ' + esc(c.toName)} · ${fmtDT(c.createdAt)}</div>
        <div class="ok">✅ हो गया: ${dn.length}/${tot}${dn.length ? ' <span class="m">(' + esc(dn.map(a => a.name).join(', ')) + ')</span>' : ''}</div>
        <div class="m">👁 सिर्फ़ देखा: ${sn.length}${sn.length ? ' (' + esc(sn.map(a => a.name).join(', ')) + ')' : ''}</div>
        <div class="row">${linkBtn(c)}<span style="flex:1"></span><button class="l r" data-cdel="${c.id}">Delete</button></div></div>`; }).join('') : '<p class="m">अभी कोई आदेश नहीं भेजा</p>');
    el.innerHTML = h;
    $('lo').onclick = () => { if (confirm('Logout करना है?')) AU.signOut(auth); };
    $('wl').onclick = () => copy('DIP Master AI वर्कर लॉगिन: ' + workerLoginLink());
    $('cText').oninput = e => C.text = e.target.value; $('iName').oninput = e => C.invName = e.target.value; $('cTo').onchange = e => C.to = e.target.value;
    if ($('cRef')) $('cRef').oninput = e => C.ref = e.target.value;
    el.querySelectorAll('[data-k]').forEach(b => b.onclick = () => { const k = KINDS.find(x => x[0] === b.dataset.k); if (C.kind !== k[0]) C.ref = '';
      if (k[2] && (!C.text || KINDS.some(x => x[2] && x[2] === C.text))) C.text = k[2]; C.kind = k[0]; drawAdmin(); });
    $('cSend').onclick = async () => { const text = val('cText'); if (!text) return toast('आदेश लिखें');
      const kind = C.kind === 'dutyEnd' ? 'duty' : C.kind; let ref = '';
      if (kind === 'link') { ref = (C.ref || '').trim(); if (!/^https:\/\/./.test(ref)) return toast('सही https:// लिंक डालें'); }
      const u = U.find(x => x.uid === C.to); $('cSend').disabled = true;
      try { await addDoc(collection(db, 'commands'), { text, to: C.to, toName: u ? u.name : 'सब', kind, ref, by: ME.uid, byName: ME.name, createdAt: serverTimestamp() }); C.text = ''; C.ref = ''; toast('📤 आदेश भेज दिया'); drawAdmin(); }
      catch (e) { toast(herr(e)); if ($('cSend')) $('cSend').disabled = false; } };
    el.querySelectorAll('[data-cdel]').forEach(b => b.onclick = async () => { if (!confirm('यह आदेश हटाना है?')) return; const id = b.dataset.cdel;
      try { await deleteDoc(doc(db, 'commands/' + id)); AK.filter(a => a.cmd === id).forEach(a => deleteDoc(doc(db, 'acks/' + id + '_' + a.uid)).catch(() => { })); } catch (e) { toast(herr(e)); } });
    el.querySelectorAll('[data-ir]').forEach(b => b.onclick = () => { C.invRole = b.dataset.ir; drawAdmin(); });
    el.querySelectorAll('[data-days]').forEach(b => b.onclick = () => { C.invDays = +b.dataset.days; drawAdmin(); });
    $('iMake').onclick = async () => { const n = val('iName'); if (!n) return toast('नए वर्कर का नाम लिखें'); $('iMake').disabled = true; const code = newCode();
      try { await setDoc(doc(db, 'invites/' + code), { name: n, role: C.invRole, used: false, exp: Timestamp.fromDate(new Date(now + C.invDays * 864e5)), by: ME.name, createdAt: serverTimestamp() });
        C.lastInv = code; C.invName = ''; toast('✅ लिंक बन गया — अब WhatsApp पर भेजें'); drawAdmin(); }
      catch (e) { toast(herr(e)); if ($('iMake')) $('iMake').disabled = false; } };
    el.querySelectorAll('[data-icp]').forEach(b => b.onclick = () => { const i = IV.find(x => x.id === b.dataset.icp); copy(i ? invText(i) : joinLink(b.dataset.icp)); });
    el.querySelectorAll('[data-idel]').forEach(b => b.onclick = () => { if (confirm('यह हायरिंग लिंक हटाना है? (जो जुड़ चुका है उसका अकाउंट नहीं हटेगा)')) deleteDoc(doc(db, 'invites/' + b.dataset.idel)).catch(e => toast(herr(e))); });
    el.querySelectorAll('[data-act]').forEach(b => b.onclick = () => updateDoc(doc(db, 'users/' + b.dataset.act), { active: b.dataset.v === '1' }).catch(e => toast(herr(e))));
  }
}
