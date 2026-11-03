/* Offline-Hülle: Die App selbst (Seite, Skripte, Symbole) wird zwischengespeichert, damit sie ohne Netz
   startet. Pläne und Konfiguration werden NICHT hier gespeichert – die holt die App selbst. */
const CACHE = 'nextlesson-huelle-v1';

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(['./', 'manifest.webmanifest', 'icon.svg'])).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.endsWith('/config.json')) return; // immer frisch vom Server

  // Seite: erst das Netz (damit Updates sofort ankommen), sonst die gespeicherte Fassung.
  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req)
        .then((r) => {
          const kopie = r.clone();
          caches.open(CACHE).then((c) => c.put('./', kopie));
          return r;
        })
        .catch(() => caches.match('./'))
    );
    return;
  }
  // Skripte, Stile, Symbole (der Dateiname enthält einen Hash): erst gespeichert, sonst Netz.
  e.respondWith(
    caches.match(req).then(
      (treffer) =>
        treffer ||
        fetch(req).then((r) => {
          if (r.ok) {
            const kopie = r.clone();
            caches.open(CACHE).then((c) => c.put(req, kopie));
          }
          return r;
        })
    )
  );
});
