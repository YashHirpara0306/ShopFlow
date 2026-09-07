# Notification Service

Port `8087` · database `shopflow_notification` (PostgreSQL) · admin/internal only · **simulated email/SMS** · [Swagger UI](http://localhost:8087/swagger-ui.html)

## Endpoints (all `ROLE_ADMIN`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/notifications` | `{eventId, userId, channel, template, data}` → create + attempt delivery. **Idempotent per `(eventId, channel)`** — a redelivered event does not resend. `201`. |
| `GET` | `/api/notifications?userId=&page=&size=` | A user's notification history. |
| `POST` | `/api/notifications/{id}/retry` | Manually re-attempt a failed notification. |

## Delivery & retry

- `SimulatedNotificationSender`: template `"FAIL"` always fails; otherwise fails with probability `shopflow.notification.fail-rate` (default `0`).
- Every attempt writes a `notification_attempt` row (attempt no, outcome, error).
- `NotificationRetryJob` (`@Scheduled`, `shopflow.notification.retry-scan-interval`, default `60s`) re-attempts anything not `SENT` with `attempts < shopflow.notification.max-attempts` (default `5`).
- Exhausted → stays `FAILED` (Phase 11: routed to `notification-events.DLT`).

## Model

`notification(id, event_id, user_id, channel EMAIL|SMS, template, data, status PENDING|SENT|FAILED, attempts, last_error, sent_at)` with `UNIQUE(event_id, channel)`,
`notification_attempt(id identity, notification_id FK, attempt_no, outcome, error)`, `processed_event`.
Flyway `V1`; Hibernate `validate`.

## Saga role (Phase 11)

The `POST` endpoint is replaced by a Kafka consumer on `notification-events` / `order-events` /
`payment-events` (consumes `USER_REGISTERED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `PAYMENT_FAILED`,
`REFUND_COMPLETED`). Consumer idempotency uses `processed_event`.
