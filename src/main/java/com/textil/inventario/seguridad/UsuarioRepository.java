package com.textil.inventario.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    boolean existsByUsername(String username);

    /**
     * Celulares de los destinatarios de las alertas de stock bajo: usuarios
     * ACTIVOS, no de prueba, con rol ADMIN o GERENTE y celular cargado. El
     * SUPERADMIN (proveedor) queda afuera a propósito.
     */
    @Query("SELECT DISTINCT u.celular FROM Usuario u " +
           "WHERE u.activo = true AND u.esPrueba = false " +
           "AND u.celular IS NOT NULL AND u.celular <> '' " +
           "AND u.rol.nombre IN ('ADMIN','GERENTE')")
    List<String> celularesDeAdminYGerente();
}
