package com.textil.inventario.auditoria;

import com.textil.inventario.seguridad.Usuario;
import com.textil.inventario.seguridad.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final LogEventoRepository logEventoRepository;
    private final UsuarioRepository usuarioRepository;

    public void registrar(String accion, String entidad, Long entidadId, String descripcion) {
        try {
            Usuario usuario = obtenerUsuarioActual();

            LogEvento log = new LogEvento();
            log.setUsuario(usuario);
            log.setAccion(accion);
            log.setEntidad(entidad);
            log.setEntidadId(entidadId);
            log.setDescripcion(descripcion);
            logEventoRepository.save(log);
        } catch (Exception e) {
            // El logging nunca debe romper la operacion principal
            System.err.println("Error al registrar log: " + e.getMessage());
        }
    }

    public void registrarLogin(String email) {
        try {
            Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
            LogEvento log = new LogEvento();
            log.setUsuario(usuario);
            log.setAccion("LOGIN");
            log.setDescripcion("Inicio de sesion: " + email);
            logEventoRepository.save(log);
        } catch (Exception e) {
            System.err.println("Error al registrar login: " + e.getMessage());
        }
    }

    private Usuario obtenerUsuarioActual() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            return usuarioRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
