package com.textil.inventario.reportes;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

    public byte[] generarExcel(String nombreHoja, List<String> encabezados, List<List<Object>> filas) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
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
                        celda.setCellValue(valor.toString());
                    }
                }
            }

            for (int col = 0; col < encabezados.size(); col++) {
                sheet.autoSizeColumn(col);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
