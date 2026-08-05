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
        // isBlank y no solo == null: el prompt pide null cuando el dato falta,
        // pero el modelo puede devolver "" y una cadena vacia no identifica nada.
        if (esVacio(p.tipoTela()) || esVacio(p.titulo()) || esVacio(p.colorCodigo())) {
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

    /**
     * Palabras que NO distinguen a una empresa de otra: aparecen en casi todas
     * las razones sociales del rubro. Si se cuentan, "TEXTIL LAURA" y "TEXTIL
     * CLEMENTE" empatan contra cualquier guia que diga "TEXTIL" y el desempate
     * termina siendo el orden de la lista, es decir, azar.
     */
    private static final java.util.Set<String> PALABRAS_GENERICAS = java.util.Set.of(
            "TEXTIL", "TEXTILES", "EMPRESA", "COMPANIA", "COMPAÑIA", "INDUSTRIA", "INDUSTRIAS",
            "CONFECCIONES", "COMERCIAL", "CORPORACION", "GRUPO", "PERU", "LIMA",
            "S.A.C.", "SAC", "S.A.", "E.I.R.L.", "EIRL", "S.R.L.", "SRL", "S.A.A.", "SAA");

    /**
     * Resuelve a que empresa del catalogo local corresponde el DESTINATARIO de
     * la guia, con el RUC como identificador principal.
     *
     * El RUC es unico por empresa (constraint en `empresas.ruc`) y viene impreso
     * en toda guia peruana, asi que el cruce es exacto y no admite ambiguedad.
     * La comparacion por razon social queda solo como respaldo para documentos
     * donde el RUC no se pudo leer, y ante un empate devuelve null a proposito:
     * es preferible que el usuario elija la empresa a sugerirle una al azar
     * (una recepcion imputada a la empresa equivocada desordena su inventario).
     *
     * @param rucDetectado RUC del destinatario leido del documento (puede venir
     *                     con guiones o espacios; se normaliza a solo digitos)
     * @return id de la empresa, o null si no hay match confiable
     */
    public Long matchEmpresa(String rucDetectado, String razonSocialDetectada, List<Empresa> empresas) {
        String ruc = soloDigitos(rucDetectado);
        if (ruc != null) {
            for (Empresa e : empresas) {
                if (ruc.equals(soloDigitos(e.getRuc()))) {
                    return e.getId();
                }
            }
        }
        return matchEmpresaPorNombre(razonSocialDetectada, empresas);
    }

    /**
     * Compatibilidad: cruce solo por razon social, para los flujos que no
     * disponen del RUC (Archivo Historico clasifica por el texto del documento).
     */
    public Long matchEmpresa(String razonSocialDetectada, List<Empresa> empresas) {
        return matchEmpresa(null, razonSocialDetectada, empresas);
    }

    private Long matchEmpresaPorNombre(String razonSocialDetectada, List<Empresa> empresas) {
        if (razonSocialDetectada == null || razonSocialDetectada.isBlank()) return null;

        String textoDetectado = razonSocialDetectada.toUpperCase();
        Empresa mejor = null;
        int mejorScore = 0;
        boolean empatado = false;

        for (Empresa e : empresas) {
            int score = 0;
            for (String palabra : e.getNombre().toUpperCase().split("\\s+")) {
                // Solo cuentan las palabras que realmente identifican a ESTA
                // empresa: las genericas del rubro se descartan.
                if (palabra.length() > 3 && !PALABRAS_GENERICAS.contains(palabra)
                        && textoDetectado.contains(palabra)) {
                    score++;
                }
            }
            if (score > mejorScore) {
                mejorScore = score;
                mejor = e;
                empatado = false;
            } else if (score == mejorScore && score > 0) {
                empatado = true;
            }
        }

        // Empate = el documento no distingue entre dos empresas del catalogo:
        // no se sugiere ninguna y el usuario decide.
        return (mejor != null && !empatado) ? mejor.getId() : null;
    }

    /** Un campo del OCR sin valor util: ausente o en blanco. */
    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    /** Deja solo digitos (los RUC pueden venir como "20549819028" o "20-54981902-8"). */
    private static String soloDigitos(String valor) {
        if (valor == null) return null;
        String limpio = valor.replaceAll("\\D", "");
        return limpio.isEmpty() ? null : limpio;
    }
}
