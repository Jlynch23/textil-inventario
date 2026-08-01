package com.textil.inventario.catalogo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock private EmpresaRepository empresaRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private TipoTelaRepository tipoTelaRepository;
    @Mock private TituloRepository tituloRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private ComposicionRepository composicionRepository;
    @Mock private AcabadoRepository acabadoRepository;
    @Mock private ArticuloRepository articuloRepository;

    @InjectMocks
    private CatalogoService service;

    private Color colorConId(long id, String nombre) {
        Color c = new Color();
        c.setId(id);
        c.setNombreOficial(nombre);
        return c;
    }

    // --- resolverColorPorCodigo: FAST DYE puede reasignar un mismo codigo a
    // mas de un color activo a lo largo del tiempo (ver comentario en el
    // servicio) -- esta es la logica de desempate mas delicada del catalogo.

    @Test
    void resolverColorPorCodigo_sinCandidatos_devuelveVacio() {
        when(colorRepository.findByCodigoFastDye("631085")).thenReturn(List.of());

        Optional<Color> resultado = service.resolverColorPorCodigo("631085", "COCOA LOLA");

        assertThat(resultado).isEmpty();
    }

    @Test
    void resolverColorPorCodigo_unSoloCandidato_loDevuelveAunqueElNombreNoCoincida() {
        Color unico = colorConId(1L, "COCOA LOLA");
        when(colorRepository.findByCodigoFastDye("631085")).thenReturn(List.of(unico));

        Optional<Color> resultado = service.resolverColorPorCodigo("631085", "NOMBRE DISTINTO");

        assertThat(resultado).contains(unico);
    }

    @Test
    void resolverColorPorCodigo_variosCandidatos_priorizaElQueCoincideEnNombre() {
        Color viejo = colorConId(1L, "COCOA VIEJO");
        Color nuevo = colorConId(2L, "COCOA LOLA");
        when(colorRepository.findByCodigoFastDye("631085")).thenReturn(List.of(viejo, nuevo));

        Optional<Color> resultado = service.resolverColorPorCodigo("631085", "cocoa lola");

        assertThat(resultado).contains(nuevo);
    }

    @Test
    void resolverColorPorCodigo_variosCandidatos_sinNombrePreferido_devuelveElDeIdMasAlto() {
        Color viejo = colorConId(1L, "COCOA VIEJO");
        Color masReciente = colorConId(5L, "COCOA NUEVO");
        when(colorRepository.findByCodigoFastDye("631085")).thenReturn(List.of(viejo, masReciente));

        Optional<Color> resultado = service.resolverColorPorCodigo("631085", null);

        assertThat(resultado).contains(masReciente);
    }

    @Test
    void resolverColorPorCodigo_variosCandidatos_nombrePreferidoNoCoincideConNinguno_caeAlMasReciente() {
        Color viejo = colorConId(1L, "COCOA VIEJO");
        Color masReciente = colorConId(5L, "COCOA NUEVO");
        when(colorRepository.findByCodigoFastDye("631085")).thenReturn(List.of(viejo, masReciente));

        Optional<Color> resultado = service.resolverColorPorCodigo("631085", "NOMBRE QUE NO EXISTE");

        assertThat(resultado).contains(masReciente);
    }

    // --- eliminarUbicacion: borra cualquier ubicacion (incluida la principal).
    // Ya NO se bloquea el borrado de la principal: una instancia nueva arranca
    // sin ninguna, asi que bloquear no garantizaba tener una y solo dejaba
    // trabado al usuario. El controlador avisa si se borro la ultima principal,
    // y la integridad real (stock, transferencias) la cubre la FK. ---

    @Test
    void eliminarUbicacion_principal_seBorraSinBloqueo() {
        service.eliminarUbicacion(1L);

        verify(ubicacionRepository).deleteById(1L);
    }

    @Test
    void eliminarUbicacion_noPrincipal_seBorra() {
        service.eliminarUbicacion(2L);

        verify(ubicacionRepository).deleteById(2L);
    }

    // --- eliminarTipoTela: borrado real; la FK bloquea si esta en uso ---

    @Test
    void eliminarTipoTela_delegaEnRepositorio() {
        service.eliminarTipoTela(7L);

        verify(tipoTelaRepository).deleteById(7L);
    }

    // --- generarCodigoInterno: evita colisiones entre variantes del mismo tipo de tela ---

    private TipoTela tipoTela(String nombre) {
        TipoTela t = new TipoTela();
        t.setNombre(nombre);
        return t;
    }

    private Titulo titulo(String valor) {
        Titulo t = new Titulo();
        t.setValor(valor);
        return t;
    }

    private Composicion composicion(String nombre) {
        Composicion c = new Composicion();
        c.setNombre(nombre);
        return c;
    }

    private Acabado acabado(String nombre) {
        Acabado a = new Acabado();
        a.setNombre(nombre);
        return a;
    }

    @Test
    void generarCodigoInterno_acabadoListo_noAgregaSufijoDeAcabado() {
        when(articuloRepository.findByCodigoInterno(anyString())).thenReturn(Optional.empty());

        String codigo = service.generarCodigoInterno(
                tipoTela("RIB 2X1"), titulo("30/1"), composicion("ALGODON"), acabado("LISO"));

        assertThat(codigo).isEqualTo("RIB2X1-301-ALGO");
    }

    @Test
    void generarCodigoInterno_acabadoDistintoDeListo_agregaSufijo() {
        when(articuloRepository.findByCodigoInterno(anyString())).thenReturn(Optional.empty());

        String codigo = service.generarCodigoInterno(
                tipoTela("RIB 2X1"), titulo("30/1"), composicion("ALGODON"), acabado("ACANALADO"));

        assertThat(codigo).isEqualTo("RIB2X1-301-ALGO-ACAN");
    }

    @Test
    void generarCodigoInterno_yaExiste_agregaSufijoNumericoHastaEncontrarUnoLibre() {
        when(articuloRepository.findByCodigoInterno("RIB2X1-301-ALGO")).thenReturn(Optional.of(new Articulo()));
        when(articuloRepository.findByCodigoInterno("RIB2X1-301-ALGO-2")).thenReturn(Optional.of(new Articulo()));
        when(articuloRepository.findByCodigoInterno("RIB2X1-301-ALGO-3")).thenReturn(Optional.empty());

        String codigo = service.generarCodigoInterno(
                tipoTela("RIB 2X1"), titulo("30/1"), composicion("ALGODON"), acabado("LISO"));

        assertThat(codigo).isEqualTo("RIB2X1-301-ALGO-3");
    }

    // --- normalizacion (mayusculas/trim) al guardar ---

    @Test
    void guardarEmpresa_normalizaNombreYRucAMayusculasSinEspacios() {
        Empresa e = new Empresa();
        e.setNombre("  textil laura  ");
        e.setRuc(" 20123456789 ");
        when(empresaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Empresa guardado = service.guardarEmpresa(e);

        assertThat(guardado.getNombre()).isEqualTo("TEXTIL LAURA");
        assertThat(guardado.getRuc()).isEqualTo("20123456789");
    }

    // #7 (DTO): editar una empresa INACTIVA por el form NO la reactiva. El DTO no
    // tiene 'activo'; el service hace fetch-or-create y preserva el estado.
    @Test
    void guardarEmpresaForm_edicionDeInactiva_preservaActivoFalse() {
        Empresa inactiva = new Empresa();
        inactiva.setId(7L);
        inactiva.setNombre("VIEJO");
        inactiva.setRuc("20123456789");
        inactiva.setActivo(false);
        when(empresaRepository.findById(7L)).thenReturn(Optional.of(inactiva));
        when(empresaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmpresaForm form = new EmpresaForm();
        form.setId(7L);
        form.setNombre("nuevo nombre");
        form.setRuc("20123456789");

        Empresa guardado = service.guardarEmpresa(form);

        assertThat(guardado.getActivo()).as("editar no debe reactivar").isFalse();
        assertThat(guardado.getNombre()).isEqualTo("NUEVO NOMBRE");
    }
}
