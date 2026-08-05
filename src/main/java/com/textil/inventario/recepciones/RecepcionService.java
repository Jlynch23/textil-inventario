package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.inventario.*;
import com.textil.inventario.seguridad.UsuarioActualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecepcionService {

    private final RecepcionRepository recepcionRepository;
    private final RecepcionDetalleRepository detalleRepository;
    private final EmpresaRepository empresaRepository;
    private final ArticuloRepository articuloRepository;
    private final ColorRepository colorRepository;
    private final UsuarioActualService usuarioActualService;
    private final StockActualRepository stockActualRepository;
    private final KardexMovimientoRepository kardexRepository;
    private final UbicacionRepository ubicacionRepository;
    private final ProgramaRepository programaRepository;
    private final ProgramaDetalleRepository programaDetalleRepository;
    private final RecepcionDocumentoRepository recepcionDocumentoRepository;
    private final DocumentoStorageService documentoStorageService;
    private final com.textil.inventario.auditoria.AuditLogService auditLogService;

    // R-C2 (red-team): cota superior por linea. rollos es int; sumar cantidades
    // enormes a stock.rollos desbordaba en silencio a negativo. Un millon de
    // rollos en UNA linea es absurdo para el negocio, asi que se rechaza antes.
    private static final int MAX_ROLLOS_POR_LINEA = 1_000_000;

    // Normaliza numeros de guia/factura a mayusculas (recorta espacios) para
    // que el matching guia<->factura y las busquedas por numero no fallen
    // por diferencias de mayus/minus (ej. "tg01-00022558" vs "TG01-00022558").
    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? valor : valor.trim().toUpperCase();
    }

    public List<Recepcion> listarRecepcionesSinFactura() {
        return recepcionRepository.findByNumeroFacturaIsNullOrderByFechaGuiaDesc();
    }

    @Transactional
    public void asignarFactura(String numeroFactura, LocalDate fechaFactura, List<Long> recepcionIds) {
        // Guarda de dominio: asignar una factura en blanco dejaria '' (no NULL) y
        // la recepcion "desapareceria" de la lista de pendientes sin factura real.
        if (numeroFactura == null || numeroFactura.isBlank()) {
            throw new IllegalArgumentException("El número de factura es obligatorio.");
        }
        for (Long id : recepcionIds) {
            Recepcion r = recepcionRepository.findById(id).orElseThrow();
            r.setNumeroFactura(normalizar(numeroFactura));
            r.setFechaFactura(fechaFactura);
            recepcionRepository.save(r);
        }
    }

    // ARQ (auditoria 17-jul-2026): sin @Transactional a proposito. La escritura
    // a disco (I/O lento e impredecible) no debe mantener abierta una conexion
    // de base de datos. El unico save() posterior ya es atomico por si solo
    // (Spring Data JPA envuelve cada metodo de repositorio en su propia transaccion).
    public void guardarDocumentoGuia(Long recepcionId, org.springframework.web.multipart.MultipartFile archivo) throws java.io.IOException {
        Recepcion r = recepcionRepository.findById(recepcionId).orElseThrow();
        String ruta = documentoStorageService.guardar(archivo, "GUIA", r.getEmpresa(), r.getFechaGuia());

        RecepcionDocumento doc = new RecepcionDocumento();
        doc.setRecepcion(r);
        doc.setTipoDocumento("GUIA");
        doc.setNombreOriginal(archivo.getOriginalFilename());
        doc.setRutaArchivo(ruta);
        recepcionDocumentoRepository.save(doc);
    }

    // Usado por Archivo Historico al crear una Recepcion automatica: el PDF ya
    // esta en disco (Archivo Historico ya lo proceso), asi que se copia
    // directo en vez de pasar por un upload HTTP con MultipartFile.
    // ARQ (auditoria 17-jul-2026): ver nota en guardarDocumentoGuia().
    public void guardarDocumentoGuiaDesdeArchivo(Long recepcionId, java.nio.file.Path archivoOrigen, String nombreOriginal) throws java.io.IOException {
        Recepcion r = recepcionRepository.findById(recepcionId).orElseThrow();
        String ruta = documentoStorageService.guardar(archivoOrigen, nombreOriginal, "GUIA", r.getEmpresa(), r.getFechaGuia());

        RecepcionDocumento doc = new RecepcionDocumento();
        doc.setRecepcion(r);
        doc.setTipoDocumento("GUIA");
        doc.setNombreOriginal(nombreOriginal);
        doc.setRutaArchivo(ruta);
        recepcionDocumentoRepository.save(doc);
    }

    // Version de guardarDocumentoFactura para cuando el PDF ya esta en disco
    // (Archivo Historico ya lo proceso), sin pasar por un upload HTTP.
    // ARQ (auditoria 17-jul-2026): sin @Transactional a proposito, para que la
    // escritura a disco no mantenga abierta una conexion de BD. Se pierde la
    // atomicidad "todo o nada" del loop de saves (cada RecepcionDocumento se
    // guarda en su propia mini-transaccion), aceptable porque esto es metadata
    // de documentos y no afecta stock_actual ni kardex_movimientos.
    public void guardarDocumentoFacturaDesdeArchivo(List<Long> recepcionIds, java.nio.file.Path archivoOrigen, String nombreOriginal) throws java.io.IOException {
        List<Recepcion> recepciones = recepcionRepository.findAllById(recepcionIds);
        if (recepciones.isEmpty()) return;

        Recepcion primera = recepciones.get(0);
        String ruta = documentoStorageService.guardar(archivoOrigen, nombreOriginal, "FACTURA", primera.getEmpresa(), primera.getFechaFactura());

        for (Recepcion r : recepciones) {
            RecepcionDocumento doc = new RecepcionDocumento();
            doc.setRecepcion(r);
            doc.setTipoDocumento("FACTURA");
            doc.setNombreOriginal(nombreOriginal);
            doc.setRutaArchivo(ruta);
            recepcionDocumentoRepository.save(doc);
        }
    }

    // ARQ (auditoria 17-jul-2026): ver nota en guardarDocumentoFacturaDesdeArchivo().
    public void guardarDocumentoFactura(List<Long> recepcionIds, org.springframework.web.multipart.MultipartFile archivo) throws java.io.IOException {
        List<Recepcion> recepciones = recepcionRepository.findAllById(recepcionIds);
        if (recepciones.isEmpty()) return;

        Long empresaIdBase = recepciones.get(0).getEmpresa().getId();
        boolean mismaEmpresa = recepciones.stream().allMatch(r -> r.getEmpresa().getId().equals(empresaIdBase));
        if (!mismaEmpresa) {
            throw new IllegalArgumentException("Todas las guías seleccionadas deben ser de la misma empresa para archivar la factura.");
        }

        Recepcion primera = recepciones.get(0);
        String ruta = documentoStorageService.guardar(archivo, "FACTURA", primera.getEmpresa(), primera.getFechaFactura());

        for (Recepcion r : recepciones) {
            RecepcionDocumento doc = new RecepcionDocumento();
            doc.setRecepcion(r);
            doc.setTipoDocumento("FACTURA");
            doc.setNombreOriginal(archivo.getOriginalFilename());
            doc.setRutaArchivo(ruta);
            recepcionDocumentoRepository.save(doc);
        }
    }

    public List<Recepcion> listarRecepciones() {
        return recepcionRepository.findAllByOrderByCreatedAtDesc();
    }

    public java.util.Optional<Recepcion> buscarPorNumeroGuia(String numeroGuia) {
        return recepcionRepository.findFirstByNumeroGuia(normalizar(numeroGuia));
    }

    public Recepcion buscarRecepcion(Long id) {
        // #9 (OSIV off): con los detalles precargados, para que las vistas de
        // detalle/confirmacion los rendericen sin sesion abierta.
        return recepcionRepository.findWithDetallesById(id).orElseThrow();
    }

    @Transactional
    public Recepcion crearRecepcion(Long empresaId, String numeroGuia, LocalDate fechaGuia, String observaciones) {
        return crearRecepcion(empresaId, numeroGuia, null, fechaGuia, observaciones);
    }

    @Transactional
    public Recepcion crearRecepcion(Long empresaId, String numeroGuia, String numeroFactura, LocalDate fechaGuia, String observaciones) {
        String guiaNorm = normalizar(numeroGuia);
        // Guias en blanco -> NULL: una recepcion puede no llevar guia, y el UNIQUE
        // de MySQL permite multiples NULL (varias recepciones sin guia no chocan),
        // pero SI bloquea dos guias reales iguales.
        String guiaFinal = (guiaNorm == null || guiaNorm.isBlank()) ? null : guiaNorm;
        // Guía duplicada: una guía = una recepción. Sin este bloqueo, cargar la
        // misma guía dos veces y confirmar ambas DUPLICA el stock y deja el
        // programa con recibido > solicitado (pendiente negativo). El aviso del
        // front es saltable; acá se hace obligatorio (defensa en el backend).
        // Este chequeo previo es solo para UX (mensaje claro); la garantia real
        // contra la carrera concurrente la da el UNIQUE de BD (INV-02, ver catch).
        if (guiaFinal != null
                && recepcionRepository.findFirstByNumeroGuia(guiaFinal).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe una recepción con la guía " + guiaFinal + ". Una guía no se puede registrar dos veces.");
        }
        Recepcion r = new Recepcion();
        r.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        r.setNumeroGuia(guiaFinal);
        // Factura en blanco -> NULL (mismo criterio que la guia). El formulario y
        // el OCR mandan "" cuando no hay factura; si se guarda '', la recepcion
        // jamas aparece en Facturar (esa lista filtra por numero_factura IS NULL).
        String facturaNorm = normalizar(numeroFactura);
        r.setNumeroFactura((facturaNorm == null || facturaNorm.isBlank()) ? null : facturaNorm);
        r.setFechaGuia(fechaGuia);
        r.setFechaRecepcion(LocalDate.now());
        r.setObservaciones(observaciones);
        r.setEstado(Recepcion.EstadoRecepcion.PENDIENTE);
        r.setUsuario(usuarioActualService.obtenerUsuarioActual());
        r.setUpdatedAt(java.time.LocalDateTime.now());
        Recepcion guardada;
        try {
            // saveAndFlush para que la violacion del UNIQUE aflore DENTRO de este
            // try (con save() normal el INSERT se difiere al commit, fuera de aca).
            guardada = recepcionRepository.saveAndFlush(r);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Auditoria INV-02: dos POST simultaneos con la misma guia pasan ambos
            // el findFirst de arriba (read-then-write); el UNIQUE deja entrar solo
            // uno y el otro cae aca. Se traduce a mensaje de dominio (no un 500).
            throw new IllegalArgumentException(
                    "Ya existe una recepción con la guía " + guiaFinal + ". Una guía no se puede registrar dos veces.");
        }
        auditLogService.registrar("CREAR", "Recepcion", guardada.getId(),
                "Creo recepcion con guia " + guardada.getNumeroGuia());
        return guardada;
    }

    @Transactional
    public RecepcionDetalle agregarDetalle(Long recepcionId, Long articuloId, Long colorId,
                                           String programa, Integer rollosGuia,
                                           java.math.BigDecimal pesoBruto) {
        Recepcion recepcion = recepcionRepository.findById(recepcionId).orElseThrow();
        // Solo se agregan lineas mientras la recepcion esta PENDIENTE. Sin este
        // guard (TransferenciaService.agregarDetalle si lo tiene), una pestaña
        // abierta desde antes de confirmar podia sumar una linea a una recepcion
        // ya CONFIRMADA: confirmarRecepcion nunca vuelve a correr, asi que esa
        // linea jamas afectaba stock ni kardex pero si aparecia en el detalle y
        // en los reportes -- el papel decia rollos que el inventario no tenia.
        if (recepcion.getEstado() != Recepcion.EstadoRecepcion.PENDIENTE) {
            throw new IllegalStateException(
                    "Solo se pueden agregar líneas mientras la recepción está pendiente (estado actual: "
                    + recepcion.getEstado() + ").");
        }

        RecepcionDetalle d = new RecepcionDetalle();
        d.setRecepcion(recepcion);
        Articulo articulo = articuloRepository.findById(articuloId).orElseThrow();
        d.setArticulo(articulo);
        d.setColor(colorRepository.findById(colorId).orElseThrow());
        d.setProgramaTenido(programa);
        d.setRollosGuia(rollosGuia);
        d.setPesoBrutoKg(pesoBruto);

        if (programa != null && !programa.isBlank()) {
            buscarProgramaNormalizado(programa).ifPresent(prog ->
                elegirLineaDePrograma(prog.getId(), articulo.getId(), colorId).ifPresent(d::setProgramaDetalle)
            );
        }

        return detalleRepository.save(d);
    }

    /**
     * La guia suele traer el numero de programa con ceros a la izquierda
     * (ej. "Guia: 0534"), mientras que el programa se registra sin ellos
     * (ej. "534"). Se intenta primero un match exacto y, si no hay, se
     * reintenta quitando los ceros a la izquierda.
     */
    java.util.Optional<Programa> buscarProgramaNormalizado(String numero) {
        String limpio = numero.trim();
        java.util.Optional<Programa> exacto = programaRepository.findByNumero(limpio);
        if (exacto.isPresent()) return exacto;

        String sinCeros = limpio.replaceFirst("^0+(?=\\d)", "");
        if (!sinCeros.equals(limpio)) {
            return programaRepository.findByNumero(sinCeros);
        }
        return java.util.Optional.empty();
    }

    /**
     * Un programa puede tener mas de una linea con el mismo articulo (es
     * normal: dos lotes separados de la misma tela+gramaje+color dentro del
     * mismo programa). Se elige la mas antigua que todavia tenga pendiente
     * (FIFO), para ir llenando las lineas en orden; si todas ya estan
     * completas, se usa la primera igual, para no perder la trazabilidad.
     */
    private java.util.Optional<ProgramaDetalle> elegirLineaDePrograma(Long programaId, Long articuloId, Long colorId) {
        List<ProgramaDetalle> lineas = programaDetalleRepository.findByProgramaIdAndArticuloIdAndColorIdOrderByIdAsc(programaId, articuloId, colorId);
        if (lineas.isEmpty()) return java.util.Optional.empty();

        return lineas.stream()
                .filter(l -> l.getCantidadRecibida() < l.getCantidadSolicitada())
                .findFirst()
                .or(() -> java.util.Optional.of(lineas.get(0)));
    }

    @Transactional
    public void confirmarRecepcion(Long recepcionId, List<Long> detalleIds,
                                    List<Integer> rollosRecibidos, List<String> observaciones) {
        Recepcion r = recepcionRepository.findById(recepcionId).orElseThrow();
        // Idempotencia (auditoria P0-1, C1): solo se confirma una recepcion
        // PENDIENTE. Sin este guard, un doble-click / reenvio del formulario /
        // reintento de POST vuelve a correr el metodo: suma el stock otra vez,
        // duplica los movimientos de kardex INGRESO y la cantidadRecibida del
        // programa, inflando el inventario en silencio.
        if (r.getEstado() != Recepcion.EstadoRecepcion.PENDIENTE) {
            throw new IllegalStateException(
                    "La recepción " + r.getNumeroGuia() + " ya fue confirmada (estado " + r.getEstado()
                    + "); no se puede volver a confirmar.");
        }
        boolean tieneDiferencias = false;

        // Auditoria (integridad): un detalleId repetido en el POST sumaria el
        // stock/kardex de esa linea dos veces. Se rechaza cualquier duplicado
        // (complementa el guard M1 de pertenencia).
        if (new java.util.HashSet<>(detalleIds).size() != detalleIds.size()) {
            throw new IllegalArgumentException("Llegaron líneas de detalle repetidas. Recarga la página e intenta de nuevo.");
        }

        // La tela recibida entra al almacen PRINCIPAL. En una instancia nueva
        // (catalogo vacio) todavia no hay ninguna marcada como principal: en vez
        // de reventar con un NoSuchElementException criptico, se avisa que hacer.
        Ubicacion praderas = ubicacionRepository.findByEsPrincipalTrue().orElseThrow(() ->
                new IllegalArgumentException(
                        "No hay una ubicación marcada como principal. Ve a Catálogo → Ubicaciones, "
                        + "crea el almacén principal y márcalo como \"Es almacén principal\" antes de confirmar recepciones."));

        for (int i = 0; i < detalleIds.size(); i++) {
            RecepcionDetalle d = detalleRepository.findById(detalleIds.get(i)).orElseThrow();
            // M1 (auditoria): el detalle DEBE pertenecer a esta recepcion. Sin el
            // chequeo, un POST manipulado con un detalleId de OTRA recepcion movia
            // stock ajeno.
            if (d.getRecepcion() == null || !d.getRecepcion().getId().equals(recepcionId)) {
                throw new IllegalArgumentException(
                        "La línea de detalle " + detalleIds.get(i) + " no pertenece a esta recepción.");
            }
            Integer rollosRecibidosLinea = i < rollosRecibidos.size() ? rollosRecibidos.get(i) : d.getRollosGuia();
            // Auditoria (rigurosidad, jul-2026): un valor negativo aca nunca es
            // legitimo -- "rollos recibidos" es una cantidad fisica contada, y
            // sin este guardado bajaria el stock en vez de subirlo durante una
            // RECEPCION (entrada), silenciosamente y sin ningun error.
            if (rollosRecibidosLinea == null || rollosRecibidosLinea < 0) {
                throw new IllegalArgumentException(
                        "La cantidad de rollos recibidos no puede ser negativa (línea de detalle " + detalleIds.get(i) + ").");
            }
            if (rollosRecibidosLinea > MAX_ROLLOS_POR_LINEA) {
                throw new IllegalArgumentException(
                        "La cantidad de rollos recibidos (" + rollosRecibidosLinea + ") es demasiado alta "
                        + "(máx " + MAX_ROLLOS_POR_LINEA + " por línea).");
            }
            d.setRollosRecibidos(rollosRecibidosLinea);
            d.setObservacion(i < observaciones.size() ? observaciones.get(i) : "");
            detalleRepository.save(d);

            if (!d.getRollosRecibidos().equals(d.getRollosGuia())) {
                tieneDiferencias = true;
            }

            // Actualizar stock (pool único por artículo+ubicación, sin partición por empresa)
            int rollos = d.getRollosRecibidos();
            // Auditoria 17-jul-2026: si rollosGuia llega en 0 o null (dato de OCR
            // fallido), evitamos ArithmeticException: / by zero en medio de una
            // transaccion que mueve stock real. Sin base confiable para prorratear,
            // se usa BigDecimal.ZERO en vez de tronar.
            java.math.BigDecimal peso = (d.getPesoBrutoKg() != null && d.getRollosGuia() != null && d.getRollosGuia() > 0)
                ? d.getPesoBrutoKg().multiply(new java.math.BigDecimal(rollos)).divide(new java.math.BigDecimal(d.getRollosGuia()), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

            StockActual stock = stockActualRepository
                .findByArticuloIdAndUbicacionIdAndColorId(d.getArticulo().getId(), praderas.getId(), d.getColor().getId())
                .orElseGet(() -> {
                    StockActual s = new StockActual();
                    s.setArticulo(d.getArticulo());
                    s.setColor(d.getColor());
                    s.setUbicacion(praderas);
                    s.setRollos(0);
                    s.setPesoKg(java.math.BigDecimal.ZERO);
                    return s;
                });

            // Math.addExact: si la suma desborda int, lanza ArithmeticException
            // en vez de envolver a negativo y corromper el stock en silencio.
            stock.setRollos(Math.addExact(stock.getRollos(), rollos));
            stock.setPesoKg(stock.getPesoKg().add(peso));
            stockActualRepository.save(stock);

            if (d.getProgramaDetalle() != null) {
                ProgramaDetalle pd = d.getProgramaDetalle();
                pd.setCantidadRecibida(Math.addExact(pd.getCantidadRecibida(), rollos));
                programaDetalleRepository.save(pd);
            }

            // Kardex: la Recepción sí registra la empresa (dato informativo/trazabilidad)
            KardexMovimiento k = new KardexMovimiento();
            k.setArticulo(d.getArticulo());
            k.setColor(d.getColor());
            k.setEmpresa(r.getEmpresa());
            k.setUbicacionDestino(praderas);
            k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.INGRESO);
            k.setRollos(rollos);
            k.setPesoKg(peso);
            k.setUsuario(r.getUsuario());
            k.setObservaciones("Recepción " + r.getNumeroGuia());
            // Enlace a la linea de recepcion que origino el movimiento: es lo que
            // usa el kardex para ofrecer el ojito "ver guia" (StockController.
            // guiaDocPorDetalle). Nunca se seteaba, asi que el campo quedaba
            // siempre null y ese enlace no aparecia jamas.
            k.setRecepcionDetalleId(d.getId());
            kardexRepository.save(k);
        }

        r.setEstado(tieneDiferencias
            ? Recepcion.EstadoRecepcion.CON_DIFERENCIAS
            : Recepcion.EstadoRecepcion.CONFIRMADA);
        recepcionRepository.save(r);
        auditLogService.registrar("CONFIRMAR", "Recepcion", r.getId(),
                "Confirmo recepcion " + r.getNumeroGuia() + (tieneDiferencias ? " (con diferencias)" : ""));
    }

    @Transactional
    public Recepcion crearRecepcionConLineas(Long empresaId, String numeroGuia, String numeroFactura, LocalDate fechaGuia,
                                              String observaciones,
                                              String emisorNombre, String emisorRuc,
                                              List<CrearRecepcionConLineasRequest.LineaRequest> lineas) {
        Recepcion r = crearRecepcion(empresaId, numeroGuia, numeroFactura, fechaGuia, observaciones);
        // Emisor (tintoreria) tal como vino en la guia (V45): queda registrado
        // para trazabilidad. En blanco -> NULL, mismo criterio que guia/factura.
        String emisorNombreNorm = normalizar(emisorNombre);
        String emisorRucNorm = emisorRuc == null ? null : emisorRuc.replaceAll("\\D", "");
        r.setEmisorNombre(emisorNombreNorm == null || emisorNombreNorm.isBlank() ? null : emisorNombreNorm);
        r.setEmisorRuc(emisorRucNorm == null || emisorRucNorm.isBlank() ? null : emisorRucNorm);
        recepcionRepository.save(r);
        // Una linea sin articulo o sin color no se puede registrar. Antes se
        // descartaba en silencio: la recepcion quedaba con MENOS lineas que la
        // guia y nadie se enteraba hasta cuadrar el inventario a mano. Ahora se
        // rechaza indicando cual, para que se resuelva el match (o se cree el
        // articulo/color al vuelo) antes de guardar.
        for (int i = 0; i < lineas.size(); i++) {
            CrearRecepcionConLineasRequest.LineaRequest linea = lineas.get(i);
            if (linea.articuloId() == null || linea.colorId() == null) {
                throw new IllegalArgumentException(
                        "La línea " + (i + 1) + " no tiene artículo o color identificado. "
                        + "Resuélvela (o créalos) antes de guardar la recepción.");
            }
        }
        for (CrearRecepcionConLineasRequest.LineaRequest linea : lineas) {
            agregarDetalle(r.getId(), linea.articuloId(), linea.colorId(), linea.programaTenido(),
                    linea.rollosGuia(), linea.pesoBrutoKg());
        }
        return r;
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public void eliminarRecepcion(Long id) {
        Recepcion r = recepcionRepository.findById(id).orElseThrow();
        if (r.getEstado() != Recepcion.EstadoRecepcion.PENDIENTE) {
            throw new IllegalStateException(
                "Solo se pueden eliminar recepciones en estado PENDIENTE. " +
                "Esta recepción ya afectó el stock (estado " + r.getEstado() + ") y borrarla dejaría el inventario inconsistente.");
        }

        List<RecepcionDocumento> documentos = recepcionDocumentoRepository.findByRecepcionId(id);
        for (RecepcionDocumento doc : documentos) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(doc.getRutaArchivo()));
            } catch (java.io.IOException ignored) {
                // si el archivo fisico ya no existe, seguimos igual
            }
        }
        recepcionDocumentoRepository.deleteAll(documentos);

        recepcionRepository.delete(r);
        auditLogService.registrar("ELIMINAR", "Recepcion", id, "Elimino recepcion " + r.getNumeroGuia() + " (estaba PENDIENTE)");
    }
}
