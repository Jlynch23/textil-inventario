package com.textil.inventario.recepciones;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProgramaRepository extends JpaRepository<Programa, Long> {
    Optional<Programa> findByNumero(String numero);
    List<Programa> findAllByOrderByFechaDesc();
}
