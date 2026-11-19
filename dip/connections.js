/* =====================================================================
   DIP Control Panel — 🔗 कनेक्शन (एक ही मेनू में)
   • कौन सी सेवा/साइट जुड़ी है (लाइव जाँच)  • जोड़ें / बदलें / लॉगआउट
   • कौन सा अपडेट आया / आने वाला है (GitHub commits, builds, बाकी काम)
   सब फ़्री: GitHub API (आपके Token से, ETag — बदली न हो तो डेटा खर्च नहीं), Firebase फ़्री Spark।
   admin.html के TOK, ls(), CFG, b64dec() इस्तेमाल करता है।
   ===================================================================== */
const OWNER = 'thakurdip881-cloud';
const FBV = 'https://www.gstatic.com/firebasejs/11.0.2/';
const MOB_DOMAIN = 'worker.masterconnector.app';
const ORIGIN = location.origin;
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ago = t => { const m = Math.round((Date.now() - new Date(t)) / 60000); return m < 1 ? 'अभी' : m < 60 ? m + ' मिनट पहले' : m < 1440 ? Math.round(m / 60) + ' घंटे पहले' : Math.round(m / 1440) + ' दिन पहले'; };
const loginId = x => { let d = String(x || '').replace(/[\s\-+]/g, ''); if (/^91\d{10}$/.test(d)) d = d.slice(2); return /^[6-9]\d{9}$/.test(d) ? d + '@' + MOB_DOMAIN : String(x || '').trim(); };
const showId = e => String(e || '').endsWith('@' + MOB_DOMAIN) ? '📱 ' + e.split('@')[0] : e;
const st = (k, v) => { try { if (v === undefined) return localStorage.getItem(k); if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { return null; } };

const CSS = `.cx .it{display:flex;gap:10px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--line)}
.cx .it:last-child{border-bottom:0}.cx .ic{font-size:22px;line-height:1;width:28px;text-align:center}
.cx .bd{flex:1;min-width:0}.cx .nm{font-weight:bold;display:flex;gap:6px;align-items:center;flex-wrap:wrap}
.cx .dt{font-size:12px;color:var(--mut);margin-top:3px;word-break:break-word}
.cx .ac{display:flex;gap:6px;flex-wrap:wrap;margin-top:7px}
.cx .ac button,.cx .ac a{background:var(--tile);border:1px solid var(--line);color:var(--txt);border-radius:8px;padding:7px 10px;font-size:12px;text-decoration:none;cursor:pointer}
.cx .ac .rd{color:var(--redTxt);border-color:var(--red)}.cx .ac .gd{background:var(--acc);color:var(--accTxt);border-color:var(--acc)}
.cx .b-bad{background:var(--redSoft);color:var(--redTxt);border-color:var(--red)}
.cx .b-info{background:var(--blueSoft);color:var(--blueTxt);border-color:var(--blue)}
.cx .b-grey{background:var(--greySoft);color:var(--mut);border-color:var(--grey)}
.cx .sum{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.cx .sum div{background:var(--tile);border-radius:10px;padding:10px;text-align:center}
.cx .sum b{display:block;font-size:22px}.cx .sum small{font-size:11px;color:var(--mut)}
.cx .frm{background:var(--tile);border-radius:10px;padding:10px;margin-top:8px}
.cx .frm input,.cx .frm textarea{margin-top:6px}.cx .hd{display:flex;justify-content:space-between;align-items:center;gap:8px}
.cx .hd button{background:none;border:1px solid var(--line);color:var(--txt);border-radius:8px;padding:6px 10px;font-size:12px}
.cx .new{color:var(--acc);font-weight:bold}`;

let EL = null, FB = null, fbUser = undefined, timer = null, busy = false;
const R = { svc: {}, sites: [], commits: [], runs: [], pend: [], ver: {}, at: 0 };
const $ = id => document.getElementById(id);

/* ---------- GitHub (ETag cache: 304 = कोई डेटा/limit खर्च नहीं) ---------- */
async function gget(path, auth = true) {
  const key = 'cx_' + path, old = st(key);
  const h = { Accept: 'application/vnd.github+json' };
  if (auth && TOK) h.Authorization = 'Bearer ' + TOK;
  if (old) { try { h['If-None-Match'] = JSON.parse(old).e; } catch (e) { } }
  const r = await fetch('https://api.github.com' + path, { headers: h, cache: 'no-store' });
  if (r.status === 304 && old) { const o = JSON.parse(old); return { ok: true, status: 200, json: o.j, exp: o.x }; }
  if (!r.ok) return { ok: false, status: r.status };
  const j = await r.json(), x = r.headers.get('github-authentication-token-expiration') || '';
  try { st(key, JSON.stringify({ e: r.headers.get('ETag') || '', j, x })); } catch (e) { }
  return { ok: true, status: r.status, json: j, exp: x };
}

/* ---------- Firebase (MASTER CONNECTOR लॉगिन — 👷 वर्कर टैब वाला ही) ---------- */
async function fb() {
  if (FB) return FB; const C = window.MC_FIREBASE; if (!C || !C.apiKey) return null;
  const A = await import(FBV + 'firebase-app.js'), AU = await import(FBV + 'firebase-auth.js');
  const app = A.getApps().find(a => a.name === 'dipw') || A.initializeApp(C, 'dipw');
  FB = { AU, auth: AU.getAuth(app) };
  AU.onAuthStateChanged(FB.auth, u => { fbUser = u; R.svc.fb = fbSvc(); draw(); });
  return FB;
}
const fbSvc = () => fbUser === undefined ? { s: 'wait', d: 'जाँच हो रही है…' }
  : fbUser ? { s: 'ok', d: 'लॉगिन: ' + showId(fbUser.email) } : { s: 'off', d: 'लॉगिन नहीं है — 👷 वर्कर टैब और ON/OFF के लिए ज़रूरी' };

/* ---------- जाँच ---------- */
async function site(name, url, opt = {}) {
  const t0 = performance.now();
  try {
    const r = await fetch(url + (url.includes('?') ? '&' : '?') + 't=' + Date.now(), { cache: 'no-store', mode: opt.cors === false ? 'no-cors' : 'cors' });
    const ms = Math.round(performance.now() - t0);
    if (opt.cors === false) return { name, url, s: 'ok', d: 'खुल रही है · ' + ms + ' ms', note: opt.note };
    return { name, url, s: r.ok ? 'ok' : 'bad', d: (r.ok ? 'चालू' : 'दिक्कत (' + r.status + ')') + ' · ' + ms + ' ms', note: opt.note, txt: opt.read && r.ok ? await r.text() : '' };
  } catch (e) { return { name, url, s: 'bad', d: 'नहीं खुली (बंद या इंटरनेट नहीं)', note: opt.note }; }
}

async function checkAll(manual) {
  if (busy) return; busy = true; draw();
  try {
    // 1) सेवाएँ
    const sv = {};
    if (!TOK) sv.gh = { s: 'off', d: 'Token नहीं है' };
    else {
      const r = await gget('/repos/' + OWNER + '/Dip');
      if (r.ok) {
        let d = 'Dip repo जुड़ा', s = 'ok';
        // ब्राउज़र में GitHub खत्म-तारीख का header अक्सर नहीं मिलता → Token जोड़ते समय डाली तारीख (पहले से: 16 Feb 2027)
        const ex = r.exp ? new Date(r.exp.replace(' ', 'T').replace(/ ([+-]\d\d)(\d\d)$/, '$1:$2')) : new Date((st('cx_tokexp') || '2027-02-16') + 'T23:59:00');
        if (!isNaN(ex)) { const days = Math.floor((ex - Date.now()) / 864e5); d += ' · खत्म: ' + ex.toLocaleDateString('hi-IN', { day: 'numeric', month: 'short', year: 'numeric' }) + ' (' + days + ' दिन बाकी)'; if (days <= 15) s = 'warn'; if (days < 0) s = 'bad'; }
        sv.gh = { s, d };
      } else sv.gh = { s: 'bad', d: r.status === 401 ? 'Token गलत है या खत्म हो गया' : 'Dip repo नहीं खुला (' + r.status + ')' };
      const c = await gget('/repos/' + OWNER + '/dip-config');
      sv.cfg = c.ok ? { s: 'ok', d: 'ON/OFF सेटिंग वाला repo जुड़ा' } : { s: 'bad', d: 'dip-config repo नहीं खुला (' + c.status + ') — Token में यह repo चुनें' };
    }
    sv.lic = PRV ? { s: 'ok', d: 'License Key बना सकते हैं' } : { s: 'off', d: 'नहीं डाली — सिर्फ़ नई License Key बनाने के लिए चाहिए' };
    sv.tg = (CFG && CFG.telegram) ? { s: 'ok', d: '@' + CFG.telegram + ' — रजिस्ट्रेशन मैसेज यहीं आते हैं' } : { s: 'off', d: 'Telegram username नहीं डाला' };
    sv.fb = fbSvc();
    R.svc = sv;

    // 2) साइटें
    const S = await Promise.all([
      site('DIP Master AI ऐप', ORIGIN + '/master-connector/dip/'),
      site('MASTER CONNECTOR', ORIGIN + '/master-connector/'),
      site('ऐप लिंक पट्टी (assetlinks)', ORIGIN + '/.well-known/assetlinks.json'),
      site('ऐप ON/OFF सेटिंग (dip-config)', 'https://raw.githubusercontent.com/' + OWNER + '/dip-config/main/config.json'),
      site('Firebase डेटाबेस', 'https://firestore.googleapis.com/v1/projects/' + ((window.MC_FIREBASE || {}).projectId || 'x') + '/databases/(default)/documents/dip/control'),
      site('GoatCounter (यूज़र गिनती)', 'https://dipmaster.goatcounter.com/', { cors: false }),
      site('पुरानी Netlify साइट', 'https://github-netlify-deploy-0cc412.netlify.app/', { cors: false, note: 'credits खत्म — अब GitHub Pages इस्तेमाल होता है' }),
      site('DIP ऐप वर्ज़न', ORIGIN + '/master-connector/dip/sw.js', { read: true }),
      site('MC वर्ज़न', ORIGIN + '/master-connector/sw.js', { read: true })
    ]);
    const vm = S.pop(), vd = S.pop();
    R.ver = { dip: (vd.txt.match(/CACHE\s*=\s*'([^']+)'/) || [])[1] || '—', mc: (vm.txt.match(/const C\s*=\s*"([^"]+)"/) || [])[1] || '—' };
    R.sites = S;

    // 3) अपडेट: commits + builds + बाकी काम
    const [c1, c2, a1, a2, tg, md] = await Promise.all([
      gget('/repos/' + OWNER + '/master-connector/commits?per_page=6'),
      TOK ? gget('/repos/' + OWNER + '/Dip/commits?per_page=4') : { ok: false },
      gget('/repos/' + OWNER + '/master-connector/actions/runs?per_page=6'),
      TOK ? gget('/repos/' + OWNER + '/Dip/actions/runs?per_page=6') : { ok: false },
      gget('/repos/' + OWNER + '/master-connector/tags?per_page=30'),
      TOK ? gget('/repos/' + OWNER + '/Dip/contents/NEXT_UPDATE.md?ref=main') : { ok: false }
    ]);
    const cm = [];
    if (c1.ok) c1.json.forEach(c => cm.push({ r: 'MASTER CONNECTOR / DIP साइट', m: c.commit.message.split('\n')[0], t: c.commit.committer.date, u: c.html_url }));
    if (c2.ok) c2.json.forEach(c => cm.push({ r: 'Dip (ऐप/सहायक)', m: c.commit.message.split('\n')[0], t: c.commit.committer.date, u: c.html_url }));
    R.commits = cm.sort((a, b) => b.t.localeCompare(a.t)).slice(0, 8);
    const rn = [];
    const addRuns = (x, repo) => { if (x.ok) x.json.workflow_runs.forEach(w => rn.push({ n: w.name, repo, s: w.status, c: w.conclusion, t: w.created_at, u: w.html_url })); };
    addRuns(a1, 'master-connector'); addRuns(a2, 'Dip');
    R.runs = rn.sort((a, b) => b.t.localeCompare(a.t)).slice(0, 8);
    R.runsDipNo = TOK && !a2.ok;
    if (tg.ok) { const v = tg.json.map(t => +(t.name.match(/^apk-v(\d+)$/) || [])[1] || 0).sort((a, b) => b - a)[0]; R.ver.apk = v ? 'v' + v : '—'; }
    if (md.ok) {
      const txt = b64dec(md.json.content || '');
      R.pend = txt.split('\n').filter(l => /^\s*- \[ \]/.test(l) && !l.includes('जो भी नया बदलाव आप बताएँ')).map(l => l.replace(/^\s*- \[ \]\s*/, '').replace(/\*\*/g, ''));
    } else R.pend = null;
    R.at = Date.now();
  } catch (e) { console.warn(e); }
  busy = false; draw(); badge();
  if (manual) toast('✅ जाँच पूरी');
}

/* ---------- दिखाना ---------- */
const BADGE = { ok: ['b-ok', '✅ जुड़ा'], warn: ['b-warn', '⚠️ ध्यान दें'], bad: ['b-bad', '❌ दिक्कत'], off: ['b-grey', '⭕ नहीं जुड़ा'], wait: ['b-info', '⏳'] };
const bdg = (s, txt) => { const b = BADGE[s] || BADGE.wait; return '<span class="badge ' + b[0] + '">' + (txt || b[1]) + '</span>'; };
function toast(t) { const m = $('cxMsg'); if (m) { m.innerHTML = '<span class="ok">' + t + '</span>'; setTimeout(() => { if (m) m.textContent = ''; }, 2500); } }
const seenAt = () => +(st('cx_seen') || 0);

function svcRow(id, ic, name, acts) {
  const v = R.svc[id] || { s: 'wait', d: 'जाँच हो रही है…' };
  return '<div class="it"><div class="ic">' + ic + '</div><div class="bd"><div class="nm">' + name + ' ' + bdg(v.s) + '</div><div class="dt">' + esc(v.d) + '</div><div class="ac">' + acts(v) + '</div><div id="f_' + id + '"></div></div></div>';
}

function draw() {
  if (!EL) return;
  const okN = R.sites.filter(s => s.s === 'ok').length, badN = R.sites.filter(s => s.s === 'bad').length + Object.values(R.svc).filter(v => v.s === 'bad').length;
  const newN = R.commits.filter(c => new Date(c.t) > seenAt()).length;
  EL.innerHTML = '<style>' + CSS + '</style><div class="cx">' +
    '<div class="card"><div class="hd"><h2 style="margin:0">🔗 कनेक्शन</h2><button id="cxRe"' + (busy ? ' disabled' : '') + '>' + (busy ? '⏳ जाँच…' : '🔄 अभी जाँचें') + '</button></div>' +
    '<div class="dt" style="margin:4px 0 10px">' + (R.at ? 'आखिरी जाँच: ' + ago(R.at) + ' · यह टैब खुला रहे तो हर 10 मिनट अपने-आप' : 'जाँच हो रही है…') + '</div>' +
    '<div class="sum"><div><b style="color:var(--greenTxt)">' + okN + '/' + (R.sites.length || '—') + '</b><small>साइटें चालू</small></div><div><b style="color:' + (badN ? 'var(--redTxt)' : 'var(--greenTxt)') + '">' + badN + '</b><small>दिक्कत</small></div><div><b style="color:var(--acc)">' + newN + '</b><small>नए अपडेट</small></div></div><div class="msg" id="cxMsg"></div></div>' +

    '<div class="card"><h2>🔌 जुड़ी हुई सेवाएँ</h2>' +
    svcRow('gh', '🐙', 'GitHub Token', v => (v.s === 'off' ? '<button class="gd" data-x="ghSet">➕ जोड़ें</button>' : '<button data-x="ghSet">🔄 बदलें</button><a href="https://github.com/settings/personal-access-tokens" target="_blank" rel="noopener">⏳ समय बढ़ाएँ</a><button class="rd" data-x="ghOut">🚪 लॉगआउट</button>')) +
    svcRow('cfg', '⚙️', 'dip-config (ऐप ON/OFF)', () => '<a href="https://github.com/' + OWNER + '/dip-config" target="_blank" rel="noopener">खोलें</a>') +
    svcRow('fb', '🔥', 'Firebase (MASTER CONNECTOR लॉगिन)', v => v.s === 'ok' ? '<button class="rd" data-x="fbOut">🚪 लॉगआउट</button>' : v.s === 'off' ? '<button class="gd" data-x="fbIn">🔑 लॉगिन</button>' : '') +
    svcRow('lic', '🔑', 'License Private Key', v => v.s === 'ok' ? '<button class="rd" data-x="licOut">🗑️ हटाएँ</button>' : '<button class="gd" data-x="licSet">➕ जोड़ें</button>') +
    svcRow('tg', '✈️', 'Telegram', () => (CFG && CFG.telegram ? '<a href="https://t.me/' + esc(CFG.telegram) + '" target="_blank" rel="noopener">खोलें</a>' : '') + '<button data-x="tgSet">✏️ बदलें</button>') +
    '</div>' +

    '<div class="card"><h2>🌐 साइटें — लाइव जाँच</h2>' + (R.sites.length ? R.sites.map(s => '<div class="it"><div class="ic">' + (s.s === 'ok' ? '🟢' : '🔴') + '</div><div class="bd"><div class="nm">' + esc(s.name) + '</div><div class="dt">' + esc(s.d) + (s.note ? ' · ' + esc(s.note) : '') + '</div><div class="ac"><a href="' + esc(s.url) + '" target="_blank" rel="noopener">खोलें ↗</a></div></div></div>').join('') : '<div class="dt">जाँच हो रही है…</div>') + '</div>' +

    '<div class="card"><h2>📦 अभी के वर्ज़न</h2><div class="sum"><div><b style="font-size:14px">' + esc(R.ver.dip || '—') + '</b><small>DIP ऐप</small></div><div><b style="font-size:14px">' + esc(R.ver.mc || '—') + '</b><small>MASTER CONNECTOR</small></div><div><b style="font-size:14px">' + esc(R.ver.apk || '—') + '</b><small>MC APK</small></div></div></div>' +

    '<div class="card"><div class="hd"><h2 style="margin:0">🆕 नए अपडेट</h2>' + (newN ? '<button data-x="seen">✓ सब देखे</button>' : '') + '</div>' +
    (R.commits.length ? R.commits.map(c => { const n = new Date(c.t) > seenAt(); return '<div class="it"><div class="ic">' + (n ? '🆕' : '•') + '</div><div class="bd"><div class="' + (n ? 'new' : '') + '">' + esc(c.m) + '</div><div class="dt">' + esc(c.r) + ' · ' + ago(c.t) + ' · <a href="' + esc(c.u) + '" target="_blank" rel="noopener" style="color:var(--acc)">देखें</a></div></div></div>'; }).join('') : '<div class="dt">' + (busy ? 'लोड हो रहा है…' : 'जानकारी नहीं मिली') + '</div>') + '</div>' +

    '<div class="card"><h2>🏗️ अपडेट बन रहे हैं (builds)</h2>' +
    (R.runs.length ? R.runs.map(w => '<div class="it"><div class="ic">' + (w.s !== 'completed' ? '⏳' : w.c === 'success' ? '✅' : '❌') + '</div><div class="bd"><div class="nm">' + esc(w.n) + '</div><div class="dt">' + esc(w.repo) + ' · ' + (w.s !== 'completed' ? 'अभी बन रहा है' : w.c === 'success' ? 'पूरा' : 'फ़ेल') + ' · ' + ago(w.t) + ' · <a href="' + esc(w.u) + '" target="_blank" rel="noopener" style="color:var(--acc)">देखें</a></div></div></div>').join('') : '<div class="dt">' + (busy ? 'लोड हो रहा है…' : 'जानकारी नहीं मिली') + '</div>') +
    (R.runsDipNo ? '<div class="note">Dip repo के APK builds देखने के लिए Token में <b>Actions: Read</b> की अनुमति जोड़ें।</div>' : '') + '</div>' +

    '<div class="card"><h2>⏭️ आने वाले अपडेट (बाकी काम)</h2>' +
    (R.pend === null ? '<div class="dt">GitHub Token जोड़ें तो यहाँ दिखेगा।</div>' : R.pend.length ? R.pend.map((p, i) => '<div class="it"><div class="ic">' + (i + 1) + '</div><div class="bd">' + esc(p) + '</div></div>').join('') : '<div class="dt">' + (busy ? 'लोड हो रहा है…' : '✅ कोई काम बाकी नहीं') + '</div>') + '</div>' +
    '</div>';
}

function badge() {
  const b = document.querySelector('[data-t="tCon"]'); if (!b) return;
  const bad = R.sites.filter(s => s.s === 'bad').length + Object.values(R.svc).filter(v => v.s === 'bad' || v.s === 'warn').length;
  const nw = R.commits.filter(c => new Date(c.t) > seenAt()).length;
  b.textContent = '🔗 कनेक्शन' + (bad ? ' ⚠️' + bad : '') + (nw ? ' 🆕' + nw : '');
}

/* ---------- बटन ---------- */
function form(id, html) { const f = $('f_' + id); if (f) f.innerHTML = '<div class="frm">' + html + '</div>'; }
async function act(x) {
  if (x === 'ghSet') form('gh', '<div class="dt">नया Token (Contents: Read & write — Dip और dip-config)</div><input id="cxTok" placeholder="github_pat_..." autocomplete="off" style="-webkit-text-security:disc"><div class="dt" style="margin-top:6px">Token कब खत्म होगा (GitHub पर दिखता है)</div><input id="cxExp" type="date" value="' + new Date(Date.now() + 90 * 864e5).toISOString().slice(0, 10) + '"><div class="ac"><button class="gd" data-x="ghSave">✅ जाँचकर सेव करें</button><a href="https://github.com/settings/personal-access-tokens/new" target="_blank" rel="noopener">➕ नया Token बनाएँ</a></div>');
  else if (x === 'ghSave') {
    const t = ($('cxTok').value || '').trim(); if (!/^github_pat_\w{70,}$/.test(t)) return toast('❌ यह GitHub Token नहीं लग रहा');
    const r = await fetch('https://api.github.com/repos/' + OWNER + '/Dip', { headers: { Authorization: 'Bearer ' + t } });
    if (!r.ok) return toast('❌ Token से Dip repo नहीं खुला (' + r.status + ')');
    ls('dp_tok', t); TOK = t; if ($('cxExp').value) st('cx_tokexp', $('cxExp').value); Object.keys(localStorage).filter(k => k.startsWith('cx_')).forEach(k => st(k, null)); toast('✅ नया Token सेव हुआ'); checkAll();
  }
  else if (x === 'ghOut') { if (confirm('GitHub Token, पैनल पासवर्ड और License Key इस फ़ोन से हटाकर लॉगआउट करें?')) { ls('dp_tok', null); ls('dp_prv', null); ls('dp_pw', null); location.reload(); } }
  else if (x === 'fbIn') form('fb', '<div class="dt">MASTER CONNECTOR वाला मोबाइल/ईमेल और पासवर्ड</div><input id="cxE" placeholder="मोबाइल या ईमेल" autocomplete="username"><input id="cxP" type="password" placeholder="पासवर्ड" autocomplete="current-password"><div class="ac"><button class="gd" data-x="fbGo">🔑 लॉगिन करें</button></div>');
  else if (x === 'fbGo') { const f = await fb(); if (!f) return toast('❌ Firebase सेटअप नहीं मिला'); try { await f.AU.signInWithEmailAndPassword(f.auth, loginId($('cxE').value), $('cxP').value); toast('✅ Firebase लॉगिन हो गया'); } catch (e) { toast('❌ मोबाइल/ईमेल या पासवर्ड गलत है'); } }
  else if (x === 'fbOut') { const f = await fb(); if (f && confirm('Firebase (MASTER CONNECTOR) से लॉगआउट करें? 👷 वर्कर टैब के लिए दोबारा लॉगिन करना होगा।')) { await f.AU.signOut(f.auth); toast('लॉगआउट हो गया'); } }
  else if (x === 'licSet') form('lic', '<div class="dt">license-private-key.txt का पूरा टेक्स्ट पेस्ट करें</div><textarea id="cxL" placeholder=\'{"kty":"EC",...}\' autocomplete="off" spellcheck="false"></textarea><div class="ac"><button class="gd" data-x="licSave">✅ सेव करें</button></div>');
  else if (x === 'licSave') { const p = ($('cxL').value || '').trim(); try { const j = JSON.parse(p); if (j.kty !== 'EC' || !j.d) throw 0; } catch (e) { return toast('❌ Private Key का टेक्स्ट सही नहीं है'); } ls('dp_prv', p); PRV = p; toast('✅ License Key सेव हुई'); checkAll(); }
  else if (x === 'licOut') { if (confirm('इस फ़ोन से License Private Key हटाएँ? (नई License Key नहीं बन पाएगी)')) { ls('dp_prv', null); PRV = ''; checkAll(); } }
  else if (x === 'tgSet') { const b = document.querySelector('[data-t="tSet"]'); if (b) b.click(); }
  else if (x === 'seen') { st('cx_seen', String(Date.now())); draw(); badge(); }
}

export function mount(el) {
  EL = el; draw();
  el.addEventListener('click', e => { const b = e.target.closest('[data-x]'); if (b) act(b.dataset.x); if (e.target.id === 'cxRe') checkAll(true); });
  fb().catch(() => { fbUser = null; });
  checkAll();
  clearInterval(timer);
  timer = setInterval(() => { if (!document.hidden && EL && EL.offsetParent) checkAll(); }, 600000);
}
// पैनल खुलते ही एक हल्की जाँच (टैब पर ⚠️/🆕 गिनती दिखाने के लिए), फिर हर 30 मिनट — सिर्फ़ जब पैनल खुला हो
export function background() {
  fb().catch(() => { fbUser = null; });
  setTimeout(() => checkAll(), 4000);
  setInterval(() => { if (!document.hidden && !(EL && EL.offsetParent)) checkAll(); }, 1800000);
}
