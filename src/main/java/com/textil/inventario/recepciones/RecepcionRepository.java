package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {
    // #9 (auditoria): trae la empresa en el MISMO query (LEFT JOIN) en vez de un
    // SELECT por fila al listar recepciones (lista + reporte muestran empresa.nombre).
    @EntityGraph(attributePaths = "empresa")
    List<Recepcion> findAllByOrderByCreatedAtDesc();
    List<Recepcion> findByEstado(Recepcion.EstadoRecepcion estado);

    // #9 (OSIV off): trae los detalles en el MISMO query. Las vistas de detalle y
    // confirmacion iteran recepcion.detalles al renderizar; con open-in-view
    // apagado, sin este fetch la sesion ya estaria cerrada -> LazyInitException.
    @EntityGraph(attributePaths = "detalles")
    java.util.Optional<Recepcion> findWithDetallesById(Long id);
    List<Recepcion> findByNumeroFacturaIsNullOrderByFechaGuiaDesc();
    java.util.Optional<Recepcion> findFirstByNumeroGuia(String numeroGuia);
}
