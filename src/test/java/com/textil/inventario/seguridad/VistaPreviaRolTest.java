package com.textil.inventario.seguridad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Ver la app como GERENTE/ADMIN/…": el SUPERADMIN cambia el rol efectivo de su
 * sesión para mirar la app con otros ojos.
 *
 * Lo que se cuida acá es que esto NO se pueda convertir en suplantar a una
 * PERSONA. El principal es un rol sintético y nunca sale de la tabla usuarios;
 * si alguien apunta el parámetro a un username real, tiene que rebotar.
 */
class VistaPreviaRolTest {

    private RolRepository rolRepository;
    private VistaPreviaRolUserDetailsService service;

    private static Rol rol(String nombre, boolean activo) {
        Rol r = new Rol();
        r.setNombre(nombre);
        r.setActivo(activo);
        return r;
    }

    @BeforeEach
    void setUp() {
        rolRepository = Mockito.mock(RolRepository.class);
        Mockito.when(rolRepository.findAllByOrderByJerarquiaAsc()).thenReturn(List.of(
                rol("SUPERADMIN", true), rol("ADMIN", true), rol("GERENTE", true),
                rol("SUPERVISOR", true), rol("VENDEDOR", true), rol("VIEJO", false)));
        service = new VistaPreviaRolUserDetailsService(rolRepository);
    }

    // --- lo que SÍ se puede -------------------------------------------------

    @Test
    void resuelveUnRolValido_conSuAuthority() {
        UserDetails ud = service.loadUserByUsername("GERENTE");

        assertThat(ud.getUsername()).isEqualTo("vista:GERENTE");
        assertThat(ud.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_GERENTE");
    }

    @Test
    void aceptaTambienElUsernameSinteticoCompleto() {
        assertThat(service.loadUserByUsername("vista:ADMIN").getUsername()).isEqualTo("vista:ADMIN");
    }

    @Test
    void noEsSensibleAMayusculasNiEspacios() {
        assertThat(service.loadUserByUsername("  gerente ").getUsername()).isEqualTo("vista:GERENTE");
    }

    // --- lo que NO -----------------------------------------------------------

    @Test
    void unUsernameReal_noSeResuelve() {
        // El corazon del asunto: 'jlynch' es una PERSONA, no un rol. Que rebote es
        // lo que impide que esto sea suplantacion de identidad.
        assertThatThrownBy(() -> service.loadUserByUsername("jlynch"))
                .isInstanceOf(UsernameNotFoundException.class);

        // Y rebota por el motivo correcto: se lo BUSCO entre los roles (no entre
        // los usuarios) y no estaba. La garantia de fondo es estructural -- este
        // servicio no recibe UsuarioRepository, asi que no puede mirar la tabla
        // usuarios ni queriendo; su unica dependencia es RolRepository.
        Mockito.verify(rolRepository).findAllByOrderByJerarquiaAsc();
        assertThat(VistaPreviaRolUserDetailsService.class.getDeclaredFields())
                .as("no debe aparecer una dependencia a usuarios acá")
                .noneMatch(f -> f.getType().getSimpleName().contains("Usuario"));
    }

    @Test
    void superadmin_noSePrevisualiza() {
        assertThatThrownBy(() -> service.loadUserByUsername("SUPERADMIN"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("SUPERADMIN");
    }

    @Test
    void unRolInventado_noSeResuelve() {
        assertThatThrownBy(() -> service.loadUserByUsername("ROL_QUE_NO_EXISTE"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void vacioONulo_noSeResuelve() {
        assertThatThrownBy(() -> service.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("   "))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void laListaOfrecida_excluyeSuperadminYLosInactivos() {
        assertThat(service.rolesPrevisualizables())
                .containsExactly("ADMIN", "GERENTE", "SUPERVISOR", "VENDEDOR")
                .doesNotContain("SUPERADMIN", "VIEJO");
    }

    // --- los helpers de lectura ---------------------------------------------

    @Test
    void sinLaAutoridadDelSwitch_noHayVistaPrevia() {
        Authentication normal = new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));

        assertThat(VistaPreviaRol.activa(normal)).isFalse();
        assertThat(VistaPreviaRol.rolActual(normal)).isNull();
        assertThat(VistaPreviaRol.activa(null)).isFalse();
    }

    @Test
    void conLaAutoridadDelSwitch_seSabeQueRolSeEstaViendo() {
        Authentication original = new UsernamePasswordAuthenticationToken(
                "jlynch", "x", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
        Authentication enVistaPrevia = new UsernamePasswordAuthenticationToken(
                "vista:GERENTE", "x",
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE"),
                        new SwitchUserGrantedAuthority("ROLE_PREVIOUS_ADMINISTRATOR", original)));

        assertThat(VistaPreviaRol.activa(enVistaPrevia)).isTrue();
        assertThat(VistaPreviaRol.rolActual(enVistaPrevia)).isEqualTo("GERENTE");
    }
}
