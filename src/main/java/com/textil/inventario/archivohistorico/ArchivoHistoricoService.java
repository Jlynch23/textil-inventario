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
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ArticuloRepository articuloRepository;
    private final AnthropicOcrService ocrService;
    private final RecepcionService recepcionService;
    private final UsuarioRepository usuarioRepository;
    private final com.textil.inventario.catalogo.CatalogoService catalogoService;
    private final DocumentoHistoricoClasificador clasificador;
    private final DocumentoHistoricoFileManager fileManager;
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
    public int subirZip(MultipartFile zip, boolean crearRecepcionAutomatica, Long usuarioId) throws IOException {
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

                DocumentoHistorico.TipoDocumentoHistorico tipo = clasificador.detectarTipo(nombreEntrada);
                Empresa empresa = clasificador.detectarEmpresaPorRuta(nombreEntrada, empresas);

                String nombreArchivo = Paths.get(nombreEntrada).getFileName().toString();
                Path carpetaDestino = Paths.get(rutaBase, "HistoricoImportado",
                        tipo.name(),
                        empresa != null ? empresa.getCarpeta() : "SinIdentificar");
                Files.createDirectories(carpetaDestino);

                String nombreGuardado = clasificador.evitarColision(nombreArchivo, nombresUsados);
                Path rutaCompleta = carpetaDestino.resolve(nombreGuardado);
                Files.write(rutaCompleta, contenido);

                DocumentoHistorico doc = new DocumentoHistorico();
                doc.setEmpresa(empresa);
                doc.setTipoDocumento(tipo);
                doc.setNombreOriginal(nombreArchivo);
                doc.setRutaRelativaZip(nombreEntrada);
                doc.setRutaArchivo(rutaCompleta.toString());
                doc.setEstadoProceso(DocumentoHistorico.EstadoProceso.PENDIENTE);
                doc.setCrearRecepcionAutomatica(crearRecepcionAutomatica);
                doc.setSubidoPorUsuarioId(usuarioId);
                documentoHistoricoRepository.save(doc);

                contador++;
            }
        }

        return contador;
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
            byte[] bytes = Files.readAllBytes(Paths.get(doc.getRutaArchivo()));
            String razonSocialDetectada;

            if (doc.getTipoDocumento() == DocumentoHistorico.TipoDocumentoHistorico.FACTURA) {
                ExtraccionFacturaResponse r = ocrService.extraerDatosFactura(bytes);
                doc.setNumeroFactura(r.numeroFactura());
                doc.setFechaDocumento(clasificador.parseFecha(r.fechaFactura()));
                doc.setRazonSocialDetectada(r.razonSocialDetectada());
                razonSocialDetectada = r.razonSocialDetectada();
                if (r.advertencia() != null) doc.setObservacion(r.advertencia());

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
                        EnriquecimientoResultado resultado = intentarEnriquecerCatalogo(p);
                        coloresCreados += resultado.colorCreado();
                        articulosCreados += resultado.articuloCreado();
                        if (resultado.articulo() != null) {
                            lineasParaRecepcion.add(new LineaParaRecepcion(resultado.articulo(), p));
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
        documentoHistoricoRepository.save(doc);
    }

    private record LineaParaRecepcion(Articulo articulo, ProductoExtraido producto) {}

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
                        recepcion.getId(), linea.articulo().getId(),
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
        var auth = new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, java.util.List.of());
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
        String sufijo = limpio.substring(guionIdx + 1).replaceFirst("^0+(?=\\d)", "");
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

    private record EnriquecimientoResultado(Articulo articulo, int colorCreado, int articuloCreado) {}

    /**
     * Resuelve (o crea si hace falta) el Articulo correspondiente al producto
     * leido, para poder usarlo tanto en el enriquecimiento de catalogo como
     * en la creacion de la Recepcion automatica. NUNCA toca stock_actual ni
     * kardex_movimientos directamente (eso solo pasa si crearYConfirmarRecepcionAutomatica
     * termina llamando a RecepcionService, que es el unico camino permitido).
     */
    private EnriquecimientoResultado intentarEnriquecerCatalogo(ProductoExtraido p) {
        if (p.tipoTela() == null || p.titulo() == null || p.colorCodigo() == null) {
            return new EnriquecimientoResultado(null, 0, 0);
        }

        Optional<TipoTela> tipoTela = tipoTelaRepository.findByNombreIgnoreCase(p.tipoTela().trim());
        if (tipoTela.isEmpty()) return new EnriquecimientoResultado(null, 0, 0);

        Optional<Titulo> titulo = tituloRepository.findByValorIgnoreCase(p.titulo().trim());
        if (titulo.isEmpty()) return new EnriquecimientoResultado(null, 0, 0);

        int colorCreado = 0;
        Optional<Color> colorOpt = catalogoService.resolverColorPorCodigo(p.colorCodigo().trim(), p.colorNombre());
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
                    return new EnriquecimientoResultado(null, 0, 0);
                }
            }
        }

        int articuloCreado = 0;
        Articulo articulo;
        Optional<Articulo> articuloOpt = articuloRepository.findByTipoTelaIdAndTituloIdAndColorId(
                tipoTela.get().getId(), titulo.get().getId(), color.getId());
        if (articuloOpt.isPresent()) {
            articulo = articuloOpt.get();
        } else {
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
                articulo = articuloRepository.save(nuevo);
                articuloCreado = 1;
            } catch (Exception e) {
                // no se pudo crear el articulo
                return new EnriquecimientoResultado(null, 0, 0);
            }
        }

        return new EnriquecimientoResultado(articulo, colorCreado, articuloCreado);
    }
}
