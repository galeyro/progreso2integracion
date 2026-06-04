package edu.udla.integracion.progreso2.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Solicitud de cita médica para el sistema Salud360")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitaRequest {

    @Schema(description = "Identificador único de la cita", example = "CITA-1001")
    @NotBlank(message = "El idCita no puede estar vacío")
    private String idCita;

    @Schema(description = "Nombre completo del paciente", example = "Ana Torres")
    @NotBlank(message = "El nombre del paciente no puede estar vacío")
    private String paciente;

    @Schema(description = "Correo electrónico del paciente", example = "ana.torres@email.com")
    @NotBlank(message = "El correo no puede estar vacío")
    @Email(message = "El formato del correo es inválido")
    private String correo;

    @Schema(description = "Especialidad médica requerida", example = "Cardiología")
    @NotBlank(message = "La especialidad no puede estar vacía")
    private String especialidad;

    @Schema(description = "Fecha de la cita (formato: YYYY-MM-DD)", example = "2026-06-15")
    @NotBlank(message = "La fechaCita no puede estar vacía")
    private String fechaCita;

    @Schema(description = "Sede donde se realizará la cita", example = "Centro Norte")
    @NotBlank(message = "La sede no puede estar vacía")
    private String sede;

    @Schema(description = "Valor monetario de la cita (debe ser mayor a 0)", example = "45.50")
    @NotNull(message = "El valor no puede ser nulo")
    @DecimalMin(value = "0.01", message = "El valor debe ser estrictamente mayor a 0")
    private Double valor;
}
