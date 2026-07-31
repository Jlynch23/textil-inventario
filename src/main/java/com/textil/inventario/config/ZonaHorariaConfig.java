package com.textil.inventario.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Fija la zona horaria de la JVM en America/Lima (UTC-5).
 *
 * El VPS (contenedor Docker) corre en UTC, asi que sin esto todos los
 * {@code LocalDateTime.now()} (created_at de las entidades, Log de Auditoria,
 * Reporte de Errores, kardex) quedaban 5 horas adelantados: un error de las
 * 21:00 de Peru se guardaba y mostraba como las 02:00 del dia siguiente.
 *
 * Se setea al arrancar, antes de atender pedidos, para que toda hora que la
 * app genere sea hora de Peru. (El JDBC ya llevaba serverTimezone=America/Lima,
 * pero eso NO afecta a LocalDateTime.now(), que usa la zona por defecto de la
 * JVM: esa es la que faltaba fijar.)
 */
@Configuration
public class ZonaHorariaConfig {

    @PostConstruct
    public void fijarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
    }
}
