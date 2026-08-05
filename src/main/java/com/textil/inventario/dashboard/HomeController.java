package com.textil.inventario.dashboard;

import com.textil.inventario.inventario.StockActual;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.catalogo.UbicacionRepository;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionRepository;
import com.textil.inventario.recepciones.EntradaRapidaRepository;
import com.textil.inventario.recepciones.SalidaRapidaRepository;
import com.textil.inventario.recepciones.ProgramaService;
import com.textil.inventario.transferencias.Transferencia;
import com.textil.inventario.transferencias.TransferenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final StockActualRepository stockActualRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final RecepcionRepository recepcionRepository;
    private final EntradaRapidaRepository entradaRapidaRepository;
    private final SalidaRapidaRepository salidaRapidaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final ProgramaService programaService;

    private static final int UMBRAL_STOCK_BAJO = 10;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {

        List<StockActual> stockDisponible = stockActualRepository.findStockDisponible();

        int totalRollos = stockDisponible.stream().mapToInt(StockActual::getRollos).sum();

        int totalRollosPraderas = stockDisponible.stream()
                .filter(s -> Boolean.TRUE.equals(s.getUbicacion().getEsPrincipal()))
                .mapToInt(StockActual::getRollos)
                .sum();

        List<Transferencia> transferenciasEnTransito =
                transferenciaRepository.findByEstado(Transferencia.EstadoTransferencia.CONFIRMADA_SALIDA);

        List<Recepcion> recepcionesPendientes =
                recepcionRepository.findByEstado(Recepcion.EstadoRecepcion.PENDIENTE);

        List<Recepcion> sinFacturar = recepcionRepository.findByNumeroFacturaIsNullOrderByFechaGuiaDesc();

        int entradasSalidasPendientes =
                entradaRapidaRepository.findByEstadoOrderByCreatedAtDesc("PENDIENTE").size()
                + salidaRapidaRepository.findByEstadoOrderByCreatedAtDesc("PENDIENTE").size();

        Map<String, Integer> stockPorUbicacion = stockDisponible.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getUbicacion().getNombre(),
                        LinkedHashMap::new,
                        Collectors.summingInt(StockActual::getRollos)
                ));

        // Por ARTICULO+COLOR (no por articulo entero): sumar todos los colores
        // escondia el color que quedo bajo (ej. Rojo en 4) detras del total del
        // articulo. Mismo criterio que el reporte de stock bajo y que la alerta
        // por correo (StockBajoEvent), que ya son por articulo+color.
        Map<String, Integer> totalPorArticuloColor = stockDisponible.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getArticulo().getId() + ":" + s.getColor().getId(),
                        Collectors.summingInt(StockActual::getRollos)
                ));

        List<StockActual> articulosStockBajo = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (StockActual s : stockDisponible) {
            String clave = s.getArticulo().getId() + ":" + s.getColor().getId();
            if (!vistos.contains(clave) && totalPorArticuloColor.get(clave) < UMBRAL_STOCK_BAJO) {
                vistos.add(clave);
                articulosStockBajo.add(s);
            }
        }

        model.addAttribute("totalRollos", totalRollos);
        model.addAttribute("totalRollosPraderas", totalRollosPraderas);
        model.addAttribute("praderasId", ubicacionRepository.findByEsPrincipalTrue().map(u -> u.getId()).orElse(null));
        model.addAttribute("transferenciasEnTransito", transferenciasEnTransito);
        model.addAttribute("recepcionesPendientes", recepcionesPendientes);
        model.addAttribute("sinFacturar", sinFacturar);
        model.addAttribute("entradasSalidasPendientes", entradasSalidasPendientes);
        model.addAttribute("stockPorUbicacion", stockPorUbicacion);
        model.addAttribute("articulosStockBajo", articulosStockBajo);
        model.addAttribute("totalPorArticuloColor", totalPorArticuloColor);
        model.addAttribute("umbralStockBajo", UMBRAL_STOCK_BAJO);

        // #9 (OSIV off): el conteo se calcula en el servicio dentro de una
        // transaccion (isCompleto() itera detalles), sin exponer entidades con
        // colecciones perezosas al render del dashboard.
        model.addAttribute("programasEnProceso", programaService.contarEnProceso());

        // GERENTE ve una version simplificada, pensada para celular: tarjetas
        // grandes con lo esencial, en vez del dashboard tecnico completo.
        boolean esGerente = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE"));
        if (esGerente) {
            return "dashboard/gerente";
        }

        return "dashboard/index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
