package com.textil.inventario.programas;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProgramaRepository extends JpaRepository<Programa, Long> {
    Optional<Programa> findByNumero(String numero);
    List<Programa> findAllByOrderByFechaDesc();

    // #9 (OSIV off): trae las lineas (detalles) junto con el programa. Las vistas
    // de seguimiento/edicion iteran programa.detalles al renderizar, y el
    // controlador tambien las recorre; sin este fetch reventarian con open-in-view
    // apagado (sesion ya cerrada). type = LOAD para que empresa (EAGER) NO se
    // degrade a proxy perezoso (el grafo FETCH por defecto la volveria LAZY).
    @EntityGraph(attributePaths = "detalles", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Programa> findWithDetallesById(Long id);

    // Listado cronologico: del mas antiguo al mas nuevo. Si dos programas son
    // del mismo dia, se desempata por numero de guia de forma NUMERICA (no de
    // texto): se ordena primero por largo y luego por valor, asi "99" va antes
    // que "100" aunque el numero se guarde como String (en la practica solo
    // tiene digitos, ver ProgramaService.normalizar).
    // #9 (auditoria): trae la empresa junto con el programa (LEFT JOIN) para no
    // disparar un SELECT por fila al listar (la lista muestra empresa.nombre).
    @EntityGraph(attributePaths = "empresa")
    @Query("SELECT p FROM Programa p ORDER BY p.fecha ASC, LENGTH(p.numero) ASC, p.numero ASC")
    List<Programa> findAllOrdenadoCronologico();

    // --- Listado filtrado (empresa / estado / número) -----------------------
    //
    // El filtro se resuelve EN LA CONSULTA, no en memoria, y esa es la razón de
    // ser de estas dos consultas. El listado no solo trae los programas: para
    // pintar la barra de progreso hay que recorrer TODAS las líneas de cada uno
    // (ver ProgramaService.listarProgramas). Filtrando después de traer, cada
    // visita a la pantalla cargaría el historial completo aunque se muestren
    // tres programas.
    //
    // "Completo" no es una columna: es que el programa tenga al menos una línea
    // y ninguna con cantidad_recibida < cantidad_solicitada — la misma regla que
    // Programa.isCompleto() y ProgramaDetalle.isCompleto(), acá en SQL. Un
    // programa SIN líneas no es completo (está a medio cargar), así que cae en
    // "en proceso" y no desaparece de la vista.
    String FILTRO_LISTADO = """
             WHERE (:empresaId IS NULL OR p.empresa.id = :empresaId)
               AND (:numero IS NULL OR p.numero LIKE CONCAT('%', :numero, '%'))
               AND (:soloEnProceso = FALSE
                    OR (SELECT COUNT(dp) FROM ProgramaDetalle dp
                        WHERE dp.programa = p AND dp.cantidadRecibida < dp.cantidadSolicitada) > 0
                    OR (SELECT COUNT(dv) FROM ProgramaDetalle dv WHERE dv.programa = p) = 0)
               AND (:soloCompletos = FALSE
                    OR ((SELECT COUNT(dc) FROM ProgramaDetalle dc
                         WHERE dc.programa = p AND dc.cantidadRecibida < dc.cantidadSolicitada) = 0
                        AND (SELECT COUNT(dz) FROM ProgramaDetalle dz WHERE dz.programa = p) > 0))
            """;

    @EntityGraph(attributePaths = "empresa")
    @Query("SELECT p FROM Programa p" + FILTRO_LISTADO
           + " ORDER BY p.fecha ASC, LENGTH(p.numero) ASC, p.numero ASC")
    List<Programa> buscarConFiltros(@Param("empresaId") Long empresaId,
                                    @Param("numero") String numero,
                                    @Param("soloEnProceso") boolean soloEnProceso,
                                    @Param("soloCompletos") boolean soloCompletos);

    // Para los contadores de los chips: cuenta sin traer ni una entidad.
    @Query("SELECT COUNT(p) FROM Programa p" + FILTRO_LISTADO)
    long contarConFiltros(@Param("empresaId") Long empresaId,
                          @Param("numero") String numero,
                          @Param("soloEnProceso") boolean soloEnProceso,
                          @Param("soloCompletos") boolean soloCompletos);
}
