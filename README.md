# Salud360 - Sistema de Integración de Citas Médicas

---

## 1. Nombre del Estudiante

**Galo Guevara**  
Materia: Integración de Plataformas / Sistemas Distribuidos

---

## 2. Descripción de la Solución

**Salud360** es un sistema de integración que recibe solicitudes de citas médicas a través de una API REST construida con Spring Boot. Cada solicitud es validada en dos niveles: sintácticamente mediante Jakarta Bean Validation y semánticamente con reglas de negocio personalizadas. Una vez aprobada, la cita es despachada de forma asíncrona al motor de integración **Apache Camel 4**, el cual aplica tres patrones de integración empresarial en paralelo mediante el patrón **Multicast EIP**:

- **Point-to-Point**: envía el comando de facturación a una cola dedicada en RabbitMQ.
- **Publish/Subscribe**: publica el evento de cita en un exchange Fanout de RabbitMQ.
- **File Transfer**: persiste el registro de la cita en un archivo CSV de auditoría.

En caso de error en la capa de integración, Camel reintenta automáticamente hasta 2 veces y registra el fallo en un archivo de log estructurado.

---

## 3. Tecnologías Utilizadas

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 21 | Lenguaje principal |
| Spring Boot | 4.0.6 | Framework base del backend |
| Apache Camel | 4.20.0 | Motor de rutas de integración |
| RabbitMQ | 3.12-management | Broker de mensajería |
| Docker / Docker Compose | - | Gestión del contenedor de RabbitMQ |
| SpringDoc OpenAPI | 3.0.3 | Documentación Swagger de la API |
| Lombok | 1.18.x | Reducción de código boilerplate |
| Jakarta Validation API | 3.x | Validación de campos en el request |

---

## 4. Instrucciones para Levantar RabbitMQ

El entorno de mensajería se gestiona con Docker Compose. El archivo `docker-compose.yml` se encuentra dentro de la carpeta del proyecto Maven.

```bash
cd progreso2-integracion-guevara-galo
docker compose up -d
```

Verifica que el contenedor esté corriendo:

```bash
docker ps
```

### Acceso a la Consola de Administración

| Parámetro | Valor |
|---|---|
| URL Web UI | http://localhost:15672 |
| Puerto AMQP | 5672 |
| Usuario | guest |
| Contraseña | guest |

---

## 5. Instrucciones para Ejecutar la Aplicación

Con el contenedor de RabbitMQ activo, ejecuta desde la carpeta del proyecto:

```bash
cd progreso2-integracion-guevara-galo
./mvnw spring-boot:run
```

La aplicación levantará en el puerto **8080**. Al iniciar, verás en consola:

```
Routes startup (total:4)
    Started citaIntegrationRoute (direct://startIntegration)
    Started billingSubRoute      (direct://sendToBilling)
    Started pubSubSubRoute       (direct://sendToPubSub)
    Started csvAuditSubRoute     (direct://writeToCsv)
Apache Camel 4.20.0 started
>> ¡Inicialización de RabbitMQ completada con éxito!
```

La **documentación Swagger** estará disponible en:  
👉 **http://localhost:8080/swagger-ui/index.html**

---

## 6. Endpoint Disponible

| Campo | Valor |
|---|---|
| Método | `POST` |
| URL | `http://localhost:8080/api/citas` |
| Content-Type | `application/json` |
| Respuesta exitosa | `202 Accepted` |
| Respuesta de error | `400 Bad Request` |

---

## 7. Ejemplo de Request Válido

Request con todos los campos correctos y `valor` mayor a 0:

```json
{
  "idCita": "CITA-1001",
  "paciente": "Ana Torres",
  "correo": "ana.torres@email.com",
  "especialidad": "Cardiología",
  "fechaCita": "2026-06-15",
  "sede": "Centro Norte",
  "valor": 45.50
}
```

**Respuesta esperada — HTTP 202 Accepted:**

```json
{
  "status": "Accepted",
  "message": "La cita ha sido recibida exitosamente y se encuentra en procesamiento.",
  "idCita": "CITA-1001"
}
```

---

## 8. Ejemplo de Request Inválido

Request con correo sin formato válido, campos vacíos y valor menor o igual a 0:

```json
{
  "idCita": "",
  "paciente": "",
  "correo": "correo-sin-formato",
  "especialidad": "Odontología",
  "fechaCita": "2026-06-16",
  "sede": "",
  "valor": -5.00
}
```

**Respuesta esperada — HTTP 400 Bad Request:**

```json
{
  "timestamp": "2026-06-03T20:00:00.000",
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

> La ruta de Apache Camel **no se invoca** ante un `400 Bad Request`. No habrá mensajes en RabbitMQ ni escrituras en el CSV.

---

## 9. Arquitectura de Integración — Patrones Aplicados

### A. Point-to-Point (Facturación)

**Dónde se aplica:** Sub-ruta `billingSubRoute` en `CitaIntegrationRoute.java`.

Cada cita aprobada genera un `BillingMessage` (con `idCita`, `paciente`, `especialidad`, `valor` y el tipo `COMANDO_FACTURAR_CITA`) que se serializa a JSON y se publica en el exchange `billing-exchange` con routing key `billing-routing-key`. Este mensaje es enrutado exclusivamente a la cola `billing.queue`, garantizando que **un único consumidor** (el sistema de facturación) lo procese.

```
CitaRequest → BillingMessage → billing-exchange → billing.queue
```

### B. Publish/Subscribe (Eventos de Citas)

**Dónde se aplica:** Sub-ruta `pubSubSubRoute` en `CitaIntegrationRoute.java`.

Cada cita confirmada genera un `AppointmentEvent` (con todos los campos del paciente y `tipoEvento: CITA_CONFIRMADA`) que se publica en el exchange `appointments.events` de tipo **Fanout**. Este exchange replica automáticamente el mensaje a **todos** los suscriptores vinculados: `notifications.queue` (sistema de notificaciones) y `analytics.queue` (sistema de analítica).

```
CitaRequest → AppointmentEvent → appointments.events (Fanout)
                                        ├── notifications.queue
                                        └── analytics.queue
```

### C. Transferencia de Archivos (File Transfer)

**Dónde se aplica:** Sub-ruta `csvAuditSubRoute` en `CitaIntegrationRoute.java`.

Dos flujos de File Transfer conviven en la ruta:

1. **Auditoría exitosa:** Cada cita integrada correctamente se escribe como línea CSV en `data/outbox/auditoria-citas.csv`. Si el archivo no existe, se genera automáticamente con fila de encabezado usando el EIP `choice()` de Camel.
2. **Registro de rechazos:** Ante cualquier fallo en la integración, el bloque `onException` de Camel escribe una línea estructurada en `data/errors/citas-rechazadas.log` con timestamp, `idCita`, motivo y payload original.

Ambos archivos usan el **componente File de Apache Camel** con `fileExist=Append`.

### D. Manejo de Errores

El sistema implementa manejo de errores en **dos niveles independientes**:

**Nivel 1 — API REST (`GlobalExceptionHandler`):**  
Intercepta errores de validación de esquema (`@Valid`) y reglas de negocio (`CitaValidationException`) antes de que el mensaje llegue a Camel. Devuelve un `400 Bad Request` con detalle de cada campo fallido. Apache Camel no es invocado.

**Nivel 2 — Capa de Integración (`onException` en Camel):**  
Si ocurre un error en cualquier sub-ruta (ej. RabbitMQ no disponible), Camel:
1. Reintenta el envío hasta **2 veces** con 1 segundo de espera entre intentos (`maximumRedeliveries(2)`, `redeliveryDelay(1000)`).
2. Si todos los reintentos fallan, captura la excepción, formatea una línea estructurada con timestamp/idCita/motivo/payload y la persiste en `data/errors/citas-rechazadas.log` mediante el componente File.
3. Marca el Exchange como manejado (`.handled(true)`) para evitar propagación de excepciones sin tratar.

---

## 10. Evidencia Esperada para Verificar el Funcionamiento

### Escenario exitoso (Flujo Principal)

**1. Consola de Spring Boot** — Se observan los logs de las 4 sub-rutas:
```
citaIntegrationRoute : Procesando cita recibida: CitaRequest(idCita=CITA-1001, ...)
billingSubRoute      : Mensaje P2P de facturación enviado exitosamente a RabbitMQ.
pubSubSubRoute       : Evento Pub/Sub publicado exitosamente a RabbitMQ.
csvAuditSubRoute     : Registro de cita escrito exitosamente en el archivo CSV de auditoría.
```

**2. Consola Web de RabbitMQ** (`http://localhost:15672`):
- Cola `billing.queue`: 1 mensaje con `"tipoMensaje": "COMANDO_FACTURAR_CITA"`.
- Cola `notifications.queue`: 1 mensaje con `"tipoEvento": "CITA_CONFIRMADA"`.
- Cola `analytics.queue`: el mismo mensaje que `notifications.queue`.

**3. Archivo CSV de Auditoría** (`data/outbox/auditoria-citas.csv`):
```
idCita,paciente,correo,especialidad,fechaCita,sede,valor
CITA-1001,Ana Torres,ana.torres@email.com,Cardiología,2026-06-15,Centro Norte,45.50
```

### Escenario de error (RabbitMQ apagado)

**1. Respuesta HTTP**: `202 Accepted` inmediato (desacoplamiento asíncrono).

**2. Consola**: Camel ejecuta 2 reintentos antes de registrar el fallo:
```
ERROR ... Error procesando cita: Connection refused: getsockopt
```

**3. Archivo de Log de Rechazos** (`data/errors/citas-rechazadas.log`):
```
[2026-06-03T20:17:56] | idCita=CITA-1001 | motivo=java.net.ConnectException: Connection refused: getsockopt | payload=CitaRequest(idCita=CITA-1001, ...)
```

Las capturas de pantalla de cada evidencia se encuentran en la carpeta `docs/capturas/`.
