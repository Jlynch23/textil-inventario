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
    private final ArticuloRepository articuloRepository;

    public LineaSugerida matchLinea(ProductoExtraido p) {
        if (p.tipoTela() == null || p.titulo() == null || p.colorCodigo() == null) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Faltan datos en el PDF para identificar el artículo");
        }

        Optional<TipoTela> tipoTela = tipoTelaRepository.findByNombreIgnoreCase(p.tipoTela().trim());
        if (tipoTela.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Tipo de tela '" + p.tipoTela() + "' no existe en el catálogo");
        }

        Optional<Titulo> titulo = tituloRepository.findByValorIgnoreCase(p.titulo().trim());
        if (titulo.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Título '" + p.titulo() + "' no existe en el catálogo");
        }

        Optional<Color> color = colorRepository.findByCodigoFastDye(p.colorCodigo().trim());
        if (color.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Color código '" + p.colorCodigo() + "' (" + p.colorNombre() + ") no existe en el catálogo");
        }

        Optional<Articulo> articulo = articuloRepository.findByTipoTelaIdAndTituloIdAndColorId(
                tipoTela.get().getId(), titulo.get().getId(), color.get().getId());

        if (articulo.isEmpty()) {
            return new LineaSugerida(null, p.tipoTela(), p.titulo(), p.colorCodigo(), p.colorNombre(),
                    p.programaTenido(), p.rollos(), p.pesoBrutoKg(), false,
                    "Esa combinación de tela/título/color no está registrada como artículo");
        }

        return new LineaSugerida(articulo.get().getId(), p.tipoTela(), p.titulo(), p.colorCodigo(), p.colorNombre(),
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
