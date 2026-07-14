package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/archivo-historico")
@RequiredArgsConstructor
public class ArchivoHistoricoController {

    private final DocumentoHistoricoRepository documentoHistoricoRepository;
    private final EmpresaRepository empresaRepository;
    private final ArchivoHistoricoService archivoHistoricoService;

    @GetMapping
    public String listar(@RequestParam(required = false) Long empresaId,
                          @RequestParam(required = false) DocumentoHistorico.TipoDocumentoHistorico tipo,
                          @RequestParam(required = false) DocumentoHistorico.EstadoProceso estado,
                          @RequestParam(required = false) Integer anio,
                          @RequestParam(required = false) String busqueda,
                          Model model) {

        model.addAttribute("documentos",
                documentoHistoricoRepository.buscarConFiltros(empresaId, tipo, estado, anio, busqueda));
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("tipos", DocumentoHistorico.TipoDocumentoHistorico.values());
        model.addAttribute("estados", DocumentoHistorico.EstadoProceso.values());
        model.addAttribute("filtroEmpresaId", empresaId);
        model.addAttribute("filtroTipo", tipo);
        model.addAttribute("filtroEstado", estado);
        model.addAttribute("filtroAnio", anio);
        model.addAttribute("filtroBusqueda", busqueda);

        model.addAttribute("totalPendientes", documentoHistoricoRepository.countByEstadoProceso(DocumentoHistorico.EstadoProceso.PENDIENTE));
        model.addAttribute("totalProcesados", documentoHistoricoRepository.countByEstadoProceso(DocumentoHistorico.EstadoProceso.PROCESADO));
        model.addAttribute("totalErrores", documentoHistoricoRepository.countByEstadoProceso(DocumentoHistorico.EstadoProceso.ERROR));

        return "archivohistorico/lista";
    }

    @PostMapping("/subir-zip")
    public String subirZip(@RequestParam("zip") MultipartFile zip, RedirectAttributes ra) {
        try {
            int cantidad = archivoHistoricoService.subirZip(zip);
            if (cantidad > 0) {
                archivoHistoricoService.procesarPendientesAsync();
            }
            ra.addFlashAttribute("mensaje",
                    "Se importaron " + cantidad + " documentos. Se están procesando en segundo plano; " +
                    "recarga esta página en unos minutos para ver el progreso.");
        } catch (IOException e) {
            ra.addFlashAttribute("error", "Error al leer el ZIP: " + e.getMessage());
        }
        return "redirect:/archivo-historico";
    }

    @GetMapping("/{id}/ver")
    public ResponseEntity<Resource> ver(@PathVariable Long id) throws MalformedURLException {
        DocumentoHistorico doc = documentoHistoricoRepository.findById(id).orElseThrow();
        Path ruta = Paths.get(doc.getRutaArchivo());
        Resource resource = new UrlResource(ruta.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getNombreOriginal() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) throws MalformedURLException {
        DocumentoHistorico doc = documentoHistoricoRepository.findById(id).orElseThrow();
        Path ruta = Paths.get(doc.getRutaArchivo());
        Resource resource = new UrlResource(ruta.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getNombreOriginal() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes ra) {
        documentoHistoricoRepository.findById(id).ifPresent(doc -> {
            try {
                java.nio.file.Files.deleteIfExists(Paths.get(doc.getRutaArchivo()));
            } catch (IOException ignored) {
                // si el archivo fisico ya no existe, igual borramos el registro
            }
            documentoHistoricoRepository.delete(doc);
        });
        ra.addFlashAttribute("mensaje", "Documento eliminado del archivo histórico.");
        return "redirect:/archivo-historico";
    }

    @PostMapping("/eliminar-seleccionados")
    public String eliminarSeleccionados(@RequestParam(value = "ids", required = false) java.util.List<Long> ids,
                                         RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "No seleccionaste ningún documento.");
            return "redirect:/archivo-historico";
        }
        int contador = 0;
        for (Long id : ids) {
            java.util.Optional<DocumentoHistorico> docOpt = documentoHistoricoRepository.findById(id);
            if (docOpt.isEmpty()) continue;
            DocumentoHistorico doc = docOpt.get();
            try {
                java.nio.file.Files.deleteIfExists(Paths.get(doc.getRutaArchivo()));
            } catch (IOException ignored) {
                // si el archivo fisico ya no existe, igual borramos el registro
            }
            documentoHistoricoRepository.delete(doc);
            contador++;
        }
        ra.addFlashAttribute("mensaje", "Se eliminaron " + contador + " documento(s) del archivo histórico.");
        return "redirect:/archivo-historico";
    }
}
