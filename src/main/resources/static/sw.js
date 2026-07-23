// Service worker de la PWA de TexControl.
//
// Objetivo: que la web sea INSTALABLE en el celular (agregar a pantalla de
// inicio, abrir en modo app sin barra del navegador) y que, sin conexion,
// muestre una pantalla amable en vez del dinosaurio del navegador.
//
// Estrategia deliberadamente conservadora porque es una app con sesion y datos
// que cambian seguido:
//   * Navegaciones (paginas HTML): SIEMPRE de la red primero (network-first).
//     Nunca se cachean las paginas: evitan mostrar stock/datos viejos o filtrar
//     una pagina de un usuario a otro. Si la red falla, se muestra offline.html.
//   * Estaticos propios (/img, /js, /css, iconos): cache-first, para que la app
//     abra rapido. Se actualizan solos cuando cambia la version del cache.
//
// Al cambiar estos archivos, subir CACHE_VERSION para invalidar el cache viejo.
const CACHE_VERSION = 'texcontrol-v1';
const OFFLINE_URL = '/offline.html';

// Recursos minimos que se guardan al instalar (la "cascara" + la pagina offline).
const PRECACHE = [
  OFFLINE_URL,
  '/img/pwa/icon-192.png',
  '/img/pwa/icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(PRECACHE))
  );
  // Activa este SW nuevo de inmediato, sin esperar a que se cierren las pestañas.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  // Borra caches de versiones anteriores.
  event.waitUntil(
    caches.keys().then((claves) =>
      Promise.all(claves.filter((c) => c !== CACHE_VERSION).map((c) => caches.delete(c)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;

  // Solo se maneja GET; POST/PUT (login, formularios) van directo a la red.
  if (req.method !== 'GET') return;

  // Navegaciones a paginas: network-first, con offline.html de respaldo.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req).catch(() => caches.match(OFFLINE_URL))
    );
    return;
  }

  // Estaticos del mismo origen: cache-first y, si no esta, red (y se guarda).
  const url = new URL(req.url);
  if (url.origin === self.location.origin &&
      /\.(png|jpg|jpeg|svg|ico|css|js|webmanifest|woff2?)$/.test(url.pathname)) {
    event.respondWith(
      caches.match(req).then((cacheada) =>
        cacheada || fetch(req).then((resp) => {
          const copia = resp.clone();
          caches.open(CACHE_VERSION).then((cache) => cache.put(req, copia));
          return resp;
        }).catch(() => cacheada)
      )
    );
  }
});
