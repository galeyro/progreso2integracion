package edu.udla.integracion.progreso2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingMessage {
    private String idCita;
    private String paciente;
    private String especialidad;
    private Double valor;
    
    @Builder.Default
    private String tipoMensaje = "COMANDO_FACTURAR_CITA";
}
