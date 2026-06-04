# Salud360 - Sistema de Integración de Citas Médicas

Este proyecto implementa el backend y la arquitectura de integración para el sistema **Salud360**, encargándose del procesamiento asíncrono, la validación y distribución de citas médicas.

---

## 1. Información del Estudiante
* **Nombre**: Galo Guevara
* **Materia**: Integración de Plataformas / Sistemas Distribuidos

---

## 2. Descripción de la Solución
**Salud360** es un sistema de integración que recibe solicitudes de citas médicas a través de una API REST de Spring Boot. Las solicitudes se validan sintácticamente (con Jakarta Bean Validation) y semánticamente (reglas de negocio en el servicio). Una vez aprobadas, se despachan de forma asíncrona hacia el motor de integración **Apache Camel 4**, el cual se encargará de:
* Canalizar datos mediante colas **Point-to-Point** (ej. facturación).
* Notificar mediante patrones **Publish/Subscribe** (ej. eventos de citas).
* Persistir registros en archivos locales (**File Transfer**).

---

## 3. Tecnologías Utilizadas
* **Java 21**
* **Spring Boot 4.0.6 / 3.x**
* **Apache Camel 4** (`camel-spring-boot-starter`, `camel-spring-rabbitmq-starter`, `camel-jackson-starter`)
* **RabbitMQ 3.12-management**
* **Docker / Docker Compose**
* **Lombok** & **Jakarta Validation API**

---

## 4. Instrucciones para Levantar RabbitMQ
El entorno de mensajería se gestiona a través de Docker. En la raíz de la carpeta `progreso2-integracion-guevara-galo` se encuentra el archivo `docker-compose.yml`.

Para iniciar el contenedor de RabbitMQ, ejecuta:
```bash
cd progreso2-integracion-guevara-galo
docker compose up -d
```

### Credenciales y Puertos de RabbitMQ:
* **Host**: `localhost`
* **Puerto AMQP (mensajería)**: `5672`
* **Consola de Administración (Web UI)**: [http://localhost:15672](http://localhost:15672)
* **Usuario**: `guest`
* **Contraseña**: `guest`

---

## 5. Instrucciones para Ejecutar la Aplicación
Una vez que el contenedor de RabbitMQ esté corriendo:

1. Ingresa a la carpeta del proyecto:
   ```bash
   cd progreso2-integracion-guevara-galo
   ```
2. Compila y ejecuta la aplicación de Spring Boot:
   ```bash
   ./mvnw spring-boot:run
   ```
La aplicación se levantará en el puerto **8080**.

---

## 6. Endpoints Disponibles

### Crear Cita Médica
* **Método**: `POST`
* **URL**: `http://localhost:8080/api/citas`
* **Headers**: `Content-Type: application/json`

---

## 7. Ejemplo de Request Válido
Una petición con todos los campos correctos y un `valor` numérico mayor a 0:

**Cuerpo (JSON):**
```json
{
  "idCita": "CITA-78923",
  "paciente": "Galo Guevara",
  "correo": "galo.guevara@udla.edu.ec",
  "especialidad": "Pediatría",
  "fechaCita": "2026-06-10 09:00",
  "sede": "Sede UDLAPark",
  "valor": 45.00
}
```

**Respuesta Esperada (HTTP 202 Accepted):**
```json
{
  "status": "Accepted",
  "message": "La cita ha sido recibida exitosamente y se encuentra en procesamiento.",
  "idCita": "CITA-78923"
}
```

---

## 8. Ejemplo de Request Inválido
Una petición con formato de correo incorrecto, campos vacíos y valor de la cita igual o menor a 0:

**Cuerpo (JSON):**
```json
{
  "idCita": "",
  "paciente": "",
  "correo": "correo_no_valido",
  "especialidad": "Odontología",
  "fechaCita": "2026-06-11 14:00",
  "sede": "",
  "valor": -10.00
}
```

**Respuesta Esperada (HTTP 400 Bad Request):**
```json
{
  "timestamp": "2026-06-03T19:46:10.123",
  "status": 400,
  "error": "Bad Request",
  "errors": {
    "idCita": "El idCita no puede estar vacío",
    "paciente": "El nombre del paciente no puede estar vacío",
    "correo": "El formato del correo es inválido",
    "sede": "La sede no puede estar vacía",
    "valor": "El valor debe ser estrictamente mayor a 0"
  }
}
```

---

## 9. Arquitectura de Integración (Patrones Aplicados)

### A. Point-to-Point (Punto a Punto)
Se aplicará en la integración con el subsistema de facturación. Los datos de cobro de cada cita aprobada se enviarán de forma directa a la cola `billing.queue` de RabbitMQ. Cada mensaje representa una transacción que debe ser consumida y procesada exactamente por un solo receptor (el servicio de facturación).

### B. Publish/Subscribe (Publicador/Suscriptor)
Se aplicará para el envío de eventos de citas agendadas (`appointments.events`). Se publicará el evento en un exchange de RabbitMQ para que múltiples sistemas interesados (como el sistema de envío de correos, el recordatorio por SMS o la app móvil del paciente) puedan suscribirse de manera independiente a sus propias colas y reaccionar al evento.

### C. Transferencia de Archivos (File Transfer)
Se implementa la persistencia local de datos en el sistema de archivos:
* **Auditoría**: Las citas válidas e integradas se formatean a CSV y se añaden al archivo `data/outbox/auditoria-citas.csv`.
* **Rechazos**: Las citas que fallen alguna validación en las rutas se guardan en `data/errors/citas-rechazadas.log`.

### D. Manejo de Errores
El manejo de errores se ejecuta en dos niveles:
1. **Filtro de Entrada (API REST)**: `GlobalExceptionHandler` captura los errores de esquema y formato HTTP devolviendo un estado `400 Bad Request` limpio.
2. **Capa de Integración (Camel Route)**: Se definirá un bloque de manejo de excepciones (`onException(...)`) en Camel para capturar errores de procesamiento, enrutar la carga a la carpeta de errores `data/errors/citas-rechazadas.log` y asegurar que no se pierdan transacciones utilizando políticas de reintento.

---

## 10. Evidencia Esperada para Verificar el Funcionamiento
Para constatar la correcta implementación y flujo de integración, se debe verificar:
1. **Consola de Spring Boot**: Se verá el log de Camel registrando cada entrada:
   `[citaIntegrationRoute] Procesando cita recibida: CitaRequest(idCita=CITA-78923, paciente=Galo Guevara, ...)`
2. **Consola Web de RabbitMQ**: Confirmación de la conexión del cliente y creación automática/manual de las colas y exchanges en `http://localhost:15672`.
3. **Persistencia**: La creación/escritura de los registros en los directorios [data/outbox/](file:///c:/Users/G_Laptop/progreso2integracion/data/outbox) y [data/errors/](file:///c:/Users/G_Laptop/progreso2integracion/data/errors).
