package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.Empresa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class DocumentoStorageService {

    @Value("${documentos.ruta-base}")
    private String rutaBase;

    public String guardar(MultipartFile archivo, String tipoDocumento, Empresa empresa, java.time.LocalDate fecha) throws IOException {
        String carpetaEmpresa = (empresa.getCarpeta() != null && !empresa.getCarpeta().isBlank())
                ? empresa.getCarpeta() : "Otros";

        String subcarpetaTipo = "FACTURA".equalsIgnoreCase(tipoDocumento) ? "Facturas" : "Guias";

        Path carpetaDestino = Paths.get(rutaBase, subcarpetaTipo, carpetaEmpresa);
        Files.createDirectories(carpetaDestino);

        String extension = obtenerExtension(archivo.getOriginalFilename());
        String prefijoFecha = fecha != null ? fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "sinfecha";
        String nombreArchivo = prefijoFecha + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path rutaCompleta = carpetaDestino.resolve(nombreArchivo);
        Files.copy(archivo.getInputStream(), rutaCompleta, StandardCopyOption.REPLACE_EXISTING);

        return rutaCompleta.toString();
    }

    public Path resolverRuta(String rutaGuardada) {
        return Paths.get(rutaGuardada);
    }

    private String obtenerExtension(String nombreOriginal) {
        if (nombreOriginal == null || !nombreOriginal.contains(".")) return ".pdf";
        return nombreOriginal.substring(nombreOriginal.lastIndexOf("."));
    }
}
