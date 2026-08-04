package com.textil.inventario.alertas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Envía la alerta de stock bajo por SMS usando la API de Twilio (un POST
 * form-urlencoded a Messages.json, autenticado con Basic Account SID + Auth
 * Token). Mismo patrón que {@code AnthropicOcrService}: RestClient con timeouts
 * acotados porque corre en un hilo @Async y un proveedor caído no debe colgarlo.
 *
 * Los destinatarios llegan resueltos (los ADMIN/GERENTE con celular). Si faltan
 * credenciales (twilio.*) o hay error, se registra y se sigue: la alerta es
 * best-effort, nunca debe tumbar la app ni la operación que la disparó. Migrar a
 * WhatsApp/Meta = crear otra implementación de NotificadorStockBajo.
 */
@Component
public class NotificadorSmsTwilio implements NotificadorStockBajo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorSmsTwilio.class);

    @Value("${twilio.account-sid:}") private String accountSid;
    @Value("${twilio.auth-token:}")  private String authToken;
    @Value("${twilio.from:}")        private String from;

    private final RestClient restClient = crearRestClientConTimeouts();

    private static RestClient crearRestClientConTimeouts() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void alertar(StockBajoEvent e, List<String> destinatarios) {
        if (blank(accountSid) || blank(authToken) || blank(from)) {
            log.warn("Alerta de stock bajo NO enviada: faltan credenciales de Twilio (twilio.*). Aviso: {}",
                    resumen(e));
            return;
        }
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("Alerta de stock bajo sin destinatarios (ningún ADMIN/GERENTE con celular). Aviso: {}",
                    resumen(e));
            return;
        }

        // ASCII y sin emoji a propósito: mantiene el SMS en 1 segmento GSM-7 (más
        // barato). Los nombres del catálogo pueden traer tildes; eso es dato, no lo
        // forzamos.
        String cuerpo = "Stock bajo en " + e.ubicacionNombre() + ": " + e.colorNombre()
                + " (" + e.articuloLabel() + ") quedo en " + e.rollosRestantes() + " rollos.";

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        for (String destino : destinatarios) {
            if (destino == null || destino.isBlank()) continue;

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", destino.trim());
            form.add("From", from);
            form.add("Body", cuerpo);

            try {
                restClient.post().uri(url)
                        .header("Authorization", basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .toBodilessEntity();
                log.info("SMS de stock bajo enviado a {} -> {}", destino.trim(), cuerpo);
            } catch (Exception ex) {
                log.error("Error enviando SMS de stock bajo a {}: {}", destino.trim(), ex.getMessage());
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String resumen(StockBajoEvent e) {
        return e.colorNombre() + " en " + e.ubicacionNombre() + " = " + e.rollosRestantes() + " rollos";
    }
}
