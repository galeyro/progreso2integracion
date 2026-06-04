package edu.udla.integracion.progreso2.controller;

import edu.udla.integracion.progreso2.model.CitaRequest;
import edu.udla.integracion.progreso2.service.CitaValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Citas Médicas", description = "API REST para el agendamiento y procesamiento de citas médicas en el sistema Salud360")
@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaValidationService validationService;
    private final ProducerTemplate producerTemplate;

    public CitaController(CitaValidationService validationService, ProducerTemplate producerTemplate) {
        this.validationService = validationService;
        this.producerTemplate = producerTemplate;
    }

    @Operation(
        summary = "Crear y procesar una cita médica",
        description = "Recibe una solicitud de cita médica, la valida y la despacha de forma asíncrona al motor de integración Apache Camel. " +
                      "Camel se encarga de: enviar a la cola de facturación (Point-to-Point), publicar el evento de cita (Pub/Sub), " +
                      "y escribir el registro en el archivo CSV de auditoría (File Transfer)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Cita recibida y en procesamiento"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos: campos vacíos, correo con formato incorrecto o valor menor o igual a 0")
    })
    @PostMapping
    public ResponseEntity<Map<String, Object>> crearCita(@Valid @RequestBody CitaRequest request) {
        // Validación programática adicional de negocio
        validationService.validate(request);

        // Envío asíncrono al flujo de Apache Camel
        producerTemplate.asyncSendBody("direct:startIntegration", request);

        // Respuesta HTTP 202 Accepted
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Accepted");
        response.put("message", "La cita ha sido recibida exitosamente y se encuentra en procesamiento.");
        response.put("idCita", request.getIdCita());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
