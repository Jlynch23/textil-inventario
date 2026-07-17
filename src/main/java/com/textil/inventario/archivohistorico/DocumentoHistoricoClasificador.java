package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        String ruta = rutaEntrada.toUpperCase();
        for (Empresa e : empresas) {
            if (e.getCarpeta() != null && !e.getCarpeta().isBlank()
                    && ruta.contains(e.getCarpeta().toUpperCase())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Detecta la empresa a partir de texto REAL leido por la IA (razon social
     * de la guia/factura), en vez de la ruta del ZIP. Permite subir un ZIP
     * sin organizar por carpetas de empresa: la IA decide, no la ruta.
     * Primero intenta con la palabra distintiva de "carpeta" (ej. LAURA,
     * CLEMENTE); si no matchea, intenta con el nombre completo de la empresa.
     */
    public Empresa detectarEmpresaPorTexto(String razonSocialDetectada, List<Empresa> empresas) {
        if (razonSocialDetectada == null || razonSocialDetectada.isBlank()) return null;
        String normalizado = razonSocialDetectada.toUpperCase();

        for (Empresa e : empresas) {
            if (e.getCarpeta() != null && !e.getCarpeta().isBlank()
                    && normalizado.contains(e.getCarpeta().toUpperCase())) {
                return e;
            }
        }
        for (Empresa e : empresas) {
            if (e.getNombre() != null && normalizado.contains(e.getNombre().toUpperCase())) {
                return e;
            }
        }
        return null;
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
