package com.textil.inventario.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * Convierte la fecha que se leyo de una guia/factura al LocalDate del sistema.
 *
 * Antes esta conversion se la pediamos al MODELO ("devuelve la fecha en formato
 * YYYY-MM-DD") sin decirle en que formato viene impresa. Las guias peruanas
 * imprimen DIA/MES/AÑO, asi que al reordenar el modelo podia intercambiar dia y
 * mes: "05/08/2026" (5 de agosto) salia como 2026-05-08 (8 de mayo). El error es
 * INVISIBLE cuando el dia es mayor que 12 -- solo aparece en el 40% de los dias
 * del mes, que es justo lo que lo hacia dificil de notar.
 *
 * Ahora el modelo copia la fecha tal cual esta impresa y la conversion se hace
 * aca: es determinista, no depende de como venga el dia, y se puede testear.
 *
 * Se acepta tambien ISO (YYYY-MM-DD) porque es lo que devuelve el navegador
 * desde un &lt;input type="date"&gt; y lo que guardaban las lecturas viejas.
 */
public final class FechaDocumento {

    /** Dia/mes/año con cualquier separador comun: 05/08/2026, 5-8-2026, 05.08.2026 */
    private static final String DMY = "^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})$";
    /** Igual pero con año de dos digitos: 05/08/26 */
    private static final String DMY_CORTO = "^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2})$";
    private static final String ISO = "^\\d{4}-\\d{1,2}-\\d{1,2}$";

    // STRICT rechaza fechas que no existen (31/02) en vez de correrlas al 3 de
    // marzo, que es lo que hace el resolver por defecto (SMART).
    private static final DateTimeFormatter ISO_ESTRICTO =
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT);

    private FechaDocumento() {}

    /**
     * @param texto la fecha como vino del documento ("05/08/2026") o en ISO
     * @return la fecha, o null si no se puede leer con certeza (el usuario la
     *         escribe a mano; es preferible a inventar un dia)
     */
    public static LocalDate parse(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();

        try {
            if (t.matches(ISO)) {
                return LocalDate.parse(t, ISO_ESTRICTO);
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(DMY).matcher(t);
            if (m.matches()) {
                return construir(m.group(3), m.group(2), m.group(1));
            }
            m = java.util.regex.Pattern.compile(DMY_CORTO).matcher(t);
            if (m.matches()) {
                // Una guia es de este siglo; "26" es 2026.
                return construir("20" + m.group(3), m.group(2), m.group(1));
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * La fecha normalizada a ISO, que es lo unico que acepta un
     * &lt;input type="date"&gt;. Devuelve null si no se pudo leer: el campo queda
     * vacio y el formulario obliga a completarlo, en vez de mostrar una fecha
     * equivocada como si fuera la del papel.
     */
    public static String aIso(String texto) {
        LocalDate fecha = parse(texto);
        return fecha == null ? null : fecha.toString();
    }

    private static LocalDate construir(String anio, String mes, String dia) {
        return LocalDate.parse(anio + "-" + mes + "-" + dia, ISO_ESTRICTO);
    }
}
