package edu.udla.integracion.progreso2.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEvent {
    private String idCita;
    private String paciente;
    private String correo;
    private String especialidad;
    private String fechaCita;
    private String sede;
    
    @Builder.Default
    private String tipoEvento = "CITA_CONFIRMADA";
}
