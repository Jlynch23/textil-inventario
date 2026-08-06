package com.textil.inventario.programas;

import com.textil.inventario.auditoria.AuditLogService;
import com.textil.inventario.catalogo.Acabado;
import com.textil.inventario.catalogo.AcabadoRepository;
import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.catalogo.CatalogoService;
import com.textil.inventario.catalogo.Color;
import com.textil.inventario.catalogo.ColorRepository;
import com.textil.inventario.catalogo.Composicion;
import com.textil.inventario.catalogo.ComposicionRepository;
import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.catalogo.EmpresaRepository;
import com.textil.inventario.catalogo.TipoTela;
import com.textil.inventario.catalogo.TipoTelaRepository;
import com.textil.inventario.catalogo.Titulo;
import com.textil.inventario.catalogo.TituloRepository;
import com.textil.inventario.recepciones.RecepcionDetalleRepository;
import com.textil.inventario.recepciones.RecepcionDocumentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El articulo y el color de una linea YA existente del programa se pueden
 * cambiar (antes habia que quitar la linea y cargarla de nuevo, recalculando el
 * total de rollos a mano por un simple error de tipeo en un desplegable), pero
 * SOLO mientras esa linea no haya recibido tela: si ya tiene recepciones
 * vinculadas, cambiar lo que pide las dejaria acreditadas a un articulo que
 * nunca entro por esa puerta.
 */
@ExtendWith(MockitoExtension.class)
class ProgramaServiceTest {

    @Mock private ProgramaRepository programaRepository;
    @Mock private ProgramaDetalleRepository programaDetalleRepository;
    @Mock private RecepcionDetalleRepository recepcionDetalleRepository;
    @Mock private RecepcionDocumentoRepository recepcionDocumentoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private TipoTelaRepository tipoTelaRepository;
    @Mock private TituloRepository tituloRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private ComposicionRepository composicionRepository;
    @Mock private AcabadoRepository acabadoRepository;
    @Mock private CatalogoService catalogoService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ProgramaService service;

    private static final Long PROGRAMA_ID = 1L;
    private static final Long DETALLE_ID = 10L;

    private Articulo articulo(Long id, String tipo) {
        TipoTela tt = new TipoTela();      tt.setId(100L); tt.setNombre(tipo);
        Titulo t = new Titulo();           t.setId(200L);  t.setValor("24/1");
        Composicion c = new Composicion(); c.setId(300L);  c.setNombre("ALGODON");
        Acabado a = new Acabado();         a.setId(400L);  a.setNombre("LISO");
        Articulo art = new Articulo();
        art.setId(id);
        art.setTipoTela(tt);
        art.setTitulo(t);
        art.setComposicion(c);
        art.setAcabado(a);
        return art;
    }

    private Color color(Long id, String nombre) {
        Color c = new Color();
        c.setId(id);
        c.setNombreOficial(nombre);
        return c;
    }

    /** Programa con UNA linea (RIB 2X1 / PPT) y el recibido que se le indique. */
    private Programa programaConUnaLinea(int recibida) {
        Programa p = new Programa();
        p.setId(PROGRAMA_ID);
        p.setNumero("626");
        p.setTotalRollos(27);

        ProgramaDetalle pd = new ProgramaDetalle();
        pd.setId(DETALLE_ID);
        pd.setPrograma(p);
        pd.setArticulo(articulo(500L, "RIB 2X1"));
        pd.setColor(color(600L, "PPT"));
        pd.setCantidadSolicitada(27);
        pd.setCantidadRecibida(recibida);
        p.setDetalles(new java.util.ArrayList<>(List.of(pd)));
        return p;
    }

    private void mockearCabecera(Programa p) {
        when(programaRepository.findById(PROGRAMA_ID)).thenReturn(Optional.of(p));
        when(empresaRepository.findById(2L)).thenReturn(Optional.of(new Empresa()));
        when(programaRepository.findByNumero("626")).thenReturn(Optional.of(p));
        when(programaDetalleRepository.findById(DETALLE_ID))
                .thenReturn(Optional.of(p.getDetalles().get(0)));
    }

    /** Deja listo el resolver para que devuelva OTRO articulo (id 501). */
    private void mockearOtroArticulo() {
        Acabado acabado = new Acabado(); acabado.setId(400L); acabado.setNombre("LISO");
        when(acabadoRepository.findById(400L)).thenReturn(Optional.of(acabado));
        when(catalogoService.buscarArticuloPorCombinacion(101L, 200L, 300L, 400L))
                .thenReturn(Optional.of(articulo(501L, "FRANELA")));
    }

    private void actualizarCambiandoTipoTela(Programa p) {
        service.actualizarPrograma(PROGRAMA_ID, "626", 2L, LocalDate.now(), null, 27,
                List.of(DETALLE_ID), List.of(27),
                List.of(101L), List.of(200L), List.of(300L), List.of(400L), List.of(600L),
                List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void lineaSinRecibir_cambiaDeArticuloYQuedaAuditado() {
        Programa p = programaConUnaLinea(0);
        mockearCabecera(p);
        mockearOtroArticulo();
        when(colorRepository.findById(600L)).thenReturn(Optional.of(color(600L, "PPT")));

        actualizarCambiandoTipoTela(p);

        ProgramaDetalle pd = p.getDetalles().get(0);
        assertThat(pd.getArticulo().getId()).isEqualTo(501L);
        verify(programaDetalleRepository).save(pd);
        verify(auditLogService).registrar(eq("EDITAR_LINEA_PROGRAMA"), eq("ProgramaDetalle"),
                eq(DETALLE_ID), contains("FRANELA"));
    }

    @Test
    void lineaConTelaRecibida_noPuedeCambiarDeArticulo() {
        Programa p = programaConUnaLinea(13);        // <- ya entraron 13 rollos
        mockearCabecera(p);
        mockearOtroArticulo();
        when(colorRepository.findById(600L)).thenReturn(Optional.of(color(600L, "PPT")));

        assertThatThrownBy(() -> actualizarCambiandoTipoTela(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya tiene tela recibida");

        assertThat(p.getDetalles().get(0).getArticulo().getId()).isEqualTo(500L);   // intacta
        verify(auditLogService, never()).registrar(eq("EDITAR_LINEA_PROGRAMA"), any(), any(), any());
    }

    @Test
    void lineaEnCeroPeroConRecepcionVinculada_tampocoPuedeCambiar() {
        // El contador puede quedar en 0 y aun asi haber lineas de recepcion
        // apuntando a esta linea; mirar solo cantidadRecibida no alcanza.
        Programa p = programaConUnaLinea(0);
        mockearCabecera(p);
        mockearOtroArticulo();
        when(colorRepository.findById(600L)).thenReturn(Optional.of(color(600L, "PPT")));
        when(recepcionDetalleRepository.existsByProgramaDetalleId(DETALLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> actualizarCambiandoTipoTela(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya tiene tela recibida");

        assertThat(p.getDetalles().get(0).getArticulo().getId()).isEqualTo(500L);
    }

    @Test
    void mismoArticuloYColor_noTocaNadaNiConsultaRecepciones() {
        // Guardar sin cambiar los desplegables (el caso normal: solo se corrige
        // la cantidad) no debe contar como un cambio de articulo.
        Programa p = programaConUnaLinea(13);
        mockearCabecera(p);
        Acabado acabado = new Acabado(); acabado.setId(400L); acabado.setNombre("LISO");
        when(acabadoRepository.findById(400L)).thenReturn(Optional.of(acabado));
        when(catalogoService.buscarArticuloPorCombinacion(100L, 200L, 300L, 400L))
                .thenReturn(Optional.of(p.getDetalles().get(0).getArticulo()));
        when(colorRepository.findById(600L)).thenReturn(Optional.of(p.getDetalles().get(0).getColor()));

        service.actualizarPrograma(PROGRAMA_ID, "626", 2L, LocalDate.now(), null, 27,
                List.of(DETALLE_ID), List.of(27),
                List.of(100L), List.of(200L), List.of(300L), List.of(400L), List.of(600L),
                List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        verify(recepcionDetalleRepository, never()).existsByProgramaDetalleId(any());
        verify(auditLogService, never()).registrar(eq("EDITAR_LINEA_PROGRAMA"), any(), any(), any());
    }

    @Test
    void sinLosDesplegablesEnElPost_soloSeActualizaLaCantidad() {
        // Compatibilidad: un POST que no trae artículo/color de las líneas
        // existentes (o una línea de solo lectura) deja la línea como estaba.
        Programa p = programaConUnaLinea(13);
        mockearCabecera(p);

        service.actualizarPrograma(PROGRAMA_ID, "626", 2L, LocalDate.now(), null, 30,
                List.of(DETALLE_ID), List.of(30),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        ProgramaDetalle pd = p.getDetalles().get(0);
        assertThat(pd.getCantidadSolicitada()).isEqualTo(30);
        assertThat(pd.getArticulo().getId()).isEqualTo(500L);
        verifyNoInteractions(catalogoService);
    }
}
