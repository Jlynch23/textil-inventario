package com.textil.inventario.config;

import com.textil.inventario.seguridad.UsuarioDetailsService;
import com.textil.inventario.auditoria.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.core.annotation.Order;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;
    private final AuditLogService auditLogService;

    // Clave que firma la cookie "recordar sesion" (remember-me). DEBE ser
    // estable entre reinicios/despliegues: si cambia, todas las cookies
    // persistentes se invalidan y los usuarios (sobre todo en el celular)
    // quedan deslogueados. Es OBLIGATORIA y única por instancia, SIN default:
    // el arranque falla si falta o está vacía (A2), para no firmar las cookies
    // con una clave pública igual en todas las copias.
    @org.springframework.beans.factory.annotation.Value("${app.remember-me-key}")
    private String rememberMeKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // R-A1: registro de sesiones activas por principal. Lo consume GestorSesiones
    // para expirar en el acto las sesiones de un usuario que se inactiva/degrada.
    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    // Propaga el ciclo de vida de la HttpSession al SessionRegistry (necesario
    // para que las sesiones destruidas se limpien del registro).
    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    // #CSS (dev): cadena de seguridad DEDICADA para los estaticos, evaluada
    // PRIMERO (@Order(1)). Spring Security, por defecto, estampa en TODA respuesta
    // (incluidos /webjars/, /css/, /js/, /img/) las cabeceras anti-cache
    // "Cache-Control: no-cache, no-store, must-revalidate" + "Pragma: no-cache" +
    // "Expires: 0". Sobre los estaticos eso es un bug de rendimiento y causaba el
    // parpadeo/estilos-sin-cargar en las paginas autenticadas (el navegador no
    // podia reutilizar el CSS/JS y a veces lo servia a medias hasta un Ctrl+Shift+R).
    // Esta cadena los deja PUBLICOS y CACHEABLES, sin CSRF (son GET estaticos) y
    // sin las cabeceras no-store. El resto de la app sigue con la cadena @Order(2).
    @Bean
    @Order(1)
    public SecurityFilterChain estaticosFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/webjars/**", "/css/**", "/js/**", "/img/**", "/fonts/**", "/favicon.ico")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                // Quita el no-store por defecto y deja que el ResourceHttpRequestHandler
                // ponga su propio Cache-Control (cacheable). nosniff se mantiene.
                .cacheControl(cache -> cache.disable()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // A2 (auditoria): la clave de firma del remember-me DEBE ser un secreto
        // único por instancia. Se valida presente y no vacía en el arranque:
        // antes caía a un default público del repo y todas las copias firmaban
        // las cookies con la misma clave.
        if (rememberMeKey == null || rememberMeKey.isBlank()) {
            throw new IllegalStateException(
                "REMEMBER_ME_KEY es obligatoria (clave única y secreta por instancia). " +
                "Generala con: openssl rand -hex 32");
        }
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/logout", "/css/**", "/js/**", "/img/**").permitAll()
                // M11: Bootstrap e iconos servidos localmente (webjars). Deben ser
                // publicos como el resto de estaticos (los pide el navegador sin sesion).
                .requestMatchers("/webjars/**").permitAll()
                // Archivos de la PWA (app movil instalable). El service worker
                // DEBE servirse desde la raiz para controlar todo el origen, y el
                // manifest/offline los pide el navegador sin sesion.
                .requestMatchers("/manifest.webmanifest", "/sw.js", "/offline.html", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                // JERARQUIA (de mayor a menor):
                //   SUPERADMIN (proveedor)  > ADMIN (dueño-cliente)
                //     > GERENTE / SUPERVISOR / VENDEDOR
                // El default (anyRequest) es ADMIN+SUPERADMIN: el ADMIN controla
                // todo su negocio, salvo lo que se RESERVA explicitamente al
                // proveedor mas abajo. SUPERADMIN es una cuenta oculta de soporte.

                // --- Autoservicio: cualquier usuario autenticado gestiona SU
                // propia cuenta (cambiar su contraseña). Va PRIMERO que /usuarios/**.
                .requestMatchers("/usuarios/mi-cuenta", "/usuarios/cambiar-mi-password").authenticated()

                // --- RESERVADO SOLO AL PROVEEDOR (SUPERADMIN) ---
                // Reporte de Errores del Sistema (diagnostico tecnico/OCR). Debe ir
                // ANTES del anyRequest ADMIN+SUPERADMIN para que el ADMIN no lo alcance.
                .requestMatchers("/reportes/errores").hasRole("SUPERADMIN")
                // La gestion de cuentas SUPERADMIN no se restringe por URL sino
                // DENTRO de UsuarioController: el ADMIN entra a /usuarios pero no
                // ve, ni crea, ni toca cuentas SUPERADMIN (quedan ocultas).
                .requestMatchers("/usuarios/**").hasAnyRole("ADMIN", "SUPERADMIN")

                // --- Almacen (operacion movil del SUPERVISOR) ---
                // La cola de revision la aprueba el dueño (ADMIN) o el proveedor.
                .requestMatchers("/almacen/revision/**").hasAnyRole("ADMIN", "SUPERADMIN")
                .requestMatchers("/almacen/**").hasAnyRole("SUPERVISOR", "ADMIN", "SUPERADMIN")

                // GERENTE: solo lectura (GET) en las areas operativas relevantes.
                // Antes de la regla general de lectura, se bloquean las paginas de
                // creacion/edicion (aunque sean GET, son puntos de entrada a una
                // escritura) dejandolas para ADMIN+SUPERADMIN. Estos mismos metodos
                // llevan ademas @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')") en
                // el controlador (defensa en profundidad): su proteccion NO depende
                // de que estas lineas queden antes de la regla amplia de abajo.
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/recepciones/nueva", "/recepciones/facturar", "/recepciones/*/confirmar",
                        "/programas/nuevo", "/programas/*/editar",
                        "/transferencias/nueva", "/transferencias/*/confirmar-salida", "/transferencias/*/confirmar-llegada",
                        "/catalogo/ubicaciones",
                        "/catalogo/empresas", "/catalogo/tipos-tela", "/catalogo/titulos",
                        "/catalogo/composiciones", "/catalogo/acabados"
                ).hasAnyRole("ADMIN", "SUPERADMIN")
                // Lectura amplia para GERENTE (mas ADMIN y SUPERADMIN). Ojo: NO se
                // incluyen /reportes/** ni /log/** aqui -- esos caen al anyRequest
                // ADMIN+SUPERADMIN, asi que GERENTE nunca los alcanza (se mantiene
                // su restriccion historica).
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/", "/dashboard",
                        "/inventario/**", "/catalogo/**", "/programas/**",
                        "/documentos/**", "/recepciones/**", "/transferencias/**"
                ).hasAnyRole("GERENTE", "ADMIN", "SUPERADMIN")
                // Descarga de documentos en zip: es solo lectura (igual que ver/descargar
                // individual, ya permitido arriba a GERENTE), aunque tecnicamente sea POST
                // porque manda una lista de ids en el body. Sin esta regla quedaria
                // silenciosamente bloqueada para GERENTE por el anyRequest() de abajo.
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/documentos/descargar-zip"
                ).hasAnyRole("GERENTE", "ADMIN", "SUPERADMIN")
                .anyRequest().hasAnyRole("ADMIN", "SUPERADMIN")
            )
            // Registro de sesiones (auditoria red-team R-A1): sin esto, inactivar,
            // degradar o resetear a un usuario NO cortaba su sesion viva -- las
            // authorities se cachean en la HttpSession (8h) y solo se releen al
            // loguear. Con el SessionRegistry, UsuarioController puede expirar las
            // sesiones del usuario afectado (SessionInformation.expireNow) y el
            // ConcurrentSessionFilter la invalida en su siguiente request.
            // maximumSessions(-1) = sin limite de sesiones, solo se usa el registro.
            .sessionManagement(session -> session
                // UX tras un deploy/reinicio: la sesion vive en memoria, asi que al
                // reiniciar la app muere y el navegador manda una cookie invalida. En
                // vez del crudo "This session has been expired (concurrent logins)",
                // se redirige LIMPIO al login. El remember-me (que sobrevive reinicios)
                // suele re-loguear solo antes de llegar aca; si no, el usuario cae en
                // una pagina de login normal, no en un mensaje tecnico de error.
                .invalidSessionUrl("/login?expirado")
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry())
                // Mismo trato para la expiracion via SessionRegistry (R-A1: cuando el
                // ADMIN inactiva/degrada a un usuario y se corta su sesion): login limpio.
                .expiredUrl("/login?expirado")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            // "Recordar sesion": cookie persistente (con Max-Age) que re-autentica
            // sola cuando la sesion de servidor caduca o cuando el celular
            // suspende/mata la PWA y descarta la cookie de sesion JSESSIONID (que
            // no tiene Max-Age). Sin esto, el almacenero/vendedor tenia que volver
            // a loguearse cada vez que reabria la app en el movil. alwaysRemember
            // la activa para todo login (no hace falta un checkbox). El token
            // incluye el hash de la password y la 'key' estable: si el usuario
            // cambia su clave, la cookie deja de valer.
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .alwaysRemember(true)
                .tokenValiditySeconds(60 * 60 * 24 * 30) // 30 dias
                .userDetailsService(usuarioDetailsService)
            )
            .logout(logout -> logout
                // Auditoria: logout por POST (con token CSRF), no GET. Con GET, una
                // pagina externa con <img src=".../logout"> podia cerrarte la sesion
                // sin tu intervencion (CSRF de logout). El POST + CSRF lo impide.
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessHandler(logoutSuccessHandler())
                // El cierre de sesion explicito SI borra la cookie persistente
                // (remember-me) ademas de invalidar la sesion.
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public org.springframework.security.web.authentication.logout.LogoutSuccessHandler logoutSuccessHandler() {
        org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler delegate =
                new org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler();
        delegate.setDefaultTargetUrl("/login?logout=true");
        return (request, response, authentication) -> {
            if (authentication != null) {
                auditLogService.registrarLogout(authentication.getName());
            }
            delegate.onLogoutSuccess(request, response, authentication);
        };
    }

    @Bean
    public org.springframework.security.web.authentication.AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            boolean esSupervisor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
            boolean esSuperadmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));

            auditLogService.registrarLogin(authentication.getName());

            if (esSupervisor && !esSuperadmin) {
                response.sendRedirect("/almacen");
            } else {
                response.sendRedirect("/");
            }
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder =
            http.getSharedObject(AuthenticationManagerBuilder.class);
        builder
            .userDetailsService(usuarioDetailsService)
            .passwordEncoder(passwordEncoder());
        return builder.build();
    }
}
