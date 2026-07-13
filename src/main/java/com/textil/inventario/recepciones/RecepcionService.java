package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.inventario.*;
import com.textil.inventario.seguridad.UsuarioRepository;
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
    private final UsuarioRepository usuarioRepository;
    private final StockActualRepository stockActualRepository;
    private final KardexMovimientoRepository kardexRepository;
    private final UbicacionRepository ubicacionRepository;
    private final ProgramaRepository programaRepository;
    private final ProgramaDetalleRepository programaDetalleRepository;
    private final RecepcionDocumentoRepository recepcionDocumentoRepository;
    private final DocumentoStorageService documentoStorageService;

    public List<Recepcion> listarRecepcionesSinFactura() {
        return recepcionRepository.findByNumeroFacturaIsNullOrderByFechaGuiaDesc();
    }

    @Transactional
    public void asignarFactura(String numeroFactura, LocalDate fechaFactura, List<Long> recepcionIds) {
        for (Long id : recepcionIds) {
            Recepcion r = recepcionRepository.findById(id).orElseThrow();
            r.setNumeroFactura(numeroFactura);
            r.setFechaFactura(fechaFactura);
            recepcionRepository.save(r);
        }
    }

    @Transactional
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

    @Transactional
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
        return recepcionRepository.findFirstByNumeroGuia(numeroGuia);
    }

    public Recepcion buscarRecepcion(Long id) {
        return recepcionRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Recepcion crearRecepcion(Long empresaId, String numeroGuia, LocalDate fechaGuia, String observaciones) {
        return crearRecepcion(empresaId, numeroGuia, null, fechaGuia, observaciones);
    }

    @Transactional
    public Recepcion crearRecepcion(Long empresaId, String numeroGuia, String numeroFactura, LocalDate fechaGuia, String observaciones) {
        Recepcion r = new Recepcion();
        r.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        r.setNumeroGuia(numeroGuia);
        r.setNumeroFactura((numeroFactura == null || numeroFactura.isBlank()) ? null : numeroFactura.trim());
        r.setFechaGuia(fechaGuia);
        r.setFechaRecepcion(LocalDate.now());
        r.setObservaciones(observaciones);
        r.setEstado(Recepcion.EstadoRecepcion.PENDIENTE);
        r.setUsuario(usuarioRepository.findById(1L).orElseThrow());
        r.setUpdatedAt(java.time.LocalDateTime.now());
        return recepcionRepository.save(r);
    }

    @Transactional
    public RecepcionDetalle agregarDetalle(Long recepcionId, Long articuloId,
                                           String programa, Integer rollosGuia,
                                           java.math.BigDecimal pesoBruto) {
        RecepcionDetalle d = new RecepcionDetalle();
        d.setRecepcion(recepcionRepository.findById(recepcionId).orElseThrow());
        Articulo articulo = articuloRepository.findById(articuloId).orElseThrow();
        d.setArticulo(articulo);
        d.setProgramaTenido(programa);
        d.setRollosGuia(rollosGuia);
        d.setPesoBrutoKg(pesoBruto);

        if (programa != null && !programa.isBlank()) {
            programaRepository.findByNumero(programa.trim()).ifPresent(prog ->
                programaDetalleRepository.findByProgramaIdAndColorId(prog.getId(), articulo.getColor().getId())
                    .ifPresent(d::setProgramaDetalle)
            );
        }

        return detalleRepository.save(d);
    }

    @Transactional
    public void confirmarRecepcion(Long recepcionId, List<Long> detalleIds,
                                    List<Integer> rollosRecibidos, List<String> observaciones) {
        Recepcion r = recepcionRepository.findById(recepcionId).orElseThrow();
        boolean tieneDiferencias = false;

        Ubicacion praderas = ubicacionRepository.findByEsPrincipalTrue().orElseThrow();

        List<RecepcionDetalle> detalles = detalleRepository.findByRecepcionId(recepcionId);

        for (int i = 0; i < detalles.size(); i++) {
            RecepcionDetalle d = detalles.get(i);
            d.setRollosRecibidos(i < rollosRecibidos.size() ? rollosRecibidos.get(i) : d.getRollosGuia());
            d.setObservacion(i < observaciones.size() ? observaciones.get(i) : "");
            detalleRepository.save(d);

            if (!d.getRollosRecibidos().equals(d.getRollosGuia())) {
                tieneDiferencias = true;
            }

            // Actualizar stock (pool único por artículo+ubicación, sin partición por empresa)
            int rollos = d.getRollosRecibidos();
            java.math.BigDecimal peso = d.getPesoBrutoKg() != null
                ? d.getPesoBrutoKg().multiply(new java.math.BigDecimal(rollos)).divide(new java.math.BigDecimal(d.getRollosGuia()), 2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

            StockActual stock = stockActualRepository
                .findByArticuloIdAndUbicacionId(d.getArticulo().getId(), praderas.getId())
                .orElseGet(() -> {
                    StockActual s = new StockActual();
                    s.setArticulo(d.getArticulo());
                    s.setUbicacion(praderas);
                    s.setRollos(0);
                    s.setPesoKg(java.math.BigDecimal.ZERO);
                    return s;
                });

            stock.setRollos(stock.getRollos() + rollos);
            stock.setPesoKg(stock.getPesoKg().add(peso));
            stockActualRepository.save(stock);

            if (d.getProgramaDetalle() != null) {
                ProgramaDetalle pd = d.getProgramaDetalle();
                pd.setCantidadRecibida(pd.getCantidadRecibida() + rollos);
                programaDetalleRepository.save(pd);
            }

            // Kardex: la Recepción sí registra la empresa (dato informativo/trazabilidad)
            KardexMovimiento k = new KardexMovimiento();
            k.setArticulo(d.getArticulo());
            k.setEmpresa(r.getEmpresa());
            k.setUbicacionDestino(praderas);
            k.setTipoMovimiento(KardexMovimiento.TipoMovimiento.INGRESO);
            k.setRollos(rollos);
            k.setPesoKg(peso);
            k.setUsuario(r.getUsuario());
            k.setObservaciones("Recepción " + r.getNumeroGuia());
            kardexRepository.save(k);
        }

        r.setEstado(tieneDiferencias
            ? Recepcion.EstadoRecepcion.CON_DIFERENCIAS
            : Recepcion.EstadoRecepcion.CONFIRMADA);
        recepcionRepository.save(r);
    }

    @Transactional
    public Recepcion crearRecepcionConLineas(Long empresaId, String numeroGuia, String numeroFactura, LocalDate fechaGuia,
                                              String observaciones,
                                              List<CrearRecepcionConLineasRequest.LineaRequest> lineas) {
        Recepcion r = crearRecepcion(empresaId, numeroGuia, numeroFactura, fechaGuia, observaciones);
        for (CrearRecepcionConLineasRequest.LineaRequest linea : lineas) {
            if (linea.articuloId() == null) continue;
            agregarDetalle(r.getId(), linea.articuloId(), linea.programaTenido(),
                    linea.rollosGuia(), linea.pesoBrutoKg());
        }
        return r;
    }
}