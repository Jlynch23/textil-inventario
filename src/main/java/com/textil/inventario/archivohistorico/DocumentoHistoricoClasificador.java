package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class DocumentoHistoricoClasificador {

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

    // Palabras que aparecen en casi cualquier razon social textil y NO sirven
    // para distinguir una empresa de otra (las dos son "TEXTIL ..."). Se
    // ignoran al buscar la palabra distintiva (LAURA, CLEMENTE, ...).
    private static final Set<String> PALABRAS_GENERICAS = Set.of(
            "TEXTIL", "TEXTILES", "SAC", "SA", "SRL", "EIRL", "SOCIEDAD", "ANONIMA",
            "CERRADA", "COMERCIAL", "COMERCIALIZADORA", "INDUSTRIA", "INDUSTRIAS",
            "INDUSTRIAL", "EMPRESA", "GRUPO", "CORPORACION", "COMPANIA", "CIA",
            "DEL", "LOS", "LAS", "FACTURAS", "GUIAS", "GUIA", "FACTURA");

    /**
     * Detecta la empresa buscando su nombre dentro de un texto (la ruta del ZIP
     * o la razon social leida por la IA). Va de lo mas estricto a lo mas
     * tolerante:
     *   1) el slug de carpeta exacto (ej. "textil-clemente"),
     *   2) el nombre completo (ej. "TEXTIL CLEMENTE"),
     *   3) la palabra distintiva (ej. "CLEMENTE", "LAURA"), que tolera carpetas
     *      tipo "T. CLEMENTE" o razones sociales "... CLEMENTE SAC".
     * En (3), si dos empresas empatan se devuelve null (ambiguo): mejor dejar
     * "sin identificar" que adivinar la empresa equivocada.
     */
    public Empresa detectarEmpresa(String texto, List<Empresa> empresas) {
        if (texto == null || texto.isBlank() || empresas == null) return null;
        String t = normalizar(texto);

        for (Empresa e : empresas) {
            if (e.getCarpeta() != null && !e.getCarpeta().isBlank()
                    && t.contains(normalizar(e.getCarpeta()))) {
                return e;
            }
        }
        for (Empresa e : empresas) {
            if (e.getNombre() != null && !e.getNombre().isBlank()
                    && t.contains(normalizar(e.getNombre()))) {
                return e;
            }
        }

        String tTokens = " " + t.replaceAll("[^A-Z0-9]+", " ").trim() + " ";
        Empresa mejor = null;
        int mejorScore = 0;
        boolean empate = false;
        for (Empresa e : empresas) {
            int score = 0;
            for (String palabra : palabrasDistintivas(e.getNombre())) {
                if (tTokens.contains(" " + palabra + " ")) score++;
            }
            if (score > mejorScore) {
                mejorScore = score; mejor = e; empate = false;
            } else if (score > 0 && score == mejorScore) {
                empate = true;
            }
        }
        return (mejorScore > 0 && !empate) ? mejor : null;
    }

    /** Palabras >=4 letras del nombre que NO son genericas (LAURA, CLEMENTE). */
    private List<String> palabrasDistintivas(String nombre) {
        List<String> res = new ArrayList<>();
        if (nombre == null) return res;
        for (String w : normalizar(nombre).split("[^A-Z0-9]+")) {
            if (w.length() >= 4 && !PALABRAS_GENERICAS.contains(w)) res.add(w);
        }
        return res;
    }

    /** Mayusculas y sin tildes, para comparar sin que acentos/ñ estorben. */
    private String normalizar(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
    }

    public LocalDate parseFecha(String fechaTexto) {
        if (fechaTexto == null || fechaTexto.isBlank()) return null;
        try {
            return LocalDate.parse(fechaTexto.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
