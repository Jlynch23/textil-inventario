package com.textil.inventario.recepciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AnthropicOcrService {

    @Value("${anthropic.api-key}")
    private String apiKey;

    // SEC-03 (auditoria 17-jul-2026): sin timeout, si la API de Anthropic
    // tarda o no responde, el hilo queda bloqueado indefinidamente. El OCR
    // corre dentro de @Async (ver AsyncConfig), asi que sin timeout un
    // proveedor caido podria agotar el pool de hilos bajo carga.
    private final RestClient restClient = crearRestClientConTimeouts();

    private static RestClient crearRestClientConTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000); // 30s para establecer conexion
        factory.setReadTimeout(90_000);    // 90s: OCR de PDF puede tardar
        return RestClient.builder().requestFactory(factory).build();
    }
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        Eres un asistente que extrae datos estructurados de guias de remision de la empresa FAST DYE (tintoreria textil).
        Analiza el PDF y devuelve EXCLUSIVAMENTE un objeto JSON valido, sin texto adicional, sin markdown, sin backticks.

        En la descripcion de cada producto suele aparecer un texto como:
        "Servicio Tenido: Tela RIB 2X1 30/1 ALG ACANALADO Color 631085 COCOA LOLA / Rollos: 27 / P.Bruto: 602.52 Guia: 658"
        o con composicion Melange:
        "Servicio Tenido: Tela RIB 2X1 24/1 MELLANGE 10% Color 732631 NEGRO 2 / Rollos: 18 / P.Bruto: 416.00 Guia: 665"
        o con acabado listado:
        "Servicio Tenido: Tela RIB 2X1 24/1 ALG LIST BLANCO Color 121299 CEMENTO / Rollos: 18 / P.Bruto: 412.15 Guia: 472"
        o con el orden invertido (acabado antes de la composicion):
        "Servicio Tenido: Tela RIB 2X1 30/1 ACANALADO MLG 3% Color 01008 PRE BLANQUEO PARA PIGMENTAR / Rollos: 13 / P.Bruto: 287.88 Guia: 380"

        De ese texto debes separar los siguientes campos:
        - tipoTela: el TEJIDO BASE, normalizado a uno de estos valores EXACTOS: "RIB 1X1", "RIB 2X1", "VERYGATE", "FRANELA".
          El tejido base es SIEMPRE independiente del acabado: "RIB 2X1 ... ACANALADO" tiene tipoTela "RIB 2X1" (el ACANALADO va en el campo acabado, NUNCA aqui).
          Si no calza con ninguno de estos valores, devuelve el texto tal cual aparece.
        - titulo: el numero de titulo de hilo, ej "24/1" o "30/1" (solo el numero con formato N/1, sin las siglas ALG u otras)
        - composicion: la composicion o variante de fibra, normalizada a uno de estos valores EXACTOS:
          "ALGODON" (cuando el texto dice "ALG", "ALGODON" o "ALG PEINADO", o no menciona ninguna variante especial),
          "MELANGE 10%", "MELANGE 3%", "MELANGE 1%" (usa el porcentaje exacto que aparece en el texto).
          IMPORTANTE: en las guias reales esta palabra puede aparecer escrita "MELLANGE" (con DOBLE L), "MELANGE" (una L) o abreviada "MLG" -- todas significan exactamente lo mismo y deben normalizarse siempre a "MELANGE N%". Es una composicion DISTINTA de ALGODON -- nunca la ignores aunque el resto del texto se vea igual a una tela normal.
          El orden puede venir invertido: "ACANALADO MLG 3%" significa composicion "MELANGE 3%" con acabado "ACANALADO".
          OJO: si el MELANGE aparece inmediatamente despues de "LIST" o "LISTADO" (ej "LIST MELANGE 10%"), NO es la composicion de la tela: es parte del acabado listado (ver campo acabado). En ese caso, si el texto no menciona otra composicion, usa "ALGODON".
        - acabado: el acabado de la tela, normalizado a uno de estos valores EXACTOS:
          "ACANALADO" (cuando aparece la palabra ACANALADO),
          "LISTADO BLANCO", "LISTADO NEGRO", "LISTADO MELANGE 10%", "LISTADO MELANGE 3%" (cuando aparece "LIST X" o "LISTADO X"; la palabra que sigue a LIST describe la variante del listado),
          "LISO" (valor por defecto: cuando NO aparece ni ACANALADO ni LIST/LISTADO en el texto).
        - colorCodigo: el codigo numerico de color (ej "631085")
        - colorNombre: el nombre del color tal como aparece (ej "COCOA LOLA")
        - programaTenido: el numero de programa que aparece como "Guia: NNN" dentro de la descripcion (ese numero, ej "658", NO es el numero de guia principal del documento)
        - rollos: el numero entero que aparece como "Rollos: N"
        - pesoBrutoKg: el numero decimal que aparece como "P.Bruto: N.NN"

        Formato exacto requerido:
        {
          "numeroGuia": "string, el numero de guia principal del documento (ej: TG01-00022558), NO el numero de programa",
          "numeroFactura": "string, el numero de factura si aparece en el documento (puede llamarse Factura, Comprobante, o similar), o null si no aparece",
          "fechaGuia": "string en formato YYYY-MM-DD, tomado de Fecha Emision",
          "razonSocialDetectada": "string, el valor de Nombre/Razon Social del documento",
          "productos": [
            {
              "tipoTela": "string",
              "titulo": "string",
              "composicion": "string",
              "acabado": "string",
              "colorCodigo": "string",
              "colorNombre": "string",
              "programaTenido": "string",
              "rollos": numero entero,
              "pesoBrutoKg": numero decimal o null
            }
          ],
          "advertencia": "string opcional; si algun dato no se pudo leer con confianza, explica cual; si todo se leyo bien, usa null"
        }

        Si no puedes identificar algun campo, usa null en ese campo especifico. Nunca inventes datos que no esten en el documento.
        """;

    private static final String SYSTEM_PROMPT_FACTURA = """
        Eres un asistente que extrae datos de facturas de la empresa FAST DYE (tintoreria textil).
        Analiza el PDF y devuelve EXCLUSIVAMENTE un objeto JSON valido, sin texto adicional, sin markdown, sin backticks.

        Formato exacto requerido:
        {
          "numeroFactura": "string, el numero de factura o comprobante del documento",
          "fechaFactura": "string en formato YYYY-MM-DD",
          "razonSocialDetectada": "string, el nombre/razon social del cliente que aparece en el documento",
          "guiasReferenciadas": ["lista de strings con los numeros de guia de remision relacionados/referenciados en la factura, tal como aparecen en el documento (ej: TG01-00022836). Si no hay ninguna referencia a guias, devolver una lista vacia []"],
          "advertencia": "string opcional; si algun dato no se pudo leer con confianza, explica cual; si todo se leyo bien, usa null"
        }

        Si no puedes identificar algun campo, usa null en ese campo especifico. Nunca inventes datos que no esten en el documento.
        """;

    public ExtraccionFacturaResponse extraerDatosFactura(MultipartFile pdf) throws IOException {
        return extraerDatosFactura(pdf.getBytes());
    }

    public ExtraccionFacturaResponse extraerDatosFactura(byte[] pdfBytes) throws IOException {
        String jsonLimpio = llamarClaude(pdfBytes, SYSTEM_PROMPT_FACTURA,
                "Extrae los datos de esta factura segun el formato indicado.", 1000);
        return mapper.readValue(jsonLimpio, ExtraccionFacturaResponse.class);
    }

    public ExtraccionGuiaResponse extraerDatosGuia(MultipartFile pdf) throws IOException {
        return extraerDatosGuia(pdf.getBytes());
    }

    public ExtraccionGuiaResponse extraerDatosGuia(byte[] pdfBytes) throws IOException {
        String jsonLimpio = llamarClaude(pdfBytes, SYSTEM_PROMPT,
                "Extrae los datos de esta guia segun el formato indicado.", 2000);
        return mapper.readValue(jsonLimpio, ExtraccionGuiaResponse.class);
    }

    private String llamarClaude(byte[] pdfBytes, String systemPrompt, String textoUsuario, int maxTokens) throws IOException {
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        Map<String, Object> requestBody = Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "document",
                                                "source", Map.of(
                                                        "type", "base64",
                                                        "media_type", "application/pdf",
                                                        "data", base64Pdf
                                                )
                                        ),
                                        Map.of(
                                                "type", "text",
                                                "text", textoUsuario
                                        )
                                )
                        )
                )
        );

        String rawResponse = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(rawResponse);
        String textoExtraido = root.path("content").get(0).path("text").asText();

        String jsonLimpio = textoExtraido.trim();
        if (jsonLimpio.startsWith("```")) {
            jsonLimpio = jsonLimpio.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        return jsonLimpio;
    }
}
