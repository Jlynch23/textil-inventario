package com.textil.inventario.archivohistorico;
import com.textil.inventario.catalogo.*;
import com.textil.inventario.recepciones.AnthropicOcrService;
import com.textil.inventario.recepciones.ExtraccionFacturaResponse;
import com.textil.inventario.recepciones.ExtraccionGuiaResponse;
import com.textil.inventario.recepciones.ProductoExtraido;
import com.textil.inventario.recepciones.Recepcion;
import com.textil.inventario.recepciones.RecepcionDetalle;
import com.textil.inventario.recepciones.RecepcionService;
import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
@Service
@RequiredArgsConstructor
@Slf4j
public class ArchivoHistoricoService {
    @Value("${documentos.ruta-base}")
    private String rutaBase;
    private final DocumentoHistoricoRepository documentoHistoricoRepository;
    private final EmpresaRepository empresaRepository;
    private final AnthropicOcrService ocrService;
    private final RecepcionService recepcionService;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoHistoricoClasificador clasificador;
    private final DocumentoHistoricoFileManager fileManager;
    private final ZipSeguroExtractor zipExtractor;
    private final DocumentoHistoricoEnriquecedor enriquecedor;
    /**
     * Descomprime el ZIP subido, guarda cada PDF en disco y crea un registro
     * PENDIENTE por documento. NO llama a la IA aquí (eso es rápido y se hace
     * en segundo plano vía procesarPendientesAsync()).
     * <p>
     * crearRecepcionAutomatica: si viene en true, cada GUIA de este lote,
     * al procesarse, ademas de enriquecer el catalogo va a crear una
     * Recepcion real y confirmarla automaticamente usando los rollos de la
     * guia como si fueran el conteo fisico (afecta stock_actual y
     * kardex_movimientos). Pensado SOLO para cargar datos de prueba masivos;
     * por defecto viene en false y el comportamiento original no cambia.
     */
    // #12: los limites anti zip-bomb y la copia en streaming viven ahora en
    // ZipSeguroExtractor (SEC-05 / R-F1).
    public int subirZip(MultipartFile zip, boolean crearRecepcionAutomatica, Long usuarioId) throws IOException {
        List<Empresa> empresas = empresaRepository.findByActivoTrue();

        // #12: la descompresion segura a disco (limites anti zip-bomb + streaming)
        // vive en ZipSeguroExtractor. Aca solo se crean los registros PENDIENTE.
        List<ZipSeguroExtractor.EntradaExtraida> entradas = zipExtractor.extraer(zip, empresas);
        for (ZipSeguroExtractor.EntradaExtraida e : entradas) {
            DocumentoHistorico doc = new DocumentoHistorico();
            doc.setEmpresa(e.empresa());
            doc.setTipoDocumento(e.tipo());
            doc.setNombreOriginal(e.nombreOriginal());
            doc.setRutaRelativaZip(e.rutaRelativaZip());
            doc.setRutaArchivo(e.rutaArchivo().toString());
            doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.PENDIENTE);
            doc.setCrearRecepcionAutomatica(crearRecepcionAutomatica);
            doc.setSubidoPorUsuarioId(usuarioId);
            documentoHistoricoRepository.save(doc);
        }
        return entradas.size();
    }

    /**
     * Copia una entrada del ZIP al disco en streaming (R-F1), abortando en cuanto
     * el tamaño descomprimido supera {@code maxBytes} — sin cargar el contenido
     * entero en memoria (que es lo que permitía la zip-bomb con readAllBytes()).
     * Devuelve los bytes escritos; borra el archivo parcial y lanza si se excede.
     */
    /**
     * Re-intenta detectar la empresa de los documentos que quedaron "(sin
     * identificar)", SIN volver a leer el PDF: reutiliza la razon social que ya
     * leyo la IA y la ruta del ZIP, y las pasa por el MISMO matcher que la
     * recepcion individual (ver DocumentoHistoricoClasificador.detectarEmpresa).
     * Sirve para arreglar en bloque los documentos viejos tras mejorar la
     * deteccion. Devuelve cuantos se pudieron asignar.
     */
    public int reDetectarEmpresas() {
        List<Empresa> empresas = empresaRepository.findByActivoTrue();
        int actualizados = 0;
        for (DocumentoHistorico doc : documentoHistoricoRepository.findByEmpresaIsNull()) {
            String razon = doc.getRazonSocialDetectada() != null ? doc.getRazonSocialDetectada() : "";
            String ruta = doc.getRutaRelativaZip() != null ? doc.getRutaRelativaZip() : "";
            Empresa e = clasificador.detectarEmpresa((razon + " " + ruta).trim(), empresas);
            if (e != null) {
                // Mueve el PDF a la carpeta de la empresa y actualiza empresa+ruta.
                fileManager.moverArchivoSiEmpresaCambio(doc, e);
                documentoHistoricoRepository.save(doc);
                actualizados++;
            }
        }
        return actualizados;
    }

    /**
     * Procesa todos los documentos PENDIENTE con la IA, en lotes, corriendo en
     * un hilo aparte para no bloquear la petición HTTP que subió el ZIP.
     */
    @Async("archivoHistoricoTaskExecutor")
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
            // Auditoria OCR-01: acotar el tamaño ANTES de cargar el PDF entero a
            // memoria (readAllBytes + Base64 + JSON multiplican la RAM). El tope
            // por entrada del ZIP (50MB) es mayor que el limite del OCR; se aplica
            // el mismo maximo que la ruta interactiva (ValidadorPdf, 20MB).
            java.nio.file.Path rutaPdf = Paths.get(doc.getRutaArchivo());
            if (Files.size(rutaPdf) > com.textil.inventario.recepciones.ValidadorPdf.MAX_PDF_BYTES) {
                throw new IllegalArgumentException(
                        "El PDF supera el tamaño permitido (máx "
                        + (com.textil.inventario.recepciones.ValidadorPdf.MAX_PDF_BYTES / 1024 / 1024) + " MB).");
            }
            byte[] bytes = Files.readAllBytes(rutaPdf);
            // Auditoria: una entrada del ZIP que no sea PDF no debe mandarse al OCR
            // (falla obscuro); se marca con un mensaje claro. La firma %PDF- es la
            // barrera real (el nombre/extension no garantiza nada).
            if (!com.textil.inventario.recepciones.ValidadorPdf.esPdf(bytes)) {
                throw new IllegalArgumentException("El archivo no es un PDF válido (no empieza con %PDF-).");
            }
            String razonSocialDetectada;

            if (doc.getTipoDocumento() == DocumentoHistorico.TipoDocumentoHistorico.FACTURA) {
                ExtraccionFacturaResponse r = ocrService.extraerDatosFactura(bytes);
                doc.setNumeroFactura(r.numeroFactura());
                doc.setFechaDocumento(clasificador.parseFecha(r.fechaFactura()));
                doc.setRazonSocialDetectada(r.razonSocialDetectada());
                razonSocialDetectada = r.razonSocialDetectada();
                if (r.advertencia() != null) doc.setObservacion(r.advertencia());

                // Dedup: si esta factura ya fue importada antes, no se reprocesa.
                if (marcarSiEsDuplicado(doc)) return;

                // La empresa se resuelve ANTES de la vinculacion factura-guia,
                // para que quede consistente con el resto del documento.
                fileManager.resolverEmpresaYMoverArchivo(doc, razonSocialDetectada);

                if (r.guiasReferenciadas() != null && !r.guiasReferenciadas().isEmpty()) {
                    doc.setGuiasReferenciadas(String.join(", ", r.guiasReferenciadas()));
                    List<Long> recepcionesVinculadas = vincularFacturaConGuiasExistentes(doc, r.guiasReferenciadas());

                    if (Boolean.TRUE.equals(doc.getCrearRecepcionAutomatica()) && !recepcionesVinculadas.isEmpty()) {
                        try {
                            // Asigna el numero de factura a la Recepcion real (no solo al
                            // registro de Archivo Historico), para que aparezca correctamente
                            // en Documentos y en el resto de la app.
                            recepcionService.asignarFactura(doc.getNumeroFactura(), doc.getFechaDocumento(), recepcionesVinculadas);
                            recepcionService.guardarDocumentoFacturaDesdeArchivo(
                                    recepcionesVinculadas, Paths.get(doc.getRutaArchivo()), doc.getNombreOriginal());
                        } catch (Exception eAdjuntar) {
                            log.warn("No se pudo adjuntar la factura del documento id={} a sus recepciones: {}",
                                    doc.getId(), eAdjuntar.getMessage());
                            String nota = "No se pudo adjuntar esta factura a Documentos: " + eAdjuntar.getMessage();
                            doc.setObservacion(doc.getObservacion() == null ? nota : doc.getObservacion() + "\n" + nota);
                        }
                    }
                }
            } else {
                // GUIA u OTRO: se intenta leer como guia (tiene los datos de productos)
                ExtraccionGuiaResponse r = ocrService.extraerDatosGuia(bytes);
                doc.setNumeroGuia(r.numeroGuia());
                if (r.numeroFactura() != null) doc.setNumeroFactura(r.numeroFactura());
                doc.setFechaDocumento(clasificador.parseFecha(r.fechaGuia()));
                doc.setRazonSocialDetectada(r.razonSocialDetectada());
                razonSocialDetectada = r.razonSocialDetectada();
                if (r.advertencia() != null) doc.setObservacion(r.advertencia());

                // Dedup: si esta guia ya fue importada antes (misma N de guia), no
                // se vuelve a enriquecer el catalogo ni a crear Recepcion; se marca
                // como duplicada. Asi, aunque se escanee/suba otra vez, no entra doble.
                if (marcarSiEsDuplicado(doc)) return;

                // La empresa se resuelve ANTES de enriquecer el catalogo y crear
                // la Recepcion, porque crear una Recepcion requiere empresaId.
                fileManager.resolverEmpresaYMoverArchivo(doc, razonSocialDetectada);

                if ((doc.getNumeroFactura() == null || doc.getNumeroFactura().isBlank())
                        && r.numeroGuia() != null && !r.numeroGuia().isBlank()) {
                    buscarFacturaQueYaReferenciaEstaGuia(r.numeroGuia().trim())
                            .ifPresent(doc::setNumeroFactura);
                }

                int productos = 0, coloresCreados = 0, articulosCreados = 0;
                List<LineaParaRecepcion> lineasParaRecepcion = new ArrayList<>();

                if (r.productos() != null) {
                    for (ProductoExtraido p : r.productos()) {
                        productos++;
                        DocumentoHistoricoEnriquecedor.Resultado resultado = enriquecedor.enriquecer(p);
                        coloresCreados += resultado.colorCreado();
                        articulosCreados += resultado.articuloCreado();
                        if (resultado.articulo() != null) {
                            lineasParaRecepcion.add(new LineaParaRecepcion(resultado.articulo(), resultado.color(), p));
                        }
                    }
                }
                doc.setProductosEncontrados(productos);
                doc.setColoresCreados(coloresCreados);
                doc.setArticulosCreados(articulosCreados);

                if (Boolean.TRUE.equals(doc.getCrearRecepcionAutomatica())) {
                    crearYConfirmarRecepcionAutomatica(doc, lineasParaRecepcion);
                }
            }

            doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.PROCESADO);
            doc.setProcesadoAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error al procesar documento historico id={}: {}", doc.getId(), e.getMessage(), e);
            doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.ERROR);
            doc.setObservacion("Error al procesar: " + e.getMessage());
        }
        // M6: se persiste el numero normalizado para que el lookup indexado de
        // duplicados (buscarYaImportado) encuentre este doc en futuras importaciones.
        doc.setNumeroNormalizado(calcularNumeroNormalizado(doc));
        documentoHistoricoRepository.save(doc);
    }

    /**
     * Evita importar dos veces la misma guia/factura, aunque se vuelva a
     * escanear o subir. Si ya existe OTRO documento PROCESADO con el mismo
     * numero (guia por N de guia normalizado, factura por N de factura), marca
     * ESTE como DUPLICADO con una nota, lo guarda y devuelve true para que el
     * proceso se corte antes de enriquecer el catalogo o crear una Recepcion.
     */
    private boolean marcarSiEsDuplicado(DocumentoHistorico doc) {
        DocumentoHistorico original = buscarYaImportado(doc);
        if (original == null) return false;

        String numero = doc.getTipoDocumento() == DocumentoHistorico.TipoDocumentoHistorico.FACTURA
                ? doc.getNumeroFactura() : doc.getNumeroGuia();
        doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.DUPLICADO);
        String nota = "Documento duplicado: «" + (numero != null ? numero.trim() : "") +
                "» ya fue importado antes (documento #" + original.getId() + "). No se creó nada nuevo.";
        doc.setObservacion(doc.getObservacion() == null ? nota : doc.getObservacion() + "\n" + nota);
        doc.setProcesadoAt(LocalDateTime.now());
        documentoHistoricoRepository.save(doc);
        return true;
    }

    /**
     * Numero normalizado de un documento para dedup: la factura por su numero en
     * mayusculas/sin espacios; la guia (o cualquier otro tipo) por su numero de
     * guia sin ceros a la izquierda (normalizarNumeroGuia). Es la MISMA logica que
     * persiste V40 en la columna numero_normalizado, para que el lookup indexado
     * coincida con lo comparado en memoria.
     */
    String calcularNumeroNormalizado(DocumentoHistorico doc) {
        boolean esFactura = doc.getTipoDocumento() == DocumentoHistorico.TipoDocumentoHistorico.FACTURA;
        String numero = esFactura ? doc.getNumeroFactura() : doc.getNumeroGuia();
        if (numero == null || numero.isBlank()) return null;
        return esFactura ? numero.trim().toUpperCase() : normalizarNumeroGuia(numero);
    }

    /**
     * Busca otro documento ya PROCESADO con el mismo numero (guia o factura).
     * M6: lookup indexado por numero_normalizado en vez de cargar toda la tabla
     * del tipo y comparar en memoria (O(n^2)).
     */
    private DocumentoHistorico buscarYaImportado(DocumentoHistorico doc) {
        String norm = calcularNumeroNormalizado(doc);
        if (norm == null || norm.isBlank()) return null;

        DocumentoHistorico.TipoDocumentoHistorico tipo =
                doc.getTipoDocumento() == DocumentoHistorico.TipoDocumentoHistorico.FACTURA
                        ? DocumentoHistorico.TipoDocumentoHistorico.FACTURA
                        : DocumentoHistorico.TipoDocumentoHistorico.GUIA;

        return documentoHistoricoRepository
                .findFirstByTipoDocumentoAndNumeroNormalizadoAndEstadoProcesoAndIdNot(
                        tipo, norm, DocumentoHistorico.EstadoProceso.PROCESADO, doc.getId())
                .orElse(null);
    }

    private record LineaParaRecepcion(Articulo articulo, Color color, ProductoExtraido producto) {}

    /**
     * Crea una Recepcion real con una linea por cada producto ya enriquecido
     * en el catalogo, y la confirma de inmediato usando los rollos leidos en
     * la guia como si fueran el conteo fisico recibido (sin diferencias).
     * Esto SI afecta stock_actual y kardex_movimientos. Es idempotente: si
     * el documento ya tiene una recepcion creada (recepcionCreadaId), no
     * vuelve a crear otra si se reprocesa por error.
     */
    private void crearYConfirmarRecepcionAutomatica(DocumentoHistorico doc, List<LineaParaRecepcion> lineas) {
        if (doc.getRecepcionCreadaId() != null) {
            return; // ya se creo antes, evitar duplicar si se reprocesa
        }
        if (doc.getEmpresa() == null) {
            String nota = "No se creo Recepcion automatica: no se pudo identificar la empresa de este documento.";
            doc.setObservacion(doc.getObservacion() == null ? nota : doc.getObservacion() + "\n" + nota);
            return;
        }
        if (doc.getNumeroGuia() == null || doc.getNumeroGuia().isBlank() || lineas.isEmpty()) {
            String nota = "No se creo Recepcion automatica: no se pudo leer numero de guia o productos validos.";
            doc.setObservacion(doc.getObservacion() == null ? nota : doc.getObservacion() + "\n" + nota);
            return;
        }

        try {
            establecerContextoDeSeguridad(doc.getSubidoPorUsuarioId());

            Recepcion recepcion = recepcionService.crearRecepcion(
                    doc.getEmpresa().getId(),
                    doc.getNumeroGuia(),
                    doc.getNumeroFactura(),
                    doc.getFechaDocumento() != null ? doc.getFechaDocumento() : LocalDate.now(),
                    "Creada automaticamente desde Archivo Historico (carga de datos de prueba, sin conteo fisico real).");

            List<Long> detalleIds = new ArrayList<>();
            List<Integer> rollosRecibidos = new ArrayList<>();
            List<String> observaciones = new ArrayList<>();

            for (LineaParaRecepcion linea : lineas) {
                ProductoExtraido p = linea.producto();
                RecepcionDetalle d = recepcionService.agregarDetalle(
                        recepcion.getId(), linea.articulo().getId(), linea.color().getId(),
                        p.programaTenido(), p.rollos(), p.pesoBrutoKg());
                detalleIds.add(d.getId());
                rollosRecibidos.add(d.getRollosGuia());
                observaciones.add("Carga automatica desde Archivo Historico (sin conteo fisico real)");
            }

            recepcionService.confirmarRecepcion(recepcion.getId(), detalleIds, rollosRecibidos, observaciones);
            doc.setRecepcionCreadaId(recepcion.getId());

            try {
                recepcionService.guardarDocumentoGuiaDesdeArchivo(
                        recepcion.getId(), Paths.get(doc.getRutaArchivo()), doc.getNombreOriginal());
            } catch (Exception eAdjuntar) {
                // La Recepcion ya quedo creada y confirmada correctamente; que no se
                // haya podido adjuntar el PDF a /documentos no debe revertir eso.
                log.warn("Recepcion {} creada, pero no se pudo adjuntar el PDF: {}",
                        recepcion.getId(), eAdjuntar.getMessage());
                String notaAdjunto = "Recepcion creada y confirmada, pero no se pudo adjuntar el PDF a Documentos: " + eAdjuntar.getMessage();
                doc.setObservacion(doc.getObservacion() == null ? notaAdjunto : doc.getObservacion() + "\n" + notaAdjunto);
            }
        } catch (Exception e) {
            log.warn("No se pudo crear/confirmar Recepcion automatica para documento id={}: {}", doc.getId(), e.getMessage());
            String nota = "No se pudo crear la Recepcion automatica: " + e.getMessage();
            doc.setObservacion(doc.getObservacion() == null ? nota : doc.getObservacion() + "\n" + nota);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * El procesamiento corre en un hilo @Async: Spring Security NO propaga
     * el contexto de sesion del usuario que subio el ZIP a ese hilo nuevo
     * (SecurityContextHolder usa ThreadLocal por hilo). Sin esto,
     * UsuarioActualService.obtenerUsuarioActual() (que usa RecepcionService
     * internamente) revienta con NullPointerException porque no hay ningun
     * usuario "logueado" en este hilo. Se establece manualmente usando el
     * usuario que efectivamente subio el ZIP (guardado en subidoPorUsuarioId
     * al momento de la peticion HTTP original, donde si habia sesion).
     */
    private void establecerContextoDeSeguridad(Long usuarioId) {
        if (usuarioId == null) {
            throw new IllegalStateException("No se registro que usuario subio este documento; no se puede crear la Recepcion.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        // Se replica el rol real del usuario (no una lista vacia de authorities):
        // si algun dia se agrega un @PreAuthorize en la cadena que este hilo
        // atraviesa (ej. RecepcionService), un usuario sin su rol real fallaria
        // esa verificacion en silencio en vez de comportarse como en la peticion
        // HTTP original donde si tenia el rol.
        var authority = new org.springframework.security.core.authority.SimpleGrantedAuthority(
                "ROLE_" + usuario.getRol().getNombre());
        var auth = new UsernamePasswordAuthenticationToken(usuario.getUsername(), null, java.util.List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Copia el numero de factura a cada guia ya subida que la factura
     * mencione, siempre que esa guia todavia no tenga numero de factura
     * asignado (para no pisar un dato que ya estaba bien).
     */
    private List<Long> vincularFacturaConGuiasExistentes(DocumentoHistorico factura, List<String> guiasReferenciadas) {
        Set<Long> recepcionesVinculadas = new java.util.LinkedHashSet<>();
        List<DocumentoHistorico> todasLasGuias = documentoHistoricoRepository
                .findByTipoDocumento(DocumentoHistorico.TipoDocumentoHistorico.GUIA);

        for (String numeroGuiaReferenciado : guiasReferenciadas) {
            if (numeroGuiaReferenciado == null || numeroGuiaReferenciado.isBlank()) continue;
            String normalizado = normalizarNumeroGuia(numeroGuiaReferenciado);

            for (DocumentoHistorico guia : todasLasGuias) {
                if (guia.getNumeroGuia() == null) continue;
                if (!normalizarNumeroGuia(guia.getNumeroGuia()).equals(normalizado)) continue;

                if (guia.getNumeroFactura() == null || guia.getNumeroFactura().isBlank()) {
                    guia.setNumeroFactura(factura.getNumeroFactura());
                    documentoHistoricoRepository.save(guia);
                }
                if (guia.getRecepcionCreadaId() != null) {
                    recepcionesVinculadas.add(guia.getRecepcionCreadaId());
                }
            }
        }
        return new ArrayList<>(recepcionesVinculadas);
    }

    /**
     * La factura suele mencionar el numero de guia SIN ceros a la izquierda
     * (ej. "TG01-21376"), mientras que la guia lo guarda CON ceros, tal como
     * lo lee la IA directamente de la guia (ej. "TG01-00021376"). Se
     * normaliza quitando los ceros a la izquierda de la parte numerica para
     * poder compararlos como el mismo numero.
     */
    String normalizarNumeroGuia(String numero) {
        if (numero == null) return "";
        String limpio = numero.trim().toUpperCase();
        int guionIdx = limpio.indexOf('-');
        if (guionIdx == -1) return limpio;
        String prefijo = limpio.substring(0, guionIdx);
        // Auditoria: se quitan TODOS los ceros a la izquierda (igual que el
        // TRIM LEADING '0' del backfill V40), para que la normalizacion Java y la
        // SQL coincidan tambien cuando el sufijo es todo ceros.
        String sufijo = limpio.substring(guionIdx + 1).replaceFirst("^0+", "");
        return prefijo + "-" + sufijo;
    }

    /**
     * Busca si alguna factura ya procesada menciona esta guia en su
     * guias_referenciadas. Devuelve el numero de factura de la primera
     * coincidencia, si existe.
     */
    private Optional<String> buscarFacturaQueYaReferenciaEstaGuia(String numeroGuia) {
        String normalizado = normalizarNumeroGuia(numeroGuia);
        List<DocumentoHistorico> facturas = documentoHistoricoRepository
                .findByTipoDocumento(DocumentoHistorico.TipoDocumentoHistorico.FACTURA);

        for (DocumentoHistorico factura : facturas) {
            if (factura.getGuiasReferenciadas() == null) continue;
            for (String ref : factura.getGuiasReferenciadas().split(",")) {
                if (normalizarNumeroGuia(ref).equals(normalizado)
                        && factura.getNumeroFactura() != null && !factura.getNumeroFactura().isBlank()) {
                    return Optional.of(factura.getNumeroFactura());
                }
            }
        }
        return Optional.empty();
    }

}
