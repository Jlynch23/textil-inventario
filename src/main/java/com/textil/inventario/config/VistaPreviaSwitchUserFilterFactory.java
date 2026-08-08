package com.textil.inventario.config;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Construye el {@link SwitchUserFilter} de la vista previa por rol.
 *
 * <h2>Por qué el filtro NO es un @Bean</h2>
 * Spring Boot registra automáticamente en el contenedor de servlets cualquier bean
 * de tipo Filter. Como @Bean, este filtro quedaría montado DOS veces: una dentro de
 * la cadena de seguridad (donde la regla {@code /vista-previa -> hasRole('SUPERADMIN')}
 * lo protege) y otra fuera de ella, delante de todo. Esa segunda copia correría SIN
 * autorización previa, que es precisamente lo que la regla existe para impedir.
 *
 * <h2>Por qué hay que llamar a afterPropertiesSet() a mano</h2>
 * Y ese es el precio de no ser un bean: nadie ejecuta su ciclo de vida.
 * {@code SwitchUserFilter.afterPropertiesSet()} no es una validación opcional --
 * es donde el filtro CONSTRUYE su successHandler a partir del targetUrl:
 * <pre>
 *   if (this.targetUrl != null) {
 *       this.successHandler = new SimpleUrlAuthenticationSuccessHandler(this.targetUrl);
 *   }
 * </pre>
 * Sin esa llamada, {@code successHandler} queda en null y {@code doFilter} lo
 * desreferencia -- en la rama de ENTRAR y en la de SALIR. Resultado: NullPointer
 * en las dos direcciones, y como salta dentro de la cadena de filtros no pasa por
 * GlobalExceptionHandler, así que el usuario ve la página de error genérica y en el
 * log no aparece el "Error no controlado" que uno iría a buscar.
 * <p>
 * Mordió de verdad (8-ago), y no lo agarró nada: compila, los tests unitarios no
 * montan la cadena, y el arranque de la app tampoco falla porque el NPE recién
 * ocurre al usar el filtro. Por eso la construcción vive acá y no incrustada en
 * SecurityConfig: para que un test pueda ejercitarla de punta a punta
 * (VistaPreviaSwitchUserFilterFactoryTest).
 */
final class VistaPreviaSwitchUserFilterFactory {

    static final String URL_ENTRAR = "/vista-previa";
    static final String URL_SALIR = "/vista-previa/salir";
    static final String PARAM_ROL = "rol";

    private VistaPreviaSwitchUserFilterFactory() {}

    static SwitchUserFilter crear(UserDetailsService vistaPreviaRolUserDetailsService) {
        SwitchUserFilter filtro = new SwitchUserFilter();
        filtro.setUserDetailsService(vistaPreviaRolUserDetailsService);
        // Entrar y salir son POST (con CSRF), no GET: con GET, un <img src="..."> en
        // cualquier página externa podría cambiarle el rol a un SUPERADMIN sin que lo
        // note -- el mismo motivo por el que el logout de este proyecto es POST.
        filtro.setSwitchUserMatcher(new AntPathRequestMatcher(URL_ENTRAR, "POST"));
        filtro.setExitUserMatcher(new AntPathRequestMatcher(URL_SALIR, "POST"));
        filtro.setUsernameParameter(PARAM_ROL);
        filtro.setTargetUrl("/");
        filtro.setSwitchFailureUrl("/usuarios?vistaPreviaError");

        // OBLIGATORIO. Ver el comentario de arriba: acá es donde nace el
        // successHandler. No es una formalidad heredada de InitializingBean.
        filtro.afterPropertiesSet();
        return filtro;
    }
}
