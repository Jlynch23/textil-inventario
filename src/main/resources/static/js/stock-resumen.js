// Vista de Stock: alterna "Resumen por color" ↔ "Detalle por artículo" y expande
// cada color para ver dónde está y en qué RIB. JS externo (CSP: script-src 'self').
(function () {
    var btnResumen = document.getElementById('vista-resumen');
    var btnDetalle = document.getElementById('vista-detalle');
    var paneResumen = document.getElementById('pane-resumen');
    var paneDetalle = document.getElementById('pane-detalle');

    function mostrar(cual) {
        if (!paneResumen || !paneDetalle) return;
        var esResumen = cual !== 'detalle';
        paneResumen.hidden = !esResumen;
        paneDetalle.hidden = esResumen;
        if (btnResumen) { btnResumen.classList.toggle('on', esResumen); btnResumen.setAttribute('aria-selected', esResumen ? 'true' : 'false'); }
        if (btnDetalle) { btnDetalle.classList.toggle('on', !esResumen); btnDetalle.setAttribute('aria-selected', !esResumen ? 'true' : 'false'); }
        try { localStorage.setItem('stockVista', esResumen ? 'resumen' : 'detalle'); } catch (e) {}
    }

    if (btnResumen) btnResumen.addEventListener('click', function () { mostrar('resumen'); });
    if (btnDetalle) btnDetalle.addEventListener('click', function () { mostrar('detalle'); });

    // Recordar la última vista elegida (por navegador).
    var guardado = null;
    try { guardado = localStorage.getItem('stockVista'); } catch (e) {}
    if (guardado === 'detalle') mostrar('detalle');

    // Expandir/colapsar una fila de color.
    document.querySelectorAll('.color-row .color-cells').forEach(function (celda) {
        function toggle() {
            var row = celda.closest('.color-row');
            var abierto = row.classList.toggle('open');
            celda.setAttribute('aria-expanded', abierto ? 'true' : 'false');
        }
        celda.addEventListener('click', toggle);
        celda.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
        });
    });
})();
