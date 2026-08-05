package com.textil.inventario;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.recepciones.Programa;
import com.textil.inventario.recepciones.ProgramaDetalle;
import com.textil.inventario.recepciones.ProgramaDetalleRepository;
import com.textil.inventario.recepciones.ProgramaRepository;
import com.textil.inventario.recepciones.ProgramaService;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionDetalle;
import com.textil.inventario.recepciones.RecepcionDetalleRepository;
import com.textil.inventario.recepciones.RecepcionRepository;
import com.textil.inventario.recepciones.RecepcionService;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import com.textil.inventario.transferencias.Transferencia;
import com.textil.inventario.transferencias.TransferenciaDetalle;
import com.textil.inventario.transferencias.TransferenciaDetalleRepository;
import com.textil.inventario.transferencias.TransferenciaRepository;
import com.textil.inventario.transferencias.TransferenciaService;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de regresión para el apagado de open-in-view (auditoría #9).
 *
 * Con OSIV apagado, la sesión de Hibernate se cierra al terminar la capa de
 * servicio; la vista recibe entidades DETACHED. Si un finder no trae precargado
 * lo que la plantilla navega, el render revienta con LazyInitializationException.
 *
 * Esto YA pasó una vez en producción-dev: el @EntityGraph por defecto es tipo
 * FETCH, que degrada a LAZY toda asociación NO listada -- incluidas las EAGER --,
 * así que recepcion.empresa quedaba como proxy y explotaba al mostrar su nombre.
 * El fix fue type = LOAD en los finder de detalle.
 *
 * Esta prueba carga cada pantalla de detalle A TRAVÉS de su método de servicio
 * (igual que el controlador) y, sobre la entidad ya detached, verifica que las
 * asociaciones que la plantilla recorre estén INICIALIZADAS. Sin el fix, las
 * aserciones Hibernate.isInitialized(...) fallan (y navegar el dato lanzaría
 * LazyInit, como en el navegador). Los tests de servicio normales NO lo cachan
 * porque no renderizan; este sí, contra un MySQL real.
 *
 * Gated por RUN_DB_IT=true (lo pone el job de CI con el servicio MySQL).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
class OsivFetchGraphTest {

    @Autowired private RecepcionService recepcionService;
    @Autowired private ProgramaService programaService;
    @Autowired private TransferenciaService transferenciaService;

    @Autowired private RecepcionRepository recepcionRepository;
    @Autowired private RecepcionDetalleRepository recepcionDetalleRepository;
    @Autowired private ProgramaRepository programaRepository;
    @Autowired private ProgramaDetalleRepository programaDetalleRepository;
    @Autowired private TransferenciaRepository transferenciaRepository;
    @Autowired private TransferenciaDetalleRepository transferenciaDetalleRepository;
    @Autowired private com.textil.inventario.inventario.KardexMovimientoRepository kardexMovimientoRepository;

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UbicacionRepository ubicacionRepository;
    @Autowired private ArticuloRepository articuloRepository;
    @Autowired private ColorRepository colorRepository;
    @Autowired private TipoTelaRepository tipoTelaRepository;
    @Autowired private TituloRepository tituloRepository;
    @Autowired private ComposicionRepository composicionRepository;
    @Autowired private AcabadoRepository acabadoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long recepcionId;
    private Long programaId;
    private Long transferenciaId;

    @BeforeEach
    void sembrar() {
        // Limpieza FK-safe de los agregados que toca esta prueba. RecepcionDetalle
        // referencia a ProgramaDetalle, así que va ANTES que programa_detalle.
        //
        // El kardex va PRIMERO: kardex_movimientos apunta a recepcion_detalles y a
        // transferencias (fk_kardex_recepcion_detalle / fk_kardex_transferencia,
        // V13). Esta limpieza no lo borraba y pasaba igual solo porque
        // recepcion_detalle_id quedaba SIEMPRE en NULL -- ese era justamente el
        // defecto que rompía el enlace "ver guía" del kardex. Al empezar a
        // llenarlo, los movimientos que deja ConfirmacionConcurrenteTest (corre
        // antes en el mismo esquema) trababan este deleteAll con la FK.
        kardexMovimientoRepository.deleteAll();
        transferenciaDetalleRepository.deleteAll();
        transferenciaRepository.deleteAll();
        recepcionDetalleRepository.deleteAll();
        recepcionRepository.deleteAll();
        programaDetalleRepository.deleteAll();
        programaRepository.deleteAll();

        Ubicacion origen = ubicacionRepository.findByEsPrincipalTrue().orElseGet(() -> {
            Ubicacion u = new Ubicacion();
            u.setCodigo("PRAD-OSIV");
            u.setNombre("Praderas (OSIV)");
            u.setTipo(Ubicacion.TipoUbicacion.ALMACEN);
            u.setEsPrincipal(true);
            u.setActivo(true);
            return ubicacionRepository.save(u);
        });

        Empresa empresa = empresaRepository.findByActivoTrue().stream().findFirst().orElseGet(() -> {
            Empresa e = new Empresa();
            e.setNombre("EMPRESA OSIV");
            e.setRuc("20888888888");
            e.setActivo(true);
            return empresaRepository.save(e);
        });

        TipoTela tipoTela = tipoTelaRepository.findByNombreIgnoreCase("RIB OSIV")
                .orElseGet(() -> { TipoTela t = new TipoTela(); t.setNombre("RIB OSIV"); t.setActivo(true); return tipoTelaRepository.save(t); });
        Titulo titulo = tituloRepository.findByValorIgnoreCase("30/1 OSIV")
                .orElseGet(() -> { Titulo t = new Titulo(); t.setValor("30/1 OSIV"); t.setActivo(true); return tituloRepository.save(t); });
        Composicion composicion = composicionRepository.findByNombreIgnoreCase("ALGODON OSIV")
                .orElseGet(() -> { Composicion c = new Composicion(); c.setNombre("ALGODON OSIV"); c.setActivo(true); return composicionRepository.save(c); });
        Acabado acabado = acabadoRepository.findByNombreIgnoreCase("LISO OSIV")
                .orElseGet(() -> { Acabado a = new Acabado(); a.setNombre("LISO OSIV"); a.setActivo(true); return acabadoRepository.save(a); });

        // find-or-create: el @BeforeEach corre por cada test y NO limpia el
        // catalogo (articulo/color quedan por sus FK con kardex/stock/detalles).
        // Sin reutilizarlos, la 2da corrida chocaba con el unique de codigo_interno.
        Articulo articulo = articuloRepository
                .findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(
                        tipoTela.getId(), titulo.getId(), composicion.getId(), acabado.getId())
                .orElseGet(() -> {
                    Articulo a = new Articulo();
                    a.setCodigoInterno("ART-OSIV-1");
                    a.setTipoTela(tipoTela);
                    a.setTitulo(titulo);
                    a.setComposicion(composicion);
                    a.setAcabado(acabado);
                    a.setActivo(true);
                    return articuloRepository.save(a);
                });

        Color color = colorRepository.findByNombreOficialIgnoreCase("NEGRO OSIV")
                .orElseGet(() -> {
                    Color c = new Color();
                    c.setNombreOficial("NEGRO OSIV");
                    c.setCodigoFastDye("888888");
                    c.setActivo(true);
                    return colorRepository.save(c);
                });

        Usuario usuario = usuarioRepository.findAll().stream().findFirst().orElseThrow(
                () -> new IllegalStateException("No hay usuarios sembrados por las migraciones."));

        // --- Recepción + detalle (la vista muestra recepcion.empresa.nombre + detalles) ---
        Recepcion r = new Recepcion();
        r.setEmpresa(empresa);
        r.setNumeroGuia("TG01-OSIV-0001");
        r.setFechaGuia(LocalDate.now());
        r.setFechaRecepcion(LocalDate.now());
        r.setEstado(Recepcion.EstadoRecepcion.PENDIENTE);
        r.setUsuario(usuario);
        r.setUpdatedAt(java.time.LocalDateTime.now());
        r = recepcionRepository.save(r);
        recepcionId = r.getId();

        RecepcionDetalle rd = new RecepcionDetalle();
        rd.setRecepcion(r);
        rd.setArticulo(articulo);
        rd.setColor(color);
        rd.setRollosGuia(10);
        rd.setPesoBrutoKg(new BigDecimal("200"));
        recepcionDetalleRepository.save(rd);

        // --- Programa + detalle (la lista/seguimiento muestran empresa + detalles) ---
        Programa p = new Programa();
        p.setNumero("PROG-OSIV-1");
        p.setEmpresa(empresa);
        p.setFecha(LocalDate.now());
        p.setTotalRollos(10);
        p = programaRepository.save(p);
        programaId = p.getId();

        ProgramaDetalle pd = new ProgramaDetalle();
        pd.setPrograma(p);
        pd.setArticulo(articulo);
        pd.setColor(color);
        pd.setCantidadSolicitada(10);
        pd.setCantidadRecibida(0);
        programaDetalleRepository.save(pd);

        // --- Transferencia + detalle (la vista muestra ubicacionOrigen.nombre + detalles) ---
        Transferencia t = new Transferencia();
        t.setNumero("TRF-OSIV-000001");
        t.setUbicacionOrigen(origen);
        t.setUsuarioSolicita(usuario);
        t.setEstado(Transferencia.EstadoTransferencia.BORRADOR);
        t = transferenciaRepository.save(t);
        transferenciaId = t.getId();

        TransferenciaDetalle td = new TransferenciaDetalle();
        td.setTransferencia(t);
        td.setArticulo(articulo);
        td.setColor(color);
        td.setCantidadSolicitada(5);
        transferenciaDetalleRepository.save(td);
    }

    @Test
    void recepcionDetalle_traeEmpresaYDetallesInicializados() {
        // Se carga como el controlador; la entidad vuelve DETACHED (sin sesión).
        Recepcion r = recepcionService.buscarRecepcion(recepcionId);

        // empresa es EAGER: el bug de FETCH la dejaba como proxy -> LazyInit al
        // mostrar recepcion.empresa.nombre. Con type=LOAD debe venir inicializada.
        assertThat(Hibernate.isInitialized(r.getEmpresa()))
                .as("recepcion.empresa debe venir inicializada (no proxy)").isTrue();
        assertThat(Hibernate.isInitialized(r.getDetalles()))
                .as("recepcion.detalles debe venir inicializada").isTrue();

        // Navegación real de la plantilla: no debe lanzar sobre la entidad detached.
        assertThat(r.getEmpresa().getNombre()).isNotBlank();
        assertThat(r.getDetalles()).isNotEmpty();
        RecepcionDetalle d = r.getDetalles().get(0);
        assertThat(d.getArticulo().getTipoTela().getNombre()).isNotBlank();
        assertThat(d.getColor().getNombreMostrar()).isNotBlank();
    }

    @Test
    void programaSeguimiento_traeEmpresaYDetallesInicializados() {
        Programa p = programaService.buscarPrograma(programaId);

        assertThat(Hibernate.isInitialized(p.getEmpresa()))
                .as("programa.empresa debe venir inicializada (no proxy)").isTrue();
        assertThat(Hibernate.isInitialized(p.getDetalles()))
                .as("programa.detalles debe venir inicializada").isTrue();

        assertThat(p.getEmpresa().getNombre()).isNotBlank();
        assertThat(p.getDetalles()).isNotEmpty();
    }

    @Test
    void programaLista_traeDetallesInicializadosParaLosHelpers() {
        // listarProgramas es @Transactional y fuerza la init de detalles (batched);
        // los helpers de la lista (completo, porcentajeProgreso) los recorren al render.
        List<Programa> programas = programaService.listarProgramas();

        assertThat(programas).isNotEmpty();
        for (Programa p : programas) {
            assertThat(Hibernate.isInitialized(p.getDetalles()))
                    .as("cada programa.detalles de la lista debe venir inicializada").isTrue();
            // isCompleto() recorre detalles: no debe lanzar sobre la entidad detached.
            p.isCompleto();
        }
    }

    @Test
    void transferenciaDetalle_traeUbicacionOrigenYDetallesInicializados() {
        Transferencia t = transferenciaService.buscarTransferencia(transferenciaId);

        // ubicacionOrigen es LAZY: debe venir listada en el grafo LOAD.
        assertThat(Hibernate.isInitialized(t.getUbicacionOrigen()))
                .as("transferencia.ubicacionOrigen debe venir inicializada").isTrue();
        assertThat(Hibernate.isInitialized(t.getDetalles()))
                .as("transferencia.detalles debe venir inicializada").isTrue();

        assertThat(t.getUbicacionOrigen().getNombre()).isNotBlank();
        assertThat(t.getDetalles()).isNotEmpty();
        TransferenciaDetalle d = t.getDetalles().get(0);
        assertThat(d.getArticulo().getTitulo().getValor()).isNotBlank();
    }
}
