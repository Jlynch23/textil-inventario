package com.textil.inventario.recepciones;

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

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
@RequestMapping("/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final RecepcionDocumentoRepository documentoRepository;
    private final EmpresaRepository empresaRepository;

    @GetMapping
    public String listar(@RequestParam(required = false) Long empresaId,
                          @RequestParam(required = false) String tipoDocumento,
                          @RequestParam(required = false) Integer anio,
                          @RequestParam(required = false) Integer mes,
                          Model model) {

        String tipoDocumentoLimpio = (tipoDocumento == null || tipoDocumento.isBlank()) ? null : tipoDocumento;
        List<RecepcionDocumento> documentos = documentoRepository.buscarConFiltros(empresaId, tipoDocumentoLimpio, anio, mes);

        model.addAttribute("documentos", documentos);
        model.addAttribute("empresas", empresaRepository.findByActivoTrue());
        model.addAttribute("filtroEmpresaId", empresaId);
        model.addAttribute("filtroTipo", tipoDocumento);
        model.addAttribute("filtroAnio", anio);
        model.addAttribute("filtroMes", mes);

        return "documentos/lista";
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) throws MalformedURLException {
        RecepcionDocumento doc = documentoRepository.findById(id).orElseThrow();
        Path ruta = Paths.get(doc.getRutaArchivo());
        Resource resource = new UrlResource(ruta.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getNombreOriginal() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/ver")
    public ResponseEntity<Resource> ver(@PathVariable Long id) throws MalformedURLException {
        RecepcionDocumento doc = documentoRepository.findById(id).orElseThrow();
        Path ruta = Paths.get(doc.getRutaArchivo());
        Resource resource = new UrlResource(ruta.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getNombreOriginal() + "\"")
                .body(resource);
    }

    @PostMapping("/descargar-zip")
    public ResponseEntity<byte[]> descargarZip(@RequestParam("ids") List<Long> ids) throws IOException {
        List<RecepcionDocumento> docs = documentoRepository.findAllById(ids);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        Set<String> nombresUsados = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (RecepcionDocumento doc : docs) {
                Path ruta = Paths.get(doc.getRutaArchivo());
                if (!Files.exists(ruta)) continue;

                String nombreEntrada = doc.getNombreOriginal();
                int contador = 1;
                while (nombresUsados.contains(nombreEntrada)) {
                    nombreEntrada = "(" + contador + ")_" + doc.getNombreOriginal();
                    contador++;
                }
                nombresUsados.add(nombreEntrada);

                zos.putNextEntry(new ZipEntry(nombreEntrada));
                Files.copy(ruta, zos);
                zos.closeEntry();
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documentos.zip\"")
                .body(baos.toByteArray());
    }
}
