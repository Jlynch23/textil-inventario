package com.textil.inventario.recepciones;

import com.textil.inventario.programas.Programa;
import com.textil.inventario.programas.ProgramaDetalle;
import com.textil.inventario.programas.ProgramaRepository;
import com.textil.inventario.programas.ProgramaDetalleRepository;
import com.textil.inventario.auditoria.AuditLogService;
import com.textil.inventario.catalogo.ArticuloRepository;
import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.inventario.KardexMovimientoRepository;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.seguridad.UsuarioActualService;
import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.Ubicacion;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecepcionServiceTest {

    @Mock private RecepcionRepository recepcionRepository;
    @Mock private RecepcionDetalleRepository detalleRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private ArticuloRepository articuloRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private UsuarioActualService usuarioActualService;
    @Mock private StockActualRepository stockActualRepository;
    @Mock private KardexMovimientoRepository kardexRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private ProgramaRepository programaRepository;
    @Mock private ProgramaDetalleRepository programaDetalleRepository;
    @Mock private RecepcionDocumentoRepository recepcionDocumentoRepository;
    @Mock private DocumentoStorageService documentoStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private RecepcionService service;

    @Test
    void buscarProgramaNormalizado_matchExacto_noReintentaSinCeros() {
        Programa programa = new Programa();
        when(programaRepository.findByNumero("534")).thenReturn(Optional.of(programa));

        Optional<Programa> resultado = service.buscarProgramaNormalizado("534");

        assertThat(resultado).contains(programa);
        verify(programaRepository, never()).findByNumero("0534");
    }

    @Test
    void buscarProgramaNormalizado_conCerosIzquierda_reintentaSinCeros() {
        Programa programa = new Programa();
        when(programaRepository.findByNumero("0534")).thenReturn(Optional.empty());
        when(programaRepository.findByNumero("534")).thenReturn(Optional.of(programa));

        Optional<Programa> resultado = service.buscarProgramaNormalizado("0534");

        assertThat(resultado).contains(programa);
        verify(programaRepository).findByNumero("0534");
        verify(programaRepository).findByNumero("534");
    }

    @Test
    void buscarProgramaNormalizado_sinMatchNiConCerosNiSin_devuelveVacio() {
        when(programaRepository.findByNumero("0534")).thenReturn(Optional.empty());
        when(programaRepository.findByNumero("534")).thenReturn(Optional.empty());

        Optional<Programa> resultado = service.buscarProgramaNormalizado("0534");

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscarProgramaNormalizado_sinCerosYSinMatch_noReintentaDeNuevo() {
        when(programaRepository.findByNumero("534")).thenReturn(Optional.empty());

        Optional<Programa> resultado = service.buscarProgramaNormalizado("534");

        assertThat(resultado).isEmpty();
        verify(programaRepository, times(1)).findByNumero(anyString());
    }

    @Test
    void buscarProgramaNormalizado_recortaEspacios() {
        Programa programa = new Programa();
        when(programaRepository.findByNumero("534")).thenReturn(Optional.of(programa));

        Optional<Programa> resultado = service.buscarProgramaNormalizado("  534  ");

        assertThat(resultado).contains(programa);
    }

    // --- confirmarRecepcion(): camino critico de negocio (mueve stock real) ---

    private Recepcion recepcionDePrueba() {
        Recepcion r = new Recepcion();
        r.setId(1L);
        r.setNumeroGuia("TG01-00021376");
        r.setEstado(Recepcion.EstadoRecepcion.PENDIENTE);
        return r;
    }

    private Ubicacion praderasDePrueba() {
        Ubicacion u = new Ubicacion();
        u.setId(1L);
        u.setNombre("Praderas");
        return u;
    }

    private Articulo articuloDePrueba() {
        Articulo a = new Articulo();
        a.setId(10L);
        return a;
    }

    // El Color ya no vive en Articulo (ver V26): cada RecepcionDetalle/StockActual
    // de prueba necesita su propio Color, igual que en el diseño real.
    private Color colorDePrueba() {
        Color c = new Color();
        c.setId(20L);
        c.setNombreOficial("NEGRO");
        return c;
    }

    @Test
    void confirmarRecepcion_sinDiferencias_calculaPesoCompletoYActualizaStockYKardex() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setRecepcion(recepcion);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        service.confirmarRecepcion(1L, List.of(100L), List.of(14), List.of(""));

        ArgumentCaptor<com.textil.inventario.inventario.StockActual> stockCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getRollos()).isEqualTo(14);
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo("300.00");

        ArgumentCaptor<com.textil.inventario.inventario.KardexMovimiento> kardexCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.KardexMovimiento.class);
        verify(kardexRepository).save(kardexCap.capture());
        assertThat(kardexCap.getValue().getRollos()).isEqualTo(14);
        assertThat(kardexCap.getValue().getPesoKg()).isEqualByComparingTo("300.00");

        assertThat(recepcion.getEstado()).isEqualTo(Recepcion.EstadoRecepcion.CONFIRMADA);
        verify(recepcionRepository).save(recepcion);
    }

    @Test
    void confirmarRecepcion_conFaltante_prorrateaPesoYMarcaConDiferencias() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setRecepcion(recepcion);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        service.confirmarRecepcion(1L, List.of(100L), List.of(13), List.of("Falto 1 rollo"));

        ArgumentCaptor<com.textil.inventario.inventario.StockActual> stockCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getRollos()).isEqualTo(13);
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo("278.57");

        assertThat(recepcion.getEstado()).isEqualTo(Recepcion.EstadoRecepcion.CON_DIFERENCIAS);
    }

    @Test
    void confirmarRecepcion_rollosGuiaCero_noLanzaExcepcion_pesoQuedaEnCero() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setRecepcion(recepcion);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(0);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        assertThatCode(() ->
            service.confirmarRecepcion(1L, List.of(100L), List.of(0), List.of("OCR fallido, revisar"))
        ).doesNotThrowAnyException();

        ArgumentCaptor<com.textil.inventario.inventario.StockActual> stockCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.StockActual.class);
        verify(stockActualRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getPesoKg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void confirmarRecepcion_rollosNegativos_lanzaExcepcionYNoTocaStock() {
        Recepcion recepcion = recepcionDePrueba();
        Ubicacion praderas = praderasDePrueba();
        Articulo articulo = articuloDePrueba();
        Color color = colorDePrueba();

        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setRecepcion(recepcion);
        detalle.setArticulo(articulo);
        detalle.setColor(color);
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderas));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L), List.of(-3), List.of(""))
        ).isInstanceOf(IllegalArgumentException.class);

        verify(stockActualRepository, never()).save(any());
    }

    @Test
    void confirmarRecepcion_yaConfirmada_lanzaYNoTocaStock() {
        // Idempotencia (auditoria P0-1, C1): una recepcion ya confirmada no se
        // vuelve a confirmar; un doble-click no debe re-sumar el stock ni duplicar
        // el kardex.
        Recepcion recepcion = recepcionDePrueba();
        recepcion.setEstado(Recepcion.EstadoRecepcion.CONFIRMADA);
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L), List.of(14), List.of(""))
        ).isInstanceOf(IllegalStateException.class);

        verify(stockActualRepository, never()).save(any());
        verify(kardexRepository, never()).save(any());
    }

    @Test
    void confirmarRecepcion_detalleDeOtraRecepcion_lanzaYNoTocaStock() {
        // M1 (auditoria): un POST manipulado con un detalleId que pertenece a OTRA
        // recepcion no debe mover stock de esta.
        Recepcion recepcion = recepcionDePrueba(); // id 1
        Recepcion otra = new Recepcion();
        otra.setId(999L);
        RecepcionDetalle detalleAjeno = new RecepcionDetalle();
        detalleAjeno.setId(100L);
        detalleAjeno.setRecepcion(otra); // pertenece a la 999, no a la 1

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderasDePrueba()));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalleAjeno));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L), List.of(14), List.of(""))
        ).isInstanceOf(IllegalArgumentException.class);

        verify(stockActualRepository, never()).save(any());
        verify(kardexRepository, never()).save(any());
    }

    @Test
    void confirmarRecepcion_detalleIdRepetido_lanzaYNoTocaStock() {
        // Auditoria: un detalleId repetido en el POST sumaria el stock dos veces.
        Recepcion recepcion = recepcionDePrueba();
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L, 100L), List.of(14, 14), List.of("", ""))
        ).isInstanceOf(IllegalArgumentException.class);

        verify(stockActualRepository, never()).save(any());
        verify(kardexRepository, never()).save(any());
    }

    @Test
    void crearRecepcion_guiaDuplicada_lanzaYNoGuarda() {
        // Una guía = una recepción: no se puede registrar dos veces (si no,
        // confirmar ambas duplica el stock y deja el programa en pendiente negativo).
        when(recepcionRepository.findFirstByNumeroGuia("TG01-00019662"))
                .thenReturn(Optional.of(new Recepcion()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.crearRecepcion(1L, "TG01-00019662", null, java.time.LocalDate.now(), null)
        ).isInstanceOf(IllegalArgumentException.class);

        verify(recepcionRepository, never()).save(any());
    }

    @Test
    void crearRecepcion_guiaEnBlanco_seGuardaComoNull() {
        // INV-02: una guía en blanco se persiste como NULL (no ""), para que el
        // UNIQUE permita varias recepciones sin guía (MySQL admite múltiples NULL).
        when(empresaRepository.findById(1L))
                .thenReturn(Optional.of(new com.textil.inventario.catalogo.Empresa()));
        when(usuarioActualService.obtenerUsuarioActual())
                .thenReturn(new com.textil.inventario.seguridad.Usuario());
        when(recepcionRepository.saveAndFlush(any(Recepcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.crearRecepcion(1L, "   ", null, java.time.LocalDate.now(), null);

        ArgumentCaptor<Recepcion> cap = ArgumentCaptor.forClass(Recepcion.class);
        verify(recepcionRepository).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getNumeroGuia()).isNull();
        // Con guía nula no tiene sentido consultar duplicados.
        verify(recepcionRepository, never()).findFirstByNumeroGuia(any());
    }

    @Test
    void crearRecepcion_violacionUniqueConcurrente_seTraduceAExcepcionDeDominio() {
        // INV-02: dos POST simultáneos con la misma guía pasan ambos el findFirst
        // (read-then-write); el UNIQUE de BD deja entrar solo uno y el otro lanza
        // DataIntegrityViolationException, que se traduce a un error de dominio
        // (mensaje claro) en vez de un 500.
        when(recepcionRepository.findFirstByNumeroGuia("TG01-00019662"))
                .thenReturn(Optional.empty());
        when(empresaRepository.findById(1L))
                .thenReturn(Optional.of(new com.textil.inventario.catalogo.Empresa()));
        when(usuarioActualService.obtenerUsuarioActual())
                .thenReturn(new com.textil.inventario.seguridad.Usuario());
        when(recepcionRepository.saveAndFlush(any(Recepcion.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_recepcion_numero_guia"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.crearRecepcion(1L, "TG01-00019662", null, java.time.LocalDate.now(), null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmarRecepcion_enlazaCadaKardexConSuLineaDeRecepcion() {
        // El kardex guarda de que linea de recepcion vino cada ingreso: es lo
        // que habilita el ojito "ver guia". El campo nunca se seteaba, asi que
        // el enlace no aparecia nunca.
        Recepcion recepcion = recepcionDePrueba();
        RecepcionDetalle detalle = new RecepcionDetalle();
        detalle.setId(100L);
        detalle.setRecepcion(recepcion);
        detalle.setArticulo(articuloDePrueba());
        detalle.setColor(colorDePrueba());
        detalle.setRollosGuia(14);
        detalle.setPesoBrutoKg(new BigDecimal("300"));

        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcion));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderasDePrueba()));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalle));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(10L, 1L, 20L)).thenReturn(Optional.empty());

        service.confirmarRecepcion(1L, List.of(100L), List.of(14), List.of(""));

        ArgumentCaptor<com.textil.inventario.inventario.KardexMovimiento> kardexCap =
                ArgumentCaptor.forClass(com.textil.inventario.inventario.KardexMovimiento.class);
        verify(kardexRepository).save(kardexCap.capture());
        assertThat(kardexCap.getValue().getRecepcionDetalleId()).isEqualTo(100L);
    }

    @Test
    void agregarDetalle_recepcionYaConfirmada_seRechaza() {
        // Una pestaña abierta desde antes de confirmar no debe poder sumar una
        // linea despues: nunca afectaria stock ni kardex, pero si figuraria en
        // el detalle y en los reportes.
        Recepcion confirmada = recepcionDePrueba();
        confirmada.setEstado(Recepcion.EstadoRecepcion.CONFIRMADA);
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(confirmada));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.agregarDetalle(1L, 10L, 20L, null, 5, new BigDecimal("100"))
        ).isInstanceOf(IllegalStateException.class);

        verify(detalleRepository, never()).save(any());
    }

    @Test
    void agregarDetalle_recepcionPendiente_seAcepta() {
        Recepcion pendiente = recepcionDePrueba();
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        when(articuloRepository.findById(10L)).thenReturn(Optional.of(articuloDePrueba()));
        when(colorRepository.findById(20L)).thenReturn(Optional.of(colorDePrueba()));
        when(detalleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() ->
                service.agregarDetalle(1L, 10L, 20L, null, 5, new BigDecimal("100"))
        ).doesNotThrowAnyException();
    }

    // --- El POST de confirmar tiene que traer TODAS las lineas -------------
    // Caso real: una pestaña abierta en Confirmar desde ANTES de que se agregara
    // otra linea desde otra pestaña (agregar esta permitido mientras siga
    // PENDIENTE). El POST viejo solo trae 2 de las 3 lineas. Antes, la tercera no
    // movia stock ni kardex pero la recepcion pasaba igual a CONFIRMADA, y esa
    // linea quedaba sin arreglo posible: visible en el detalle y en los reportes,
    // ausente del inventario.

    private RecepcionDetalle detalleDePrueba(Long id, Recepcion r) {
        RecepcionDetalle d = new RecepcionDetalle();
        d.setId(id);
        d.setRecepcion(r);
        d.setArticulo(articuloDePrueba());
        d.setColor(colorDePrueba());
        d.setRollosGuia(5);
        return d;
    }

    @Test
    void confirmarRecepcion_faltaUnaLineaEnElPost_seRechazaYNoMueveStock() {
        Recepcion pendiente = recepcionDePrueba();
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        // Sin stub de ubicacion a proposito: el guard corta ANTES de buscarla.
        when(detalleRepository.findByRecepcionId(1L)).thenReturn(List.of(
                detalleDePrueba(100L, pendiente),
                detalleDePrueba(101L, pendiente),
                detalleDePrueba(102L, pendiente)));   // <- la 102 no viaja en el POST

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.confirmarRecepcion(1L, List.of(100L, 101L), List.of(5, 5), List.of("", ""))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Faltan líneas por contar");

        verify(stockActualRepository, never()).save(any());
        verify(kardexRepository, never()).save(any());
        assertThat(pendiente.getEstado()).isEqualTo(Recepcion.EstadoRecepcion.PENDIENTE);
    }

    @Test
    void confirmarRecepcion_conTodasLasLineas_pasaElGuard() {
        Recepcion pendiente = recepcionDePrueba();
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        when(ubicacionRepository.findByEsPrincipalTrue()).thenReturn(Optional.of(praderasDePrueba()));
        when(detalleRepository.findByRecepcionId(1L)).thenReturn(List.of(detalleDePrueba(100L, pendiente)));
        when(detalleRepository.findById(100L)).thenReturn(Optional.of(detalleDePrueba(100L, pendiente)));
        when(stockActualRepository.findByArticuloIdAndUbicacionIdAndColorId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatCode(() ->
                service.confirmarRecepcion(1L, List.of(100L), List.of(5), List.of(""))
        ).doesNotThrowAnyException();

        verify(stockActualRepository).save(any());
        verify(kardexRepository).save(any());
    }

    // --- Una factura pertenece a UNA empresa -------------------------------
    // guardarDocumentoFactura ya lo exigia para archivar el PDF; asignarFactura,
    // que es la mitad que ESCRIBE el numero, no lo validaba. Con guias de dos
    // empresas el numero se asignaba igual (y las recepciones salian de "sin
    // factura") y recien despues fallaba el archivado del PDF.

    private Recepcion recepcionDeEmpresa(Long id, Long empresaId) {
        Recepcion r = new Recepcion();
        r.setId(id);
        com.textil.inventario.catalogo.Empresa e = new com.textil.inventario.catalogo.Empresa();
        e.setId(empresaId);
        r.setEmpresa(e);
        return r;
    }

    @Test
    void asignarFactura_guiasDeEmpresasDistintas_seRechaza() {
        when(recepcionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                recepcionDeEmpresa(1L, 10L),
                recepcionDeEmpresa(2L, 99L)));    // <- otra empresa

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.asignarFactura("F001-123", java.time.LocalDate.now(), List.of(1L, 2L))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("misma empresa");

        verify(recepcionRepository, never()).save(any());
    }

    @Test
    void asignarFactura_mismaEmpresa_asignaANormalizado() {
        when(recepcionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                recepcionDeEmpresa(1L, 10L),
                recepcionDeEmpresa(2L, 10L)));
        when(recepcionRepository.findById(1L)).thenReturn(Optional.of(recepcionDeEmpresa(1L, 10L)));
        when(recepcionRepository.findById(2L)).thenReturn(Optional.of(recepcionDeEmpresa(2L, 10L)));

        service.asignarFactura(" f001-123 ", java.time.LocalDate.now(), List.of(1L, 2L));

        ArgumentCaptor<Recepcion> captor = ArgumentCaptor.forClass(Recepcion.class);
        verify(recepcionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(r -> "F001-123".equals(r.getNumeroFactura()));
    }

    // --- Lineas de la guia que NO se registran -----------------------------
    // Una linea sin articulo/color se descartaba en silencio en el navegador
    // (nunca llegaba al backend): la recepcion quedaba con menos lineas que el
    // papel y no habia forma de saber cual falto ni por que. Ahora el que carga
    // tiene que marcarla "No incluir" a proposito, y esa decision queda escrita.

    private void mockearCreacionDeRecepcion() {
        when(empresaRepository.findById(1L))
                .thenReturn(Optional.of(new com.textil.inventario.catalogo.Empresa()));
        when(usuarioActualService.obtenerUsuarioActual())
                .thenReturn(new com.textil.inventario.seguridad.Usuario());
        when(recepcionRepository.saveAndFlush(any(Recepcion.class)))
                .thenAnswer(inv -> {
                    Recepcion r = inv.getArgument(0);
                    r.setId(77L);
                    return r;
                });
    }

    @Test
    void crearRecepcionConLineas_conExcluidas_lasDejaEscritasEnObservacionesYEnElLog() {
        mockearCreacionDeRecepcion();

        var excluida = new CrearRecepcionConLineasRequest.LineaExcluidaRequest(
                "FRANELA 20/1+10/1 · ALGODON · LISO", "ACERO 2", "233689", "571",
                13, new BigDecimal("286.62"), "Tipo de tela 'FRANELA' no existe en el catálogo");

        service.crearRecepcionConLineas(1L, "TG01-00020379", null, java.time.LocalDate.now(),
                "Llegó de noche", null, null, List.of(), List.of(excluida));

        ArgumentCaptor<Recepcion> cap = ArgumentCaptor.forClass(Recepcion.class);
        verify(recepcionRepository).save(cap.capture());
        String observaciones = cap.getValue().getObservaciones();
        assertThat(observaciones)
                .contains("Llegó de noche")                  // no pisa lo que ya habia
                .contains("NO incluidas")
                .contains("FRANELA 20/1+10/1")
                .contains("ACERO 2").contains("233689")
                .contains("571").contains("13 rollos").contains("286.62 kg")
                .contains("FRANELA' no existe");

        verify(auditLogService).registrar(eq("EXCLUIR_LINEAS"), eq("Recepcion"), eq(77L), contains("FRANELA"));
    }

    @Test
    void crearRecepcionConLineas_sinExcluidas_noEnsuciaLasObservaciones() {
        mockearCreacionDeRecepcion();

        service.crearRecepcionConLineas(1L, "TG01-00020380", null, java.time.LocalDate.now(),
                "Todo conforme", null, null, List.of(), null);

        ArgumentCaptor<Recepcion> cap = ArgumentCaptor.forClass(Recepcion.class);
        verify(recepcionRepository).save(cap.capture());
        assertThat(cap.getValue().getObservaciones()).isEqualTo("Todo conforme");
        verify(auditLogService, never()).registrar(eq("EXCLUIR_LINEAS"), any(), any(), any());
    }

    @Test
    void crearRecepcionConLineas_lineaSinArticulo_seRechaza() {
        // Guard del backend: el front ya no manda lineas a medio resolver, pero
        // si llegaran (POST armado a mano), no se aceptan en silencio.
        mockearCreacionDeRecepcion();

        var incompleta = new CrearRecepcionConLineasRequest.LineaRequest(
                null, 5L, "571", 13, new BigDecimal("286.62"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.crearRecepcionConLineas(1L, "TG01-00020381", null, java.time.LocalDate.now(),
                        null, null, null, List.of(incompleta), List.of())
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("no tiene artículo o color");

        verify(detalleRepository, never()).save(any());
    }
}
