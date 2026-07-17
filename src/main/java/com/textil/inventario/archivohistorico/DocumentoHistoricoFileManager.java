package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import com.textil.inventario.catalogo.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Extraido de ArchivoHistoricoService (auditoria 17-jul-2026, God Class de
 * 584 lineas). Agrupa el manejo de archivos en disco relacionado a Archivo
 * Historico: mover el PDF a la carpeta de la empresa correcta cuando la IA
 * detecta una empresa distinta a la asignada inicialmente por ruta del ZIP.
 * Incluye el fix de concurrencia del 15-jul-2026 (dos ZIPs subidos casi al
 * mismo tiempo podian pisar el mismo archivo ya movido).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoHistoricoFileManager {

    @Value("${documentos.ruta-base}")
    private String rutaBase;

    private final EmpresaRepository empresaRepository;
    private final DocumentoHistoricoClasificador clasificador;

    public void resolverEmpresaYMoverArchivo(DocumentoHistorico doc, String razonSocialDetectada) {
        List<Empresa> empresas = empresaRepository.findByActivoTrue();
        Empresa empresaDetectadaPorIA = clasificador.detectarEmpresaPorTexto(razonSocialDetectada, empresas);
        if (empresaDetectadaPorIA != null) {
            moverArchivoSiEmpresaCambio(doc, empresaDetectadaPorIA);
        }
    }

    /**
     * Si la empresa detectada por la IA es distinta a la que tiene el
     * documento (o no tenia ninguna), mueve el PDF en disco a la carpeta
     * de la empresa correcta y actualiza empresa + rutaArchivo. Si el
     * movimiento en disco falla por cualquier motivo, NO se toca ni la
     * empresa ni la ruta en base de datos (para que ambas sigan siendo
     * consistentes entre si), y se deja constancia en observacion.
     */
    public void moverArchivoSiEmpresaCambio(DocumentoHistorico doc, Empresa nuevaEmpresa) {
        boolean yaCorrecta = doc.getEmpresa() != null && doc.getEmpresa().getId().equals(nuevaEmpresa.getId());
        if (yaCorrecta) return;

        Path origen = Paths.get(doc.getRutaArchivo());
        Path carpetaDestino = Paths.get(rutaBase, "HistoricoImportado",
                doc.getTipoDocumento().name(), nuevaEmpresa.getCarpeta());
        Path destino = carpetaDestino.resolve(origen.getFileName());

        try {
            if (!Files.exists(origen) && Files.exists(destino)) {
                // El archivo ya no esta en el origen pero SI existe en el destino:
                // ya fue movido antes (ej. procesamiento concurrente del mismo ZIP
                // subido dos veces). No es un error, solo falta reflejarlo en el
                // registro para que quede consistente con lo que ya hay en disco.
                doc.setRutaArchivo(destino.toString());
                doc.setEmpresa(nuevaEmpresa);
                return;
            }

            Files.createDirectories(carpetaDestino);
            Files.move(origen, destino);

            doc.setRutaArchivo(destino.toString());
            doc.setEmpresa(nuevaEmpresa);
        } catch (Exception e) {
            log.warn("No se pudo mover el documento historico id={} a la carpeta de {}: {}",
                    doc.getId(), nuevaEmpresa.getNombre(), e.getMessage());
            String nota = "No se pudo mover el archivo a la carpeta de " + nuevaEmpresa.getNombre()
                    + " tras detectarla por IA: " + e.getMessage();
            doc.setObservacion(doc.getObservacion() == null ? nota : doc.getObservacion() + "\n" + nota);
        }
    }
}
