package com.textil.inventario.common;

/**
 * Recorta el numero de una guia o factura a lo unico que se usa para
 * reconocerla de un vistazo.
 *
 * Los documentos vienen con serie y relleno de ceros ("TG01-00022836"): la
 * serie es siempre la misma dentro de un tipo y los ceros solo empujan el
 * digito util hacia la derecha, asi que al comparar dos guias en pantalla hay
 * que leer 13 caracteres para distinguir dos que difieren en uno. Se muestra
 * "22836" y el numero completo queda en el tooltip, que no se pierde nada.
 */
public final class NumeroDocumento {

    private NumeroDocumento() {}

    /**
     * @param numero numero completo tal como esta en la base ("TG01-00022836")
     * @return la parte util ("22836"), o null si no hay numero
     */
    public static String corto(String numero) {
        if (numero == null || numero.isBlank()) return null;
        String n = numero.trim();

        // La serie va antes del ultimo guion; si no hay guion, es el numero entero.
        int guion = n.lastIndexOf('-');
        String cuerpo = guion >= 0 ? n.substring(guion + 1) : n;
        if (cuerpo.isBlank()) return n;

        // Solo se tocan los ceros si lo que queda es un numero: un correlativo
        // con letras ("TG01-A0022") se muestra tal cual antes que recortarlo mal.
        if (!cuerpo.matches("\\d+")) return cuerpo;

        String sinCeros = cuerpo.replaceFirst("^0+", "");
        return sinCeros.isEmpty() ? "0" : sinCeros;
    }
}
