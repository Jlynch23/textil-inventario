package com.textil.inventario.seguridad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GeneradorUsernameTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private GeneradorUsername generador;

    /** Por defecto, ningún username está tomado. */
    private void nadaTomado() {
        lenient().when(usuarioRepository.findByUsername(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void inicialMasApellido() {
        nadaTomado();
        assertThat(generador.generar("Joseph Lynch")).isEqualTo("jlynch");
        assertThat(generador.generar("Oscar Clemente")).isEqualTo("oclemente");
    }

    @Test
    void usaSoloElPrimerApellido() {
        nadaTomado();
        assertThat(generador.generar("Oscar Clemente Reyes")).isEqualTo("oclemente");
    }

    @Test
    void quitaTildesYEnies() {
        nadaTomado();
        assertThat(generador.generar("José Ramírez")).isEqualTo("jramirez");
        assertThat(generador.generar("María Ñandú")).isEqualTo("mnandu");
    }

    @Test
    void unSoloNombreUsaLaPalabraCompleta() {
        nadaTomado();
        assertThat(generador.generar("Madonna")).isEqualTo("madonna");
    }

    @Test
    void nombreVacioCaeEnFallback() {
        nadaTomado();
        assertThat(generador.generar("   ")).isEqualTo("usuario");
    }

    @Test
    void colisionAgregaSufijoNumerico() {
        Usuario existente = new Usuario();
        existente.setId(1L);
        when_(existente, "jlynch");
        lenient().when(usuarioRepository.findByUsername("jlynch2")).thenReturn(Optional.empty());

        assertThat(generador.generar("Joseph Lynch")).isEqualTo("jlynch2");
    }

    @Test
    void alEditarNoChocaConsigoMismo() {
        Usuario mismo = new Usuario();
        mismo.setId(7L);
        when_(mismo, "jlynch");

        // Excluyendo su propio id, "jlynch" queda libre para él.
        assertThat(generador.generar("Joseph Lynch", 7L)).isEqualTo("jlynch");
    }

    private void when_(Usuario u, String username) {
        lenient().when(usuarioRepository.findByUsername(username)).thenReturn(Optional.of(u));
    }
}
