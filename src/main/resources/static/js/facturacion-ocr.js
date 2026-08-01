// Facturación por OCR (#13): extraído de recepciones/facturar.html.
// Usa fetchConCsrf() (global, /js/fetch-csrf.js) para las llamadas AJAX.

        // Seguridad (auditoria A4): los datos de la factura salen del OCR del PDF
        // (contenido NO confiable). Se escapan antes de inyectarlos en innerHTML
        // para que una factura con "<img src=x onerror=...>" no ejecute JS.
        function escapeHtml(valor) {
            if (valor === null || valor === undefined) return '';
            return String(valor)
                .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
        }
        document.getElementById('btnExtraerFactura').addEventListener('click', async () => {
            const fileInput = document.getElementById('pdfFactura');
            if (!fileInput.files.length) {
                alert('Selecciona un archivo PDF primero.');
                return;
            }

            const estadoDiv = document.getElementById('estadoFactura');
            estadoDiv.innerHTML = '<span class="text-primary"><i class="bi bi-hourglass-split me-1"></i>Leyendo factura...</span>';

            const formData = new FormData();
            formData.append('file', fileInput.files[0]);

            try {
                const resp = await fetchConCsrf('/recepciones/extraer-factura', { method: 'POST', body: formData });
                const data = await resp.json();

                if (!resp.ok) {
                    estadoDiv.innerHTML = '<span class="text-danger">Error: ' + escapeHtml(data.error || 'No se pudo procesar el PDF') + '</span>';
                    return;
                }

                document.getElementById('numeroFacturaInput').value = data.numeroFactura || '';
                document.getElementById('fechaFacturaInput').value = data.fechaFactura || '';

                function normalizarGuia(str) {
                    const match = str.trim().toUpperCase().match(/^([A-Z0-9]+)-(\d+)$/);
                    if (match) {
                        return match[1] + '-' + parseInt(match[2], 10);
                    }
                    return str.trim().toUpperCase();
                }

                let coincidencias = 0;
                const guiasRef = data.guiasReferenciadas || [];
                const guiasRefNormalizadas = guiasRef.map(normalizarGuia);

                if (guiasRef.length > 0) {
                    document.querySelectorAll('.chk-recepcion').forEach(chk => {
                        const numeroGuiaFila = chk.closest('tr').querySelector('code').textContent.trim();
                        if (guiasRefNormalizadas.includes(normalizarGuia(numeroGuiaFila))) {
                            chk.checked = true;
                            coincidencias++;
                        }
                    });
                }

                let msg = '<span class="text-success"><i class="bi bi-check-circle me-1"></i>Factura leída correctamente.</span>';
                if (coincidencias > 0) {
                    msg += '<br><span class="text-primary small">' + coincidencias + ' guía(s) marcada(s) automáticamente por coincidencia.</span>';
                } else if (guiasRef.length > 0) {
                    msg += '<br><span class="text-warning small">La factura referencia guías (' + guiasRef.map(escapeHtml).join(', ') + ') pero no se encontraron en la lista de pendientes — verifica manualmente.</span>';
                }
                if (data.advertencia) {
                    msg += '<br><span class="text-warning small">' + escapeHtml(data.advertencia) + '</span>';
                }
                estadoDiv.innerHTML = msg;

            } catch (e) {
                estadoDiv.innerHTML = '<span class="text-danger">Error de conexión: ' + escapeHtml(e.message) + '</span>';
            }
        });

        document.getElementById('btnAsignarFactura').addEventListener('click', async () => {
            const numeroFactura = document.getElementById('numeroFacturaInput').value.trim();
            const errorDiv = document.getElementById('errorAsignar');
            errorDiv.textContent = '';

            if (!numeroFactura) {
                errorDiv.textContent = 'Ingresa el número de factura.';
                return;
            }

            const recepcionIds = Array.from(document.querySelectorAll('.chk-recepcion:checked'))
                .map(chk => parseInt(chk.value));

            if (recepcionIds.length === 0) {
                errorDiv.textContent = 'Selecciona al menos una guía.';
                return;
            }

            const fechaFactura = document.getElementById('fechaFacturaInput').value || null;

            try {
                const resp = await fetchConCsrf('/recepciones/asignar-factura', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ numeroFactura, fechaFactura, recepcionIds })
                });
                const data = await resp.json();

                if (!resp.ok) {
                    errorDiv.textContent = data.error || 'Error al asignar la factura.';
                    return;
                }

                const fileInput = document.getElementById('pdfFactura');
                if (fileInput.files.length > 0) {
                    const fd = new FormData();
                    fd.append('file', fileInput.files[0]);
                    recepcionIds.forEach(id => fd.append('recepcionIds', id));
                    try {
                        await fetchConCsrf('/recepciones/guardar-documento-factura', { method: 'POST', body: fd });
                    } catch (err) {
                        console.error('No se pudo archivar copia de la factura:', err);
                    }
                }

                window.location.href = '/recepciones';

            } catch (e) {
                errorDiv.textContent = 'Error de conexión: ' + e.message;
            }
        });
