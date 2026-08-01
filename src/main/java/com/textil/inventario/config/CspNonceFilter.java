package com.textil.inventario.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * #13 (auditoría): Content-Security-Policy con nonce por request.
 *
 * Genera un nonce aleatorio en CADA request, lo publica como atributo de request
 * ("cspNonce", que GlobalModelAttributes pasa al modelo para que las plantillas
 * lo pongan en sus &lt;script&gt; inline) y emite la cabecera CSP referenciándolo.
 *
 * Política (defensa en profundidad contra XSS):
 * <ul>
 *   <li>{@code script-src 'self' 'nonce-XXX'}: solo corren los scripts propios
 *       (mismo origen) y los inline que lleven ESTE nonce. Un &lt;script&gt;
 *       inyectado (XSS almacenado) NO tiene el nonce → el navegador lo bloquea.
 *       Es el vector de XSS más peligroso y queda cerrado.</li>
 *   <li>{@code script-src-attr 'unsafe-inline'}: se PERMITEN a propósito los
 *       manejadores inline existentes (onclick/onchange/onsubmit) para no reescribir
 *       ~30 handlers en 16 plantillas (riesgo alto de romper la UI). El nonce en
 *       script-src no aplica a atributos, por eso se declara aparte. Endurecerlo
 *       (moverlos a addEventListener) queda como mejora futura.</li>
 *   <li>{@code style-src 'self' 'unsafe-inline'}: los estilos inline (Bootstrap,
 *       th:style, bloques &lt;style&gt;) siguen; la inyección de estilos es de bajo
 *       riesgo frente a la de scripts.</li>
 *   <li>{@code object-src 'none'}, {@code base-uri 'self'}, {@code frame-ancestors 'self'}
 *       (anti-clickjacking), {@code form-action 'self'}: cierres estándar.</li>
 * </ul>
 *
 * Se salta los estáticos (los sirve otra cadena, cacheables): no necesitan CSP ni
 * conviene variarles una cabecera por request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CspNonceFilter extends OncePerRequestFilter {

    public static final String NONCE_ATTR = "cspNonce";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/webjars/") || uri.startsWith("/css/")
                || uri.startsWith("/js/") || uri.startsWith("/img/")
                || uri.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute(NONCE_ATTR, nonce);

        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'nonce-" + nonce + "'; " +
                "script-src-attr 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "font-src 'self'; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "frame-ancestors 'self'; " +
                "form-action 'self'");

        filterChain.doFilter(request, response);
    }
}
