package com.textil.inventario.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A qué pantalla cae cada rol.
 *
 * Importa porque el SUPERVISOR NO puede abrir "/" ni "/dashboard" (SecurityConfig
 * reserva esa regla a GERENTE+ADMIN+SUPERADMIN). Mandarlo ahí no le muestra un
 * menú recortado: le da un 403. La regla la comparten el login, la vista previa
 * por rol y el manejo de 403, y basta con que uno se desincronice para dejar a
 * alguien en una pantalla que no puede ver.
 */
class DestinoPorRolTest {

    private static Authentication con(String... roles) {
        return new UsernamePasswordAuthenticationToken("x", "y",
                List.of(roles).stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    @Test
    void elSupervisorVaAlAlmacen() {
        assertThat(DestinoPorRol.destinoPara(con("SUPERVISOR"))).isEqualTo("/almacen");
    }

    @Test
    void losDemasVanAlInicio() {
        assertThat(DestinoPorRol.destinoPara(con("ADMIN"))).isEqualTo("/");
        assertThat(DestinoPorRol.destinoPara(con("GERENTE"))).isEqualTo("/");
        assertThat(DestinoPorRol.destinoPara(con("SUPERADMIN"))).isEqualTo("/");
        assertThat(DestinoPorRol.destinoPara(con("VENDEDOR"))).isEqualTo("/");
    }

    @Test
    void unSuperadminQueAdemasSeaSupervisor_vaAlInicio() {
        // Comportamiento historico del login: el proveedor no queda atrapado en la
        // pantalla movil de almacen.
        assertThat(DestinoPorRol.destinoPara(con("SUPERVISOR", "SUPERADMIN"))).isEqualTo("/");
    }

    @Test
    void sinSesion_alInicio() {
        assertThat(DestinoPorRol.destinoPara(null)).isEqualTo("/");
    }
}
