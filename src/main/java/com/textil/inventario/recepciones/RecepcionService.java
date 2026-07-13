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

    public List<Recepcion> listarRecepciones() {
        return recepcionRepository.findAllByOrderByCreatedAtDesc();
    }

    public Recepcion buscarRecepcion(Long id) {
        return recepcionRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Recepcion crearRecepcion(Long empresaId, String numeroGuia, LocalDate fechaGuia, String observaciones) {
        Recepcion r = new Recepcion();
        r.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        r.setNumeroGuia(numeroGuia);
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
        d.setArticulo(articuloRepository.findById(articuloId).orElseThrow());
        d.setProgramaTenido(programa);
        d.setRollosGuia(rollosGuia);
        d.setPesoBrutoKg(pesoBruto);
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
    public Recepcion crearRecepcionConLineas(Long empresaId, String numeroGuia, LocalDate fechaGuia,
                                              String observaciones,
                                              List<CrearRecepcionConLineasRequest.LineaRequest> lineas) {
        Recepcion r = crearRecepcion(empresaId, numeroGuia, fechaGuia, observaciones);
        for (CrearRecepcionConLineasRequest.LineaRequest linea : lineas) {
            if (linea.articuloId() == null) continue;
            agregarDetalle(r.getId(), linea.articuloId(), linea.programaTenido(),
                    linea.rollosGuia(), linea.pesoBrutoKg());
        }
        return r;
    }
}