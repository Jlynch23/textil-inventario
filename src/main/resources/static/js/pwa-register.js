// Registra el service worker de la PWA (app movil instalable). Se carga con
// `defer` en el <head> del layout. Si el navegador no soporta service workers,
// simplemente no hace nada (la web sigue funcionando igual).
if ('serviceWorker' in navigator) {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('/sw.js').catch(function (err) {
      console.warn('No se pudo registrar el service worker:', err);
    });
  });
}
