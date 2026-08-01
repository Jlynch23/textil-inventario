package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.Empresa;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Descompresión SEGURA del ZIP de Archivo Histórico a disco (#12, auditoría).
 *
 * Extraído de ArchivoHistoricoService para que ese servicio quede como
 * orquestador y no cargue también con las defensas anti-zip-bomb (R-F1): tope de
 * entradas, filtro .pdf, y copia en streaming controlando el tamaño DURANTE la
 * copia (por-entrada y acumulado), sin materializar nada en memoria. Devuelve
 * descriptores de los PDFs ya guardados; crear los registros en BD es tarea del
 * servicio.
 */
@Component
@RequiredArgsConstructor
public class ZipSeguroExtractor {

    // R-F1: defensas anti zip-bomb (cantidad de entradas y tamaño descomprimido).
    private static final int MAX_ENTRADAS_ZIP = 1000;
    private static final long MAX_TAMANO_DESCOMPRIMIDO_TOTAL = 200L * 1024 * 1024; // 200 MB
    private static final long MAX_TAMANO_POR_ENTRADA = 50L * 1024 * 1024; // 50 MB

    @Value("${documentos.ruta-base}")
    private String rutaBase;

    private final DocumentoHistoricoClasificador clasificador;

    /** Un PDF ya extraído y guardado en disco, con su clasificación. */
    public record EntradaExtraida(String nombreOriginal, String rutaRelativaZip, Path rutaArchivo,
                                  DocumentoHistorico.TipoDocumentoHistorico tipo, Empresa empresa) {}

    /**
     * Descomprime el ZIP a disco de forma segura y devuelve un descriptor por
     * cada PDF guardado. Lanza IllegalArgumentException si el ZIP viola los
     * límites (demasiadas entradas o tamaño descomprimido excesivo).
     */
    public List<EntradaExtraida> extraer(MultipartFile zip, List<Empresa> empresas) throws IOException {
        List<EntradaExtraida> resultado = new ArrayList<>();
        int entradasProcesadas = 0;
        long tamanoTotalDescomprimido = 0;
        Set<String> nombresUsados = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                entradasProcesadas++;
                if (entradasProcesadas > MAX_ENTRADAS_ZIP) {
                    throw new IllegalArgumentException(
                            "El ZIP supera el limite de " + MAX_ENTRADAS_ZIP + " archivos permitidos.");
                }
                String nombreEntrada = entry.getName();
                if (!nombreEntrada.toLowerCase().endsWith(".pdf")) continue;

                DocumentoHistorico.TipoDocumentoHistorico tipo = clasificador.detectarTipo(nombreEntrada);
                Empresa empresa = clasificador.detectarEmpresaPorRuta(nombreEntrada, empresas);

                String nombreArchivo = Paths.get(nombreEntrada).getFileName().toString();
                Path carpetaDestino = Paths.get(rutaBase, "HistoricoImportado",
                        tipo.name(),
                        empresa != null ? empresa.getCarpeta() : "SinIdentificar");
                Files.createDirectories(carpetaDestino);

                String nombreGuardado = clasificador.evitarColision(nombreArchivo, nombresUsados);
                Path rutaCompleta = carpetaDestino.resolve(nombreGuardado);

                // R-F1: copia en streaming controlando el tamaño DURANTE la copia,
                // sin materializar la entrada en memoria. El tope efectivo es el
                // menor entre el máximo por-entrada y lo que resta del cap acumulado.
                long escritos = copiarEntradaConTope(zis, rutaCompleta,
                        Math.min(MAX_TAMANO_POR_ENTRADA, MAX_TAMANO_DESCOMPRIMIDO_TOTAL - tamanoTotalDescomprimido));
                if (escritos == 0) {
                    Files.deleteIfExists(rutaCompleta);
                    continue;
                }
                tamanoTotalDescomprimido += escritos;

                resultado.add(new EntradaExtraida(nombreArchivo, nombreEntrada, rutaCompleta, tipo, empresa));
            }
        }
        return resultado;
    }

    private long copiarEntradaConTope(InputStream in, Path destino, long maxBytes) throws IOException {
        long total = 0;
        boolean excedido = false;
        byte[] buffer = new byte[8192];
        try (OutputStream out = Files.newOutputStream(destino)) {
            int leidos;
            while ((leidos = in.read(buffer)) != -1) {
                total += leidos;
                if (total > maxBytes) { excedido = true; break; }
                out.write(buffer, 0, leidos);
            }
        }
        if (excedido) {
            Files.deleteIfExists(destino);
            throw new IllegalArgumentException(
                    "Una entrada del ZIP supera el límite de seguridad de descompresión "
                    + "(máx " + (MAX_TAMANO_POR_ENTRADA / (1024 * 1024)) + " MB por archivo, "
                    + (MAX_TAMANO_DESCOMPRIMIDO_TOTAL / (1024 * 1024)) + " MB en total). "
                    + "Posible archivo corrupto o zip-bomb; se abortó la importación.");
        }
        return total;
    }
}
