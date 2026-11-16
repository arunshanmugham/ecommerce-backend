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

## Architecture

### Components

```mermaid
flowchart LR
    client["REST client<br/>curl / Postman / .http"]

    subgraph app["Spring Boot application"]
        api["REST API<br/>OrderController<br/>ProductController"]
        orderSvc["OrderService"]
        subgraph workers["Temporal workers"]
            wOrder["Order worker<br/>OrderWorkflowImpl<br/>OrderActivities"]
            wPay["Payment worker<br/>PaymentActivities"]
            wInv["Inventory worker<br/>InventoryActivities"]
            wShip["Shipping worker<br/>ShippingActivities"]
            wNotif["Notification worker<br/>NotificationActivities"]
        end
        services["Domain services<br/>Payment / Inventory / Shipping /<br/>Notification / Order"]
    end

    subgraph temporal["Temporal server"]
        qOrder[["ORDER_TASK_QUEUE"]]
        qPay[["PAYMENT_TASK_QUEUE"]]
        qInv[["INVENTORY_TASK_QUEUE"]]
        qShip[["SHIPPING_TASK_QUEUE"]]
        qNotif[["NOTIFICATION_TASK_QUEUE"]]
        history[("Event history")]
    end

    db[("MySQL<br/>ecommerce")]

    client -->|HTTP/JSON| api
    api --> orderSvc
    orderSvc -->|"save order (PENDING)"| db
    orderSvc -->|"start workflow order-{orderNumber}"| qOrder
    api -->|"describe + getProgress query"| temporal

    qOrder <-->|poll| wOrder
    qPay <-->|poll| wPay
    qInv <-->|poll| wInv
    qShip <-->|poll| wShip
    qNotif <-->|poll| wNotif

    wOrder -.->|schedules activities| qPay & qInv & qShip & qNotif

    wPay & wInv & wShip & wNotif & wOrder --> services
    services -->|JPA| db
```

The system has three layers:

| Layer | Classes | Responsibility |
|-------|---------|----------------|
| **API** | `OrderController`, `ProductController`, `ApiExceptionHandler` | Validates requests, saves the order, starts the workflow, exposes order/workflow/audit views. |
| **Orchestration** | `OrderWorkflowImpl` | Decides *what happens next*: step order, retries, compensation. Deterministic code only: no I/O, clocks or randomness. |
| **Services** | `*ActivitiesImpl` → `PaymentService`, `InventoryService`, `ShippingService`, `NotificationService`, `OrderService` | Do the actual work (DB writes, simulated external calls). Activities are thin adapters that turn business errors into non-retryable Temporal failures. |

The workflow never calls a service directly. It schedules an activity on that service's **task queue**,
Temporal stores the request durably, and whichever worker polls that queue runs it. That is what makes
the services independently deployable, and why an order survives a crash or restart of any process: on
restart, the workflow is replayed from its event history and continues from the last completed step.

### How the workflow calls a service: activity stubs

`OrderWorkflowImpl` creates one **activity stub** per service:

```java
private final PaymentActivities payment =
        Workflow.newActivityStub(PaymentActivities.class, optionsFor(TaskQueues.PAYMENT));
```

"Stub" is the classic RPC term for a local object that looks like the real service but contains no
business logic. `newActivityStub` returns a dynamic proxy implementing `PaymentActivities`; calling
`payment.processPayment(...)` does not charge anything. It turns the call into a request to the Temporal
server, and the real `PaymentActivitiesImpl` runs wherever a payment worker is polling.

**The stub never calls the worker directly, even in the same JVM.** With the default `WORKERS` setting,
all five workers run in the same Spring Boot process as the workflow. Each worker has poller threads
that hold long-poll gRPC requests open to the Temporal server for their own task queue:

```
                               Spring Boot JVM
 ┌──────────────────────────────────────────────────────────────────────────┐
 │  CALLER SIDE (order worker)            │  CALLEE SIDE (payment worker)   │
 │                                        │                                 │
 │  OrderWorkflowImpl                     │  PaymentActivitiesImpl          │
 │    │ payment.processPayment(...)       │    ▲ processPayment(...)        │
 │    ▼                                   │    │ (real code: DB write)      │
 │  PaymentActivities STUB                │    │                            │
 │  (dynamic proxy, no business logic)    │  Payment worker (SDK)           │
 │    │ ScheduleActivityTask command      │    ▲ activity task              │
 │    ▼                                   │    │                            │
 │  Order worker (SDK)                    │  Poller thread                  │
 │    │ PollWorkflowTaskQueue(ORDER)      │    │ PollActivityTaskQueue      │
 │    │                                   │    │ (PAYMENT)                  │
 └────┼───────────────────────────────────┴────┼────────────────────────────┘
      │      no direct Java call between        │
      │      the stub and the implementation    │
      ▼        gRPC (localhost:7233)            │
 ┌──────────────────────────────────────────────┴──┐
 │ Temporal server: event history + task queues    │
 └─────────────────────────────────────────────────┘
```

One stub call takes several round trips through the server. The stub (left) and the implementation
(right) never talk to each other directly:

```mermaid
sequenceDiagram
    autonumber
    box Order worker - caller side
        participant WF as OrderWorkflowImpl<br/>(workflow code)
        participant Stub as PaymentActivities<br/>STUB (proxy)
        participant OW as Order worker<br/>(Temporal SDK)
    end
    participant T as Temporal server
    box Payment worker - callee side
        participant PW as Payment worker<br/>(Temporal SDK poller)
        participant Impl as PaymentActivitiesImpl<br/>(real implementation)
    end

    WF->>Stub: payment.processPayment(orderNumber, amount, token)
    Stub->>Stub: serialize args to JSON,<br/>build ScheduleActivityTask command
    Stub->>OW: hand over command (workflow thread blocks)
    OW->>T: RespondWorkflowTaskCompleted [ScheduleActivityTask]
    T->>T: write ActivityTaskScheduled,<br/>queue task on PAYMENT_TASK_QUEUE
    PW->>T: PollActivityTaskQueue (long poll, already waiting)
    T-->>PW: activity task (type + JSON args)
    PW->>Impl: processPayment(orderNumber, amount, token)
    Impl->>Impl: PaymentService.charge() → MySQL
    Impl-->>PW: "TXN-…"
    PW->>T: RespondActivityTaskCompleted("TXN-…")
    T->>T: write ActivityTaskCompleted,<br/>queue workflow task on ORDER_TASK_QUEUE
    OW->>T: PollWorkflowTaskQueue
    T-->>OW: workflow task (history incl. result)
    OW->>WF: replay processOrder from history
    WF->>Stub: payment.processPayment(...) again
    Stub->>Stub: result already in history,<br/>nothing scheduled
    Stub-->>WF: return "TXN-…"
```

Steps 1–4 happen on the caller side, 6–11 on the callee side, and 12–18 back on the caller side:

1. **Workflow → stub.** `OrderWorkflowImpl` calls `payment.processPayment(...)`. `payment` is the stub,
   not `PaymentActivitiesImpl`.
2. **Stub builds a command.** It serializes the arguments to JSON and creates a `ScheduleActivityTask`
   command with the activity type, task queue, timeouts and retry policy from `ActivityOptions`.
3. **Stub → order worker.** The command goes to the SDK and the workflow thread blocks.
4. **Order worker → server.** The SDK sends the command with `RespondWorkflowTaskCompleted`.
5. **Server.** Writes `ActivityTaskScheduled` to the event history and queues a task on `PAYMENT_TASK_QUEUE`.
6. **Payment worker polls.** Its waiting `PollActivityTaskQueue` request…
7. …receives the task.
8. **Payment worker → implementation.** The SDK deserializes the arguments and calls the real
   `PaymentActivitiesImpl.processPayment(...)` on an activity thread.
9. **Implementation does the work:** `PaymentService.charge()` writes the payment to MySQL.
10. It returns the transaction ID to the worker…
11. …which reports it with `RespondActivityTaskCompleted`. On failure or timeout, the server retries
    according to the retry policy; the workflow only sees an `ActivityFailure` once retries run out.
12. **Server.** Writes `ActivityTaskCompleted` and queues a workflow task on `ORDER_TASK_QUEUE`.
13. **Order worker polls**…
14. …and receives the workflow task with the updated history.
15. **Replay.** The SDK re-runs `processOrder` from the start, using the history.
16. **Workflow → stub again** at the same line.
17. **Stub finds the result** in the history and schedules nothing new.
18. **Stub → workflow.** The call returns `"TXN-…"` and the workflow continues to the inventory step.

**Why there is no in-process shortcut.** A direct method call would not be recorded on the server. Going
through it every time is what gives retries, timeouts and crash recovery, and it makes **replay** safe:
if the order worker crashes after payment succeeded, the replayed workflow reaches `processPayment` again,
finds the result already in the history, and returns it without charging the customer a second time.
It is also why splitting services into separate processes (`WORKERS=payment` etc.) needs no code
changes: only the location of the poller changes.

**Related APIs**

| API | Difference |
|-----|------------|
| `Workflow.newActivityStub` *(used here)* | Typed, compile-checked; carries `ActivityOptions` (task queue, timeouts, retries). |
| `Workflow.newUntypedActivityStub` | Same execution path, but activities are called by string name; useful for activities written in another language. |
| `Workflow.newLocalActivityStub` | Runs in the workflow's own worker without a task queue; only the result is recorded. Cannot route to another service's task queue, so not suitable here. |
| `Workflow.executeActivity` | `@Experimental` (SDK 1.40). Invokes a stub method reference with `ActivityInvocationOptions`, which currently only sets a custom activity ID. Still needs a stub for task queue, timeouts and retries. |

Temporal's *eager activity execution* can skip the queue wait when the activity is on the **same** task
queue as the workflow. Here each service has its own queue, so it does not apply.

**See it yourself**

- Temporal UI (http://localhost:8233): open an `order-ORD-…` workflow; each step shows
  `ActivityTaskScheduled` → `ActivityTaskStarted` (with the worker identity) → `ActivityTaskCompleted`.
- Connections from the app to the server (PowerShell): `Get-NetTCPConnection -RemotePort 7233 -State Established`
- Stop the Temporal server while an order is running: the order freezes at its current step and continues
  when the server comes back.

### Order flow

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant API as REST API
    participant DB as MySQL
    participant T as Temporal
    participant WF as OrderWorkflow
    participant P as Payment
    participant I as Inventory
    participant S as Shipping
    participant N as Notification
    participant O as Order service

    C->>API: POST /api/orders
    API->>DB: insert order (PENDING), prices from catalogue
    API->>T: start workflow order-{orderNumber}
    API-->>C: 202 Accepted + orderNumber

    T->>WF: run processOrder(details)
    WF->>P: processPayment(order, amount, token)
    P->>DB: insert payment (CHARGED)
    P-->>WF: transactionId
    WF->>O: updateOrder(PAYMENT_PROCESSED)

    WF->>I: reserveInventory(order, items)
    I->>DB: decrement stock + insert reservations (one transaction)
    WF->>O: updateOrder(INVENTORY_RESERVED)

    WF->>S: shipOrder(order, address)
    S->>DB: insert shipment
    S-->>WF: trackingNumber
    WF->>O: updateOrder(SHIPPED)

    WF->>N: notifyCustomer(ORDER_CONFIRMED)
    N->>DB: insert notification
    WF->>O: updateOrder(COMPLETED)

    C->>API: GET /api/orders/{n}/workflow
    API->>T: describe + getProgress query
    API-->>C: status, current step, history
```

`POST /api/orders` returns as soon as the workflow has *started*; the client polls the order or the
`/workflow` endpoint to follow progress. The order row is committed before the workflow starts, so
activities can always find it. If Temporal is unreachable the order is marked `FAILED` and the API
returns `503`.

### Failure handling

**Retries.** Every activity runs with a 30s start-to-close timeout and this retry policy: first retry
after 1s, backoff ×2, at most 30s between attempts, 5 attempts in total. Timeouts, exceptions and
worker crashes are all retried. Try it with `paymentToken: tok_flaky`.

**Permanent failures** are raised as non-retryable `ApplicationFailure`s, so they stop immediately:

| Step | Failure type | Trigger in this demo |
|------|--------------|----------------------|
| Payment | `PaymentDeclined` | `tok_declined`, `tok_insufficient_funds` |
| Inventory | `OutOfStock` | quantity greater than available stock |
| Shipping | `UndeliverableAddress` | address contains `UNDELIVERABLE` |

**Compensation (saga).** Before each step runs, the workflow registers its undo action with a Temporal
`Saga`. When a step fails permanently, the undo actions run in reverse order:

```mermaid
flowchart LR
    pay["1. Process payment"] --> inv["2. Reserve inventory"] --> ship["3. Ship"] --> notify["4. Notify customer"] --> done(["COMPLETED"])
    pay -. fails .-> c1["Refund payment"]
    inv -. fails .-> c2["Release stock"] --> c1
    ship -. fails .-> c2
    c1 --> failed(["FAILED + ORDER_FAILED email"])
```

Undo actions are registered *before* their step, so a payment that went through but whose activity
timed out is still refunded. Both undo actions are safe no-ops when there is nothing to undo.
A failed notification is logged but does not undo a shipped order.

**Idempotency.** Temporal guarantees at-least-once activity execution, so each service checks for
existing work by order number before acting: an existing payment, reservation set, shipment or
notification of the same type is returned instead of being created again.

### Order status lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: POST /api/orders
    PENDING --> PAYMENT_PROCESSED: payment charged
    PAYMENT_PROCESSED --> INVENTORY_RESERVED: stock reserved
    INVENTORY_RESERVED --> SHIPPED: shipment created
    SHIPPED --> COMPLETED: customer notified
    PENDING --> FAILED: payment declined / Temporal unavailable
    PAYMENT_PROCESSED --> FAILED: out of stock, refunded
    INVENTORY_RESERVED --> FAILED: undeliverable, released + refunded
    COMPLETED --> [*]
    FAILED --> [*]
```

The status is stored in `orders.status` (written by the order service) and is also kept in the
workflow's `getProgress()` query, together with the current step and a step-by-step history.

### Data model

```mermaid
erDiagram
    ORDERS ||--|{ ORDER_ITEMS : contains
    PRODUCTS ||--o{ ORDER_ITEMS : "sku"
    ORDERS ||--o| PAYMENTS : "order_number"
    ORDERS ||--o{ INVENTORY_RESERVATIONS : "order_number"
    ORDERS ||--o| SHIPMENTS : "order_number"
    ORDERS ||--o{ NOTIFICATIONS : "order_number"

    ORDERS {
        bigint id PK
        varchar order_number UK
        varchar status
        decimal total_amount
        varchar payment_transaction_id
        varchar tracking_number
        varchar failure_reason
        varchar workflow_id
    }
    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        varchar sku
        int quantity
        decimal unit_price
    }
    PRODUCTS {
        bigint id PK
        varchar sku UK
        varchar name
        decimal price
        int available_quantity
    }
    PAYMENTS {
        bigint id PK
        varchar order_number UK
        varchar transaction_id UK
        decimal amount
        varchar status "CHARGED | REFUNDED"
    }
    INVENTORY_RESERVATIONS {
        bigint id PK
        varchar order_number
        varchar sku
        int quantity
        varchar status "RESERVED | RELEASED"
    }
    SHIPMENTS {
        bigint id PK
        varchar order_number UK
        varchar tracking_number
        varchar carrier
    }
    NOTIFICATIONS {
        bigint id PK
        varchar order_number
        varchar recipient
        varchar type
        varchar message
    }
```

Each service owns its own tables and links to the order only by `order_number`, so the services could
later be split into separate databases without changing the workflow. Stock is decremented with a
single conditional `UPDATE ... WHERE available_quantity >= qty`, so concurrent orders cannot oversell.
Tables are created by Hibernate (`ddl-auto: update`); use a migration tool such as Flyway in production.

### Deployment options

| Mode | How | When |
|------|-----|------|
| **Single process** (default) | `WORKERS=order,payment,inventory,shipping,notification` | Local development and demos |
| **One process per service** | Start the same jar several times with different `WORKERS` values | Scale or deploy services independently; see [below](#running-services-as-separate-processes) |

In every mode, the REST API runs in each process and all processes share the same Temporal namespace
and MySQL database. For production, replace the Temporal dev server with a self-hosted cluster or
Temporal Cloud (`TEMPORAL_TARGET`, `TEMPORAL_NAMESPACE`), and the simulated payment, shipping and
email logic in the services with real provider calls.

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
