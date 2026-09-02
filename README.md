# PaymentSystemWithPostgreSQL
This is the Payment System. I have used PostgreSQL as the underlying database.

## 1. Problem Statement

Design and implement a payment processing system where:

- A user can make a payment to a merchant.
- Supported payment methods: `UPI` and `CARD`.
- The system exposes REST APIs.
- Payment creation is idempotent.
- An external payment provider is mocked.
- Final payment status is updated asynchronously using a webhook.
- PostgreSQL is used for persistence.

## 2. High-Level Flow

```text
Client / Postman
      |
      v
PaymentController
      |
      v
PaymentService
      |
      +----> PaymentRepository ----> PostgreSQL
      |
      v
PaymentProcessor registry
      |
      +---- UPI  ---> UpiPaymentProcessor
      |
      +---- CARD ---> CardPaymentProcessor
                         |
                         v
                  PaymentProvider
                         |
                         v
                 MockPaymentProvider

Later:

Mock/External Provider
      |
      v
WebhookController
      |
      v
PaymentService
      |
      v
PaymentRepository
      |
      v
PostgreSQL
```

## 3. Payment State Machine

```text
CREATED
   |
   | provider accepts initiation
   v
PROCESSING
   |
   +------> SUCCESS
   |
   +------> FAILED
```

Rules:
- New payment starts as `CREATED`.
- Provider `ACCEPTED` means accepted for processing, not successful.
- `ACCEPTED` moves the payment to `PROCESSING`.
- Webhook later moves it to `SUCCESS` or `FAILED`.
- Duplicate same-final-status webhook is a no-op.
- Conflicting transitions such as `SUCCESS -> FAILED` are rejected.

## 4. APIs

### Create Payment

```http
POST /payments
Idempotency-Key: order-123-payment
Content-Type: application/json
```

```json
{
  "userId": "user-1",
  "merchantId": "merchant-1",
  "amount": 50000,
  "currency": "INR",
  "paymentMethod": "UPI"
}
```

`amount` is stored in the smallest currency unit. For INR, `50000 paise = ₹500`.

Response:

```json
{
  "paymentId": "pay_xxx",
  "status": "PROCESSING"
}
```

### Get Payment

```http
GET /payments/{paymentId}
```

### Payment Webhook

```http
POST /webhooks/payments
```

```json
{
  "eventId": "evt-123",
  "providerPaymentId": "provider-999",
  "status": "SUCCESS"
}
```

## 5. Package Structure

```text
com.example.demo

controller/
    PaymentController
    WebhookController

dto/
    CreatePaymentRequest
    PaymentResponse
    PaymentWebhookRequest

model/
    Payment
    PaymentStatus
    PaymentMethod
    ProviderPaymentStatus

repository/
    PaymentRepository

service/
    PaymentService

processor/
    PaymentProcessor
    UpiPaymentProcessor
    CardPaymentProcessor

provider/
    PaymentProvider
    MockPaymentProvider
    ProviderResult
    ProviderResultType

exception/
    PaymentNotFoundException
    IdempotencyConflictException
    InvalidPaymentStateException
    ApiError
    GlobalExceptionHandler
```

## 6. Class-Level Details

### PaymentController

Responsibility:
- HTTP entry point for payment APIs.
- Reads request body and `Idempotency-Key`.
- Uses `@Valid` for validation.
- Delegates to `PaymentService`.

Keep controllers thin. Do not put DB access, provider calls, or state-transition business logic here.

### WebhookController

Responsibility:
- Receives provider callbacks.
- Validates webhook request.
- Delegates to `PaymentService`.

### CreatePaymentRequest

Incoming API DTO containing:

```text
userId
merchantId
amount
currency
paymentMethod
```

Typical validation:

```java
@NotBlank private String userId;
@NotBlank private String merchantId;
@Positive private long amount;
@NotBlank private String currency;
@NotNull private PaymentMethod paymentMethod;
```

Why DTO instead of accepting `Payment` directly?
- Prevents clients from setting internal fields.
- Separates API contract from persistence model.
- Client cannot set `status`, `retryCount`, timestamps, etc.

### PaymentResponse

Small response DTO:

```text
paymentId
status
```

Convenient Lombok annotations are fine for DTOs:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
```

### Payment

JPA entity representing persisted payment state.

Important fields:

```text
id
userId
merchantId
amount
currency
paymentMethod
idempotencyKey
status
providerPaymentId
retryCount
createdAt
updatedAt
```

Important annotations:

```java
@Entity
@Table(...)
@Id
@Column(...)
@Enumerated(EnumType.STRING)
```

Why `EnumType.STRING`?
- Stores values such as `PROCESSING` rather than ordinal numbers.
- Safer if enum order changes.

Why avoid unrestricted setters?
- Domain state should be controlled.
- Prefer methods like `updateStatus()`, `incrementRetryCount()`, `setProviderPaymentId()`.

Why `@NoArgsConstructor`?
- Hibernate/JPA needs a no-argument constructor.
- Prefer `@NoArgsConstructor(access = AccessLevel.PROTECTED)` when possible.

Why avoid `@AllArgsConstructor` on an entity?
- It lets callers create invalid state combinations.
- Prefer a controlled constructor that creates a valid new payment.

### PaymentStatus

```java
CREATED,
PROCESSING,
SUCCESS,
FAILED
```

### PaymentMethod

```java
UPI,
CARD
```

### ProviderPaymentStatus

Provider webhook status:

```java
SUCCESS,
FAILED
```

Keep this separate from `PaymentStatus` so providers cannot send internal states such as `CREATED` or `PROCESSING`.

## 7. Repository Layer

```java
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
}
```

Spring Data supplies `save()`, `saveAndFlush()`, `findById()`, `findAll()`, etc.

## 8. Why Idempotency Is Needed

If the client sends a payment, times out, and retries, you must not charge twice.

Without idempotency:

```text
Request 1 -> ₹500
Request 2 -> ₹500
Total = ₹1000
```

With the same idempotency key:

```text
same key + same request -> return existing payment
same key + different request -> 409 Conflict
```

Compare material request fields such as:

```text
userId
merchantId
amount
currency
paymentMethod
```

## 9. Concurrent Idempotency Race

This is not enough:

```text
findByIdempotencyKey()
then
save()
```

Two requests can both see "not found" before either inserts.

Correctness comes from a DB unique constraint on `idempotency_key`.

```text
Request A                    Request B
find -> empty                find -> empty
saveAndFlush -> success      saveAndFlush -> duplicate key
provider call                catch exception
                             fetch A's payment
                             return it
```

Typical handling:

```java
try {
    savedPayment = paymentRepository.saveAndFlush(newPayment);
} catch (DataIntegrityViolationException ex) {
    Payment existing = paymentRepository
            .findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> ex);

    validateSameRequest(existing, request);
    return toResponse(existing);
}
```

Why `saveAndFlush()`?
- `save()` may delay the SQL insert.
- `saveAndFlush()` forces the insert immediately so the unique constraint error is caught in the expected place.

The losing request must not call the payment provider.

## 10. PaymentProcessor — Strategy Pattern

```java
public interface PaymentProcessor {
    PaymentMethod supportedMethod();
    ProviderResult process(Payment payment);
}
```

Implementations:

```text
UpiPaymentProcessor
CardPaymentProcessor
```

Why?
- Payment methods can diverge later.
- Avoids large `if/else` chains in `PaymentService`.

Spring injects all processors as a list, which the service converts to:

```java
Map<PaymentMethod, PaymentProcessor>
```

Example registry:

```java
this.processors = paymentProcessors.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(
                processor -> processor.supportedMethod(),
                processor -> processor
        ));
```

Result:

```text
UPI  -> UpiPaymentProcessor
CARD -> CardPaymentProcessor
```

## 11. PaymentProvider

```java
public interface PaymentProvider {
    ProviderResult initiatePayment(Payment payment);
}
```

Purpose:
- Isolates external payment-gateway integration.
- Makes testing easy.
- Lets implementations change later.

For this exercise:

```text
MockPaymentProvider
```

## 12. ProviderResult / ProviderResultType

Provider result contains:

```text
resultType
providerPaymentId
message
```

Types:

```java
ACCEPTED,
DEFINITIVE_FAILURE,
RETRYABLE_FAILURE
```

Meaning:
- `ACCEPTED` -> `PROCESSING`
- `DEFINITIVE_FAILURE` -> `FAILED`
- `RETRYABLE_FAILURE` -> keep `PROCESSING` because the outcome may be unknown

Example of retryable/unknown outcome:
- timeout
- provider 500
- network failure

Do not immediately mark such a payment failed because the provider might actually have processed it.

## 13. createPayment() End-to-End Flow

1. Client sends `POST /payments` with an idempotency key.
2. `PaymentController` validates the request.
3. `PaymentService` checks whether the key already exists.
4. If it exists, validate the payload and return the existing payment.
5. Otherwise create a new domain object in `CREATED` state.
6. Persist it using `saveAndFlush()`.
7. DB unique constraint handles concurrent duplicate requests.
8. Select the processor from the registry based on `PaymentMethod`.
9. Processor calls `PaymentProvider`.
10. Mock provider returns a provider payment ID and `ACCEPTED`.
11. Service stores provider ID and changes state to `PROCESSING`.
12. Save updated payment.
13. Return payment ID and status.

Sequence:

```text
POST /payments
     |
validate
     |
check idempotency
     |
create Payment(CREATED)
     |
saveAndFlush()
     |
select processor
     |
call provider
     |
store providerPaymentId
     |
PROCESSING
     |
return response
```

## 14. Webhook Flow

Later the provider sends the final outcome:

```text
Provider
   |
WebhookController
   |
PaymentService
   |
findByProviderPaymentId()
   |
validate state transition
   |
SUCCESS / FAILED
   |
save()
```

Allowed transitions:

```text
PROCESSING -> SUCCESS
PROCESSING -> FAILED
```

Duplicate final callbacks are no-ops:

```text
SUCCESS -> SUCCESS
FAILED -> FAILED
```

Rejected:

```text
SUCCESS -> FAILED
FAILED -> SUCCESS
CREATED -> SUCCESS
```

## 15. Error Handling

Use `@RestControllerAdvice` to centralize errors.

### PaymentNotFoundException

```text
404 NOT FOUND
PAYMENT_NOT_FOUND
```

### IdempotencyConflictException

Same key + different request:

```text
409 CONFLICT
IDEMPOTENCY_CONFLICT
```

### InvalidPaymentStateException

Invalid transition:

```text
409 CONFLICT
INVALID_PAYMENT_STATE
```

### Validation Error

Bad fields such as negative amount:

```text
400 BAD REQUEST
VALIDATION_ERROR
```

Also ideally handle `HttpMessageNotReadableException` for malformed JSON/invalid enum values as `400`.

## 16. ApiError

Consistent error structure:

```json
{
  "timestamp": "...",
  "status": 409,
  "code": "IDEMPOTENCY_CONFLICT",
  "message": "Idempotency key already used for a different request"
}
```

## 17. PostgreSQL

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/payments_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Important unique constraints:
- `idempotency_key`
- `provider_payment_id`

Why PostgreSQL instead of only an in-memory map?
- Durable data
- DB constraints
- Multi-instance correctness
- Transactions
- Real persistence behavior

## 18. Dependency Injection / Lombok

### `@NoArgsConstructor`
Generates a zero-argument constructor. Useful for JPA/Jackson.

### `@AllArgsConstructor`
Generates a constructor containing every field. Fine for simple DTOs.

### `@RequiredArgsConstructor`
Generates a constructor for `final` and `@NonNull` fields. Very useful for Spring constructor injection.

Prefer constructor injection because dependencies are explicit, immutable, and easy to test.

## 19. Design Patterns Used

### Layered Architecture

```text
Controller -> Service -> Repository
```

### Strategy Pattern

```text
PaymentProcessor
   +-- UpiPaymentProcessor
   +-- CardPaymentProcessor
```

### Repository Pattern

`PaymentRepository` abstracts persistence.

### Dependency Inversion

Business logic depends on abstractions such as `PaymentProcessor`, `PaymentProvider`, and `PaymentRepository`, not concrete infrastructure details.

## 20. Postman Test Checklist

1. New payment -> `201`, `PROCESSING`.
2. GET payment -> current status + provider ID.
3. Same key + same request -> same payment ID, no duplicate row.
4. Same key + different request -> `409`.
5. Success webhook -> `PROCESSING -> SUCCESS`.
6. Duplicate success webhook -> no error.
7. Failed webhook -> `PROCESSING -> FAILED`.
8. Conflicting webhook -> `409 INVALID_PAYMENT_STATE`.
9. Validation errors -> `400`.

## 21. Interview Questions to Be Ready For

### Why idempotency?
Prevents duplicate payments when clients retry after network failures/timeouts.

### Why DB uniqueness?
Application-level `find -> save` can race under concurrency. The DB constraint is the final guarantee.

### Why `saveAndFlush()`?
Forces the insert immediately so the duplicate-key violation can be handled at that point.

### Why not mark payment SUCCESS after provider initiation?
Provider acceptance only means processing has started. Final financial outcome may arrive asynchronously.

### Why webhook?
Payment providers often finalize transactions asynchronously.

### Why `PaymentProcessor`?
Allows payment-method-specific behavior without changing core service orchestration.

### Why `PaymentProvider`?
Decouples external gateway integration and makes testing/substitution easier.

### What happens on provider timeout?
Outcome may be ambiguous, so keep it `PROCESSING` and reconcile/retry safely instead of immediately marking it failed.

### How do you avoid duplicate provider calls?
Only the request that successfully creates the DB record calls the provider. Concurrent losers fetch and return the existing payment.

## 22. Production Improvements

If asked what you would add beyond machine-coding scope:

- Persist webhook events with unique `eventId`.
- Verify webhook signatures.
- Retry worker with exponential backoff.
- Reconciliation jobs.
- Transactional outbox.
- Authentication/authorization.
- Structured logging, metrics, tracing.
- Audit records.
- Payment-attempt table.
- Refund flow.
- Dedicated response DTOs.
- Integration and concurrency tests.

Do not overbuild these unless requested in a 90-minute round.

## 23. Transaction / External Call Consideration

Avoid holding a long DB transaction open while calling an external provider because external calls may block, timeout, or fail unpredictably.

A production design may use:

```text
DB transaction
   ->
outbox/event
   ->
async worker
   ->
provider
```

For machine coding, a synchronous mocked provider is acceptable.

## 24. Unit Tests to Mention

Important `PaymentService` tests:

```text
new payment happy path
same key + same request
same key + different request
concurrent insert conflict
provider accepted
webhook success
webhook failed
duplicate webhook
invalid status transition
payment not found
```

Use JUnit 5 + Mockito.

## 25. 30-Second Architecture Explanation

> The system uses a layered Spring Boot architecture. `PaymentController` accepts requests and delegates to `PaymentService`, which owns payment creation, idempotency, state transitions, and orchestration. Payments are persisted in PostgreSQL through `PaymentRepository`, with a unique constraint on the idempotency key for concurrency safety. Payment-method-specific behavior is implemented using a `PaymentProcessor` strategy, currently with UPI and card processors. The processors call a `PaymentProvider` abstraction, which is mocked for this exercise. Provider acceptance moves a payment to `PROCESSING`; final `SUCCESS` or `FAILED` status arrives asynchronously through a webhook.

## 26. 60-Second Idempotency Explanation

> Clients often retry payment requests when they encounter network timeouts, so every create-payment request contains an idempotency key. I first check whether a payment already exists for that key; if it does, I verify the request parameters match and return the existing payment. However, that lookup alone is not concurrency-safe because two requests can both observe that no row exists. Therefore, the database has a unique constraint on the idempotency key. I use `saveAndFlush()` so a duplicate insert raises a constraint violation immediately. The losing request catches that exception, fetches the payment created by the winning request, validates the payload, and returns it without calling the provider again.

## 27. 30-Second Strategy Pattern Explanation

> I use `PaymentProcessor` as a strategy abstraction. Spring injects all processor implementations and I register them in a map keyed by `PaymentMethod`. The service can then select the appropriate processor without `if/else` chains. UPI and card currently delegate to the same mock provider, but the abstraction allows their behavior to diverge later without changing the service workflow.

## 28. Final Revision Cheat Sheet

```text
POST /payments
     |
validate request
     |
check idempotency
     |
create CREATED payment
     |
saveAndFlush()
     |
DB unique constraint
     |
select PaymentProcessor
     |
call PaymentProvider
     |
store providerPaymentId
     |
PROCESSING
     |
return response

------- asynchronous boundary -------

provider webhook
     |
lookup providerPaymentId
     |
validate transition
     |
SUCCESS / FAILED
     |
save
```

Five things to emphasize:

```text
1. Idempotency
2. DB concurrency safety
3. Explicit payment state machine
4. Provider abstraction + Strategy Pattern
5. Async webhook finalization
```

If pressed for production improvements, mention:

```text
transactional outbox
webhook event dedupe
retries + reconciliation
observability
security
```