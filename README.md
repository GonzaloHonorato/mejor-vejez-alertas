# mejor-vejez-alertas

Microservicio de alertas y notificaciones de Mejor Vejez: detecta tareas atrasadas, genera las alertas preventivas y envía los correos (avisos y reportes) con reintentos. No tiene base de datos: consulta y registra todo en el servicio core por REST.

Forma parte de la arquitectura de microservicios de Mejor Vejez:

| Microservicio | Repositorio | Puerto | Base de datos |
|---|---|---|---|
| Core (reglas del cuidado) | `mejor-vejez-api` | 8080 | Oracle (única) |
| Alertas y notificaciones | `mejor-vejez-alertas` | 8081 | No |
| Reportes | `mejor-vejez-reportes` | 8082 | No |

La aplicación web (`mejor-vejez-web`) solo habla con el core. Los microservicios se comunican por REST en rutas `/api/interno/**`, protegidas con la cabecera `X-Clave-Servicio` (variable `CLAVE_SERVICIO`, la misma en los tres).

## Requisitos

- Java 21 o superior (no hace falta instalar Maven: se usa `./mvnw`).

## Desarrollo

```sh
./mvnw spring-boot:run      # http://localhost:8081/api/salud
./mvnw test
```

Variables de entorno: ver `.env.example`.

## Despliegue en Dokploy

App de tipo **Compose** apuntando a este repositorio, archivo `docker-compose.yml`. En la pestaña Environment definir las variables de `.env.example`. No necesita dominio público: el core lo llama por la red interna `dokploy-network`.
