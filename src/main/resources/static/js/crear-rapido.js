/**
 * Alta "al vuelo" de una entrada de catalogo desde un modal (crear color, tipo
 * de tela, titulo, composicion... sin salir de la pantalla en la que estas).
 *
 * Este helper existe porque el mismo bloque estaba copiado en las pantallas de
 * programa (nueva y edicion), en el alta de articulos y en la recepcion nueva,
 * y ya habia divergido: el endurecimiento contra el doble click solo se aplico
 * a una de ellas, asi que en las demas un doble click seguia creando la entrada
 * dos veces o duplicandola en la lista en memoria. Con un solo punto, el arreglo
 * vale para todas.
 *
 * Que hace, en orden:
 *  1. Corta si ya hay un alta en curso (el guard del doble click) y deshabilita
 *     el boton hasta terminar.
 *  2. POSTea al endpoint crear-rapido correspondiente.
 *  3. Agrega el elemento a la lista en memoria SOLO si no estaba: el backend es
 *     idempotente (si ya existia devuelve el mismo id), asi que sin este dedupe
 *     el desplegable mostraba la misma entrada repetida.
 *  4. Reconstruye TODOS los <select> del mismo tipo preservando lo que cada uno
 *     tenia elegido, y preselecciona el nuevo valor solo en el que abrio el modal.
 *  5. Cierra el modal. Ante error, deja el mensaje en el div de error del modal.
 *
 * @param {Object}      opciones
 * @param {HTMLElement} opciones.boton           boton que dispara el alta (ev.currentTarget)
 * @param {string}      opciones.url             endpoint crear-rapido
 * @param {Object}      opciones.payload         cuerpo JSON a enviar
 * @param {HTMLElement} opciones.errorDiv        donde mostrar el error dentro del modal
 * @param {Array}       opciones.lista           lista en memoria que alimenta los <select>
 * @param {Function}    opciones.mapear          (data) => item a agregar a la lista
 * @param {string}      opciones.selectorSelects selector CSS de los <select> a refrescar
 * @param {Function}    opciones.renderOpciones  (idPreseleccionar) => HTML de <option>s
 * @param {HTMLElement} [opciones.selectActual]  el <select> que abrio el modal (recibe el valor nuevo)
 * @param {string}      opciones.modalId         id del modal a cerrar al terminar
 * @param {string}      [opciones.mensajeError]  texto por defecto si el backend no manda uno
 */
/**
 * Corre `accion` con el boton deshabilitado, de modo que un doble click no
 * dispare el alta dos veces. Para los modales que no reconstruyen listas
 * completas (ej. el form de Articulo, que solo agrega una <option>) y por eso
 * no encajan en crearRapido, pero necesitan el mismo guard.
 */
window.sinDobleClick = async function (boton, accion) {
    if (boton.disabled) return;
    boton.disabled = true;
    try {
        await accion();
    } finally {
        boton.disabled = false;
    }
};

window.crearRapido = async function ({
    boton, url, payload, errorDiv, lista, mapear,
    selectorSelects, renderOpciones, selectActual, modalId,
    mensajeError = 'Error al crear.'
}) {
    if (boton.disabled) return;   // ya hay un alta en curso: evita el doble click
    boton.disabled = true;
    try {
        const resp = await fetchConCsrf(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await resp.json();
        if (!resp.ok) { errorDiv.textContent = data.error || mensajeError; return; }

        if (!lista.some(x => x.id === data.id)) {
            lista.push(mapear(data));
        }
        document.querySelectorAll(selectorSelects).forEach(sel => {
            const idPreseleccionar = sel === selectActual ? data.id : null;
            const valorActual = sel.value;
            sel.innerHTML = renderOpciones(idPreseleccionar);
            if (!idPreseleccionar && valorActual) sel.value = valorActual;
        });
        bootstrap.Modal.getInstance(document.getElementById(modalId)).hide();
    } catch (e) {
        errorDiv.textContent = 'Error de conexión: ' + e.message;
    } finally {
        boton.disabled = false;
    }
};
