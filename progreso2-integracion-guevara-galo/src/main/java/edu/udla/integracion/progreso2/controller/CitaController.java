package edu.udla.integracion.progreso2.controller;

import edu.udla.integracion.progreso2.model.CitaRequest;
import edu.udla.integracion.progreso2.service.CitaValidationService;
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

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaValidationService validationService;
    private final ProducerTemplate producerTemplate;

    public CitaController(CitaValidationService validationService, ProducerTemplate producerTemplate) {
        this.validationService = validationService;
        this.producerTemplate = producerTemplate;
    }

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
