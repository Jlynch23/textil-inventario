package com.textil.inventario;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.inventario.KardexMovimientoRepository;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionDetalle;
import com.textil.inventario.recepciones.RecepcionDetalleRepository;
import com.textil.inventario.recepciones.RecepcionRepository;
import com.textil.inventario.recepciones.RecepcionService;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de CONCURRENCIA REAL (auditoría Sprint 2 #5): demuestra —contra un MySQL
 * de verdad, en dos transacciones independientes— que confirmar la MISMA recepción
 * desde dos hilos a la vez no duplica stock ni kardex.
 *
 * Se comprueba: una confirmación termina bien, la otra falla (por bloqueo
 * optimista @Version o por el guard de idempotencia), el stock sube UNA sola vez,
 * el kardex registra UN solo movimiento y no quedan cambios parciales.
 *
 * Gated por RUN_DB_IT=true (lo pone el job de CI con el servicio MySQL). En un
 * `mvn test` local sin BD, se salta.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
class ConfirmacionConcurrenteTest {

    @Autowired private RecepcionService recepcionService;
    @Autowired private RecepcionRepository recepcionRepository;
    @Autowired private RecepcionDetalleRepository detalleRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private ArticuloRepository articuloRepository;
    @Autowired private ColorRepository colorRepository;
    @Autowired private TipoTelaRepository tipoTelaRepository;
    @Autowired private TituloRepository tituloRepository;
    @Autowired private ComposicionRepository composicionRepository;
    @Autowired private AcabadoRepository acabadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private StockActualRepository stockActualRepository;
    @Autowired private KardexMovimientoRepository kardexRepository;

    private Long recepcionId;
    private Long detalleId;
    private Long articuloId;

    @BeforeEach
    void sembrar() {
        // Limpieza de lo que toca esta prueba (la BD de CI es efímera, pero así el
        // estado inicial es determinista aunque se re-ejecute).
        kardexRepository.deleteAll();
        stockActualRepository.deleteAll();
        detalleRepository.deleteAll();
        recepcionRepository.deleteAll();

        Ubicacion principal = ubicacionRepository.findByEsPrincipalTrue().orElseGet(() -> {
            Ubicacion u = new Ubicacion();
            u.setCodigo("PRAD-IT");
            u.setNombre("Praderas (IT)");
            u.setTipo(Ubicacion.TipoUbicacion.ALMACEN);
            u.setEsPrincipal(true);
            u.setActivo(true);
            return ubicacionRepository.save(u);
        });

        Empresa empresa = empresaRepository.findByActivoTrue().stream().findFirst().orElseGet(() -> {
            Empresa e = new Empresa();
            e.setNombre("EMPRESA IT");
            e.setRuc("20999999999");
            e.setActivo(true);
            return empresaRepository.save(e);
        });

        TipoTela tipoTela = crearTipoTela();
        Titulo titulo = crearTitulo();
        Composicion composicion = crearComposicion();
        Acabado acabado = crearAcabado();

        Articulo articulo = new Articulo();
        articulo.setCodigoInterno("ART-IT-1");
        articulo.setTipoTela(tipoTela);
        articulo.setTitulo(titulo);
        articulo.setComposicion(composicion);
        articulo.setAcabado(acabado);
        articulo.setActivo(true);
        articulo = articuloRepository.save(articulo);
        articuloId = articulo.getId();

        Color color = new Color();
        color.setNombreOficial("NEGRO IT");
        color.setCodigoFastDye("999999");
        color.setActivo(true);
        color = colorRepository.save(color);

        Usuario usuario = usuarioRepository.findAll().stream().findFirst().orElseThrow(
                () -> new IllegalStateException("No hay usuarios sembrados por las migraciones."));

        Recepcion r = new Recepcion();
        r.setEmpresa(empresa);
        r.setNumeroGuia("TG01-IT-0001");
        r.setFechaGuia(LocalDate.now());
        r.setFechaRecepcion(LocalDate.now());
        r.setEstado(Recepcion.EstadoRecepcion.PENDIENTE);
        r.setUsuario(usuario);
        // updated_at es NOT NULL y Recepcion solo lo setea en @PreUpdate (no en el
        // insert); el crearRecepcion real lo pone a mano, el seed tambien debe.
        r.setUpdatedAt(java.time.LocalDateTime.now());
        r = recepcionRepository.save(r);
        recepcionId = r.getId();

        RecepcionDetalle d = new RecepcionDetalle();
        d.setRecepcion(r);
        d.setArticulo(articulo);
        d.setColor(color);
        d.setRollosGuia(14);
        d.setPesoBrutoKg(new BigDecimal("300"));
        d = detalleRepository.save(d);
        detalleId = d.getId();
    }

    @Test
    void confirmarLaMismaRecepcionDesdeDosHilos_noDuplicaStockNiKardex() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);

        Callable<String> confirmar = () -> {
            largada.await();
            try {
                recepcionService.confirmarRecepcion(
                        recepcionId, List.of(detalleId), List.of(14), List.of(""));
                return "OK";
            } catch (Exception e) {
                return "FAIL:" + e.getClass().getSimpleName();
            }
        };

        Future<String> f1 = pool.submit(confirmar);
        Future<String> f2 = pool.submit(confirmar);
        largada.countDown(); // suelta los dos hilos a la vez

        String r1 = f1.get(30, TimeUnit.SECONDS);
        String r2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        long exitos = List.of(r1, r2).stream().filter("OK"::equals).count();
        long fallos = List.of(r1, r2).stream().filter(s -> s.startsWith("FAIL")).count();

        // Exactamente una confirmación termina bien y la otra es rechazada.
        assertThat(exitos).as("una sola confirmación debe terminar bien (r1=%s, r2=%s)", r1, r2).isEqualTo(1);
        assertThat(fallos).as("la otra confirmación debe fallar (r1=%s, r2=%s)", r1, r2).isEqualTo(1);

        // La recepción queda CONFIRMADA (una vez).
        Recepcion recargada = recepcionRepository.findById(recepcionId).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(Recepcion.EstadoRecepcion.CONFIRMADA);

        // El stock subió UNA sola vez (14 rollos, no 28).
        int totalRollos = stockActualRepository.findByUbicacionId(
                        ubicacionRepository.findByEsPrincipalTrue().orElseThrow().getId()).stream()
                .filter(s -> s.getArticulo().getId().equals(articuloId))
                .mapToInt(s -> s.getRollos()).sum();
        assertThat(totalRollos).as("el stock debe subir una sola vez").isEqualTo(14);

        // El kardex registró UN solo movimiento para el artículo.
        assertThat(kardexRepository.findByArticuloIdOrderByFechaDesc(articuloId))
                .as("un solo movimiento de kardex").hasSize(1);
    }

    private TipoTela crearTipoTela() {
        TipoTela t = new TipoTela();
        t.setNombre("RIB 2X1 IT");
        t.setActivo(true);
        return tipoTelaRepository.save(t);
    }

    private Titulo crearTitulo() {
        Titulo t = new Titulo();
        t.setValor("30/1 IT");
        t.setActivo(true);
        return tituloRepository.save(t);
    }

    private Composicion crearComposicion() {
        Composicion c = new Composicion();
        c.setNombre("ALGODON IT");
        c.setActivo(true);
        return composicionRepository.save(c);
    }

    private Acabado crearAcabado() {
        Acabado a = new Acabado();
        a.setNombre("LISO IT");
        a.setActivo(true);
        return acabadoRepository.save(a);
    }
}
