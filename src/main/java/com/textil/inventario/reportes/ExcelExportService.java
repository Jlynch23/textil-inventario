package com.textil.inventario.reportes;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

    // R-F2 (red-team): tope duro de filas. Antes, exportar el kardex/stock SIN
    // rango de fechas materializaba TODO el historico en un XSSFWorkbook (DOM
    // entero en heap) + autoSizeColumn O(filas) -> OutOfMemoryError con un click.
    // Ahora: (a) SXSSFWorkbook mantiene solo una ventana de filas en memoria y
    // vuelca el resto a disco; (b) si el dataset supera este tope, se corta con
    // un mensaje claro pidiendo filtrar, en vez de tumbar la instancia.
    private static final int MAX_FILAS = 100_000;
    private static final int VENTANA_FILAS_EN_MEMORIA = 100;

    public byte[] generarExcel(String nombreHoja, List<String> encabezados, List<List<Object>> filas) throws IOException {
        if (filas.size() > MAX_FILAS) {
            throw new IllegalArgumentException(
                    "El reporte tiene " + filas.size() + " filas, supera el máximo exportable de "
                    + MAX_FILAS + ". Filtra por un rango de fechas más acotado para descargarlo.");
        }

        SXSSFWorkbook workbook = new SXSSFWorkbook(VENTANA_FILAS_EN_MEMORIA);
        try {
            Sheet sheet = workbook.createSheet(nombreHoja);

            CellStyle estiloEncabezado = workbook.createCellStyle();
            Font fuenteEncabezado = workbook.createFont();
            fuenteEncabezado.setBold(true);
            fuenteEncabezado.setColor(IndexedColors.WHITE.getIndex());
            estiloEncabezado.setFont(fuenteEncabezado);
            estiloEncabezado.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row filaEncabezado = sheet.createRow(0);
            for (int col = 0; col < encabezados.size(); col++) {
                Cell celda = filaEncabezado.createCell(col);
                celda.setCellValue(encabezados.get(col));
                celda.setCellStyle(estiloEncabezado);
                // Ancho fijo por columna: autoSizeColumn bajo streaming exige
                // trackear todas las columnas en memoria (defo el propósito de
                // SXSSF) y es O(filas). Un ancho derivado del encabezado alcanza.
                sheet.setColumnWidth(col, Math.min(60, Math.max(14, encabezados.get(col).length() + 4)) * 256);
            }

            int numeroFila = 1;
            for (List<Object> fila : filas) {
                Row row = sheet.createRow(numeroFila++);
                for (int col = 0; col < fila.size(); col++) {
                    Object valor = fila.get(col);
                    Cell celda = row.createCell(col);
                    if (valor == null) {
                        celda.setBlank();
                    } else if (valor instanceof Number n) {
                        celda.setCellValue(n.doubleValue());
                    } else {
                        celda.setCellValue(sanitizarCeldaTexto(valor.toString()));
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } finally {
            // Libera los archivos temporales que SXSSF vuelca a disco.
            workbook.dispose();
            workbook.close();
        }
    }

    private static final java.util.Set<Character> CARACTERES_FORMULA = java.util.Set.of('=', '+', '-', '@', '\t', '\r');

    /**
     * Neutraliza inyección de fórmulas (CWE-1236): varios strings exportados
     * (numeroGuia, numeroFactura, razonSocialDetectada) provienen del OCR de
     * PDFs de terceros, no de datos tecleados internamente. Si el valor
     * empieza con un caracter que Excel interpreta como inicio de fórmula, se
     * le antepone un apóstrofe para forzar que se trate como texto literal.
     */
    private String sanitizarCeldaTexto(String valor) {
        if (valor.isEmpty() || !CARACTERES_FORMULA.contains(valor.charAt(0))) {
            return valor;
        }
        return "'" + valor;
    }
}
