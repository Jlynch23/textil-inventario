package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticuloMatchingService {

    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ComposicionRepository composicionRepository;
    private final ArticuloRepository articuloRepository;
    private final com.textil.inventario.catalogo.CatalogoService catalogoService;

    public LineaSugerida matchLinea(ProductoExtraido p) {
        if (p.tipoTela() == null || p.titulo() == null || p.colorCodigo() == null) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), null, p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Faltan datos en el PDF para identificar el artículo");
        }

        Optional<TipoTela> tipoTela = tipoTelaRepository.findByNombreIgnoreCase(p.tipoTela().trim());
        if (tipoTela.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), null, p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Tipo de tela '" + p.tipoTela() + "' no existe en el catálogo");
        }

        Optional<Titulo> titulo = tituloRepository.findByValorIgnoreCase(p.titulo().trim());
        if (titulo.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), null, p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Título '" + p.titulo() + "' no existe en el catálogo");
        }

        // La composicion (ALGODON, MELANGE N%) es obligatoria para identificar el Articulo,
        // ya que el Articulo ya no incluye Color -- si el PDF no trajo una composicion
        // reconocible, no se puede armar el match sin adivinar.
        if (p.composicion() == null || p.composicion().isBlank()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), null, p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "No se pudo identificar la composición (ALGODON, MELANGE, etc.) en el PDF");
        }
        Optional<Composicion> composicion = composicionRepository.findByNombreIgnoreCase(p.composicion().trim());
        if (composicion.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), null, p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Composición '" + p.composicion() + "' no existe en el catálogo");
        }

        Optional<Color> color = catalogoService.resolverColorPorCodigo(p.colorCodigo().trim(), p.colorNombre());
        if (color.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), null, p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Color código '" + p.colorCodigo() + "' (" + p.colorNombre() + ") no existe en el catálogo");
        }

        // Acabado extraido del PDF por la IA; si la guia no menciona ninguno,
        // aplica el defecto LISO (asi funcionan las guias reales de FAST DYE).
        String acabadoNombre = (p.acabado() == null || p.acabado().isBlank()) ? "LISO" : p.acabado().trim();
        Optional<Acabado> acabado = catalogoService.buscarAcabadoPorNombre(acabadoNombre);
        if (acabado.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), color.get().getId(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Acabado '" + acabadoNombre + "' no existe en el catálogo");
        }
        Optional<Articulo> articulo = articuloRepository.findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(
                tipoTela.get().getId(), titulo.get().getId(), composicion.get().getId(), acabado.get().getId());

        if (articulo.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), color.get().getId(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Esa combinación de tejido/título/composición/acabado no está registrada como artículo");
        }

        return new LineaSugerida(articulo.get().getId(), p.tipoTela(), p.titulo(), p.composicion(), p.acabado(), color.get().getId(), p.colorCodigo(), p.colorNombre(),
                p.programaTenido(), p.rollos(), p.pesoBrutoKg(), true, null);
    }

    public Long matchEmpresa(String razonSocialDetectada, List<Empresa> empresas) {
        if (razonSocialDetectada == null || razonSocialDetectada.isBlank()) return null;

        String textoDetectado = razonSocialDetectada.toUpperCase();
        Empresa mejor = null;
        int mejorScore = 0;

        for (Empresa e : empresas) {
            String[] palabras = e.getNombre().toUpperCase().split("\\s+");
            int score = 0;
            for (String palabra : palabras) {
                if (palabra.length() > 3 && textoDetectado.contains(palabra)) {
                    score++;
                }
            }
            if (score > mejorScore) {
                mejorScore = score;
                mejor = e;
            }
        }

        return mejor != null ? mejor.getId() : null;
    }
}
