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
        // Auditoria: validar en el backend antes de escribir en disco (no confiar
        // en accept=".pdf" del navegador): vacio / tamaño / firma magica %PDF-.
        ValidadorPdf.validar(archivo);

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

    // Variante sin MultipartFile: para cuando el PDF ya esta en disco (ej. un
    // documento que Archivo Historico ya proceso) y solo hace falta copiarlo
    // a la ubicacion definitiva de una Recepcion real, sin pasar por upload HTTP.
    public String guardar(Path archivoOrigen, String nombreOriginal, String tipoDocumento, Empresa empresa, java.time.LocalDate fecha) throws IOException {
        String carpetaEmpresa = (empresa.getCarpeta() != null && !empresa.getCarpeta().isBlank())
                ? empresa.getCarpeta() : "Otros";

        String subcarpetaTipo = "FACTURA".equalsIgnoreCase(tipoDocumento) ? "Facturas" : "Guias";

        Path carpetaDestino = Paths.get(rutaBase, subcarpetaTipo, carpetaEmpresa);
        Files.createDirectories(carpetaDestino);

        String extension = obtenerExtension(nombreOriginal);
        String prefijoFecha = fecha != null ? fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "sinfecha";
        String nombreArchivo = prefijoFecha + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path rutaCompleta = carpetaDestino.resolve(nombreArchivo);
        Files.copy(archivoOrigen, rutaCompleta, StandardCopyOption.REPLACE_EXISTING);

        return rutaCompleta.toString();
    }

    public String guardarFotoRapida(org.springframework.web.multipart.MultipartFile archivo, String subcarpeta) throws IOException {
        // Auditoria FILE-01: validar el CONTENIDO de la foto en el backend (firma
        // magica + tamaño), no solo la extension del nombre.
        ValidadorImagen.validar(archivo);

        Path carpetaDestino = Paths.get(rutaBase, subcarpeta);
        Files.createDirectories(carpetaDestino);

        String extension = obtenerExtension(archivo.getOriginalFilename());
        String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
        String nombreArchivo = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path rutaCompleta = carpetaDestino.resolve(nombreArchivo);
        Files.copy(archivo.getInputStream(), rutaCompleta, StandardCopyOption.REPLACE_EXISTING);

        return rutaCompleta.toString();
    }

    /**
     * Resuelve una ruta guardada en BD garantizando que quede DENTRO de
     * {@code documentos.ruta-base} (auditoría FILE-02). Las rutas de descarga se
     * construían con {@code Paths.get(doc.getRutaArchivo())} confiando en el valor
     * de la BD; si ese valor se alterara (o una función administrativa futura lo
     * tomara de otra fuente), se podría leer un archivo fuera del almacenamiento
     * permitido. Se normaliza base y destino y se exige contención.
     */
    public Path resolverRutaSegura(String rutaGuardada) {
        if (rutaGuardada == null || rutaGuardada.isBlank()) {
            throw new IllegalArgumentException("Ruta de documento vacía.");
        }
        Path base = Paths.get(rutaBase).toAbsolutePath().normalize();
        Path resuelta = Paths.get(rutaGuardada).toAbsolutePath().normalize();
        if (!resuelta.startsWith(base)) {
            throw new IllegalArgumentException("Ruta de documento fuera del almacenamiento permitido.");
        }
        return resuelta;
    }

    private static final java.util.Set<String> EXTENSIONES_PERMITIDAS =
            java.util.Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp");

    /**
     * Resuelve la extensión del archivo subido contra una lista blanca
     * (ver AUDIT.md, hallazgo SEC-03). Antes se tomaba el substring desde el
     * último "." del nombre original sin validarlo, lo que permitía un path
     * traversal: un nombre como "foto.png/../../../../etc/algo" generaba una
     * "extensión" que contenía "..", usada luego para construir la ruta final
     * en disco. Cualquier valor fuera de la lista blanca (incluyendo cualquiera
     * con separadores de ruta o "..") se reemplaza por ".pdf" por defecto.
     */
    private String obtenerExtension(String nombreOriginal) {
        if (nombreOriginal == null || !nombreOriginal.contains(".")) return ".pdf";
        String extension = nombreOriginal.substring(nombreOriginal.lastIndexOf(".")).toLowerCase();
        return EXTENSIONES_PERMITIDAS.contains(extension) ? extension : ".pdf";
    }
}
