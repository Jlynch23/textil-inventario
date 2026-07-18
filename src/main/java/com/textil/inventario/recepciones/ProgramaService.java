package com.textil.inventario.recepciones;

import com.textil.inventario.catalogo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProgramaService {

    private final ProgramaRepository programaRepository;
    private final ProgramaDetalleRepository programaDetalleRepository;
    private final RecepcionDetalleRepository recepcionDetalleRepository;
    private final RecepcionDocumentoRepository recepcionDocumentoRepository;
    private final EmpresaRepository empresaRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final TituloRepository tituloRepository;
    private final ColorRepository colorRepository;
    private final ComposicionRepository composicionRepository;
    private final CatalogoService catalogoService;

    // Normaliza a mayusculas (recorta espacios), igual que en Catalogo y
    // Recepciones, para mantener consistencia aunque el numero de programa
    // hoy en la practica solo tenga digitos.
    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? valor : valor.trim().toUpperCase();
    }

    public List<Programa> listarProgramas() {
        return programaRepository.findAllByOrderByFechaDesc();
    }

    public Programa buscarPrograma(Long id) {
        return programaRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Programa crearPrograma(String numero, Long empresaId, LocalDate fecha, String observaciones,
                                   Integer totalRollos,
                                   List<Long> tipoTelaIds, List<Long> tituloIds, List<Long> composicionIds,
                                   List<Long> colorIds, List<Integer> cantidades) {
        int suma = cantidades.stream().mapToInt(c -> c != null ? c : 0).sum();
        if (totalRollos == null || !totalRollos.equals(suma)) {
            throw new IllegalArgumentException(
                    "El total de rollos ingresado (" + (totalRollos != null ? totalRollos : 0) +
                    ") no coincide con la suma de las cantidades de las líneas (" + suma + ").");
        }

        Programa p = new Programa();
        p.setNumero(normalizar(numero));
        p.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        p.setFecha(fecha);
        p.setObservaciones(observaciones);
        p.setTotalRollos(totalRollos);
        p = programaRepository.save(p);

        for (int i = 0; i < cantidades.size(); i++) {
            if (tipoTelaIds.get(i) == null || tituloIds.get(i) == null
                    || composicionIds.get(i) == null || colorIds.get(i) == null) continue;

            Articulo articulo = resolverOCrearArticulo(tipoTelaIds.get(i), tituloIds.get(i), composicionIds.get(i));
            Color color = colorRepository.findById(colorIds.get(i)).orElseThrow();

            ProgramaDetalle pd = new ProgramaDetalle();
            pd.setPrograma(p);
            pd.setArticulo(articulo);
            pd.setColor(color);
            pd.setCantidadSolicitada(cantidades.get(i));
            pd.setCantidadRecibida(0);
            programaDetalleRepository.save(pd);
        }

        return p;
    }

    /**
     * Un programa puede pedir una combinacion tipo de tela + titulo +
     * composicion que todavia no exista como Articulo en el catalogo (ej.
     * una composicion nueva para este programa). Se reutiliza la misma
     * logica de generacion de codigo interno que ARQ-02 (CatalogoService),
     * en vez de duplicarla aqui. El Color ya no forma parte del Articulo
     * (ver V26): se resuelve/asigna por separado a nivel de ProgramaDetalle.
     */
    private Articulo resolverOCrearArticulo(Long tipoTelaId, Long tituloId, Long composicionId) {
        Optional<Articulo> existente = catalogoService.buscarArticuloPorCombinacion(tipoTelaId, tituloId, composicionId);
        if (existente.isPresent()) return existente.get();

        TipoTela tipoTela = tipoTelaRepository.findById(tipoTelaId).orElseThrow();
        Titulo titulo = tituloRepository.findById(tituloId).orElseThrow();
        Composicion composicion = composicionRepository.findById(composicionId).orElseThrow();

        Articulo nuevo = new Articulo();
        nuevo.setTipoTela(tipoTela);
        nuevo.setTitulo(titulo);
        nuevo.setComposicion(composicion);
        nuevo.setCodigoInterno(catalogoService.generarCodigoInterno(tipoTela, titulo, composicion));
        nuevo.setActivo(true);
        return catalogoService.guardarArticulo(nuevo);
    }

    /**
     * Actualiza los datos generales del programa, la cantidad solicitada de
     * lineas existentes, elimina las lineas marcadas (falla con
     * DataIntegrityViolationException si alguna ya tiene recepciones
     * vinculadas, protegiendo esa trazabilidad) y agrega lineas nuevas.
     * No permite cambiar el articulo (tipo/titulo/color) de una linea ya
     * existente, solo su cantidad, para no romper vinculos ya hechos con
     * recepciones reales. Valida que totalRollos coincida con la suma de
     * TODAS las cantidades finales (existentes que quedan + nuevas).
     * Bloquea la edicion por completo si el programa ya esta completo
     * (todas sus lineas recibieron la cantidad solicitada), como defensa
     * adicional al bloqueo que ya existe en el Controller a nivel de GET.
     */
    @Transactional
    public void actualizarPrograma(Long programaId, String numero, Long empresaId, LocalDate fecha, String observaciones,
                                    Integer totalRollos,
                                    List<Long> detalleIdsExistentes, List<Integer> cantidadesExistentes,
                                    List<Long> detalleIdsAEliminar,
                                    List<Long> nuevosTipoTelaIds, List<Long> nuevosTituloIds,
                                    List<Long> nuevosComposicionIds, List<Long> nuevosColorIds,
                                    List<Integer> nuevasCantidades) {

        Programa p = programaRepository.findById(programaId).orElseThrow();
        if (p.isCompleto()) {
            throw new IllegalArgumentException("Este programa ya está completo y no se puede editar.");
        }

        int sumaExistentes = cantidadesExistentes.stream().mapToInt(c -> c != null ? c : 0).sum();
        int sumaNuevas = nuevasCantidades.stream().mapToInt(c -> c != null ? c : 0).sum();
        int sumaTotal = sumaExistentes + sumaNuevas;
        if (totalRollos == null || !totalRollos.equals(sumaTotal)) {
            throw new IllegalArgumentException(
                    "El total de rollos ingresado (" + (totalRollos != null ? totalRollos : 0) +
                    ") no coincide con la suma de las cantidades de las líneas (" + sumaTotal + ").");
        }

        p.setNumero(normalizar(numero));
        p.setEmpresa(empresaRepository.findById(empresaId).orElseThrow());
        p.setFecha(fecha);
        p.setObservaciones(observaciones);
        p.setTotalRollos(totalRollos);
        programaRepository.save(p);

        for (int i = 0; i < detalleIdsExistentes.size(); i++) {
            ProgramaDetalle pd = programaDetalleRepository.findById(detalleIdsExistentes.get(i)).orElseThrow();
            pd.setCantidadSolicitada(cantidadesExistentes.get(i));
            programaDetalleRepository.save(pd);
        }

        for (Long detalleId : detalleIdsAEliminar) {
            programaDetalleRepository.deleteById(detalleId);
        }

        for (int i = 0; i < nuevasCantidades.size(); i++) {
            if (nuevosTipoTelaIds.get(i) == null || nuevosTituloIds.get(i) == null
                    || nuevosComposicionIds.get(i) == null || nuevosColorIds.get(i) == null) continue;
            Articulo articulo = resolverOCrearArticulo(nuevosTipoTelaIds.get(i), nuevosTituloIds.get(i), nuevosComposicionIds.get(i));
            Color color = colorRepository.findById(nuevosColorIds.get(i)).orElseThrow();

            ProgramaDetalle pd = new ProgramaDetalle();
            pd.setPrograma(p);
            pd.setArticulo(articulo);
            pd.setColor(color);
            pd.setCantidadSolicitada(nuevasCantidades.get(i));
            pd.setCantidadRecibida(0);
            programaDetalleRepository.save(pd);
        }
    }

    public List<RecepcionDetalle> historialDeLinea(Long programaDetalleId) {
        return recepcionDetalleRepository.findByProgramaDetalleId(programaDetalleId);
    }

    /**
     * Vista simplificada del historial de una linea para la pantalla de
     * seguimiento: por cada recepcion que aporto a esta linea, incluye el
     * id del RecepcionDocumento tipo GUIA (si el PDF fue subido) para poder
     * mostrar un link directo al visor de PDF (/documentos/{id}/ver) en vez
     * de mandar a la pantalla de detalle de la recepcion.
     */
    public record HistorialGuiaView(Long recepcionId, String numeroGuia, Long documentoId) {}

    public List<HistorialGuiaView> historialDeLineaConDocumento(Long programaDetalleId) {
        List<RecepcionDetalle> detalles = recepcionDetalleRepository.findByProgramaDetalleId(programaDetalleId);
        return detalles.stream().map(rd -> {
            Long docId = recepcionDocumentoRepository.findByRecepcionId(rd.getRecepcion().getId()).stream()
                    .filter(d -> "GUIA".equals(d.getTipoDocumento()))
                    .map(RecepcionDocumento::getId)
                    .findFirst()
                    .orElse(null);
            return new HistorialGuiaView(rd.getRecepcion().getId(), rd.getRecepcion().getNumeroGuia(), docId);
        }).toList();
    }
}
