package com.textil.inventario.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Resuelve los principales sintéticos de la vista previa por rol
 * ({@code vista:GERENTE}). Lo consume ÚNICAMENTE el SwitchUserFilter que arma
 * SecurityConfig -- no está cableado al AuthenticationManager del formulario de
 * login, así que estos principales no son credenciales: no hay forma de
 * autenticarse "como" uno de ellos desde afuera.
 *
 * <p>Aun así la contraseña se genera ALEATORIA en cada llamada, en vez de dejar
 * una constante. No es paranoia gratuita: si algún día alguien cablea este
 * servicio al login por comodidad, una constante seria una puerta trasera con
 * clave conocida; un valor aleatorio de 32 bytes que nadie guarda, no.
 */
@Service
@RequiredArgsConstructor
public class VistaPreviaRolUserDetailsService implements UserDetailsService {

    private final RolRepository rolRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Falta el rol a previsualizar");
        }

        // Se acepta tanto "GERENTE" (lo que manda el formulario) como el username
        // sintetico completo "vista:GERENTE". Lo que impide apuntar esto a una
        // PERSONA no es el prefijo sino la comprobacion de mas abajo: el valor
        // tiene que ser un ROL existente, y un username nunca lo es. Este servicio
        // no consulta la tabla usuarios en ningun caso.
        final String rol = (username.startsWith(VistaPreviaRol.PREFIJO)
                ? username.substring(VistaPreviaRol.PREFIJO.length())
                : username).trim().toUpperCase();

        if (VistaPreviaRol.ROL_EXCLUIDO.equals(rol)) {
            throw new UsernameNotFoundException("No se previsualiza " + VistaPreviaRol.ROL_EXCLUIDO);
        }

        // El rol tiene que EXISTIR en la tabla. Si no, cualquier texto se
        // convertiria en una authority ROLE_<loquesea>: inofensiva hoy (ninguna
        // regla la nombra), pero es la clase de cabo suelto que despues alguien
        // usa sin querer al agregar un hasRole nuevo.
        boolean existe = rolRepository.findAllByOrderByJerarquiaAsc().stream()
                .anyMatch(r -> rol.equalsIgnoreCase(r.getNombre()));
        if (!existe) {
            throw new UsernameNotFoundException("Rol inexistente: " + rol);
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String passwordInutilizable = "{noop-jamas}" + Base64.getEncoder().encodeToString(bytes);

        return new org.springframework.security.core.userdetails.User(
                VistaPreviaRol.usernameDe(rol),
                passwordInutilizable,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol))
        );
    }

    /** Roles ofrecibles en la vista previa: todos los activos menos SUPERADMIN. */
    public List<String> rolesPrevisualizables() {
        return rolRepository.findAllByOrderByJerarquiaAsc().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActivo()))
                .map(Rol::getNombre)
                .filter(n -> n != null && !VistaPreviaRol.ROL_EXCLUIDO.equalsIgnoreCase(n))
                .toList();
    }
}
