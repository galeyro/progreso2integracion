package edu.udla.integracion.progreso2.service;

import edu.udla.integracion.progreso2.exception.CitaValidationException;
import edu.udla.integracion.progreso2.model.CitaRequest;
import org.springframework.stereotype.Service;

@Service
public class CitaValidationService {

    public void validate(CitaRequest request) {
        if (request == null) {
            throw new CitaValidationException("La solicitud de cita no puede ser nula");
        }

        // Ejemplo de regla de negocio adicional:
        if (request.getValor() > 10000.0) {
            throw new CitaValidationException("El valor de la cita excede el límite máximo permitido de 10,000");
        }
    }
}
