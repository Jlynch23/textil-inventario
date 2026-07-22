package com.textil.inventario.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Optional;

/**
 * Genera el nombre de usuario a partir del nombre completo, con la convención
 * del negocio: primera letra del primer nombre + primer apellido, en minúscula,
 * sin espacios ni tildes.
 * <p>
 * Ej: "Joseph Lynch" -> "jlynch"; "Oscar Clemente Reyes" -> "oclemente".
 * Si el resultado ya existe, agrega un sufijo numérico ("oclemente2", etc.),
 * respetando la unicidad del username. Al editar, se excluye al propio usuario
 * para que regenerar su nombre no choque consigo mismo.
 */
@Service
@RequiredArgsConstructor
public class GeneradorUsername {

    private final UsuarioRepository usuarioRepository;

    /** Genera un username único para un usuario nuevo. */
    public String generar(String nombreCompleto) {
        return generar(nombreCompleto, null);
    }

    /**
     * Genera un username único a partir del nombre.
     * @param excluirId id del usuario a excluir del chequeo de unicidad (para
     *                  ediciones: no debe chocar consigo mismo). null para altas.
     */
    public String generar(String nombreCompleto, Long excluirId) {
        String base = base(nombreCompleto);
        String candidato = base;
        int n = 2;
        while (tomado(candidato, excluirId)) {
            candidato = base + n;
            n++;
        }
        return candidato;
    }

    private boolean tomado(String username, Long excluirId) {
        Optional<Usuario> existente = usuarioRepository.findByUsername(username);
        if (existente.isEmpty()) return false;
        return excluirId == null || !existente.get().getId().equals(excluirId);
    }

    /** Base sin sufijo: inicial del primer token + segundo token (primer apellido). */
    private String base(String nombreCompleto) {
        if (nombreCompleto == null) nombreCompleto = "";
        // Quita tildes/diacríticos y deja solo letras a-z, separando por espacios.
        String limpio = Normalizer.normalize(nombreCompleto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        String[] tokens = limpio.trim().split("\\s+");
        java.util.List<String> palabras = new java.util.ArrayList<>();
        for (String t : tokens) {
            String soloLetras = t.replaceAll("[^a-z0-9]", "");
            if (!soloLetras.isBlank()) palabras.add(soloLetras);
        }
        if (palabras.isEmpty()) return "usuario";
        if (palabras.size() == 1) return palabras.get(0);
        return palabras.get(0).substring(0, 1) + palabras.get(1);
    }
}
