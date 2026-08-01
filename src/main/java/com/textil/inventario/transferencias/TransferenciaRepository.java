package com.textil.inventario.transferencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    // #9 (auditoria): trae la ubicacion de origen en el mismo query. Las demas
    // to-one de Transferencia (los 3 usuarios) pasaron a LAZY y no se listan, asi
    // que ya no disparan un SELECT por fila.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "ubicacionOrigen")
    List<Transferencia> findAllByOrderByFechaSolicitudDesc();

    // #9 (OSIV off): el dashboard muestra t.ubicacionOrigen.nombre de las
    // transferencias en transito; ubicacionOrigen es LAZY, asi que se trae en el
    // mismo query para no reventar al renderizar con open-in-view apagado.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "ubicacionOrigen")
    List<Transferencia> findByEstado(Transferencia.EstadoTransferencia estado);

    // #9 (OSIV off): trae ubicacionOrigen (LAZY) y las lineas (detalles) junto con
    // la transferencia. Las vistas de detalle/confirmacion navegan ambas al
    // renderizar; sin este fetch, con la sesion cerrada, darian LazyInitException.
    // type = LOAD: ambas asociaciones van listadas (se cargan), y las EAGER que no
    // liste no se degradan a proxy (que es lo que hace el grafo FETCH por defecto).
    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"ubicacionOrigen", "detalles"},
            type = org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD)
    java.util.Optional<Transferencia> findWithDetallesById(Long id);

    // A5: el numero se deriva del MAXIMO existente, NO de count(). Con count(),
    // borrar una transferencia BORRADOR intermedia bajaba el contador y el
    // siguiente alta reusaba un TRF-NNNNNN ya existente -> viola el UNIQUE.
    @Query("SELECT MAX(t.numero) FROM Transferencia t")
    String findMaxNumero();
}
