# mejor-vejez-alertas

Microservicio de alertas y notificaciones de Mejor Vejez: detecta tareas atrasadas, genera las alertas preventivas y envía los correos (avisos y reportes semanales) con reintentos. No tiene base de datos: consulta y registra todo en el servicio core por REST.

Forma parte de la arquitectura de microservicios de Mejor Vejez:

| Microservicio | Repositorio | Puerto | Base de datos |
|---|---|---|---|
| Core (reglas del cuidado) | `mejor-vejez-api` | 8080 | Oracle (única) |
| Alertas y notificaciones | `mejor-vejez-alertas` | 8081 | No |
| Reportes | `mejor-vejez-reportes` | 8082 | No |

La aplicación web (`mejor-vejez-web`, puerto 4200) solo habla con el core. Los microservicios se comunican por REST en rutas `/api/interno/**`, protegidas con la cabecera `X-Clave-Servicio` (variable `CLAVE_SERVICIO`, la misma en los tres; comparación en tiempo constante, 401 "Clave de servicio inválida" si falta o no coincide).

## Requisitos

- Java 21 o superior (no hace falta instalar Maven: se usa `./mvnw`).

## Rutas que expone

| Método y ruta | Auth | Quién la llama | Para qué |
|---|---|---|---|
| `GET /api/salud` | pública | Dokploy / humano | `200` si el proceso vive (healthcheck) |
| `POST /api/interno/procesos/deteccion-atrasos` | `X-Clave-Servicio` | Core (`/api/administracion/procesos/deteccion-atrasos`); el cron interno llama al servicio directamente, no esta ruta | Corre la detección de atrasos; devuelve `ResumenCorrida(idEjecucion, creadas, errores, resumen)` |
| `POST /api/interno/procesos/alertas-preventivas` | `X-Clave-Servicio` | Core (`/api/administracion/procesos/alertas-preventivas`); el cron interno llama al servicio directamente, no esta ruta | Corre las alertas preventivas (citas y reposición de medicamentos); mismo `ResumenCorrida` |
| `POST /api/interno/envios-reporte` | `X-Clave-Servicio` | `mejor-vejez-reportes` | Envía el correo del reporte semanal (PDF adjunto en base64) a cada destinatario del grupo; un destinatario que falla no detiene a los demás; nunca guarda el PDF |

## Rutas que consume

Todas en el core (`CORE_URL`), con la cabecera `X-Clave-Servicio`:

- `GET /api/interno/alertas/candidatos-atraso` — tareas candidatas a alerta de atraso.
- `GET /api/interno/alertas/candidatos-preventivos` — citas próximas y reposiciones de medicamentos candidatas.
- `POST /api/interno/alertas` — crea una alerta (idempotente por clave).
- `GET /api/interno/alertas/envios-pendientes` — alertas con intentos de correo pendientes.
- `POST /api/interno/alertas/{idAlerta}/intentos/reservar` — reserva los intentos de notificación de una alerta.
- `PUT /api/interno/intentos/{idIntento}` — informa el resultado (`ENVIADO`/`FALLIDO`) de un intento.
- `POST /api/interno/ejecuciones` y `PUT /api/interno/ejecuciones/{idEjecucion}` — registra el inicio y el cierre de cada corrida.

## Variables de entorno

Ver también `.env.example`.

| Variable | Obligatoria | Por omisión | Uso |
|---|---|---|---|
| `SERVER_PORT` | no | `8081` | Puerto HTTP (fijo en `8081` en el `docker-compose.yml` de Dokploy) |
| `CORE_URL` | no | `http://localhost:8080` (local) / `http://mejor-vejez-api:8080` (Dokploy) | URL interna del servicio core |
| `CLAVE_SERVICIO` | **sí** | — (`:?falta CLAVE_SERVICIO` en el compose de Dokploy) | Cabecera `X-Clave-Servicio` de las llamadas internas; la misma en core, alertas y reportes |
| `PROCESOS_HABILITADOS` | no | `true` | Apaga los procesos programados (detección de atrasos y alertas preventivas) sin detener el servicio; la corrida manual sigue disponible |
| `ALERTAS_HORAS_RETROSPECTIVA` | no | `48` | Ventana retrospectiva de la detección de atrasos |
| `ALERTAS_HORAS_CITA` | no | `24` | Ventana de aviso previo de citas de control médico |
| `ALERTAS_DIAS_REPOSICION` | no | `3` | Días de anticipación para avisar reposición de medicamentos |
| `ALERTAS_CRON_DETECCION` | no | `0 */5 * * * *` | Cron de la detección de atrasos (America/Santiago) |
| `ALERTAS_CRON_PREVENTIVAS` | no | `0 10 * * * *` | Cron de las alertas preventivas (America/Santiago) |
| `MAIL_HOST` | no (sí para enviar correos) | vacío | Servidor SMTP. Vacío: la detección/preventivas no reservan ningún intento en el core (quedan pendientes, sin gastar sus 3 intentos, hasta que se configure); `envios-reporte` sí responde con cada destinatario `enviado=false` ("El envío de correo no está configurado."); `LOG.warn` al arrancar y en cada corrida mientras falte |
| `MAIL_PORT` | no | `587` | Puerto SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | no | vacío | Credenciales SMTP; solo en el panel de Dokploy, nunca en el repo |
| `MAIL_SMTP_AUTH` | no | `true` | Autenticación SMTP |
| `MAIL_STARTTLS` | no | `true` | STARTTLS |
| `MAIL_TIMEOUT_MS` | no | `10000` | Timeout SMTP de conexión, lectura y escritura (ms); un SMTP colgado termina en intento `FALLIDO` |
| `MAIL_REMITENTE` | no (sí para enviar correos) | vacío | Dirección `From` de los correos; vacío: igual que sin `MAIL_HOST` |
| `APP_URL` | no | `http://localhost:4200` (local) / URL pública del front (Dokploy) | URL del front para el enlace de la alerta en el correo |
| `JAVA_OPTS` | no | `-XX:MaxRAMPercentage=75` | Opciones de la JVM (fijado en el `docker-compose.yml` de Dokploy) |

## Desarrollo y pruebas en local

```sh
./mvnw spring-boot:run      # http://localhost:8081/api/salud
./mvnw test                 # sin Docker: usa MockWebServer (simula el core) y GreenMail (simula el SMTP)
```

Para que el servicio consulte el core y envíe correo a Mailpit en local (ver la guía de instalación: `planes/guia-instalacion-local.md`):

```sh
CORE_URL=http://localhost:8080 \
CLAVE_SERVICIO=<la misma clave que usan core y reportes> \
MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_STARTTLS=false \
MAIL_REMITENTE=alertas@mejorvejez.local \
./mvnw spring-boot:run
```

Para probar a mano, con el core y Mailpit levantados:

```sh
curl -s -X POST http://localhost:8081/api/interno/procesos/deteccion-atrasos \
  -H "X-Clave-Servicio: <la clave>"
```

(o, equivalente, disparando la ruta de administración del core — ver la guía de instalación local). El correo (si corresponde) aparece en la bandeja de Mailpit, <http://localhost:8025>.

## Despliegue en Dokploy

App de tipo **Compose** apuntando a este repositorio, archivo `docker-compose.yml`.

1. **Environment:** las variables de arriba. `CLAVE_SERVICIO` es obligatoria y debe ser **la misma** que en `mejor-vejez-api` y `mejor-vejez-reportes`. Las credenciales SMTP (`MAIL_USERNAME`/`MAIL_PASSWORD`) solo en el panel de Dokploy, nunca en el repo.
2. **Domains:** no se configura ninguno. Sus rutas `/api/interno/**` solo las llaman el core y reportes, por la red interna `dokploy-network`.
3. **Una sola réplica: no escalar horizontalmente.** La garantía "al menos una vez" de los reintentos de correo y el registro en memoria de `claveEnvio` en curso (409) asumen un solo proceso; dos réplicas duplicarían además los disparos programados.
4. Deploy. Verificar desde el propio contenedor o con `docker exec`: `curl http://localhost:8081/api/salud` (no tiene dominio público).

### Notas de las rutas internas

- `POST /api/interno/envios-reporte` envía los correos del grupo uno tras otro, cada uno con hasta 10 s de conexión + 10 s de lectura + 10 s de escritura (`MAIL_TIMEOUT_MS`). Con un SMTP lento (pero que sí entrega) y un grupo grande, el tiempo total puede superar el timeout de lectura con el que `mejor-vejez-reportes` llama a esta ruta: el reporte se audita como fallo aunque los correos sí se hayan enviado, y la corrida horaria siguiente los duplica. Con grupos pequeños es poco probable, y el duplicado está dentro de lo que acepta la garantía "al menos una vez".
