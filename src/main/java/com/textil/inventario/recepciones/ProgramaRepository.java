package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProgramaRepository extends JpaRepository<Programa, Long> {
    Optional<Programa> findByNumero(String numero);
    List<Programa> findAllByOrderByFechaDesc();

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
}
