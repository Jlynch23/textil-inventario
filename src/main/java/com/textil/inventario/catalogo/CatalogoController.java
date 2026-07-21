package com.textil.inventario.catalogo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/catalogo")
@RequiredArgsConstructor
@Slf4j
public class CatalogoController {

    private final CatalogoService catalogoService;

    private String primerError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    // ─── COLORES ───────────────────────────────────────────
    @GetMapping("/colores")
    public String listarColores(Model model) {
        model.addAttribute("colores", catalogoService.listarColores());
        model.addAttribute("color", new Color());
        return "catalogo/colores";
    }

    @PostMapping("/colores/guardar")
    public String guardarColor(@Valid @ModelAttribute Color color, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/colores";
        }
        catalogoService.guardarColor(color);
        ra.addFlashAttribute("mensaje", "Color guardado correctamente.");
        return "redirect:/catalogo/colores";
    }

    @GetMapping("/colores/editar/{id}")
    public String editarColor(@PathVariable Long id, Model model) {
        model.addAttribute("colores", catalogoService.listarColores());
        model.addAttribute("color", catalogoService.buscarColor(id));
        return "catalogo/colores";
    }

    @PostMapping("/colores/inactivar/{id}")
    public String inactivarColor(@PathVariable Long id, RedirectAttributes ra) {
        Color c = catalogoService.buscarColor(id);
        c.setActivo(false);
        catalogoService.guardarColor(c);
        ra.addFlashAttribute("mensaje", "Color inactivado.");
        return "redirect:/catalogo/colores";
    }

    @PostMapping("/colores/eliminar/{id}")
    public String eliminarColor(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarColor(id);
            ra.addFlashAttribute("mensaje", "Color eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este color está en uso por uno o más artículos. Usa \"Inactivar\" en su lugar.");
        }
        return "redirect:/catalogo/colores";
    }

    @PostMapping("/colores/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearColorRapido(@RequestBody ColorRapidoRequest request) {
        try {
            if (request.nombreOficial() == null || request.nombreOficial().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre oficial es obligatorio."));
            }
            // Idempotente: si ya existe un color activo con ese codigo FAST DYE,
            // se reutiliza en vez de intentar crear un duplicado.
            if (request.codigoFastDye() != null && !request.codigoFastDye().isBlank()) {
                Optional<Color> existente = catalogoService.resolverColorPorCodigo(request.codigoFastDye(), request.nombreOficial());
                if (existente.isPresent()) {
                    return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombreOficial", existente.get().getNombreOficial(), "yaExistia", true));
                }
            }
            Color color = new Color();
            color.setNombreOficial(request.nombreOficial());
            color.setCodigoFastDye(request.codigoFastDye());
            color.setActivo(true);
            Color guardado = catalogoService.guardarColor(color);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombreOficial", guardado.getNombreOficial()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un color con ese nombre. FAST DYE repite nombres con códigos distintos: usa un nombre diferenciado (ej. BLANCO AZULADO / BLANCO CREMOSO)."));
        } catch (Exception e) {
            log.error("Error en crearColorRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    // ─── TIPOS DE TELA ───────────────────────────────────────
    @GetMapping("/tipos-tela")
    public String listarTiposTela(Model model) {
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("tipoTela", new TipoTela());
        return "catalogo/tipos-tela";
    }

    @PostMapping("/tipos-tela/guardar")
    public String guardarTipoTela(@Valid @ModelAttribute TipoTela tipoTela, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/tipos-tela";
        }
        try {
            catalogoService.guardarTipoTela(tipoTela);
            ra.addFlashAttribute("mensaje", "Tipo de tela guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un tipo de tela con ese nombre.");
        }
        return "redirect:/catalogo/tipos-tela";
    }

    @GetMapping("/tipos-tela/editar/{id}")
    public String editarTipoTela(@PathVariable Long id, Model model) {
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("tipoTela", catalogoService.buscarTipoTela(id));
        return "catalogo/tipos-tela";
    }

    @PostMapping("/tipos-tela/inactivar/{id}")
    public String inactivarTipoTela(@PathVariable Long id, RedirectAttributes ra) {
        TipoTela t = catalogoService.buscarTipoTela(id);
        t.setActivo(false);
        catalogoService.guardarTipoTela(t);
        ra.addFlashAttribute("mensaje", "Tipo de tela inactivado.");
        return "redirect:/catalogo/tipos-tela";
    }

    // ─── TITULOS ─────────────────────────────────────────────
    @GetMapping("/titulos")
    public String listarTitulos(Model model) {
        model.addAttribute("titulos", catalogoService.listarTitulos());
        // Nombre "tituloForm" (no "titulo") a proposito: el fragmento del layout
        // (layout/base.html) declara un parametro llamado "titulo" para el texto
        // de la pestana del navegador -- si el atributo del modelo se llamara
        // igual, ese parametro lo tapa y Thymeleaf falla al resolver "titulo.id"
        // dentro de esta pantalla (EL1008E: Fragment has no field 'id').
        model.addAttribute("tituloForm", new Titulo());
        return "catalogo/titulos";
    }

    @PostMapping("/titulos/guardar")
    public String guardarTituloForm(@Valid @ModelAttribute("tituloForm") Titulo tituloForm, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/titulos";
        }
        try {
            catalogoService.guardarTitulo(tituloForm);
            ra.addFlashAttribute("mensaje", "Título guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un título con ese valor.");
        }
        return "redirect:/catalogo/titulos";
    }

    @GetMapping("/titulos/editar/{id}")
    public String editarTitulo(@PathVariable Long id, Model model) {
        model.addAttribute("titulos", catalogoService.listarTitulos());
        model.addAttribute("tituloForm", catalogoService.buscarTitulo(id));
        return "catalogo/titulos";
    }

    @PostMapping("/titulos/inactivar/{id}")
    public String inactivarTitulo(@PathVariable Long id, RedirectAttributes ra) {
        Titulo t = catalogoService.buscarTitulo(id);
        t.setActivo(false);
        catalogoService.guardarTitulo(t);
        ra.addFlashAttribute("mensaje", "Título inactivado.");
        return "redirect:/catalogo/titulos";
    }

    // ─── COMPOSICIONES ───────────────────────────────────────
    @GetMapping("/composiciones")
    public String listarComposiciones(Model model) {
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("composicion", new Composicion());
        return "catalogo/composiciones";
    }

    @PostMapping("/composiciones/guardar")
    public String guardarComposicionForm(@Valid @ModelAttribute Composicion composicion, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/composiciones";
        }
        try {
            catalogoService.guardarComposicion(composicion);
            ra.addFlashAttribute("mensaje", "Composición guardada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe una composición con ese nombre.");
        }
        return "redirect:/catalogo/composiciones";
    }

    @GetMapping("/composiciones/editar/{id}")
    public String editarComposicion(@PathVariable Long id, Model model) {
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("composicion", catalogoService.buscarComposicion(id));
        return "catalogo/composiciones";
    }

    @PostMapping("/composiciones/inactivar/{id}")
    public String inactivarComposicion(@PathVariable Long id, RedirectAttributes ra) {
        Composicion c = catalogoService.buscarComposicion(id);
        c.setActivo(false);
        catalogoService.guardarComposicion(c);
        ra.addFlashAttribute("mensaje", "Composición inactivada.");
        return "redirect:/catalogo/composiciones";
    }

    // ─── ACABADOS ────────────────────────────────────────────
    @GetMapping("/acabados")
    public String listarAcabados(Model model) {
        model.addAttribute("acabados", catalogoService.listarAcabados());
        model.addAttribute("acabado", new Acabado());
        return "catalogo/acabados";
    }

    @PostMapping("/acabados/guardar")
    public String guardarAcabadoForm(@Valid @ModelAttribute Acabado acabado, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/acabados";
        }
        try {
            catalogoService.guardarAcabado(acabado);
            ra.addFlashAttribute("mensaje", "Acabado guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un acabado con ese nombre.");
        }
        return "redirect:/catalogo/acabados";
    }

    @GetMapping("/acabados/editar/{id}")
    public String editarAcabado(@PathVariable Long id, Model model) {
        model.addAttribute("acabados", catalogoService.listarAcabados());
        model.addAttribute("acabado", catalogoService.buscarAcabado(id));
        return "catalogo/acabados";
    }

    @PostMapping("/acabados/inactivar/{id}")
    public String inactivarAcabado(@PathVariable Long id, RedirectAttributes ra) {
        Acabado a = catalogoService.buscarAcabado(id);
        a.setActivo(false);
        catalogoService.guardarAcabado(a);
        ra.addFlashAttribute("mensaje", "Acabado inactivado.");
        return "redirect:/catalogo/acabados";
    }

    // ─── EMPRESAS ────────────────────────────────────────────
    @GetMapping("/empresas")
    public String listarEmpresas(Model model) {
        model.addAttribute("empresas", catalogoService.listarEmpresas());
        model.addAttribute("empresa", new Empresa());
        return "catalogo/empresas";
    }

    @PostMapping("/empresas/guardar")
    public String guardarEmpresaForm(@Valid @ModelAttribute Empresa empresa, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/empresas";
        }
        try {
            catalogoService.guardarEmpresa(empresa);
            ra.addFlashAttribute("mensaje", "Empresa guardada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe una empresa con ese RUC.");
        }
        return "redirect:/catalogo/empresas";
    }

    @GetMapping("/empresas/editar/{id}")
    public String editarEmpresa(@PathVariable Long id, Model model) {
        model.addAttribute("empresas", catalogoService.listarEmpresas());
        model.addAttribute("empresa", catalogoService.buscarEmpresa(id));
        return "catalogo/empresas";
    }

    @PostMapping("/empresas/inactivar/{id}")
    public String inactivarEmpresa(@PathVariable Long id, RedirectAttributes ra) {
        Empresa e = catalogoService.buscarEmpresa(id);
        e.setActivo(false);
        catalogoService.guardarEmpresa(e);
        ra.addFlashAttribute("mensaje", "Empresa inactivada.");
        return "redirect:/catalogo/empresas";
    }

    // ─── TIPOS DE TELA / TITULOS (creacion rapida) ──────────
    @PostMapping("/tipos-tela/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearTipoTelaRapido(@RequestBody TipoTelaRapidoRequest request) {
        try {
            if (request.nombre() == null || request.nombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
            }
            Optional<TipoTela> existente = catalogoService.buscarTipoTelaPorNombre(request.nombre());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombre", existente.get().getNombre(), "yaExistia", true));
            }
            TipoTela tipoTela = new TipoTela();
            tipoTela.setNombre(request.nombre().trim());
            tipoTela.setActivo(true);
            TipoTela guardado = catalogoService.guardarTipoTela(tipoTela);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombre", guardado.getNombre(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un tipo de tela con ese nombre. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearTipoTelaRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/titulos/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearTituloRapido(@RequestBody TituloRapidoRequest request) {
        try {
            if (request.valor() == null || request.valor().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El valor es obligatorio."));
            }
            Optional<Titulo> existente = catalogoService.buscarTituloPorValor(request.valor());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "valor", existente.get().getValor(), "yaExistia", true));
            }
            Titulo titulo = new Titulo();
            titulo.setValor(request.valor().trim());
            titulo.setActivo(true);
            Titulo guardado = catalogoService.guardarTitulo(titulo);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "valor", guardado.getValor(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un título con ese valor. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearTituloRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/composiciones/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearComposicionRapido(@RequestBody ComposicionRapidoRequest request) {
        try {
            if (request.nombre() == null || request.nombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
            }
            Optional<Composicion> existente = catalogoService.buscarComposicionPorNombre(request.nombre());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombre", existente.get().getNombre(), "yaExistia", true));
            }
            Composicion composicion = new Composicion();
            composicion.setNombre(request.nombre().trim());
            composicion.setActivo(true);
            Composicion guardado = catalogoService.guardarComposicion(composicion);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombre", guardado.getNombre(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe una composición con ese nombre. Puede que otro usuario la haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearComposicionRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    @PostMapping("/acabados/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearAcabadoRapido(@RequestBody AcabadoRapidoRequest request) {
        try {
            if (request.nombre() == null || request.nombre().isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
            }
            Optional<Acabado> existente = catalogoService.buscarAcabadoPorNombre(request.nombre());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "nombre", existente.get().getNombre(), "yaExistia", true));
            }
            Acabado acabado = new Acabado();
            acabado.setNombre(request.nombre().trim());
            acabado.setActivo(true);
            Acabado guardado = catalogoService.guardarAcabado(acabado);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "nombre", guardado.getNombre(), "yaExistia", false));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(Map.of("error",
                    "Ya existe un acabado con ese nombre. Puede que otro usuario lo haya creado justo ahora — recarga e intenta de nuevo."));
        } catch (Exception e) {
            log.error("Error en crearAcabadoRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }

    // ─── ARTÍCULOS ─────────────────────────────────────────
    @PostMapping("/articulos/crear-rapido")
    @ResponseBody
    public ResponseEntity<?> crearArticuloRapido(@RequestBody ArticuloRapidoRequest request) {
        try {
            Optional<TipoTela> tipoTela = catalogoService.buscarTipoTelaPorNombre(request.tipoTelaNombre());
            if (tipoTela.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Tipo de tela '" + request.tipoTelaNombre() + "' no existe en el catálogo base."));
            }

            Optional<Titulo> titulo = catalogoService.buscarTituloPorValor(request.tituloValor());
            if (titulo.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Título '" + request.tituloValor() + "' no existe en el catálogo base."));
            }

            Optional<Composicion> composicion = catalogoService.buscarComposicionPorNombre(request.composicionNombre());
            if (composicion.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Composición '" + request.composicionNombre() + "' no existe en el catálogo base."));
            }

            String acabadoNombre = (request.acabadoNombre() == null || request.acabadoNombre().isBlank())
                    ? "LISO" : request.acabadoNombre();
            Optional<Acabado> acabado = catalogoService.buscarAcabadoPorNombre(acabadoNombre);
            if (acabado.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error",
                        "Acabado '" + acabadoNombre + "' no existe en el catálogo base."));
            }

            Optional<Articulo> existente = catalogoService.buscarArticuloPorCombinacion(
                    tipoTela.get().getId(), titulo.get().getId(), composicion.get().getId(), acabado.get().getId());
            if (existente.isPresent()) {
                return ResponseEntity.ok(Map.of("id", existente.get().getId(), "yaExistia", true));
            }

            Articulo articulo = new Articulo();
            articulo.setTipoTela(tipoTela.get());
            articulo.setTitulo(titulo.get());
            articulo.setComposicion(composicion.get());
            articulo.setAcabado(acabado.get());
            articulo.setCodigoInterno(catalogoService.generarCodigoInterno(tipoTela.get(), titulo.get(), composicion.get(), acabado.get()));
            articulo.setActivo(true);

            Articulo guardado = catalogoService.guardarArticulo(articulo);
            return ResponseEntity.ok(Map.of("id", guardado.getId(), "yaExistia", false));

        } catch (Exception e) {
            log.error("Error en crearArticuloRapido: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno. Intenta de nuevo o contacta al administrador."));
        }
    }


    @GetMapping("/articulos")
    public String listarArticulos(Model model) {
        model.addAttribute("articulos", catalogoService.listarArticulos());
        model.addAttribute("articulo", new Articulo());
        model.addAttribute("tiposTela", catalogoService.listarTiposTela());
        model.addAttribute("titulos", catalogoService.listarTitulos());
        model.addAttribute("composiciones", catalogoService.listarComposiciones());
        model.addAttribute("acabados", catalogoService.listarAcabados());
        return "catalogo/articulos";
    }

    @PostMapping("/articulos/guardar")
    public String guardarArticulo(@ModelAttribute Articulo articulo,
                                   @RequestParam Long tipoTelaId,
                                   @RequestParam Long tituloId,
                                   @RequestParam Long composicionId,
                                   @RequestParam Long acabadoId,
                                   RedirectAttributes ra) {
        articulo.setTipoTela(catalogoService.listarTiposTela().stream()
            .filter(t -> t.getId().equals(tipoTelaId)).findFirst().orElseThrow());
        articulo.setTitulo(catalogoService.listarTitulos().stream()
            .filter(t -> t.getId().equals(tituloId)).findFirst().orElseThrow());
        articulo.setComposicion(catalogoService.listarComposiciones().stream()
            .filter(c -> c.getId().equals(composicionId)).findFirst().orElseThrow());
        articulo.setAcabado(catalogoService.listarAcabados().stream()
            .filter(a -> a.getId().equals(acabadoId)).findFirst().orElseThrow());

        // Generar código interno automático
        if (articulo.getCodigoInterno() == null || articulo.getCodigoInterno().isBlank()) {
            articulo.setCodigoInterno(catalogoService.generarCodigoInterno(
                    articulo.getTipoTela(), articulo.getTitulo(), articulo.getComposicion(), articulo.getAcabado()));
        }

        try {
            catalogoService.guardarArticulo(articulo);
            ra.addFlashAttribute("mensaje", "Artículo guardado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Ya existe un artículo con esa combinación de tejido, título, composición y acabado.");
        }
        return "redirect:/catalogo/articulos";
    }

    @PostMapping("/articulos/eliminar/{id}")
    public String eliminarArticulo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarArticulo(id);
            ra.addFlashAttribute("mensaje", "Artículo eliminado correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: este artículo tiene stock, movimientos de kardex u otros registros asociados.");
        }
        return "redirect:/catalogo/articulos";
    }

    // ─── UBICACIONES ───────────────────────────────────────
    @GetMapping("/ubicaciones")
    public String listarUbicaciones(Model model) {
        model.addAttribute("ubicaciones", catalogoService.listarUbicaciones());
        model.addAttribute("ubicacion", new Ubicacion());
        return "catalogo/ubicaciones";
    }

    @PostMapping("/ubicaciones/guardar")
    public String guardarUbicacion(@Valid @ModelAttribute Ubicacion ubicacion, BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", primerError(bindingResult));
            return "redirect:/catalogo/ubicaciones";
        }
        catalogoService.guardarUbicacion(ubicacion);
        ra.addFlashAttribute("mensaje", "Ubicación guardada correctamente.");
        return "redirect:/catalogo/ubicaciones";
    }

    @PostMapping("/ubicaciones/eliminar/{id}")
    public String eliminarUbicacion(@PathVariable Long id, RedirectAttributes ra) {
        try {
            catalogoService.eliminarUbicacion(id);
            ra.addFlashAttribute("mensaje", "Ubicación eliminada correctamente.");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "No se puede eliminar: esta ubicación tiene stock o transferencias asociadas.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/catalogo/ubicaciones";
    }
}
