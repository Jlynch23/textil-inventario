package com.textil.inventario.transferencias;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CorrelativoRepository extends JpaRepository<Correlativo, String> {

    // #6 (auditoria): SELECT ... FOR UPDATE sobre la fila del correlativo. Dentro
    // de la transaccion de crearTransferencia, serializa a los concurrentes: el
    // segundo espera a que el primero confirme antes de leer el valor, asi cada
    // uno reserva un numero distinto (sin colision contra el UNIQUE).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Correlativo c WHERE c.nombre = :nombre")
    Optional<Correlativo> bloquearPorNombre(@Param("nombre") String nombre);
}
