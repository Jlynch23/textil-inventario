// Búsqueda instantánea (del lado del cliente) de la Carta de Colores.
// La tabla ya trae todos los colores, así que filtramos las filas en el navegador
// mientras el usuario escribe -- sin recargar la página. Matchea contra el nombre
// (mostrado y oficial) y el código FAST DYE, ignorando mayúsculas y tildes.
(function () {
  const input = document.getElementById('buscadorColores');
  const cuerpo = document.getElementById('tablaColoresBody');
  if (!input || !cuerpo) return;

  // Solo las filas de color (tienen data-buscar); excluye la fila de "sin resultados".
  const filas = Array.from(cuerpo.querySelectorAll('tr[data-buscar]'));
  const badge = document.getElementById('contadorColores');
  const total = filas.length;
  const sinResultados = document.getElementById('sinResultadosColores');

  // Normaliza a minúsculas y sin tildes para que "borgoña" matchee "borgona".
  function normalizar(s) {
    return (s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  function filtrar() {
    const q = normalizar(input.value.trim());
    let visibles = 0;
    filas.forEach(function (tr) {
      const txt = normalizar(tr.getAttribute('data-buscar'));
      const match = q === '' || txt.indexOf(q) !== -1;
      tr.hidden = !match;
      if (match) visibles++;
    });
    if (badge) badge.textContent = (q ? visibles + ' de ' + total : total) + ' colores';
    if (sinResultados) sinResultados.hidden = visibles !== 0;
  }

  input.addEventListener('input', filtrar);
})();
