// Desplegable dependiente del form "Agregar Artículo" de una transferencia.
// Al elegir un artículo, el select de Color se rellena SOLO con los colores que
// ese artículo tiene en el origen (Praderas), mostrando los rollos disponibles.
// Los datos vienen de window.STOCK_ORIGEN (lo inyecta la vista desde el server).
(function () {
  const data = Array.isArray(window.STOCK_ORIGEN) ? window.STOCK_ORIGEN : [];
  const selArticulo = document.getElementById('selArticulo');
  const selColor = document.getElementById('selColor');
  if (!selArticulo || !selColor) return;

  function opcion(valor, texto) {
    const o = document.createElement('option');
    o.value = valor;
    o.textContent = texto;
    return o;
  }

  function poblarColores() {
    const artId = selArticulo.value;
    selColor.innerHTML = '';

    if (!artId) {
      selColor.appendChild(opcion('', 'Elige primero un artículo'));
      return;
    }
    const opciones = data
      .filter(function (x) { return String(x.articuloId) === String(artId); })
      .sort(function (a, b) { return a.colorLabel.localeCompare(b.colorLabel); });

    if (opciones.length === 0) {
      selColor.appendChild(opcion('', 'Sin stock en el origen para este artículo'));
      return;
    }
    selColor.appendChild(opcion('', 'Seleccionar...'));
    opciones.forEach(function (x) {
      selColor.appendChild(opcion(x.colorId, x.colorLabel + ' — ' + x.rollos + ' rollos'));
    });
  }

  selArticulo.addEventListener('change', poblarColores);
  poblarColores(); // estado inicial
})();
