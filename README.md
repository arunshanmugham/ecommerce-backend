# ecommerce-backend

E-commerce order fulfilment backend in **Java 17 / Spring Boot 3.5**, orchestrated with the
**Temporal Java SDK** and persisted in **MySQL**.

When an order is placed, a Temporal workflow runs the complete order flow:

```
POST /api/orders ──► OrderWorkflow (ORDER_TASK_QUEUE)
                       1. processPayment     ──► Payment service       (PAYMENT_TASK_QUEUE)
                       2. reserveInventory   ──► Inventory service     (INVENTORY_TASK_QUEUE)
                       3. shipOrder          ──► Shipping service      (SHIPPING_TASK_QUEUE)
                       4. notifyCustomer     ──► Notification service  (NOTIFICATION_TASK_QUEUE)
                       + updateOrder after each step ──► Order service (ORDER_TASK_QUEUE)
```

- **Each service has its own Temporal task queue.** The workflow calls the services through Temporal,
  never directly, so each service can run (and scale) in its own process.
- **Retries:** transient errors are retried automatically (1s initial backoff, x2, up to 5 attempts).
- **Saga / compensation:** permanent failures (card declined, out of stock, undeliverable address)
  are non-retryable. The workflow then releases reserved stock, refunds the payment, marks the order
  `FAILED` and emails the customer.
- **Idempotent activities:** every service keys its work on the order number, so retries never
  double-charge, double-reserve, double-ship or send duplicate emails.
- **Live status:** the workflow exposes a `getProgress()` query, surfaced at
  `GET /api/orders/{orderNumber}/workflow`.

## Project layout

```
src/main/java/com/example/ecommerce
├── config/        Temporal client + workers, properties, product seed data
├── domain/        JPA entities: CustomerOrder, OrderItem, Product, Payment,
│                  InventoryReservation, Shipment, Notification
├── repository/    Spring Data repositories
├── service/       Business logic per service (payment, inventory, shipping, notification, order)
├── temporal/
│   ├── workflow/    OrderWorkflow + OrderWorkflowImpl (the order flow)
│   ├── activities/  One activity interface + impl per service
│   ├── model/       Workflow input/output/progress POJOs
│   └── TaskQueues   Task queue names
└── web/           REST controllers, DTOs, error handling
```

## Prerequisites

- JDK 17+, Maven 3.9+
- [Temporal CLI](https://docs.temporal.io/cli) (`temporal --version`)
- MySQL 8 (a local install, or `docker compose up -d`, which starts one on port 3307)

## Run it

1. **Start Temporal** (dev server; the Web UI is at http://localhost:8233):

   ```bash
   temporal server start-dev --db-filename temporal-dev.db
   ```

2. **Start the backend.** The database `ecommerce` and all tables are created automatically.
   Set your MySQL credentials:

   ```bash
   DB_USERNAME=root DB_PASSWORD=<your-password> mvn spring-boot:run
   ```

   On Windows PowerShell: `$env:DB_PASSWORD="<your-password>"; mvn spring-boot:run`

   | Env var            | Default                  |
   |--------------------|--------------------------|
   | `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `ecommerce` |
   | `DB_USERNAME` / `DB_PASSWORD`     | `root` / `root`          |
   | `TEMPORAL_TARGET`  | `localhost:7233`         |
   | `TEMPORAL_NAMESPACE` | `default`              |
   | `WORKERS`          | `order,payment,inventory,shipping,notification` |
   | `SERVER_PORT`      | `8080`                   |

### Running services as separate processes

By default one JVM hosts all five workers. To run them as separate services, start the jar several
times with different `WORKERS` values (and different ports):

```bash
mvn -DskipTests package
WORKERS=order SERVER_PORT=8080 java -jar target/ecommerce-backend-0.0.1-SNAPSHOT.jar
WORKERS=payment SERVER_PORT=8081 java -jar target/ecommerce-backend-0.0.1-SNAPSHOT.jar
WORKERS=inventory,shipping,notification SERVER_PORT=8082 java -jar target/ecommerce-backend-0.0.1-SNAPSHOT.jar
```

Stop the payment process while an order is in flight: the workflow waits at the payment step and
carries on as soon as the payment service comes back.

## REST API

| Method | Path                                   | Description |
|--------|----------------------------------------|-------------|
| GET    | `/api/products`                        | List products and stock |
| GET    | `/api/products/{sku}`                  | One product |
| PUT    | `/api/products`                        | Create or update a product (incl. restock) |
| POST   | `/api/orders`                          | Place an order and start its workflow (202 Accepted) |
| GET    | `/api/orders`                          | List orders |
| GET    | `/api/orders/{orderNumber}`            | Order as stored in MySQL |
| GET    | `/api/orders/{orderNumber}/workflow`   | Temporal execution status + live progress query |
| GET    | `/api/orders/{orderNumber}/audit`      | Payment, reservations, shipment and notifications for the order |

Place an order:

```bash
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{
  "customerName": "Jane Doe",
  "customerEmail": "jane@example.com",
  "shippingAddress": "221B Baker Street, London",
  "paymentToken": "tok_visa",
  "items": [ { "sku": "LAPTOP-001", "quantity": 1 }, { "sku": "MOUSE-001", "quantity": 2 } ]
}'
```

Then follow it with `GET /api/orders/{orderNumber}/workflow` and `/audit`, or in the Temporal Web UI
(workflow id `order-{orderNumber}`).

[`api-requests.http`](api-requests.http) contains ready-made requests for every scenario.

### Test scenarios

| Input                                   | Result |
|-----------------------------------------|--------|
| `paymentToken: tok_visa`                | `COMPLETED`: charged, reserved, shipped, customer emailed |
| `paymentToken: tok_flaky`               | Payment fails twice, Temporal retries, then `COMPLETED` |
| `paymentToken: tok_declined` / `tok_insufficient_funds` | `FAILED` at payment, nothing reserved or shipped |
| quantity > stock (e.g. 5 × `MONITOR-001`) | `FAILED`: payment refunded |
| address containing `UNDELIVERABLE`      | `FAILED`: stock released, payment refunded |

Seeded products: `LAPTOP-001`, `PHONE-001`, `HEADPHONES-001`, `MOUSE-001`, `MONITOR-001` (stock 2).

## Tests

```bash
mvn test
```

`OrderWorkflowTest` runs the real workflow on Temporal's in-memory test server with fake services on
each task queue. It covers the happy path, retried transient errors, a declined payment and
out-of-stock compensation. These tests need neither MySQL nor a Temporal server.

Notifications are simulated: each one is logged and stored in the `notifications` table.
