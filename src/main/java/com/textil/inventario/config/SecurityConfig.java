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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;
    private final AuditLogService auditLogService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/logout", "/css/**", "/js/**", "/img/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/almacen/revision/**").hasRole("SUPERADMIN")
                .requestMatchers("/almacen/**").hasAnyRole("SUPERVISOR", "SUPERADMIN")
                // GERENTE: solo lectura (GET) en las areas operativas relevantes.
                // Antes de la regla general de lectura, se bloquean explicitamente
                // las paginas de creacion/edicion (subida de guias/facturas,
                // confirmacion de recepcion, edicion de programa) -- aunque sean
                // GET (muestran un formulario), son puntos de entrada a una accion
                // de escritura y no deben ser accesibles de solo-lectura.
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/recepciones/nueva", "/recepciones/facturar", "/recepciones/*/confirmar",
                        "/programas/nuevo", "/programas/*/editar",
                        "/transferencias/nueva", "/transferencias/*/confirmar-salida", "/transferencias/*/confirmar-llegada",
                        "/catalogo/ubicaciones"
                ).hasRole("SUPERADMIN")
                // NUNCA se le da acceso a /log/** ni /reportes/** (ni siquiera de
                // lectura), y cualquier accion de escritura (POST/PUT/DELETE) a
                // estas mismas rutas cae al anyRequest().hasRole("SUPERADMIN") de
                // abajo, que GERENTE no cumple -- queda bloqueada automaticamente.
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/", "/dashboard",
                        "/inventario/**", "/catalogo/**", "/programas/**",
                        "/documentos/**", "/recepciones/**", "/transferencias/**"
                ).hasAnyRole("GERENTE", "SUPERADMIN")
                // Descarga de documentos en zip: es solo lectura (igual que ver/descargar
                // individual, ya permitido arriba a GERENTE), aunque tecnicamente sea POST
                // porque manda una lista de ids en el body. Sin esta regla quedaria
                // silenciosamente bloqueada para GERENTE por el anyRequest() de abajo.
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/documentos/descargar-zip"
                ).hasAnyRole("GERENTE", "SUPERADMIN")
                .anyRequest().hasRole("SUPERADMIN")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            );

        return http.build();
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
