package com.textil.inventario.catalogo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final EmpresaRepository empresaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ArticuloRepository articuloRepository;

    // Normaliza a mayusculas (recorta espacios) para mantener consistencia
    // en el catalogo y evitar duplicados como "Negro" / "negro" / "NEGRO".
    // Null-safe: si el campo viene null o vacio, no se toca.
    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? valor : valor.trim().toUpperCase();
    }

    // EMPRESAS
    public List<Empresa> listarEmpresas() { return empresaRepository.findByActivoTrue(); }
    public Empresa guardarEmpresa(Empresa e) {
        e.setNombre(normalizar(e.getNombre()));
        return empresaRepository.save(e);
    }
    public Empresa buscarEmpresa(Long id) { return empresaRepository.findById(id).orElseThrow(); }

    // UBICACIONES
    public List<Ubicacion> listarUbicaciones() { return ubicacionRepository.findByActivoTrue(); }
    public Ubicacion guardarUbicacion(Ubicacion u) {
        u.setNombre(normalizar(u.getNombre()));
        return ubicacionRepository.save(u);
    }
    public Ubicacion buscarUbicacion(Long id) { return ubicacionRepository.findById(id).orElseThrow(); }

    // TIPOS DE TELA
    public List<TipoTela> listarTiposTela() { return tipoTelaRepository.findByActivoTrue(); }
    public TipoTela guardarTipoTela(TipoTela t) {
        t.setNombre(normalizar(t.getNombre()));
        return tipoTelaRepository.save(t);
    }

    // TÍTULOS
    public List<Titulo> listarTitulos() { return tituloRepository.findByActivoTrue(); }
    public Titulo guardarTitulo(Titulo t) {
        t.setValor(normalizar(t.getValor()));
        return tituloRepository.save(t);
    }

    // COLORES
    public List<Color> listarColores() { return colorRepository.findByActivoTrue(); }
    public Color guardarColor(Color c) {
        c.setNombreOficial(normalizar(c.getNombreOficial()));
        c.setCodigoFastDye(normalizar(c.getCodigoFastDye()));
        c.setApodo(normalizar(c.getApodo()));
        return colorRepository.save(c);
    }
    public Color buscarColor(Long id) { return colorRepository.findById(id).orElseThrow(); }

    // ARTÍCULOS
    public List<Articulo> listarArticulos() { return articuloRepository.findByActivoTrue(); }
    public Articulo guardarArticulo(Articulo a) { return articuloRepository.save(a); }
    public Articulo buscarArticulo(Long id) { return articuloRepository.findById(id).orElseThrow(); }

    // BÚSQUEDAS PARA MATCHING / CREACIÓN RÁPIDA
    public Optional<TipoTela> buscarTipoTelaPorNombre(String nombre) { return tipoTelaRepository.findByNombreIgnoreCase(nombre.trim()); }
    public Optional<Titulo> buscarTituloPorValor(String valor) { return tituloRepository.findByValorIgnoreCase(valor.trim()); }
    public Optional<Color> buscarColorPorCodigoFastDye(String codigo) { return resolverColorPorCodigo(codigo, null); }

    /**
     * FAST DYE reasigna codigos con el tiempo, asi que puede haber mas de un
     * color activo con el mismo codigo_fast_dye (uno viejo, uno nuevo).
     * Si se da un nombre (ej. leido por la IA de la guia/factura), se
     * prioriza el color cuyo nombre coincide exactamente. Si no hay
     * coincidencia clara, se usa el color creado mas recientemente,
     * asumiendo que la reasignacion mas nueva es la vigente.
     */
    public Optional<Color> resolverColorPorCodigo(String codigo, String nombrePreferido) {
        List<Color> candidatos = colorRepository.findByCodigoFastDye(codigo.trim());
        if (candidatos.isEmpty()) return Optional.empty();
        if (candidatos.size() == 1) return Optional.of(candidatos.get(0));

        if (nombrePreferido != null && !nombrePreferido.isBlank()) {
            Optional<Color> porNombre = candidatos.stream()
                    .filter(c -> c.getNombreOficial().equalsIgnoreCase(nombrePreferido.trim()))
                    .findFirst();
            if (porNombre.isPresent()) return porNombre;
        }
        return candidatos.stream().max(java.util.Comparator.comparing(Color::getId));
    }
    public Optional<Articulo> buscarArticuloPorCombinacion(Long tipoTelaId, Long tituloId, Long colorId) {
        return articuloRepository.findByTipoTelaIdAndTituloIdAndColorId(tipoTelaId, tituloId, colorId);
    }

    // GENERACIÓN DE CÓDIGO INTERNO (ARQ-02: evita colisiones entre variantes de un mismo tipo de tela,
    // ej. "RIB 2x1" vs "RIB 1x1" vs "RIB Acanalado" vs "RIB Listado", que antes truncaban todas a "RIB").
    // Usa iniciales por palabra del tipo de tela en vez de las primeras 3 letras del nombre concatenado,
    // y valida contra la base de datos para garantizar unicidad real (nunca depende solo de la suerte).
    public String generarCodigoInterno(TipoTela tipoTela, Titulo titulo, Color color) {
        String base = abreviarTipoTela(tipoTela.getNombre())
                + "-" + titulo.getValor().replace("/", "")
                + "-" + color.getNombreOficial().replace(" ", "")
                        .substring(0, Math.min(4, color.getNombreOficial().replace(" ", "").length())).toUpperCase();

        String codigo = base;
        int sufijo = 2;
        while (articuloRepository.findByCodigoInterno(codigo).isPresent()) {
            codigo = base + "-" + sufijo;
            sufijo++;
        }
        return codigo;
    }

    private String abreviarTipoTela(String nombre) {
        String[] palabras = nombre.trim().split("\\s+");
        if (palabras.length == 1) {
            return palabras[0].replace(" ", "").substring(0, Math.min(3, palabras[0].length())).toUpperCase();
        }
        String primera = palabras[0].toUpperCase();
        String segunda = palabras[1].replaceAll("[^a-zA-Z0-9]", "");
        segunda = segunda.substring(0, Math.min(3, segunda.length())).toUpperCase();
        return primera + segunda;
    }

    // ELIMINAR (borrado real). Si el registro esta en uso por otras tablas (FK),
    // la base de datos rechaza el borrado y el controlador debe capturar la excepcion.
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERADMIN')")
    public void eliminarColor(Long id) { colorRepository.deleteById(id); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERADMIN')")
    public void eliminarArticulo(Long id) { articuloRepository.deleteById(id); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPERADMIN')")
    public void eliminarUbicacion(Long id) { ubicacionRepository.deleteById(id); }
}
