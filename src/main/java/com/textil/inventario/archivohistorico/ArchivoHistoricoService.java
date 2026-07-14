package com.textil.inventario.archivohistorico;

import com.textil.inventario.catalogo.*;
import com.textil.inventario.recepciones.AnthropicOcrService;
import com.textil.inventario.recepciones.ExtraccionFacturaResponse;
import com.textil.inventario.recepciones.ExtraccionGuiaResponse;
import com.textil.inventario.recepciones.ProductoExtraido;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ArchivoHistoricoService {

    @Value("${documentos.ruta-base}")
    private String rutaBase;

    private final DocumentoHistoricoRepository documentoHistoricoRepository;
    private final EmpresaRepository empresaRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ArticuloRepository articuloRepository;
    private final AnthropicOcrService ocrService;

    /**
     * Descomprime el ZIP subido, guarda cada PDF en disco y crea un registro
     * PENDIENTE por documento. NO llama a la IA aquí (eso es rápido y se hace
     * en segundo plano vía procesarPendientesAsync()).
     */
    public int subirZip(MultipartFile zip) throws IOException {
        List<Empresa> empresas = empresaRepository.findByActivoTrue();
        int contador = 0;
        Set<String> nombresUsados = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String nombreEntrada = entry.getName();
                if (!nombreEntrada.toLowerCase().endsWith(".pdf")) continue;

                byte[] contenido = zis.readAllBytes();
                if (contenido.length == 0) continue;

                DocumentoHistorico.TipoDocumentoHistorico tipo = detectarTipo(nombreEntrada);
                Empresa empresa = detectarEmpresa(nombreEntrada, empresas);

                String nombreArchivo = Paths.get(nombreEntrada).getFileName().toString();
                Path carpetaDestino = Paths.get(rutaBase, "HistoricoImportado",
                        tipo.name(),
                        empresa != null ? empresa.getCarpeta() : "SinIdentificar");
                Files.createDirectories(carpetaDestino);

                String nombreGuardado = evitarColision(nombreArchivo, nombresUsados);
                Path rutaCompleta = carpetaDestino.resolve(nombreGuardado);
                Files.write(rutaCompleta, contenido);

                DocumentoHistorico doc = new DocumentoHistorico();
                doc.setEmpresa(empresa);
                doc.setTipoDocumento(tipo);
                doc.setNombreOriginal(nombreArchivo);
                doc.setRutaRelativaZip(nombreEntrada);
                doc.setRutaArchivo(rutaCompleta.toString());
                doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.PENDIENTE);
                documentoHistoricoRepository.save(doc);

                contador++;
            }
        }

        return contador;
    }

    private String evitarColision(String nombreOriginal, Set<String> nombresUsados) {
        String nombre = UUID.randomUUID().toString().substring(0, 8) + "_" + nombreOriginal;
        nombresUsados.add(nombre);
        return nombre;
    }

    private DocumentoHistorico.TipoDocumentoHistorico detectarTipo(String rutaEntrada) {
        String ruta = rutaEntrada.toUpperCase();
        if (ruta.contains("FACTURA")) return DocumentoHistorico.TipoDocumentoHistorico.FACTURA;
        if (ruta.contains("GUIA")) return DocumentoHistorico.TipoDocumentoHistorico.GUIA;
        return DocumentoHistorico.TipoDocumentoHistorico.OTRO;
    }

    private Empresa detectarEmpresa(String rutaEntrada, List<Empresa> empresas) {
        String ruta = rutaEntrada.toUpperCase();
        for (Empresa e : empresas) {
            if (e.getCarpeta() != null && !e.getCarpeta().isBlank()
                    && ruta.contains(e.getCarpeta().toUpperCase())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Procesa todos los documentos PENDIENTE con la IA, en lotes, corriendo en
     * un hilo aparte para no bloquear la petición HTTP que subió el ZIP.
     */
    @Async
    public void procesarPendientesAsync() {
        List<DocumentoHistorico> pendientes;
        do {
            pendientes = documentoHistoricoRepository
                    .findTop500ByEstadoProcesoOrderByIdAsc(DocumentoHistorico.EstadoProceso.PENDIENTE);
            for (DocumentoHistorico doc : pendientes) {
                procesarUno(doc);
            }
        } while (!pendientes.isEmpty());
    }

    private void procesarUno(DocumentoHistorico doc) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(doc.getRutaArchivo()));

            if (doc.getTipoDocumento() == DocumentoHistorico.TipoDocumentoHistorico.FACTURA) {
                ExtraccionFacturaResponse r = ocrService.extraerDatosFactura(bytes);
                doc.setNumeroFactura(r.numeroFactura());
                doc.setFechaDocumento(parseFecha(r.fechaFactura()));
                doc.setRazonSocialDetectada(r.razonSocialDetectada());
                if (r.advertencia() != null) doc.setObservacion(r.advertencia());
            } else {
                // GUIA u OTRO: se intenta leer como guia (tiene los datos de productos)
                ExtraccionGuiaResponse r = ocrService.extraerDatosGuia(bytes);
                doc.setNumeroGuia(r.numeroGuia());
                if (r.numeroFactura() != null) doc.setNumeroFactura(r.numeroFactura());
                doc.setFechaDocumento(parseFecha(r.fechaGuia()));
                doc.setRazonSocialDetectada(r.razonSocialDetectada());
                if (r.advertencia() != null) doc.setObservacion(r.advertencia());

                int productos = 0, coloresCreados = 0, articulosCreados = 0;
                if (r.productos() != null) {
                    for (ProductoExtraido p : r.productos()) {
                        productos++;
                        int[] resultado = intentarEnriquecerCatalogo(p);
                        coloresCreados += resultado[0];
                        articulosCreados += resultado[1];
                    }
                }
                doc.setProductosEncontrados(productos);
                doc.setColoresCreados(coloresCreados);
                doc.setArticulosCreados(articulosCreados);
            }

            doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.PROCESADO);
            doc.setProcesadoAt(LocalDateTime.now());
        } catch (Exception e) {
            doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.ERROR);
            doc.setObservacion("Error al procesar: " + e.getMessage());
        }
        documentoHistoricoRepository.save(doc);
    }

    /**
     * Solo crea Color/Articulo si hacen falta. NUNCA toca stock_actual ni kardex_movimientos.
     * Devuelve [coloresCreados, articulosCreados] (0 o 1 cada uno).
     */
    private int[] intentarEnriquecerCatalogo(ProductoExtraido p) {
        if (p.tipoTela() == null || p.titulo() == null || p.colorCodigo() == null) {
            return new int[]{0, 0};
        }

        Optional<TipoTela> tipoTela = tipoTelaRepository.findByNombreIgnoreCase(p.tipoTela().trim());
        if (tipoTela.isEmpty()) return new int[]{0, 0};

        Optional<Titulo> titulo = tituloRepository.findByValorIgnoreCase(p.titulo().trim());
        if (titulo.isEmpty()) return new int[]{0, 0};

        int colorCreado = 0;
        Optional<Color> colorOpt = colorRepository.findByCodigoFastDye(p.colorCodigo().trim());
        Color color;
        if (colorOpt.isPresent()) {
            color = colorOpt.get();
        } else {
            String nombreOficial = (p.colorNombre() != null && !p.colorNombre().isBlank())
                    ? p.colorNombre().trim() : "Color " + p.colorCodigo().trim();

            // Puede que el MISMO color ya exista con otro codigo_fast_dye
            // (FAST DYE reasigna codigos con el tiempo). Si el nombre ya existe, se reutiliza
            // en vez de fallar por la restriccion de nombre unico.
            Optional<Color> porNombre = colorRepository.findByNombreOficialIgnoreCase(nombreOficial);
            if (porNombre.isPresent()) {
                color = porNombre.get();
            } else {
                try {
                    Color nuevo = new Color();
                    nuevo.setNombreOficial(nombreOficial);
                    nuevo.setCodigoFastDye(p.colorCodigo().trim());
                    nuevo.setActivo(true);
                    color = colorRepository.save(nuevo);
                    colorCreado = 1;
                } catch (Exception e) {
                    // choque de datos que no pudimos anticipar: no se puede crear con seguridad
                    return new int[]{0, 0};
                }
            }
        }

        int articuloCreado = 0;
        Optional<Articulo> articuloOpt = articuloRepository.findByTipoTelaIdAndTituloIdAndColorId(
                tipoTela.get().getId(), titulo.get().getId(), color.getId());
        if (articuloOpt.isEmpty()) {
            try {
                Articulo nuevo = new Articulo();
                nuevo.setTipoTela(tipoTela.get());
                nuevo.setTitulo(titulo.get());
                nuevo.setColor(color);
                String codigo = tipoTela.get().getNombre().replace(" ", "").substring(0, 3).toUpperCase()
                        + "-" + titulo.get().getValor().replace("/", "")
                        + "-" + color.getNombreOficial().replace(" ", "").substring(0, Math.min(4, color.getNombreOficial().length())).toUpperCase()
                        + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                nuevo.setCodigoInterno(codigo);
                nuevo.setActivo(true);
                articuloRepository.save(nuevo);
                articuloCreado = 1;
            } catch (Exception e) {
                // no se pudo crear el articulo; el color creado igual queda
            }
        }

        return new int[]{colorCreado, articuloCreado};
    }

    private LocalDate parseFecha(String fechaTexto) {
        if (fechaTexto == null || fechaTexto.isBlank()) return null;
        try {
            return LocalDate.parse(fechaTexto.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
