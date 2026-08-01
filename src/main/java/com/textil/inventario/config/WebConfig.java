package com.textil.inventario.config;

import com.textil.inventario.seguridad.CambioPasswordInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el interceptor que fuerza el cambio de la contraseña por defecto
 * (auditoría A1). Ver {@link CambioPasswordInterceptor}.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CambioPasswordInterceptor cambioPasswordInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cambioPasswordInterceptor)
                // Los RECURSOS ESTATICOS nunca deben pasar por el interceptor: si
                // se redirigen a mi-cuenta, el CSS/JS no carga y la pantalla de
                // cambio forzado queda sin estilos. Se excluyen aca (ademas del
                // allowlist interno), asi es imposible que un estatico sea bloqueado.
                .excludePathPatterns(
                        "/webjars/**", "/css/**", "/js/**", "/img/**",
                        "/favicon.ico", "/manifest.webmanifest", "/sw.js", "/offline.html");
    }
}
