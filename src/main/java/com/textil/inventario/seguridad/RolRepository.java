package com.textil.inventario.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Long> {
    Optional<Rol> findByNombre(String nombre);

    // Roles ordenados por poder (mayor -> menor), para mostrarlos consistentes
    // en la UI sin depender del id. Ver Rol.jerarquia / V36__roles_jerarquia.
    List<Rol> findAllByOrderByJerarquiaAsc();
}
