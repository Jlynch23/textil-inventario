// Helper CSRF compartido (#13, auditoría): wrapper de fetch() que agrega el
// token CSRF como header, leyéndolo de los <meta> que pone base.html. Usar en
// vez de fetch() directo en cualquier llamada AJAX que MUTE datos (POST/PUT/DELETE).
//
// Se carga en el <head> del layout, así queda definido como global ANTES de que
// corra cualquier script de página. Extraerlo del <script> inline es el primer
// paso para poder activar una Content-Security-Policy estricta más adelante.
function fetchConCsrf(url, options) {
    options = options || {};
    const token = document.querySelector('meta[name="_csrf"]').content;
    const header = document.querySelector('meta[name="_csrf_header"]').content;
    options.headers = options.headers || {};
    options.headers[header] = token;
    return fetch(url, options);
}
