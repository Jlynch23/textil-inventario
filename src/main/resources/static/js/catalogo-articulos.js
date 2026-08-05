// Form de Artículo (#13): extraído de catalogo/articulos.html. JS puro.

        function agregarOpcion(select, id, texto) {
            const option = document.createElement('option');
            option.value = id;
            option.textContent = texto;
            option.selected = true;
            select.appendChild(option);
        }

        document.getElementById('btnGuardarTipoTela').addEventListener('click', async (ev) => {
            const nombre = document.getElementById('modalTipoTelaNombre').value.trim();
            const errorDiv = document.getElementById('modalTipoTelaError');
            if (!nombre) { errorDiv.textContent = 'El nombre es obligatorio.'; return; }

            await sinDobleClick(ev.currentTarget, async () => {
                try {
                    const resp = await fetchConCsrf('/catalogo/tipos-tela/crear-rapido', {
                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ nombre })
                    });
                    const data = await resp.json();
                    if (!resp.ok) { errorDiv.textContent = data.error || 'Error al crear.'; return; }

                    agregarOpcion(document.getElementById('selectTipoTela'), data.id, data.nombre);
                    bootstrap.Modal.getInstance(document.getElementById('modalTipoTelaNuevo')).hide();
                    document.getElementById('modalTipoTelaNombre').value = '';
                    errorDiv.textContent = '';
                } catch (e) { errorDiv.textContent = 'Error de conexión: ' + e.message; }
            });
        });

        document.getElementById('btnGuardarTitulo').addEventListener('click', async (ev) => {
            const valor = document.getElementById('modalTituloValor').value.trim();
            const errorDiv = document.getElementById('modalTituloError');
            if (!valor) { errorDiv.textContent = 'El valor es obligatorio.'; return; }

            await sinDobleClick(ev.currentTarget, async () => {
                try {
                    const resp = await fetchConCsrf('/catalogo/titulos/crear-rapido', {
                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ valor })
                    });
                    const data = await resp.json();
                    if (!resp.ok) { errorDiv.textContent = data.error || 'Error al crear.'; return; }

                    agregarOpcion(document.getElementById('selectTitulo'), data.id, data.valor);
                    bootstrap.Modal.getInstance(document.getElementById('modalTituloNuevo')).hide();
                    document.getElementById('modalTituloValor').value = '';
                    errorDiv.textContent = '';
                } catch (e) { errorDiv.textContent = 'Error de conexión: ' + e.message; }
            });
        });

        document.getElementById('btnGuardarComposicion').addEventListener('click', async (ev) => {
            const nombre = document.getElementById('modalComposicionNombre').value.trim();
            const errorDiv = document.getElementById('modalComposicionError');
            if (!nombre) { errorDiv.textContent = 'El nombre es obligatorio.'; return; }

            await sinDobleClick(ev.currentTarget, async () => {
                try {
                    const resp = await fetchConCsrf('/catalogo/composiciones/crear-rapido', {
                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ nombre })
                    });
                    const data = await resp.json();
                    if (!resp.ok) { errorDiv.textContent = data.error || 'Error al crear.'; return; }

                    agregarOpcion(document.getElementById('selectComposicion'), data.id, data.nombre);
                    bootstrap.Modal.getInstance(document.getElementById('modalComposicionNueva')).hide();
                    document.getElementById('modalComposicionNombre').value = '';
                    errorDiv.textContent = '';
                } catch (e) { errorDiv.textContent = 'Error de conexión: ' + e.message; }
            });
        });

        document.getElementById('btnGuardarAcabado').addEventListener('click', async (ev) => {
            const nombre = document.getElementById('modalAcabadoNombre').value.trim();
            const errorDiv = document.getElementById('modalAcabadoError');
            if (!nombre) { errorDiv.textContent = 'El nombre es obligatorio.'; return; }

            await sinDobleClick(ev.currentTarget, async () => {
                try {
                    const resp = await fetchConCsrf('/catalogo/acabados/crear-rapido', {
                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ nombre })
                    });
                    const data = await resp.json();
                    if (!resp.ok) { errorDiv.textContent = data.error || 'Error al crear.'; return; }

                    agregarOpcion(document.getElementById('selectAcabado'), data.id, data.nombre);
                    bootstrap.Modal.getInstance(document.getElementById('modalAcabadoNuevo')).hide();
                    document.getElementById('modalAcabadoNombre').value = '';
                    errorDiv.textContent = '';
                } catch (e) { errorDiv.textContent = 'Error de conexión: ' + e.message; }
            });
        });
