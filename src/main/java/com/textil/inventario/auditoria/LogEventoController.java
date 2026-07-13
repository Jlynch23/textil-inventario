package com.textil.inventario.auditoria;

import com.textil.inventario.seguridad.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogEventoController {

    private final LogEventoRepository logEventoRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public String listar(@RequestParam(required = false) Long usuarioId,
                          @RequestParam(required = false) String accion,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                          Model model) {

        LocalDateTime desdeDateTime = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime hastaDateTime = hasta != null ? hasta.atTime(LocalTime.MAX) : null;

        String accionLimpia = (accion == null || accion.isBlank()) ? null : accion;

        List<LogEvento> eventos = logEventoRepository.buscarConFiltros(usuarioId, accionLimpia, desdeDateTime, hastaDateTime);

        model.addAttribute("eventos", eventos);
        model.addAttribute("usuarios", usuarioRepository.findAll());
        model.addAttribute("filtroUsuarioId", usuarioId);
        model.addAttribute("filtroAccion", accion);
        model.addAttribute("filtroDesde", desde);
        model.addAttribute("filtroHasta", hasta);

        return "auditoria/lista";
    }
}
