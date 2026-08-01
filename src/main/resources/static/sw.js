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
// v2 (M11): se agregan Bootstrap e iconos (servidos localmente en /webjars) al
// precache. Antes venian del CDN de jsdelivr, que el SW NO podia cachear (otro
// origen) -> offline / CDN bloqueado = app sin estilos ni JS.
const CACHE_VERSION = 'texcontrol-v3';
const OFFLINE_URL = '/offline.html';

// Recursos minimos que se guardan al instalar (la "cascara" + la pagina offline).
// Los webjars tienen la version en la URL, asi que son cache-first sin riesgo de
// quedar viejos (una version nueva cambia la URL). La fuente .woff2 la referencia
// el CSS con un ?hash y se cachea sola en la primera carga (regex de abajo).
const PRECACHE = [
  OFFLINE_URL,
  '/img/pwa/icon-192.png',
  '/img/pwa/icon-512.png',
  '/js/pwa-register.js',
  '/webjars/bootstrap/5.3.0/css/bootstrap.min.css',
  '/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js',
  '/webjars/bootstrap-icons/1.11.0/font/bootstrap-icons.css'
];

self.addEventListener('install', (event) => {
  // Auditoria: cache.addAll() es atomico -> si UN recurso falla (ej. una URL de
  // webjar mal escrita), la instalacion ENTERA se aborta y la PWA queda sin
  // cache. Se cachea cada recurso por separado y se toleran fallos individuales,
  // asi un recurso ausente no rompe el resto del precache.
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) =>
      Promise.all(PRECACHE.map((url) =>
        cache.add(url).catch((err) => console.warn('SW: no se pudo precachear', url, err))
      ))
    )
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
        }).catch(() => cacheada || Response.error())
      )
    );
  }
});
