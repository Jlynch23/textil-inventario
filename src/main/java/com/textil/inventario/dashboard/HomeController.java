package com.textil.inventario.dashboard;

import com.textil.inventario.inventario.StockActual;
import com.textil.inventario.inventario.StockActualRepository;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionRepository;
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

        Map<String, Integer> stockPorUbicacion = stockDisponible.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getUbicacion().getNombre(),
                        LinkedHashMap::new,
                        Collectors.summingInt(StockActual::getRollos)
                ));

        Map<Long, Integer> totalPorArticulo = stockDisponible.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getArticulo().getId(),
                        Collectors.summingInt(StockActual::getRollos)
                ));

        List<StockActual> articulosStockBajo = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (StockActual s : stockDisponible) {
            Long articuloId = s.getArticulo().getId();
            if (!vistos.contains(articuloId) && totalPorArticulo.get(articuloId) < UMBRAL_STOCK_BAJO) {
                vistos.add(articuloId);
                articulosStockBajo.add(s);
            }
        }

        model.addAttribute("totalRollos", totalRollos);
        model.addAttribute("totalRollosPraderas", totalRollosPraderas);
        model.addAttribute("transferenciasEnTransito", transferenciasEnTransito);
        model.addAttribute("recepcionesPendientes", recepcionesPendientes);
        model.addAttribute("stockPorUbicacion", stockPorUbicacion);
        model.addAttribute("articulosStockBajo", articulosStockBajo);
        model.addAttribute("totalPorArticulo", totalPorArticulo);
        model.addAttribute("umbralStockBajo", UMBRAL_STOCK_BAJO);

        return "dashboard/index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
