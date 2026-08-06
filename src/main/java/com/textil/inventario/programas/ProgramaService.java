package com.textil.inventario.programas;

import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionDetalle;
import com.textil.inventario.recepciones.RecepcionDocumento;
import com.textil.inventario.recepciones.RecepcionDocumentoRepository;
import com.textil.inventario.recepciones.RecepcionDetalleRepository;
import com.textil.inventario.catalogo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramaService {

    private final ProgramaRepository programaRepository;
    private final ProgramaDetalleRepository programaDetalleRepository;
    private final RecepcionDetalleRepository recepcionDetalleRepository;
    private final RecepcionDocumentoRepository recepcionDocumentoRepository;
    private final EmpresaRepository empresaRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ComposicionRepository composicionRepository;
    private final AcabadoRepository acabadoRepository;
    private final CatalogoService catalogoService;
    private final com.textil.inventario.auditoria.AuditLogService auditLogService;

    // Normaliza a mayusculas (recorta espacios), igual que en Catalogo y
    // Recepciones, para mantener consistencia aunque el numero de programa
    // hoy en la practica solo tenga digitos.
    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? valor : valor.trim().toUpperCase();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Programa> listarProgramas() {
        List<Programa> programas = programaRepository.findAllOrdenadoCronologico();
        // #9 (OSIV off): la lista usa helpers (completo, porcentajeProgreso,
        // totalDetalles) que iteran getDetalles(). Se fuerza la inicializacion
        // DENTRO de la transaccion; con default_batch_fetch_size las colecciones
        // se cargan por lotes (no N+1). Sin esto, al apagar open-in-view el
        // th:each de la vista reventaria con LazyInitializationException.
        programas.forEach(p -> p.getDetalles().size());
        return programas;
    }

    public Programa buscarPrograma(Long id) {
        // #9 (OSIV off): con las lineas precargadas para render/recorrido posterior.
        return programaRepository.findWithDetallesById(id).orElseThrow();
    }

    // #9 (OSIV off): el dashboard solo necesita el CONTEO de programas en proceso.
    // Se calcula aca dentro de la transaccion (isCompleto() itera detalles), asi
    // ninguna entidad con colecciones perezosas escapa al render del dashboard.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public long contarEnProceso() {
        return programaRepository.findAllByOrderByFechaDesc().stream()
                .filter(p -> !p.isCompleto())
                .count();
    }

    /**
     * Borra un programa y sus líneas (cascade). Borrado PROTEGIDO: si alguna
     * línea del programa ya fue usada en una recepción, no se borra (perderíamos
     * la trazabilidad del kardex); se avisa para que anulen/editen esa recepción
     * primero.
     */
    @Transactional
    public void eliminarPrograma(Long id) {
        Programa programa = buscarPrograma(id);
        if (recepcionDetalleRepository.existsByProgramaDetalle_ProgramaId(id)) {
            throw new IllegalStateException(
                "No se puede borrar: el programa ya tiene recepciones asociadas. Anula o edita esas recepciones antes de borrarlo.");
        }
        programaRepository.delete(programa); // cascade ALL borra las líneas (ProgramaDetalle)
    }

    @Transactional
    public Programa crearPrograma(String numero, Long empresaId, LocalDate fecha, String observaciones,
                                   Integer totalRollos,
                                   List<Long> tipoTelaIds, List<Long> tituloIds, List<Long> composicionIds,
                                   List<Long> acabadoIds, List<Long> colorIds, List<Integer> cantidades) {
        // Auditoria: mismas listas paralelas que actualizarPrograma; hay que
        // validar que TODAS tengan el mismo largo antes de indexar por posicion,
        // o un POST desalineado lanza IndexOutOfBounds -> 500 opaco.
        if (tipoTelaIds.size() != cantidades.size() || tituloIds.size() != cantidades.size()
                || composicionIds.size() != cantidades.size() || acabadoIds.size() != cantidades.size()
                || colorIds.size() != cantidades.size()) {
            throw new IllegalArgumentException("Los datos de las líneas llegaron incompletos. Recarga la página e intenta de nuevo.");
        }
        // Validacion ANTES de guardar nada: una linea con ALGO cargado debe estar
        // COMPLETA. Antes, el loop de guardado hacia `continue` si faltaba algun
        // campo (ej. la composicion), descartando la linea EN SILENCIO: el
        // programa quedaba con menos lineas que las cargadas y el total no
        // cuadraba, sin ningun aviso -> se perdian colores. Ahora se avisa cual
        // linea completar (una linea totalmente vacia si se ignora, es normal).
        for (int i = 0; i < cantidades.size(); i++) {
            boolean vacia = lineaVacia(i, tipoTelaIds, tituloIds, composicionIds, acabadoIds, colorIds, cantidades);
            if (vacia) continue;
            boolean completa = tipoTelaIds.get(i) != null && tituloIds.get(i) != null
                    && composicionIds.get(i) != null && acabadoIds.get(i) != null
                    && colorIds.get(i) != null && cantidades.get(i) != null && cantidades.get(i) > 0;
            if (!completa) {
                throw new IllegalArgumentException(
                        "La línea " + (i + 1) + " está incompleta: falta tipo de tela, título, " +
                        "composición, acabado, color o cantidad. Complétala o quítala antes de guardar.");
            }
        }

        int suma = cantidades.stream().mapToInt(c -> c != null ? c : 0).sum();
        if (totalRollos == null || !totalRollos.equals(suma)) {
            throw new IllegalArgumentException(
                    "El total de rollos ingresado (" + (totalRollos != null ? totalRollos : 0) +
                    ") no coincide con la suma de las cantidades de las líneas (" + suma + ").");
        }

        // El numero de programa es UNICO. Se valida ANTES de guardar para dar un
        // mensaje claro ("ya existe el numero X") en vez de que reviente como
        // DataIntegrityViolationException / error del sistema.
        String numeroNormalizado = normalizar(numero);
        if (programaRepository.findByNumero(numeroNormalizado).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un programa con el número «" + numeroNormalizado + "». Usa otro número.");
        }

        Programa p = new Programa();
        p.setNumero(numeroNormalizado);
        p.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        p.setFecha(fecha);
        p.setObservaciones(observaciones);
        p.setTotalRollos(totalRollos);
        p = programaRepository.save(p);

        for (int i = 0; i < cantidades.size(); i++) {
            // Solo se saltan las lineas TOTALMENTE vacias; las incompletas ya
            // fueron rechazadas arriba, asi que aca todo lo no-vacio esta completo.
            if (lineaVacia(i, tipoTelaIds, tituloIds, composicionIds, acabadoIds, colorIds, cantidades)) continue;

            Articulo articulo = resolverOCrearArticulo(tipoTelaIds.get(i), tituloIds.get(i), composicionIds.get(i), acabadoIds.get(i));
            Color color = colorRepository.findById(colorIds.get(i)).orElseThrow();

            ProgramaDetalle pd = new ProgramaDetalle();
            pd.setPrograma(p);
            pd.setArticulo(articulo);
            pd.setColor(color);
            pd.setCantidadSolicitada(cantidades.get(i));
            pd.setCantidadRecibida(0);
            programaDetalleRepository.save(pd);
        }

        return p;
    }

    /** Una linea esta "totalmente vacia" (el usuario no cargo nada en ella) si
     *  no tiene ningun campo seleccionado ni cantidad. Esas se ignoran; las que
     *  tienen algo cargado deben estar completas (ver crearPrograma). */
    private boolean lineaVacia(int i, List<Long> tipoTelaIds, List<Long> tituloIds,
                               List<Long> composicionIds, List<Long> acabadoIds,
                               List<Long> colorIds, List<Integer> cantidades) {
        return tipoTelaIds.get(i) == null && tituloIds.get(i) == null
                && composicionIds.get(i) == null && acabadoIds.get(i) == null
                && colorIds.get(i) == null && (cantidades.get(i) == null || cantidades.get(i) == 0);
    }

    /**
     * Un programa puede pedir una combinacion tipo de tela + titulo +
     * composicion que todavia no exista como Articulo en el catalogo (ej.
     * una composicion nueva para este programa). Se reutiliza la misma
     * logica de generacion de codigo interno que ARQ-02 (CatalogoService),
     * en vez de duplicarla aqui. El Color ya no forma parte del Articulo
     * (ver V26): se resuelve/asigna por separado a nivel de ProgramaDetalle.
     */
    private Articulo resolverOCrearArticulo(Long tipoTelaId, Long tituloId, Long composicionId, Long acabadoId) {
        Acabado acabado = acabadoRepository.findById(acabadoId).orElseThrow();

        Optional<Articulo> existente = catalogoService.buscarArticuloPorCombinacion(tipoTelaId, tituloId, composicionId, acabado.getId());
        if (existente.isPresent()) return existente.get();

        TipoTela tipoTela = tipoTelaRepository.findById(tipoTelaId).orElseThrow();
        Titulo titulo = tituloRepository.findById(tituloId).orElseThrow();
        Composicion composicion = composicionRepository.findById(composicionId).orElseThrow();

        Articulo nuevo = new Articulo();
        nuevo.setTipoTela(tipoTela);
        nuevo.setTitulo(titulo);
        nuevo.setComposicion(composicion);
        nuevo.setAcabado(acabado);
        nuevo.setCodigoInterno(catalogoService.generarCodigoInterno(tipoTela, titulo, composicion, acabado));
        nuevo.setActivo(true);
        return catalogoService.guardarArticulo(nuevo);
    }

    /**
     * Actualiza los datos generales del programa, la cantidad solicitada de
     * lineas existentes, elimina las lineas marcadas (falla con
     * DataIntegrityViolationException si alguna ya tiene recepciones
     * vinculadas, protegiendo esa trazabilidad) y agrega lineas nuevas.
     * No permite cambiar el articulo (tipo/titulo/color) de una linea ya
     * existente, solo su cantidad, para no romper vinculos ya hechos con
     * recepciones reales. Valida que totalRollos coincida con la suma de
     * TODAS las cantidades finales (existentes que quedan + nuevas).
     * Bloquea la edicion por completo si el programa ya esta completo
     * (todas sus lineas recibieron la cantidad solicitada), como defensa
     * adicional al bloqueo que ya existe en el Controller a nivel de GET.
     */
    @Transactional
    public void actualizarPrograma(Long programaId, String numero, Long empresaId, LocalDate fecha, String observaciones,
                                    Integer totalRollos,
                                    List<Long> detalleIdsExistentes, List<Integer> cantidadesExistentes,
                                    List<Long> detalleIdsAEliminar,
                                    List<Long> nuevosTipoTelaIds, List<Long> nuevosTituloIds,
                                    List<Long> nuevosComposicionIds, List<Long> nuevosAcabadoIds,
                                    List<Long> nuevosColorIds, List<Integer> nuevasCantidades) {

        Programa p = programaRepository.findById(programaId).orElseThrow();
        if (p.isCompleto()) {
            throw new IllegalArgumentException("Este programa ya está completo y no se puede editar.");
        }

        int sumaExistentes = cantidadesExistentes.stream().mapToInt(c -> c != null ? c : 0).sum();
        int sumaNuevas = nuevasCantidades.stream().mapToInt(c -> c != null ? c : 0).sum();
        int sumaTotal = sumaExistentes + sumaNuevas;
        if (totalRollos == null || !totalRollos.equals(sumaTotal)) {
            throw new IllegalArgumentException(
                    "El total de rollos ingresado (" + (totalRollos != null ? totalRollos : 0) +
                    ") no coincide con la suma de las cantidades de las líneas (" + sumaTotal + ").");
        }

        // Numero UNICO: si el usuario cambio el numero a uno que YA usa OTRO
        // programa, se avisa claro en vez de reventar con DataIntegrity/error.
        String numeroNormalizado = normalizar(numero);
        programaRepository.findByNumero(numeroNormalizado)
                .filter(otro -> !otro.getId().equals(programaId))
                .ifPresent(otro -> { throw new IllegalArgumentException(
                        "Ya existe otro programa con el número «" + numeroNormalizado + "». Usa otro número."); });

        p.setNumero(numeroNormalizado);
        p.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        p.setFecha(fecha);
        p.setObservaciones(observaciones);
        p.setTotalRollos(totalRollos);
        programaRepository.save(p);

        // R-P1 (red-team): las listas paralelas se indexan por posicion; si el POST
        // viene desalineado, get(i) lanzaba IndexOutOfBounds (500). Se valida el
        // tamano antes de recorrer.
        if (cantidadesExistentes.size() != detalleIdsExistentes.size()) {
            throw new IllegalArgumentException(
                    "Los datos de las líneas llegaron incompletos. Recarga la página e intenta de nuevo.");
        }
        for (int i = 0; i < detalleIdsExistentes.size(); i++) {
            ProgramaDetalle pd = programaDetalleRepository.findById(detalleIdsExistentes.get(i)).orElseThrow();
            // Guard de pertenencia (mismo criterio que confirmarRecepcion): las
            // lineas se cargan por id venido del POST, asi que sin esta
            // verificacion un formulario manipulado editaba lineas de OTRO
            // programa desde la pantalla de este.
            exigirQuePertenezcaAlPrograma(pd, programaId);
            Integer cant = cantidadesExistentes.get(i);
            // R-P1: sin esto, una cantidad vacia se persistia como NULL (viola
            // NOT NULL -> 500) y una negativa dejaba la linea incoherente.
            if (cant == null || cant <= 0) {
                throw new IllegalArgumentException("La cantidad de cada línea debe ser mayor a cero.");
            }
            if (cant < pd.getCantidadRecibida()) {
                throw new IllegalArgumentException(
                        "No puedes dejar la cantidad solicitada (" + cant + ") por debajo de lo ya recibido ("
                        + pd.getCantidadRecibida() + ") en una línea.");
            }
            pd.setCantidadSolicitada(cant);
            programaDetalleRepository.save(pd);
        }

        for (Long detalleId : detalleIdsAEliminar) {
            // Igual que arriba: sin el guard, un POST manipulado BORRABA lineas
            // de otro programa (deleteById no mira a quien pertenecen).
            ProgramaDetalle pd = programaDetalleRepository.findById(detalleId).orElseThrow();
            exigirQuePertenezcaAlPrograma(pd, programaId);
            programaDetalleRepository.delete(pd);
        }

        // R-P1: las listas de lineas nuevas tambien se indexan en paralelo.
        int nuevas = nuevasCantidades.size();
        if (nuevosTipoTelaIds.size() != nuevas || nuevosTituloIds.size() != nuevas
                || nuevosComposicionIds.size() != nuevas || nuevosAcabadoIds.size() != nuevas
                || nuevosColorIds.size() != nuevas) {
            throw new IllegalArgumentException(
                    "Los datos de las líneas nuevas llegaron incompletos. Recarga la página e intenta de nuevo.");
        }
        for (int i = 0; i < nuevasCantidades.size(); i++) {
            if (nuevosTipoTelaIds.get(i) == null || nuevosTituloIds.get(i) == null
                    || nuevosComposicionIds.get(i) == null || nuevosAcabadoIds.get(i) == null
                    || nuevosColorIds.get(i) == null) continue;
            // R-P1: fila nueva con ids pero cantidad vacia/<=0 -> se omite (no se
            // crea con cantidad NULL). Coherente con la validacion de suma de arriba.
            if (nuevasCantidades.get(i) == null || nuevasCantidades.get(i) <= 0) continue;
            Articulo articulo = resolverOCrearArticulo(nuevosTipoTelaIds.get(i), nuevosTituloIds.get(i), nuevosComposicionIds.get(i), nuevosAcabadoIds.get(i));
            Color color = colorRepository.findById(nuevosColorIds.get(i)).orElseThrow();

            ProgramaDetalle pd = new ProgramaDetalle();
            pd.setPrograma(p);
            pd.setArticulo(articulo);
            pd.setColor(color);
            pd.setCantidadSolicitada(nuevasCantidades.get(i));
            pd.setCantidadRecibida(0);
            programaDetalleRepository.save(pd);
        }
    }

    /**
     * Vista simplificada del historial de una linea para la pantalla de
     * seguimiento: por cada recepcion que aporto a esta linea, incluye el
     * id del RecepcionDocumento tipo GUIA (si el PDF fue subido) para poder
     * mostrar un link directo al visor de PDF (/documentos/{id}/ver) en vez
     * de mandar a la pantalla de detalle de la recepcion.
     */
    public record HistorialGuiaView(Long recepcionId, String numeroGuia, Long documentoId) {}

    /**
     * Una linea de recepcion que dice pertenecer a este programa pero que NO
     * quedo vinculada a ninguna de sus lineas: entro al stock sin descontar del
     * programa. Se muestra en Seguimiento para que el faltante deje de ser
     * invisible y se vea QUE llego y por que no cuadro.
     */
    /**
     * Las cuatro partes del articulo van SEPARADAS, no como getDescripcion():
     * ese texto omite el acabado LISO a proposito (es el valor por defecto y en
     * las pantallas normales solo agrega ruido), y aca el acabado es justamente
     * el dato que explica por que la linea no vinculo. Separadas ademas se
     * comparan columna contra columna con la tabla de abajo, que es lo que uno
     * hace mirando esta pantalla.
     */
    public record LineaHuerfanaView(Long recepcionDetalleId, Long recepcionId, String numeroGuia, Long documentoId,
                                    String tipoTela, String titulo, String composicion, String acabado,
                                    String color, String codigoColor, Integer rollos, boolean confirmada) {}

    /**
     * Recepciones que nombran este programa y no descontaron nada de el.
     *
     * El vinculo se hace en RecepcionService.agregarDetalle y puede fallar por
     * dos caminos que hasta ahora eran mudos: que el numero de programa de la
     * guia no resuelva, o que el programa no tenga una linea con ese
     * articulo+color exacto (misma tela y color pero otra composicion o acabado
     * ya es OTRO articulo). En los dos casos el stock entra igual y el programa
     * se queda esperando tela que en realidad ya llego.
     */
    @Transactional(readOnly = true)
    public List<LineaHuerfanaView> lineasHuerfanas(String numeroPrograma) {
        if (numeroPrograma == null || numeroPrograma.isBlank()) return List.of();
        return recepcionDetalleRepository.huerfanasDelPrograma(numeroPrograma.trim()).stream()
                .map(d -> new LineaHuerfanaView(
                        d.getId(),
                        d.getRecepcion().getId(),
                        d.getRecepcion().getNumeroGuia(),
                        idDocumentoGuia(d.getRecepcion().getId()),
                        d.getArticulo().getTipoTela().getNombre(),
                        d.getArticulo().getTitulo().getValor(),
                        d.getArticulo().getComposicion().getNombre(),
                        d.getArticulo().getAcabado().getNombre(),
                        d.getColor().getNombreMostrar(),
                        d.getColor().getCodigoFastDye(),
                        d.getRollosRecibidos() != null ? d.getRollosRecibidos() : d.getRollosGuia(),
                        d.getRecepcion().getEstado() != Recepcion.EstadoRecepcion.PENDIENTE))
                .toList();
    }

    /**
     * Vincula A MANO una linea de recepcion huerfana con una linea de este
     * programa, para el caso normal: la tela es la misma pero el articulo no
     * coincide exacto (tipico, el acabado -- el programa pide LISTADO BLANCO y
     * la guia trajo LISO). El match automatico de agregarDetalle exige
     * articulo+color identicos y por eso no las une; esta es la salida manual.
     *
     * NO toca el stock ni el kardex, a proposito. El stock ya se movio cuando se
     * confirmo la recepcion, y bajo el articulo que dice la recepcion. Si lo que
     * esta mal es la recepcion (el OCR leyo mal el acabado), vincular deja el
     * programa cuadrado pero el stock sigue con el articulo equivocado: eso se
     * arregla aparte, con un ajuste. Vincular dice "estas dos lineas son la misma
     * tela", no "corrige el inventario".
     */
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public void vincularHuerfana(Long programaId, Long recepcionDetalleId, Long programaDetalleId) {
        RecepcionDetalle rd = recepcionDetalleRepository.findById(recepcionDetalleId)
                .orElseThrow(() -> new IllegalArgumentException("La línea de recepción ya no existe."));

        // Idempotencia: si ya esta vinculada, volver a hacerlo sumaria los rollos
        // por segunda vez al programa (doble descuento). Un doble click o un
        // reenvio del formulario alcanza para provocarlo.
        if (rd.getProgramaDetalle() != null) {
            throw new IllegalStateException(
                    "Esa línea ya está vinculada a una línea del programa. Recarga la página.");
        }

        ProgramaDetalle pd = programaDetalleRepository.findById(programaDetalleId)
                .orElseThrow(() -> new IllegalArgumentException("La línea del programa ya no existe."));
        // La linea destino DEBE ser de este programa: sin este chequeo un POST
        // manipulado acreditaria rollos al programa de otro.
        if (!pd.getPrograma().getId().equals(programaId)) {
            throw new IllegalArgumentException("Esa línea no pertenece a este programa.");
        }

        rd.setProgramaDetalle(pd);
        recepcionDetalleRepository.save(rd);

        // Solo se suma si la recepcion YA fue confirmada. Si sigue PENDIENTE, el
        // que suma es confirmarRecepcion cuando se confirme; hacerlo aca tambien
        // contaria los rollos dos veces.
        boolean yaConfirmada = rd.getRecepcion().getEstado() != Recepcion.EstadoRecepcion.PENDIENTE;
        if (yaConfirmada) {
            int rollos = rd.getRollosRecibidos() != null ? rd.getRollosRecibidos() : 0;
            pd.setCantidadRecibida(Math.addExact(pd.getCantidadRecibida(), rollos));
            programaDetalleRepository.save(pd);
        }

        auditLogService.registrar("VINCULAR", "ProgramaDetalle", pd.getId(),
                "Vinculo a mano la linea de recepcion " + rd.getId()
                + " (guia " + rd.getRecepcion().getNumeroGuia() + ", "
                + rd.getArticulo().getDescripcion() + " " + rd.getColor().getNombreMostrar() + ")"
                + " con la linea del programa " + pd.getPrograma().getNumero()
                + " (" + pd.getArticulo().getDescripcion() + " " + pd.getColor().getNombreMostrar() + ")"
                + (yaConfirmada ? " sumando " + rd.getRollosRecibidos() + " rollos" : " (aun sin confirmar)"));
    }

    /**
     * Id del PDF de la GUIA de una recepcion, o null si no se archivo ninguno.
     * Lo usan el historial de cada linea y el aviso de lineas huerfanas: los dos
     * alimentan el mismo ojito (fragments/guias :: ojoGuia).
     */
    private Long idDocumentoGuia(Long recepcionId) {
        return recepcionDocumentoRepository.findByRecepcionId(recepcionId).stream()
                .filter(d -> "GUIA".equals(d.getTipoDocumento()))
                .map(RecepcionDocumento::getId)
                .findFirst()
                .orElse(null);
    }

    public List<HistorialGuiaView> historialDeLineaConDocumento(Long programaDetalleId) {
        List<RecepcionDetalle> detalles = recepcionDetalleRepository.findByProgramaDetalleId(programaDetalleId);
        return detalles.stream()
                .map(rd -> new HistorialGuiaView(
                        rd.getRecepcion().getId(),
                        rd.getRecepcion().getNumeroGuia(),
                        idDocumentoGuia(rd.getRecepcion().getId())))
                .toList();
    }

    /**
     * Una linea solo puede editarse o borrarse desde el programa al que
     * pertenece. Los ids llegan del formulario (entrada no confiable) y tanto
     * findById como deleteById los aceptan sin mirar el dueño.
     */
    private void exigirQuePertenezcaAlPrograma(ProgramaDetalle pd, Long programaId) {
        if (pd.getPrograma() == null || !pd.getPrograma().getId().equals(programaId)) {
            throw new IllegalArgumentException(
                    "La línea " + pd.getId() + " no pertenece a este programa.");
        }
    }

}
