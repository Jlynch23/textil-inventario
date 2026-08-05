package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.recepciones.ProductoExtraido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * #12: enriquecimiento del catálogo a partir de un producto leído por la IA.
 * Se extrajo de ArchivoHistoricoService para aislar la lógica de
 * resolver-o-crear (TipoTela/Título/Composición/Color/Artículo) del resto del
 * flujo de importación.
 * <p>
 * NUNCA toca stock_actual ni kardex_movimientos: solo consulta o inserta piezas
 * del catálogo. El movimiento de stock, si corresponde, lo hace después
 * ArchivoHistoricoService vía RecepcionService (único camino permitido).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentoHistoricoEnriquecedor {

    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ComposicionRepository composicionRepository;
    private final ArticuloRepository articuloRepository;
    private final CatalogoService catalogoService;

    /**
     * Resultado del enriquecimiento de una línea: el artículo y color resueltos
     * (o null si no se pudo), más cuántas piezas se crearon (para el contador de
     * la importación).
     */
    public record Resultado(Articulo articulo, Color color, int colorCreado, int articuloCreado) {
        static Resultado vacio() {
            return new Resultado(null, null, 0, 0);
        }
    }

    /**
     * Resuelve (o crea si hace falta) el Articulo correspondiente al producto
     * leido, para poder usarlo tanto en el enriquecimiento de catalogo como
     * en la creacion de la Recepcion automatica.
     */
    public Resultado enriquecer(ProductoExtraido p) {
        // isBlank y no solo == null: una cadena vacia del OCR no identifica nada
        // y antes llegaba a resolverColorPorCodigo("") devolviendo un color al azar.
        if (esVacio(p.tipoTela()) || esVacio(p.titulo()) || esVacio(p.colorCodigo())
                || esVacio(p.composicion())) {
            return Resultado.vacio();
        }

        Optional<TipoTela> tipoTela = tipoTelaRepository.findByNombreIgnoreCase(p.tipoTela().trim());
        if (tipoTela.isEmpty()) return Resultado.vacio();

        Optional<Titulo> titulo = tituloRepository.findByValorIgnoreCase(p.titulo().trim());
        if (titulo.isEmpty()) return Resultado.vacio();

        // La composicion (ALGODON, MELANGE N%) es obligatoria para poder
        // identificar/crear el Articulo, ya que este ya no incluye Color
        // (ver V26). Si el PDF no trajo una composicion reconocible, no se
        // auto-crea el articulo -- mejor no enriquecer esta linea que
        // adivinar y dejar datos mal etiquetados en el catalogo.
        if (p.composicion() == null || p.composicion().isBlank()) {
            return Resultado.vacio();
        }
        Optional<Composicion> composicion = composicionRepository.findByNombreIgnoreCase(p.composicion().trim());
        if (composicion.isEmpty()) return Resultado.vacio();

        int colorCreado = 0;
        Optional<Color> colorOpt = catalogoService.resolverColorPorCodigo(p.colorCodigo().trim(), p.colorNombre());
        Color color;
        if (colorOpt.isPresent()) {
            color = colorOpt.get();
        } else {
            String nombreOficial = (p.colorNombre() != null && !p.colorNombre().isBlank())
                    ? p.colorNombre().trim() : "Color " + p.colorCodigo().trim();

            // Puede que el MISMO color ya exista con otro codigo_fast_dye
            // (FAST DYE reasigna codigos con el tiempo). Si el nombre ya existe, se reutiliza
            // en vez de fallar por la restriccion de nombre unico.
            Optional<Color> porNombre = colorRepository.findByNombreOficialIgnoreCase(nombreOficial);
            if (porNombre.isPresent()) {
                color = porNombre.get();
            } else {
                try {
                    Color nuevo = new Color();
                    nuevo.setNombreOficial(nombreOficial);
                    nuevo.setCodigoFastDye(p.colorCodigo().trim());
                    nuevo.setActivo(true);
                    color = colorRepository.save(nuevo);
                    colorCreado = 1;
                } catch (Exception e) {
                    // choque de datos que no pudimos anticipar: no se puede crear con seguridad.
                    // R-S1 (auditoria): se LOGUEA antes de degradar; sin esto, las lineas
                    // perdidas de un import quedaban sin ningun rastro de por que.
                    log.warn("No se pudo crear el color '{}' (cod '{}') durante el enriquecimiento: {}",
                            p.colorNombre(), p.colorCodigo(), e.getMessage());
                    return Resultado.vacio();
                }
            }
        }

        int articuloCreado = 0;
        Articulo articulo;
        // Importacion legacy: los nombres de archivo historicos no traen acabado
        // de forma confiable, se asume LISO (defecto).
        Acabado acabadoLiso = catalogoService.buscarAcabadoPorNombre("LISO").orElseThrow();
        Optional<Articulo> articuloOpt = articuloRepository.findByTipoTelaIdAndTituloIdAndComposicionIdAndAcabadoId(
                tipoTela.get().getId(), titulo.get().getId(), composicion.get().getId(), acabadoLiso.getId());
        if (articuloOpt.isPresent()) {
            articulo = articuloOpt.get();
        } else {
            try {
                Articulo nuevo = new Articulo();
                nuevo.setTipoTela(tipoTela.get());
                nuevo.setTitulo(titulo.get());
                nuevo.setComposicion(composicion.get());
                nuevo.setAcabado(acabadoLiso);
                nuevo.setCodigoInterno(catalogoService.generarCodigoInterno(tipoTela.get(), titulo.get(), composicion.get(), acabadoLiso));
                nuevo.setActivo(true);
                articulo = articuloRepository.save(nuevo);
                articuloCreado = 1;
            } catch (Exception e) {
                // R-S1 (auditoria): loguear antes de degradar, para no perder en
                // silencio la linea que no se pudo enriquecer.
                log.warn("No se pudo crear el articulo ({}/{}/{}) durante el enriquecimiento: {}",
                        tipoTela.get().getNombre(), titulo.get().getValor(), composicion.get().getNombre(), e.getMessage());
                return Resultado.vacio();
            }
        }

        return new Resultado(articulo, color, colorCreado, articuloCreado);
    }

    /** Un campo del OCR sin valor util: ausente o en blanco. */
    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
