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
        registry.addInterceptor(cambioPasswordInterceptor);
    }
}
