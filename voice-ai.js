/* 🎙️ DIP बोलकर पूछें — सभी ऐप में एक जैसा "AI सहायक"
 * पूरी तरह फ़्री: फ़ोन पर ही चलता है, कोई पैसे वाली AI सेवा या API key नहीं, अलग से डेटा नहीं लगता।
 * पूछने के लिए ऐप जो "तथ्य" (facts) देता है, उन्हीं में से जवाब ढूँढकर लिखता और बोलता है।
 * fact = { t: 'शीर्षक', d: 'जानकारी/कैसे करें', s: 'baki' | 'done' | 'info', k: 'और खोज-शब्द' }
 *
 * इस्तेमाल:  DipVoiceAI.init({ facts: async () => [...], title: 'DIP Control Panel', bottom: 18 })
 * सिर्फ़ जवाब चाहिए (अपना UI हो तो):  DipVoiceAI.answer('कितने काम बाकी हैं', facts)
 */
(function () {
  'use strict';
  const NUK = s => s.replace(/[़]/g, '').replace(/ॉ/g, 'ा').replace(/ं/g, 'ं');
  // बोलचाल के शब्द → एक जैसे शब्द (ताकि "पेंडिंग", "बचा", "बाकी" सब एक माने जाएँ)
  const SYN = [
    [/पेंडिंग|पेन्डिंग|बचा|बचे|बची|रुका|रुके|अधूर[ाेी]|pending|remaining|left/g, ' बाकी '],
    [/कंप्लीट|कम्प्लीट|पूर[ाेी]|हो गय[ाेी]|हो चुक[ाेी]|डन|done|complete[d]?/g, ' पूरा '],
    [/डिप|दीप|डीप|dip/g, ' dip '],
    [/मास्टर\s*कनेक्टर|मास्टर\s*कनेक्टर|master\s*connector|कनेक्टर|connector/g, ' connector '],
    [/सहायक|सहायता ऐप|sahayak/g, ' सहायक '],
    [/फ[ाा]यरबेस|फायरबेस|firebase/g, ' firebase '],
    [/रूल्स|रूल|नियम|rules?/g, ' rules '],
    [/टोकन|token/g, ' token '],
    [/लाइसेंस|लायसेंस|लाइसेन्स|license|licence|\bkey\b/g, ' license '],
    [/एपीके|ए\s*पी\s*के|apk|ऐप फ़ाइल/g, ' apk '],
    [/ग्राहक|कस्टमर|customer[s]?/g, ' ग्राहक '],
    [/वर्कर[सों]*|कर्मचारी|स्टाफ|worker[s]?|staff/g, ' वर्कर '],
    [/ड्यूटी|ड्युटी|हाजिरी|हाज़िरी|हाजरी|attendance|duty/g, ' ड्यूटी '],
    [/आदेश|कमांड|हुक्म|command[s]?/g, ' आदेश '],
    [/टेस्ट|जांच|जाँच|चेक|test|check/g, ' जाँच '],
    [/इंस्टॉल|इंस्टाल|डाउनलोड|install|download/g, ' इंस्टॉल '],
    [/दूर|दूरी|far|away/g, ' दूर '],
    [/ट्रेनिंग|ट्रेनी|training|trainee/g, ' ट्रेनिंग '],
    [/कंट्रोल\s*पैनल|पैनल|control\s*panel|panel/g, ' panel '],
    [/चालू|ऑन|on\b/g, ' चालू '], [/बंद|ऑफ|off\b/g, ' बंद ']
  ];
  const STOP = new Set('का की के को में से है हैं था थे और या भी तो ही पर कर करो करना करें करे क्या कैसे कब कौन कौनसा कौनसे कितने कितना कितनी बताओ बताइए बताएं बताना बता दो दीजिए जी मुझे मेरा मेरे मेरी हमारा हमारे अभी आज एक वो वह ये यह इस उस the a is are of to me my what how tell about please काम ऐप app वाला वाली वाले हुआ हुई होगा'.split(' '));
  const norm = s => {
    let x = ' ' + NUK(String(s || '').toLowerCase()) + ' ';
    x = x.replace(/[^\p{L}\p{M}\p{N}\s.]/gu, ' ');
    SYN.forEach(([re, w]) => { x = x.replace(re, w); });
    return x.replace(/\s+/g, ' ').trim();
  };
  const toks = s => norm(s).split(' ').filter(w => w.length > 1 && !STOP.has(w));

  function score(qt, f) {
    const hay = ' ' + norm(f.t + ' ' + (f.k || '') + ' ' + (f.d || '')) + ' ';
    const title = ' ' + norm(f.t + ' ' + (f.k || '')) + ' ';
    let sc = 0;
    qt.forEach(w => {
      if (w === 'बाकी' || w === 'पूरा') return;
      if (title.includes(' ' + w + ' ')) sc += 3; else if (hay.includes(' ' + w + ' ')) sc += 1.5;
      else if (w.length > 3 && hay.includes(w.slice(0, -1))) sc += 0.7;
    });
    return sc;
  }
  const join = (arr, n) => arr.slice(0, n).map((f, i) => (i + 1) + '. ' + f.t).join('\n');
  const ST = { baki: '⏳ अभी बाकी है', done: '✅ पूरा हो गया', info: 'ℹ️' };

  function answer(q, facts) {
    facts = facts || [];
    const n = norm(q), qt = toks(q);
    const baki = facts.filter(f => f.s === 'baki'), done = facts.filter(f => f.s === 'done');
    if (!n) return 'कुछ पूछिए — जैसे "कितने काम बाकी हैं?"';
    if (/मदद|help|क्या पूछ/.test(n)) return help(facts);
    // किसी ख़ास चीज़ के बारे में?
    const ranked = facts.map(f => ({ f, sc: score(qt, f) })).filter(x => x.sc >= 1.5).sort((a, b) => b.sc - a.sc || (a.f.s === 'baki' ? -1 : 0) - (b.f.s === 'baki' ? -1 : 0));
    const asksCount = /कितन|गिनती|count|how many/.test(n);
    const asksBaki = n.includes('बाकी') || /क्या करना|करना है|काम दो|todo/.test(n);
    const asksDone = n.includes('पूरा');
    const asksStatus = /हाल|स्थिति|स्टेटस|status|सब ठीक|क्या चल/.test(n);
    if (asksStatus && !(ranked.length && ranked[0].sc >= 3)) return summary(facts);
    if (ranked.length && ranked[0].sc >= 3) {
      let list = ranked.map(x => x.f);
      if (asksBaki) list = list.filter(f => f.s !== 'done').concat(list.filter(f => f.s === 'done'));
      if (asksDone) list = list.filter(f => f.s === 'done').concat(list.filter(f => f.s !== 'done'));
      const top = list.filter((f, i) => i === 0 || ranked.find(x => x.f === f).sc >= ranked[0].sc - 1).slice(0, 3);
      if (asksCount && top.length > 1) {
        const b = list.filter(f => f.s === 'baki').length;
        return 'इससे जुड़े ' + list.length + ' काम मिले — ' + b + ' बाकी।\n' + list.slice(0, 5).map(f => (f.s === 'done' ? '✅ ' : '⏳ ') + f.t).join('\n');
      }
      return top.map(f => (f.s === 'info' ? 'ℹ️ ' : ST[f.s] + ': ') + f.t + (f.d ? '\n   ' + f.d : '')).join('\n\n');
    }
    if (asksDone && !asksBaki) return done.length ? done.length + ' काम पूरे हो चुके हैं। हाल के:\n' + join(done.slice(-5).reverse(), 5) : 'अभी कोई पूरा काम दर्ज नहीं है।';
    if (asksCount || asksBaki) {
      if (!baki.length) return '🎉 कोई काम बाकी नहीं है!';
      return baki.length + ' काम बाकी हैं' + (done.length ? ' (' + done.length + ' पूरे)' : '') + ':\n' + join(baki, 6) + (baki.length > 6 ? '\n…और ' + (baki.length - 6) : '');
    }
    if (ranked.length) return ranked.slice(0, 2).map(x => (x.f.s === 'info' ? 'ℹ️ ' : ST[x.f.s] + ': ') + x.f.t + (x.f.d ? '\n   ' + x.f.d : '')).join('\n\n');
    if (/हाल|स्थिति|स्टेटस|status|सब ठीक|क्या चल|अपडेट|update/.test(n)) return summary(facts);
    return 'माफ़ कीजिए, ये समझ नहीं आया। ऐसे पूछिए:\n' + examples(facts);
  }
  function summary(facts) {
    const b = facts.filter(f => f.s === 'baki'), info = facts.filter(f => f.s === 'info');
    let t = info.slice(0, 4).map(f => 'ℹ️ ' + f.t).join('\n');
    t += (t ? '\n' : '') + (b.length ? '⏳ ' + b.length + ' काम बाकी — सबसे पहले: ' + b[0].t : '🎉 कोई काम बाकी नहीं');
    return t;
  }
  function examples(facts) {
    const ex = ['"कितने काम बाकी हैं?"', '"क्या-क्या पूरा हो गया?"', '"आज का हाल बताओ"'];
    const t = facts.find(f => f.s === 'baki'); if (t) ex.push('"' + toks(t.t).slice(0, 2).join(' ') + ' के बारे में बताओ"');
    return ex.join('\n');
  }
  function help(facts) { return '🎙️ बोलकर या लिखकर पूछिए:\n' + examples(facts) + '\n\nजवाब यहीं लिखा भी आएगा और 🔊 बोलकर भी।'; }

  /* ---------- काम की लिस्ट (NEXT_UPDATE.md) से तथ्य ---------- */
  function factsFromMd(md) {
    const out = [], seen = new Set(); let sec = '', log = false;
    String(md || '').split('\n').forEach(line => {
      const l = line.trim();
      if (/^#{1,3} /.test(l)) { sec = l.replace(/^#+ /, ''); log = /सुझाव लॉग|हमेशा का नियम|ज़रूरी लिंक/.test(sec); return; }
      if (log) return;
      const m = l.match(/^- \[( |x|X)\]\s*(.*)$/); if (!m) return;
      let txt = m[2].replace(/\*\*|`|~~/g, '').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1').trim();
      txt = txt.replace(/^\d{1,2}-\d{1,2}-\d{4}[^:—]{0,25}[:—]\s*/, '').trim();
      const key = txt.slice(0, 60); if (seen.has(key)) return; seen.add(key);
      const parts = txt.split(/ — |: /);
      const t = parts[0].length > 8 ? parts[0] : txt.slice(0, 90);
      const rest = txt.slice(t.length).replace(/^[\s—:,]+/, '');
      out.push({ t: t.slice(0, 110), d: rest.slice(0, 200), s: m[1] === ' ' ? 'baki' : 'done', k: sec });
    });
    return out;
  }

  /* ---------- सुनना / बोलना ---------- */
  function speak(t) {
    t = String(t).replace(/[⏳✅ℹ️🎉⚠️📍👷🔊🎙️•]/gu, '').replace(/\n+/g, '। ');
    try { if (window.MCNative && MCNative.speak) { MCNative.speak(t); return; } } catch (e) {}
    try { if (window.N && N.speak) { N.speak(t); return; } } catch (e) {}
    try {
      const ss = window.speechSynthesis; if (!ss) return; ss.cancel();
      const u = new SpeechSynthesisUtterance(t); u.lang = 'hi-IN'; u.rate = 0.95;
      const v = ss.getVoices().find(v => /hi[-_]IN/i.test(v.lang)); if (v) u.voice = v;
      ss.speak(u);
    } catch (e) {}
  }
  function listen(cb, onErr) {
    try { if (window.MCNative && MCNative.listen) { window.__dipVoice = t => (t && t !== '?' && t !== '!') ? cb(t) : onErr(t); MCNative.listen('hi-IN'); return true; } } catch (e) {}
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return false;
    try {
      const r = new SR(); r.lang = 'hi-IN'; r.interimResults = false; r.maxAlternatives = 1;
      r.onresult = e => cb(e.results[0][0].transcript);
      r.onerror = () => onErr('?'); r.start(); return true;
    } catch (e) { return false; }
  }

  /* ---------- बटन + डिब्बा ---------- */
  let OPT = null, box = null, FACTS = [], speakOn = true;
  function css() {
    const s = document.createElement('style');
    s.textContent = `.dva-fab{position:fixed;right:14px;z-index:9998;border:0;border-radius:28px;padding:12px 16px;font:600 15px system-ui,sans-serif;color:#1e0b36;background:linear-gradient(135deg,#fde68a,#f59e0b);box-shadow:0 6px 18px rgba(0,0,0,.35)}
.dva{position:fixed;left:8px;right:8px;z-index:9999;max-width:560px;margin:auto;background:#111827;color:#f3f4f6;border:1px solid #f59e0b;border-radius:16px;padding:12px;box-shadow:0 10px 30px rgba(0,0,0,.5);font:15px system-ui,sans-serif}
.dva h4{margin:0 0 8px;display:flex;justify-content:space-between;align-items:center;font-size:16px}.dva h4 button{background:none;border:0;color:#9ca3af;font-size:20px}
.dva .row{display:flex;gap:6px}.dva input{flex:1;min-width:0;padding:11px;border-radius:10px;border:1px solid #374151;background:#1f2937;color:#fff;font-size:15px}
.dva .b{border:0;border-radius:10px;padding:10px 12px;font-weight:700;background:#f59e0b;color:#1e0b36}.dva .b.alt{background:#374151;color:#fff}
.dva .ans{white-space:pre-wrap;margin-top:10px;max-height:45vh;overflow:auto;line-height:1.5;background:#0b1220;border-radius:10px;padding:10px}
.dva .chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.dva .chips button{border:1px solid #4b5563;background:#1f2937;color:#e5e7eb;border-radius:14px;padding:6px 10px;font-size:13px}
.dva .mut{color:#9ca3af;font-size:12px;margin-top:6px}`;
    document.head.appendChild(s);
  }
  /* ⚡ काम करवाना: OPT.action(q) → {text, buttons:[{label, run:async()=>text|{text,buttons}}], confirm:true}
     confirm वाले काम पर बोलकर "हाँ" कहें या ✅ दबाएँ → काम हो जाता है */
  let PEND = null, NEXT = null;
  function showAct(r, q) {
    const a = box.querySelector('.ans'); if (typeof r === 'string') r = { text: r };
    a.textContent = (q ? '❓ ' + q + '\n\n' : '') + r.text;
    PEND = r.confirm && r.buttons && r.buttons[0] ? r.buttons[0] : null; NEXT = r.next || null;
    if (r.buttons && r.buttons.length) {
      const d = document.createElement('div'); d.className = 'chips';
      r.buttons.forEach(b => { const x = document.createElement('button'); x.textContent = b.label; x.style.cssText = 'background:#f59e0b;color:#1e0b36;font-weight:700;border:0';
        x.onclick = async () => { PEND = null; x.disabled = true; a.textContent = '⏳ कर रहा हूँ…'; let o; try { o = await b.run(); } catch (e) { o = '❌ नहीं हुआ: ' + (e.message || e); } showAct(o || '✅ हो गया'); }; d.appendChild(x); });
      a.appendChild(d);
    }
    if (speakOn) speak(r.say || r.text);
  }
  async function ask(q) {
    const a = box.querySelector('.ans');
    const nq = norm(q);
    if (PEND && /^(हा|हां|हाँ|yes|ok|ओके|ठीक|कर दो|करो|जी)/.test(nq)) { const b = PEND; PEND = null; a.textContent = '⏳ कर रहा हूँ…'; let o; try { o = await b.run(); } catch (e) { o = '❌ नहीं हुआ: ' + (e.message || e); } showAct(o || '✅ हो गया', q); return; }
    if ((PEND || NEXT) && /^(नहीं|नही|no|रद्द|मत|cancel)/.test(nq)) { PEND = null; NEXT = null; showAct('❌ ठीक है, नहीं किया।', q); return; }
    if (NEXT) { const f = NEXT; NEXT = null; a.textContent = '⏳ देख रहा हूँ…'; let o; try { o = await f(q); } catch (e) { o = '❌ ' + (e.message || e); } showAct(o || '✅ हो गया', q); return; }
    if (OPT.action) { let r = null; a.textContent = '⏳ देख रहा हूँ…'; try { r = await OPT.action(q); } catch (e) { r = '❌ ' + (e.message || e); } if (r) { showAct(r, q); return; } }
    a.textContent = '⏳ देख रहा हूँ…';
    try { FACTS = (await OPT.facts()) || []; } catch (e) { FACTS = []; a.textContent = '❌ जानकारी नहीं मिल पाई: ' + (e.message || e); return; }
    const t = answer(q, FACTS); a.textContent = '❓ ' + q + '\n\n' + t; if (speakOn) speak(t);
  }
  function mic() {
    const inp = box.querySelector('input'), m = box.querySelector('.mut');
    m.textContent = '🎙️ बोलिए…';
    const ok = listen(t => { inp.value = t; m.textContent = ''; ask(t); },
      e => { m.textContent = e === '!' ? 'इस फ़ोन में बोलकर लिखने वाला Google ऐप नहीं मिला — कीबोर्ड का 🎤 दबाकर बोलें' : '🎙️ आवाज़ समझ नहीं आई — फिर से बोलें'; });
    if (!ok) { m.textContent = 'कीबोर्ड पर 🎤 वाला बटन दबाकर बोलें, फिर ➤ दबाएँ'; inp.focus(); }
  }
  function open() {
    if (box) { box.remove(); box = null; return; }
    box = document.createElement('div'); box.className = 'dva'; box.style.bottom = ((OPT.bottom || 18) + 56) + 'px';
    box.innerHTML = `<h4>🤖 ${OPT.title || 'AI सहायक'} — पूछिए<span><button class="sp" aria-label="आवाज़">${speakOn ? '🔊' : '🔇'}</button><button class="x" aria-label="बंद">✕</button></span></h4>
<div class="row"><input placeholder="जैसे: कितने काम बाकी हैं?" enterkeyhint="send"><button class="b m" aria-label="बोलें">🎙️</button><button class="b alt go" aria-label="पूछें">➤</button></div>
<div class="mut"></div><div class="chips">${(OPT.chips || ['कितने काम बाकी हैं?', 'आज का हाल बताओ', 'क्या-क्या पूरा हो गया?']).map(c => `<button>${c}</button>`).join('')}</div><div class="ans">${help([])}</div>`;
    document.body.appendChild(box);
    const inp = box.querySelector('input');
    box.querySelector('.x').onclick = open;
    box.querySelector('.sp').onclick = e => { speakOn = !speakOn; try { localStorage.setItem('dva_sp', speakOn ? '1' : '0'); } catch (x) {} e.target.textContent = speakOn ? '🔊' : '🔇'; if (!speakOn) try { speechSynthesis.cancel(); } catch (x) {} };
    box.querySelector('.m').onclick = mic;
    box.querySelector('.go').onclick = () => inp.value.trim() && ask(inp.value.trim());
    inp.onkeydown = e => { if (e.key === 'Enter' && inp.value.trim()) ask(inp.value.trim()); };
    box.querySelectorAll('.chips button').forEach(b => b.onclick = () => { inp.value = b.textContent; ask(b.textContent); });
  }
  function init(opt) {
    OPT = opt; try { speakOn = localStorage.getItem('dva_sp') !== '0'; } catch (e) {}
    if (document.querySelector('.dva-fab')) return;
    css();
    const f = document.createElement('button'); f.className = 'dva-fab'; f.textContent = '🎙️ पूछें'; f.style.bottom = (opt.bottom || 18) + 'px';
    f.setAttribute('aria-label', 'AI से बोलकर पूछें'); f.onclick = open; document.body.appendChild(f);
  }
  function remove() { document.querySelector('.dva-fab')?.remove(); box?.remove(); box = null; }
  window.DipVoiceAI = { init, remove, answer, factsFromMd, speak, listen, norm };
})();
