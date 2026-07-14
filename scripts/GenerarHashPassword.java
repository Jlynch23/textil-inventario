package scripts;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad de línea de comandos para generar un hash bcrypt compatible con
 * Spring Security, sin hardcodear ninguna contraseña en el código fuente.
 * <p>
 * Vive fuera del árbol de compilación de src/main y src/test a propósito
 * (ver AUDIT.md, hallazgo SEC-01) — reemplaza al antiguo GenHashTest.java,
 * que hardcodeaba la contraseña real del admin.
 * <p>
 * Uso: pasar la contraseña deseada como argumento de línea de comandos.
 * Requiere el classpath del proyecto (usar mvn dependency:build-classpath
 * o correrlo con el proyecto ya compilado).
 */
public class GenerarHashPassword {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java -cp <classpath> scripts.GenerarHashPassword <contraseña>");
            System.exit(1);
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(args[0]);
        System.out.println(hash);
    }
}
