package edu.udla.integracion.progreso2.routes;

import edu.udla.integracion.progreso2.model.AppointmentEvent;
import edu.udla.integracion.progreso2.model.CitaRequest;
import edu.udla.integracion.progreso2.model.BillingMessage;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CitaIntegrationRoute extends RouteBuilder {

    @Value("${citas.path.auditoria-csv}")
    private String auditoriaCsvPath;

    @Value("${citas.path.rechazadas-log}")
    private String rechazadasLogPath;

    @Override
    public void configure() throws Exception {
        // Extraer directorio y nombre de archivo para el componente file de Camel
        int lastSlash = auditoriaCsvPath.lastIndexOf('/');
        if (lastSlash == -1) {
            lastSlash = auditoriaCsvPath.lastIndexOf('\\');
        }
        String dir = lastSlash != -1 ? auditoriaCsvPath.substring(0, lastSlash) : "data/outbox";
        String fileName = lastSlash != -1 ? auditoriaCsvPath.substring(lastSlash + 1) : "auditoria-citas.csv";

        // Extraer directorio y nombre de archivo para el log de errores
        int lastSlashError = rechazadasLogPath.lastIndexOf('/');
        if (lastSlashError == -1) {
            lastSlashError = rechazadasLogPath.lastIndexOf('\\');
        }
        String errorDir = lastSlashError != -1 ? rechazadasLogPath.substring(0, lastSlashError) : "data/errors";
        String errorFileName = lastSlashError != -1 ? rechazadasLogPath.substring(lastSlashError + 1) : "citas-rechazadas.log";

        // =========================================================================
        // RF5: Manejo básico de errores a nivel de Camel
        // =========================================================================
        onException(Exception.class)
            .handled(true)
            .maximumRedeliveries(2)
            .redeliveryDelay(1000)
            .log(org.apache.camel.LoggingLevel.ERROR, "Error procesando cita: ${exception.message}")
            .process(exchange -> {
                Exception cause = exchange.getProperty(org.apache.camel.Exchange.EXCEPTION_CAUGHT, Exception.class);
                String exceptionMsg = cause != null ? cause.getMessage() : "Error desconocido";

                Object bodyObj = exchange.getProperty("originalRequest");
                if (bodyObj == null) {
                    bodyObj = exchange.getIn().getBody();
                }

                String payloadStr = bodyObj != null ? bodyObj.toString() : "null";
                String idCita = "N/A";
                if (bodyObj instanceof CitaRequest) {
                    idCita = ((CitaRequest) bodyObj).getIdCita();
                }

                String timestamp = java.time.LocalDateTime.now().toString();
                String errorLogLine = String.format("[%s] | idCita=%s | motivo=%s | payload=%s%n",
                        timestamp, idCita, exceptionMsg, payloadStr);

                exchange.getIn().setBody(errorLogLine);
            })
            .to("file:" + errorDir + "?fileName=" + errorFileName + "&fileExist=Append");

        // =========================================================================
        // Ruta Principal (Orquestación con Multicast EIP)
        // =========================================================================
        from("direct:startIntegration")
            .routeId("citaIntegrationRoute")
            .log("Procesando cita recibida: ${body}")
            .process(exchange -> {
                exchange.setProperty("originalRequest", exchange.getIn().getBody());
            })
            .multicast().shareUnitOfWork()
                .to("direct:sendToBilling", "direct:sendToPubSub", "direct:writeToCsv")
            .end();

        // =========================================================================
        // RF2: Sub-ruta para Facturación (Point-to-Point)
        // =========================================================================
        from("direct:sendToBilling")
            .routeId("billingSubRoute")
            .process(exchange -> {
                CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
                BillingMessage billing = BillingMessage.builder()
                        .idCita(req.getIdCita())
                        .paciente(req.getPaciente())
                        .especialidad(req.getEspecialidad())
                        .valor(req.getValor())
                        .build();
                exchange.getIn().setBody(billing);
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("spring-rabbitmq:billing-exchange?routingKey=billing-routing-key")
            .log("Mensaje P2P de facturación enviado exitosamente a RabbitMQ.");

        // =========================================================================
        // RF3: Sub-ruta para Eventos (Publish/Subscribe)
        // =========================================================================
        from("direct:sendToPubSub")
            .routeId("pubSubSubRoute")
            .process(exchange -> {
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
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("spring-rabbitmq:appointments.events")
            .log("Evento Pub/Sub publicado exitosamente a RabbitMQ.");

        // =========================================================================
        // RF4: Sub-ruta para Auditoría en Archivo CSV (File Transfer)
        // =========================================================================
        from("direct:writeToCsv")
            .routeId("csvAuditSubRoute")
            .process(exchange -> {
                CitaRequest req = exchange.getIn().getBody(CitaRequest.class);
                String csvLine = String.format(java.util.Locale.US, "%s,%s,%s,%s,%s,%s,%.2f%n",
                        req.getIdCita(),
                        req.getPaciente(),
                        req.getCorreo(),
                        req.getEspecialidad(),
                        req.getFechaCita(),
                        req.getSede(),
                        req.getValor()
                );
                exchange.getIn().setBody(csvLine);
            })
            .to("file:" + dir + "?fileName=" + fileName + "&fileExist=Append")
            .log("Registro de cita escrito exitosamente en el archivo CSV de auditoría.");
    }
}
