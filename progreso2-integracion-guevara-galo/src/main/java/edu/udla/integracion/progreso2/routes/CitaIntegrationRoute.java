package edu.udla.integracion.progreso2.routes;

import edu.udla.integracion.progreso2.model.AppointmentEvent;
import edu.udla.integracion.progreso2.model.BillingMessage;
import edu.udla.integracion.progreso2.model.CitaRequest;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Ruta principal de integración para el sistema Salud360.
 *
 * Implementa tres patrones de integración empresarial (EIP) con Apache Camel:
 *   - Multicast EIP:        orquesta el despacho paralelo a las tres sub-rutas.
 *   - Point-to-Point (P2P): envía el comando de facturación a billing.queue.
 *   - Publish/Subscribe:    publica el evento de cita en appointments.events (Fanout).
 *   - File Transfer:        persiste el registro en auditoria-citas.csv.
 *   - Error Handling:       captura excepciones, reintenta 2 veces y registra en citas-rechazadas.log.
 */
@Component
public class CitaIntegrationRoute extends RouteBuilder {

    @Value("${citas.outbox.dir}")
    private String outboxDir;

    @Value("${citas.outbox.filename}")
    private String outboxFilename;

    @Value("${citas.errors.dir}")
    private String errorsDir;

    @Value("${citas.errors.filename}")
    private String errorsFilename;

    // =========================================================================
    // Configuración de Rutas
    // =========================================================================

    @Override
    public void configure() {

        // --- RF5: Manejo de Errores con Reintentos ---
        onException(Exception.class)
            .handled(true)
            .maximumRedeliveries(2)
            .redeliveryDelay(1000)
            .log(LoggingLevel.ERROR, "Error procesando cita: ${exception.message}")
            .process(buildErrorLogProcessor())
            .to("file:" + errorsDir + "?fileName=" + errorsFilename + "&fileExist=Append");

        // --- Ruta Principal: Orquestación con Multicast EIP ---
        from("direct:startIntegration")
            .routeId("citaIntegrationRoute")
            .log("Procesando cita recibida: ${body}")
            .process(exchange -> exchange.setProperty("originalRequest", exchange.getIn().getBody()))
            .multicast().shareUnitOfWork()
                .to("direct:sendToBilling", "direct:sendToPubSub", "direct:writeToCsv")
            .end();

        // --- RF2: Sub-ruta Point-to-Point (Facturación) ---
        from("direct:sendToBilling")
            .routeId("billingSubRoute")
            .process(buildBillingProcessor())
            .marshal().json(JsonLibrary.Jackson)
            .to("spring-rabbitmq:billing-exchange?routingKey=billing-routing-key")
            .log("Mensaje P2P de facturación enviado exitosamente a RabbitMQ.");

        // --- RF3: Sub-ruta Publish/Subscribe (Eventos de Citas) ---
        from("direct:sendToPubSub")
            .routeId("pubSubSubRoute")
            .process(buildAppointmentEventProcessor())
            .marshal().json(JsonLibrary.Jackson)
            .to("spring-rabbitmq:appointments.events")
            .log("Evento Pub/Sub publicado exitosamente a RabbitMQ.");

        // --- RF4: Sub-ruta File Transfer (CSV de Auditoría) ---
        from("direct:writeToCsv")
            .routeId("csvAuditSubRoute")
            .process(buildCsvLineProcessor())
            .choice()
                .when(exchange -> {
                    File csvFile = new File(outboxDir, outboxFilename);
                    return !csvFile.exists() || csvFile.length() == 0;
                })
                    .setBody(exchange -> "idCita,paciente,correo,especialidad,fechaCita,sede,valor\n"
                            + exchange.getProperty("csvDataLine", String.class))
                .otherwise()
                    .setBody(exchange -> exchange.getProperty("csvDataLine", String.class))
            .end()
            .to("file:" + outboxDir + "?fileName=" + outboxFilename + "&fileExist=Append")
            .log("Registro de cita escrito exitosamente en el archivo CSV de auditoría.");
    }

    // =========================================================================
    // Processors — Transformaciones de Mensajes
    // =========================================================================

    /**
     * RF2: Transforma CitaRequest → BillingMessage para el canal Point-to-Point.
     */
    private Processor buildBillingProcessor() {
        return exchange -> {
            CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
            BillingMessage billing = BillingMessage.builder()
                    .idCita(req.getIdCita())
                    .paciente(req.getPaciente())
                    .especialidad(req.getEspecialidad())
                    .valor(req.getValor())
                    .build();
            exchange.getIn().setBody(billing);
        };
    }

    /**
     * RF3: Transforma CitaRequest → AppointmentEvent para el canal Publish/Subscribe.
     */
    private Processor buildAppointmentEventProcessor() {
        return exchange -> {
            CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
            AppointmentEvent event = AppointmentEvent.builder()
                    .idCita(req.getIdCita())
                    .paciente(req.getPaciente())
                    .correo(req.getCorreo())
                    .especialidad(req.getEspecialidad())
                    .fechaCita(req.getFechaCita())
                    .sede(req.getSede())
                    .build();
            exchange.getIn().setBody(event);
        };
    }

    /**
     * RF4: Formatea CitaRequest como línea CSV y la guarda en una propiedad del Exchange.
     * El EIP choice() decide si anteponer el encabezado.
     */
    private Processor buildCsvLineProcessor() {
        return exchange -> {
            CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
            String dataLine = String.format(Locale.US, "%s,%s,%s,%s,%s,%s,%.2f%n",
                    req.getIdCita(), req.getPaciente(), req.getCorreo(),
                    req.getEspecialidad(), req.getFechaCita(), req.getSede(), req.getValor());
            exchange.setProperty("csvDataLine", dataLine);
        };
    }

    /**
     * RF5: Construye la línea estructurada del log de errores a partir del Exchange.
     */
    private Processor buildErrorLogProcessor() {
        return exchange -> {
            Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            String exceptionMsg = cause != null ? cause.getMessage() : "Error desconocido";

            Object bodyObj = exchange.getProperty("originalRequest");
            if (bodyObj == null) {
                bodyObj = exchange.getIn().getBody();
            }

            String idCita = (bodyObj instanceof CitaRequest req) ? req.getIdCita() : "N/A";
            String payload = bodyObj != null ? bodyObj.toString() : "null";

            String errorLine = String.format("[%s] | idCita=%s | motivo=%s | payload=%s%n",
                    LocalDateTime.now(), idCita, exceptionMsg, payload);

            exchange.getIn().setBody(errorLine);
        };
    }
}
