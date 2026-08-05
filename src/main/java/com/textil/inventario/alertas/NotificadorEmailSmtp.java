package com.textil.inventario.alertas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Envía la alerta de stock bajo por CORREO (SMTP, por defecto Gmail). Es el
 * canal ACTIVO (@Primary): Twilio/SMS ({@link NotificadorSmsTwilio}) queda como
 * implementación alternativa sin cablear.
 *
 * Los destinatarios NO salen de la lista de celulares que resuelve el listener
 * (eso es del canal SMS): van en {@code alerta.email.to} (ALERTA_EMAIL_TO,
 * separados por coma), porque los usuarios ya no tienen email en la base (V28).
 *
 * Igual que el SMS: best-effort con timeouts acotados (spring.mail.properties),
 * corre en el hilo @Async del listener; si faltan credenciales o falla el envío,
 * se loggea y se sigue — jamás tumba la operación que disparó la alerta.
 */
@Component
@Primary
public class NotificadorEmailSmtp implements NotificadorStockBajo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorEmailSmtp.class);

    private final JavaMailSender mailSender;

    /** Destinatarios del aviso, separados por coma. Sin esto el canal queda apagado. */
    @Value("${alerta.email.to:}")
    private String destinatariosConfig;

    /** Remitente; si está vacío se usa el mismo usuario SMTP (lo usual en Gmail). */
    @Value("${alerta.email.from:}")
    private String from;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    public NotificadorEmailSmtp(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public boolean alertar(StockBajoEvent e, List<String> celularesIgnorados) {
        if (blank(smtpUsername) || blank(smtpPassword)) {
            log.warn("Alerta de stock bajo NO enviada por email: faltan credenciales SMTP "
                    + "(MAIL_USERNAME/MAIL_PASSWORD). Aviso: {}", resumen(e));
            return false;
        }
        if (blank(destinatariosConfig)) {
            log.warn("Alerta de stock bajo NO enviada por email: ALERTA_EMAIL_TO vacio. Aviso: {}",
                    resumen(e));
            return false;
        }

        String remitente = blank(from) ? smtpUsername : from.trim();
        String asunto = "Alerta de stock bajo: " + e.colorNombre() + " en " + e.ubicacionNombre();
        String cuerpo = "Aviso automático de TexControl:\n\n"
                + "El stock de " + e.articuloLabel() + " color " + e.colorNombre()
                + " en " + e.ubicacionNombre() + " quedó en " + e.rollosRestantes()
                + " rollos (umbral de alerta: " + e.umbral() + ").\n\n"
                + "Revisa el inventario y programa una reposición si corresponde.";

        boolean algunoEntregado = false;
        for (String destino : destinatariosConfig.split(",")) {
            String d = destino.trim();
            if (d.isEmpty()) continue;
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(remitente);
                msg.setTo(d);
                msg.setSubject(asunto);
                msg.setText(cuerpo);
                mailSender.send(msg);
                log.info("Email de stock bajo enviado a {} -> {}", enmascarar(d), asunto);
                algunoEntregado = true;
            } catch (Exception ex) {
                log.error("Error enviando email de stock bajo a {}: {}", enmascarar(d), ex.getMessage());
            }
        }
        return algunoEntregado;
    }

    /**
     * Deja el destinatario reconocible en el log sin escribirlo completo:
     * "jlynch@gmail.com" -> "j***@gmail.com". Los logs de una instancia se
     * comparten al depurar y no tienen por que llevar datos de contacto en claro.
     */
    private static String enmascarar(String destino) {
        if (destino == null || destino.isBlank()) return "(vacio)";
        int arroba = destino.indexOf('@');
        if (arroba <= 0) return destino.charAt(0) + "***";
        return destino.charAt(0) + "***" + destino.substring(arroba);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String resumen(StockBajoEvent e) {
        return e.colorNombre() + " en " + e.ubicacionNombre() + " = " + e.rollosRestantes() + " rollos";
    }
}
