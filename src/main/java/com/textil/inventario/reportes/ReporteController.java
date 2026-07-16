package com.textil.inventario.reportes;

import com.textil.inventario.auditoria.LogEvento;
import com.textil.inventario.catalogo.Articulo;
import com.textil.inventario.inventario.KardexMovimiento;
import com.textil.inventario.inventario.StockActual;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.transferencias.Transferencia;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private static final int UMBRAL_STOCK_BAJO_DEFECTO = 10;
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReporteService reporteService;
    private final ExcelExportService excelExportService;

    @GetMapping
    public String menu() {
        return "reportes/menu";
    }

    // ---------- STOCK POR UBICACION ----------

    @GetMapping("/stock")
    public String stock(@RequestParam(required = false) Long ubicacionId,
                         @RequestParam(required = false) Long tipoTelaId,
                         Model model) {
        model.addAttribute("stockList", reporteService.filtrarStock(ubicacionId, tipoTelaId));
        model.addAttribute("ubicaciones", reporteService.listarUbicacionesActivas());
        model.addAttribute("tiposTela", reporteService.listarTiposTelaActivos());
        model.addAttribute("filtroUbicacionId", ubicacionId);
        model.addAttribute("filtroTipoTelaId", tipoTelaId);
        return "reportes/stock";
    }

    @GetMapping("/stock/excel")
    public ResponseEntity<byte[]> stockExcel(@RequestParam(required = false) Long ubicacionId,
                                              @RequestParam(required = false) Long tipoTelaId) throws IOException {
        List<StockActual> stock = reporteService.filtrarStock(ubicacionId, tipoTelaId);
        List<String> encabezados = List.of("Ubicación", "Tipo Tela", "Título", "Color", "Rollos", "Peso (kg)");
        List<List<Object>> filas = new ArrayList<>();
        for (StockActual s : stock) {
            filas.add(List.of(
                    s.getUbicacion().getNombre(),
                    s.getArticulo().getTipoTela().getNombre(),
                    s.getArticulo().getTitulo().getValor(),
                    s.getArticulo().getColor().getNombreOficial(),
                    s.getRollos(),
                    s.getPesoKg()
            ));
        }
        return excelResponse("Stock", encabezados, filas, "reporte-stock.xlsx");
    }

    // ---------- KARDEX POR RANGO DE FECHAS ----------

    @GetMapping("/kardex")
    public String kardex(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                          Model model) {
        model.addAttribute("movimientos", reporteService.filtrarKardex(desde, hasta));
        model.addAttribute("filtroDesde", desde);
        model.addAttribute("filtroHasta", hasta);
        return "reportes/kardex";
    }

    @GetMapping("/kardex/excel")
    public ResponseEntity<byte[]> kardexExcel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        List<KardexMovimiento> movimientos = reporteService.filtrarKardex(desde, hasta);
        List<String> encabezados = List.of("Fecha", "Tipo", "Artículo", "Origen", "Destino", "Rollos", "Peso (kg)", "Usuario");
        List<List<Object>> filas = new ArrayList<>();
        for (KardexMovimiento m : movimientos) {
            filas.add(List.of(
                    m.getFecha().format(FMT_FECHA_HORA),
                    m.getTipoMovimiento().toString(),
                    reporteService.descripcionArticulo(m.getArticulo()),
                    m.getUbicacionOrigen() != null ? m.getUbicacionOrigen().getNombre() : "",
                    m.getUbicacionDestino() != null ? m.getUbicacionDestino().getNombre() : "",
                    m.getRollos(),
                    m.getPesoKg() != null ? m.getPesoKg() : "",
                    m.getUsuario().getNombre()
            ));
        }
        return excelResponse("Kardex", encabezados, filas, "reporte-kardex.xlsx");
    }

    // ---------- RECEPCIONES POR PROVEEDOR / FECHA ----------

    @GetMapping("/recepciones")
    public String recepciones(@RequestParam(required = false) Long empresaId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                               Model model) {
        model.addAttribute("recepciones", reporteService.filtrarRecepciones(empresaId, desde, hasta));
        model.addAttribute("empresas", reporteService.listarEmpresasActivas());
        model.addAttribute("filtroEmpresaId", empresaId);
        model.addAttribute("filtroDesde", desde);
        model.addAttribute("filtroHasta", hasta);
        return "reportes/recepciones";
    }

    @GetMapping("/recepciones/excel")
    public ResponseEntity<byte[]> recepcionesExcel(@RequestParam(required = false) Long empresaId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        List<Recepcion> recepciones = reporteService.filtrarRecepciones(empresaId, desde, hasta);
        List<String> encabezados = List.of("N° Guía", "N° Factura", "Proveedor", "Fecha Guía", "Estado", "Rollos Guía", "Rollos Recibidos");
        List<List<Object>> filas = new ArrayList<>();
        for (Recepcion r : recepciones) {
            int rollosGuia = r.getDetalles().stream().mapToInt(d -> d.getRollosGuia() == null ? 0 : d.getRollosGuia()).sum();
            int rollosRecibidos = r.getDetalles().stream().mapToInt(d -> d.getRollosRecibidos() == null ? 0 : d.getRollosRecibidos()).sum();
            filas.add(List.of(
                    r.getNumeroGuia(),
                    r.getNumeroFactura() != null ? r.getNumeroFactura() : "",
                    r.getEmpresa().getNombre(),
                    r.getFechaGuia().format(FMT_FECHA),
                    r.getEstado().toString(),
                    rollosGuia,
                    rollosRecibidos
            ));
        }
        return excelResponse("Recepciones", encabezados, filas, "reporte-recepciones.xlsx");
    }

    // ---------- TRANSFERENCIAS ENTRE UBICACIONES ----------

    @GetMapping("/transferencias")
    public String transferencias(@RequestParam(required = false) Long ubicacionOrigenId,
                                  @RequestParam(required = false) Transferencia.EstadoTransferencia estado,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                  Model model) {
        model.addAttribute("transferencias", reporteService.filtrarTransferencias(ubicacionOrigenId, estado, desde, hasta));
        model.addAttribute("ubicaciones", reporteService.listarUbicacionesActivas());
        model.addAttribute("estados", reporteService.estadosTransferencia());
        model.addAttribute("filtroUbicacionOrigenId", ubicacionOrigenId);
        model.addAttribute("filtroEstado", estado);
        model.addAttribute("filtroDesde", desde);
        model.addAttribute("filtroHasta", hasta);
        return "reportes/transferencias";
    }

    @GetMapping("/transferencias/excel")
    public ResponseEntity<byte[]> transferenciasExcel(@RequestParam(required = false) Long ubicacionOrigenId,
                                                        @RequestParam(required = false) Transferencia.EstadoTransferencia estado,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        List<Transferencia> transferencias = reporteService.filtrarTransferencias(ubicacionOrigenId, estado, desde, hasta);
        List<String> encabezados = List.of("N° Transferencia", "Origen", "Estado", "Fecha Solicitud", "Fecha Salida", "Fecha Llegada", "Total Rollos Solicitados");
        List<List<Object>> filas = new ArrayList<>();
        for (Transferencia t : transferencias) {
            int totalRollos = t.getDetalles().stream().mapToInt(d -> d.getCantidadSolicitada() == null ? 0 : d.getCantidadSolicitada()).sum();
            filas.add(List.of(
                    t.getNumero(),
                    t.getUbicacionOrigen().getNombre(),
                    t.getEstado().toString(),
                    t.getFechaSolicitud().format(FMT_FECHA_HORA),
                    t.getFechaConfirmacionSalida() != null ? t.getFechaConfirmacionSalida().format(FMT_FECHA_HORA) : "",
                    t.getFechaConfirmacionLlegada() != null ? t.getFechaConfirmacionLlegada().format(FMT_FECHA_HORA) : "",
                    totalRollos
            ));
        }
        return excelResponse("Transferencias", encabezados, filas, "reporte-transferencias.xlsx");
    }

    // ---------- STOCK BAJO / CRITICO ----------

    @GetMapping("/stock-bajo")
    public String stockBajo(@RequestParam(required = false) Integer umbral, Model model) {
        int umbralFinal = umbral != null ? umbral : UMBRAL_STOCK_BAJO_DEFECTO;
        model.addAttribute("articulos", reporteService.articulosStockBajo(umbralFinal));
        model.addAttribute("filtroUmbral", umbralFinal);
        return "reportes/stock-bajo";
    }

    @GetMapping("/stock-bajo/excel")
    public ResponseEntity<byte[]> stockBajoExcel(@RequestParam(required = false) Integer umbral) throws IOException {
        int umbralFinal = umbral != null ? umbral : UMBRAL_STOCK_BAJO_DEFECTO;
        List<String> encabezados = List.of("Tipo Tela", "Título", "Color", "Total Rollos (todas las ubicaciones)");
        List<List<Object>> filas = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : reporteService.articulosStockBajo(umbralFinal).entrySet()) {
            filas.add(List.of(entry.getKey(), entry.getValue()));
        }
        return excelResponse("Stock Bajo", encabezados, filas, "reporte-stock-bajo.xlsx");
    }

    // ---------- ERRORES DEL SISTEMA (solo SUPERADMIN) ----------

    @GetMapping("/errores")
    public String errores(Model model) {
        List<LogEvento> errores = reporteService.erroresSistema();
        model.addAttribute("errores", errores);
        return "reportes/errores";
    }

    // ---------- HELPER EXCEL ----------

    private ResponseEntity<byte[]> excelResponse(String hoja, List<String> encabezados, List<List<Object>> filas, String nombreArchivo) throws IOException {
        byte[] excel = excelExportService.generarExcel(hoja, encabezados, filas);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .body(excel);
    }
}
