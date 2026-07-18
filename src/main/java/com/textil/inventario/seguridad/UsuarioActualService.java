package com.textil.inventario.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resuelve el {@link Usuario} autenticado en la sesión actual a partir del
 * contexto de seguridad de Spring.
 * <p>
 * Reemplaza el antipatrón detectado en la auditoría (ver AUDIT.md, hallazgo BUG-01)
 * donde varios servicios usaban {@code usuarioRepository.findById(1L)} como
 * "usuario actual" hardcodeado. Eso atribuía TODAS las transferencias y
 * recepciones creadas al usuario con id=1, sin importar quién había iniciado
 * sesión realmente, invalidando la trazabilidad de auditoría del sistema
 * (quién solicitó/confirmó cada movimiento de stock).
 */
@Service
@RequiredArgsConstructor
public class UsuarioActualService {

    private final UsuarioRepository usuarioRepository;

    /**
     * @return el usuario autenticado actual.
     * @throws java.util.NoSuchElementException si no hay sesión autenticada o el
     *         usuario del token ya no existe en base de datos.
     */
    public Usuario obtenerUsuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByUsername(username).orElseThrow();
    }

    /**
     * Variante tolerante a fallos, pensada para logging/auditoría best-effort
     * donde no queremos que un problema al resolver el usuario rompa la
     * operación principal.
     */
    public Usuario obtenerUsuarioActualOrNull() {
        try {
            return obtenerUsuarioActual();
        } catch (Exception e) {
            return null;
        }
    }
}
