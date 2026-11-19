/* DIP Master AI — service worker (ऑफ़लाइन चलाने के लिए)
   नया वर्ज़न डालें तो CACHE का नाम बदल दें (v1 → v2), ताकि फ़ोन में नई फ़ाइलें आ जाएँ। */
const CACHE = 'dip-master-ai-v10';
const FILES = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png', './icon-maskable-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;            // Telegram वगैरह बाहर के लिंक: सीधे इंटरनेट से
  if (url.pathname.endsWith('status.json')) return;      // Admin ON/OFF हमेशा ताज़ा इंटरनेट से

  // पहले इंटरनेट से लें (नया वर्ज़न मिले), न मिले तो सेव की हुई कॉपी चलाएँ
  e.respondWith(
    fetch(req)
      .then(res => {
        if (res && res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
        return res;
      })
      .catch(() => caches.match(req).then(r => r || caches.match('./index.html')))
  );
});
