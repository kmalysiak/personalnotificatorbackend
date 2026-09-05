# Notificator Backend

A Spring Boot backend service that bridges **Home Assistant** events with mobile clients. It listens to HA events via RabbitMQ, processes them, and delivers real-time notifications through **WebSocket** or **Firebase Cloud Messaging (FCM)**.

---

## Architecture

```
Home Assistant
      │
      ▼
  RabbitMQ
  (ha_queue_last_hr)
      │
      ▼
HaQueueConsumer
      │
      ▼  checks: notifyMobile, notifyFreq, recipients
HaEventService
      │
      ├──► FreeMarker template rendering
      │
      ▼
NotificationManager
      │
      ├──► WebSocket broadcast (connected clients)
      └──► FCM push (offline clients)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2.5, Java 21 |
| Real-time | Spring WebSocket |
| Messaging | Spring AMQP / RabbitMQ |
| Database | PostgreSQL + Spring Data JPA |
| Push notifications | Firebase Admin SDK 9.4.1 |
| Templating | FreeMarker 2.3.34 |
| Auth | Firebase JWT verification |
| Container | Docker, Jib Maven Plugin |

---

## Features

- **Real-time WebSocket delivery** — authenticated sessions tracked via BiMap (Firebase UID + FCM token)
- **FCM fallback** — push notifications to offline clients via Firebase
- **Flexible notification frequency** — ISO-8601 duration/period per entity (e.g. `PT30M`, `P1D`)
- **FreeMarker templates** — custom notification message rendering per entity
- **Notification audit log** — full history of sent notifications
- **Keep-alive ping scheduler** — prevents WebSocket session drops
- **Firebase JWT authentication** — only `password` sign-in provider accepted

---

## Project Structure

```
src/main/java/pl/kmalysiak/notificator/
├── controller/
│   ├── AuthController.java          # POST /api/register, GET /api/heartbeat
│   └── PushNotificationController.java
├── service/
│   ├── HaEventService.java          # HA entity state updates & notification trigger
│   ├── NotificationManager.java     # Broadcast logic
│   ├── NotificationService.java     # FCM & WebSocket sending
│   ├── NotificationMapper.java      # FreeMarker template rendering
│   ├── TokenVerifierService.java    # Firebase JWT verification
│   ├── UserTokenService.java        # Token lifecycle management
│   └── ws/
│       ├── NotificationHandler.java # WebSocket text handler
│       ├── SessionRegistry.java     # Active session BiMap
│       ├── PingScheduler.java       # Keep-alive pings
│       └── WsWakeUpService.java     # Wake-up for disconnected clients
├── rabbit/
│   ├── HaQueueConsumer.java         # @RabbitListener for HA events
│   ├── ConnectionConfig.java
│   └── RoutingConfig.java
├── model/                           # JPA entities & records
├── dto/
└── repo/                            # Spring Data repositories
```

---

## API Reference

### Auth

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/register` | Register FCM token (requires Firebase ID token) |
| `GET` | `/api/heartbeat` | Health check |

**Register request:**
```json
{
  "fcmToken": "<device-fcm-token>"
}
```
> Requires `Authorization: Bearer <Firebase ID Token>` header.

### WebSocket

| Endpoint | `/ws/notifications` |
|---|---|
| Auth | `Authorization: Bearer <Firebase ID Token>` |
| Header | `X-FCM-Token: <device-fcm-token>` |

**Incoming message example:**
```json
{ "type": "get_number" }
```

**Outgoing notification payload:**
```json
{
  "entityFriendlyName": "Kitchen light",
  "type": "light",
  "msg": "Kitchen light turned on",
  "timestamp": 1748600000
}
```

---

## Database Schema

```
user_token               ha_entity
─────────────────        ──────────────────────────────
userFirebaseGuid PK      id PK
fcmToken         PK      entityId
connected                notifyMobile
retryCount               notifyFreq (ISO-8601)
email                    state / prevState
                         recipientEmails
                         notificationTemplate FK
notification_template    ha_notification_log_entity
─────────────────────    ──────────────────────────
id PK                    id PK
statusTemplate           userFirebaseGuid
                         entityId
                         message
                         sentAt
```

---

## Configuration

The application is configured via environment variables. Create a `.env` file (see example below):

```env
# Firebase
PROJECT_ID=your-firebase-project-id
CRED_FILE=/app/config/firebase-credentials.json

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=notificator
DB_USER=postgres
DB_PASS=secret

# RabbitMQ
RABBIT_HOST=localhost
RABBIT_PORT=5672
RABBIT_USER=guest
RABBIT_PASS=guest

# App
ADMIN_EMAIL=admin@example.com
WS_BS_INT=30000
```

---

## Running with Docker Compose

```bash
# Build the image
./mvnw package -DskipTests

# Start all services
docker compose up -d
```

The service exposes port **8080**.

Logs are written to `/app/logs/` with 7-day rolling retention.

---

## Building

```bash
# Build JAR
./mvnw clean package

# Build Docker image via Jib (no Docker daemon required)
./mvnw jib:build
```

Requires Java 21.

---

## Notification Frequency Control

Each Home Assistant entity can define `notifyFreq` to throttle duplicate notifications:

| Format | Example | Meaning |
|---|---|---|
| ISO Duration | `PT30M` | Don't notify twice within 30 minutes |
| ISO Period | `P1D` | Don't notify twice within 1 day |
| _(default)_ | `P100Y` | Effectively suppress all duplicates |

---

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

For commercial licensing inquiries, contact [kmalysiak.pl@gmail.com](mailto:kmalysiak.pl@gmail.com).
