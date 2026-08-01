package com.textil.inventario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Validación de drift entidad↔esquema (auditoría M15).
 *
 * Arranca el contexto de Spring completo, lo que dispara: (1) Flyway aplicando
 * las 39 migraciones sobre una BD MySQL vacía, y (2) Hibernate con
 * {@code ddl-auto: validate} comparando cada @Entity contra el esquema migrado.
 * Si una entidad no calza con las migraciones (una columna que se agregó al
 * Java pero no al SQL, o al revés), el contexto NO carga y este test falla —
 * atrapando en CI el mismo error que hoy solo reventaba en el deploy.
 *
 * Se activa SOLO cuando existe la variable RUN_DB_IT=true (la pone el job de CI
 * que levanta el servicio MySQL). En un `mvn test` local sin BD, se salta.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
class EsquemaFlywayTest {

    @Test
    void elContextoArranca_migracionesYValidateCoinciden() {
        // Sin asserts: si el contexto cargó, Flyway migró y Hibernate validó el
        // esquema contra las entidades sin detectar diferencias.
    }
}
