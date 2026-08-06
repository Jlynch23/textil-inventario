package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.recepciones.ArticuloMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Extraido de ArchivoHistoricoService (auditoria 17-jul-2026, God Class de
 * 584 lineas). Agrupa toda la logica de CLASIFICACION: que tipo de
 * documento es, que empresa corresponde, y parseo de fechas. Son funciones
 * practicamente puras (no dependen de repositorios ni de estado propio,
 * reciben todo lo que necesitan como parametro), por eso quedan aparte del
 * manejo de archivos en disco y de la orquestacion general.
 */
@Component
@RequiredArgsConstructor
public class DocumentoHistoricoClasificador {

    private final ArticuloMatchingService articuloMatchingService;

    // FAST DYE no incluye la palabra "FACTURA" o "GUIA" en el nombre del
    // archivo: usa el numero de serie real. Guias: TG01-00022558. Facturas:
    // F003-00037985 (formato estandar de series de facturacion en Peru,
    // letra F + 3 digitos + guion + correlativo).
    private static final Pattern PATRON_GUIA =
            Pattern.compile("TG\\d+-\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATRON_FACTURA =
            Pattern.compile("F\\d{3}-\\d+", Pattern.CASE_INSENSITIVE);

    public String evitarColision(String nombreOriginal, Set<String> nombresUsados) {
        String nombre = UUID.randomUUID().toString().substring(0, 8) + "_" + nombreOriginal;
        nombresUsados.add(nombre);
        return nombre;
    }

    public DocumentoHistorico.TipoDocumentoHistorico detectarTipo(String rutaEntrada) {
        String ruta = rutaEntrada.toUpperCase();
        if (ruta.contains("FACTURA")) return DocumentoHistorico.TipoDocumentoHistorico.FACTURA;
        if (ruta.contains("GUIA")) return DocumentoHistorico.TipoDocumentoHistorico.GUIA;
        if (PATRON_FACTURA.matcher(ruta).find()) return DocumentoHistorico.TipoDocumentoHistorico.FACTURA;
        if (PATRON_GUIA.matcher(ruta).find()) return DocumentoHistorico.TipoDocumentoHistorico.GUIA;
        return DocumentoHistorico.TipoDocumentoHistorico.OTRO;
    }

    public Empresa detectarEmpresaPorRuta(String rutaEntrada, List<Empresa> empresas) {
        return detectarEmpresa(rutaEntrada, empresas);
    }

    /**
     * Detecta la empresa a partir de texto REAL leido por la IA (razon social
     * de la guia/factura), en vez de la ruta del ZIP. Permite subir un ZIP
     * sin organizar por carpetas de empresa: la IA decide, no la ruta.
     */
    public Empresa detectarEmpresaPorTexto(String razonSocialDetectada, List<Empresa> empresas) {
        return detectarEmpresa(razonSocialDetectada, empresas);
    }

    /**
     * Detecta la empresa buscando su nombre dentro de un texto (la ruta del ZIP
     * o la razon social leida por la IA). Usa EXACTAMENTE el mismo matcher que
     * la recepcion individual (ArticuloMatchingService.matchEmpresa): cuenta
     * cuantas palabras (>3 letras) del nombre de cada empresa aparecen en el
     * texto y se queda con la de mayor coincidencia. Asi "T. CLEMENTE" o
     * "...CLEMENTE SAC" resuelven a "TEXTIL CLEMENTE" igual que en el flujo
     * individual, en vez de exigir el slug de carpeta exacto.
     */
    public Empresa detectarEmpresa(String texto, List<Empresa> empresas) {
        if (texto == null || texto.isBlank() || empresas == null) return null;
        Long id = articuloMatchingService.matchEmpresa(texto, empresas);
        if (id == null) return null;
        return empresas.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
    }

    /**
     * Solo aceptaba ISO, asi que una fecha copiada del papel ("05/08/2026")
     * caia en el catch y el documento historico quedaba SIN fecha, en silencio.
     * Se delega en FechaDocumento, que entiende los dos formatos.
     */
    public LocalDate parseFecha(String fechaTexto) {
        return com.textil.inventario.common.FechaDocumento.parse(fechaTexto);
    }
}
