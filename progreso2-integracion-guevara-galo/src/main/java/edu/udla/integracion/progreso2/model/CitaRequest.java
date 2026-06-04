package edu.udla.integracion.progreso2.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitaRequest {

    @NotBlank(message = "El idCita no puede estar vacío")
    private String idCita;

    @NotBlank(message = "El paciente no puede estar vacío")
    private String paciente;

    @NotBlank(message = "El correo no puede estar vacío")
    @Email(message = "El formato del correo es inválido")
    private String correo;

    @NotBlank(message = "La especialidad no puede estar vacía")
    private String especialidad;

    @NotBlank(message = "La fechaCita no puede estar vacía")
    private String fechaCita;

    @NotBlank(message = "La sede no puede estar vacía")
    private String sede;

    @NotNull(message = "El valor no puede ser nulo")
    @DecimalMin(value = "0.01", message = "El valor debe ser estrictamente mayor a 0")
    private Double valor;
}
