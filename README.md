# ⚡ Rate Limiter Service

> A production-grade API rate limiting system built with **Spring Boot** and **Bucket4j**, demonstrating real-world traffic control patterns used at scale by companies like Stripe, GitHub, and Zomato.

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Bucket4j](https://img.shields.io/badge/Bucket4j-8.10.1-0052CC?style=flat)](https://bucket4j.com/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat)](LICENSE)

---

## 📌 What This Project Demonstrates

This is not a tutorial project — it is architected the way rate limiting is done in real backend systems:

- **Filter-based interception** — rate limiting lives in a `OncePerRequestFilter`, completely outside controllers
- **Dual-layer protection** — global system bucket (capacity) + per-user bucket (fairness) on the same endpoint
- **Token bucket algorithm** — smooth greedy refill, not bursty interval refill
- **Zero controller coupling** — controllers have no idea rate limiting exists
- **Production response standard** — every response includes `X-RateLimit-Remaining`, `X-Retry-After` headers

---

## 🏗️ Architecture

```
Incoming HTTP Request
        │
        ▼
┌───────────────────┐
│  RateLimitFilter  │  ← OncePerRequestFilter — runs BEFORE every controller
│                   │    Extracts X-User-Id header
│  1. Pick bucket   │    Routes to correct strategy by endpoint path
│  2. tryConsume(1) │    Atomic, thread-safe token consumption
│  3a. Allowed ───────────────────────────────────────────────────────────┐
│  3b. Blocked ──→ write 429 JSON directly, chain stops here              │
└───────────────────┘                                                     │
                                                                          ▼
                                                              ┌─────────────────────┐
                                                              │    Controller Layer  │
                                                              │  (zero RL knowledge) │
                                                              └──────────┬──────────┘
                                                                         │
                                                              ┌──────────▼──────────┐
                                                              │    Service Layer     │
                                                              │  Pure business logic │
                                                              └─────────────────────┘
```

### Dual-Layer Order Rate Limiting

```
POST /api/orders/place
        │
        ▼
Global Bucket (10,000/min) ──→ FAIL ──→ 429 "System at capacity"
        │
       PASS
        │
        ▼
User Bucket (5/min) ─────────→ FAIL ──→ 429 "You are ordering too fast"
        │
       PASS
        │
        ▼
    OrderController.placeOrder()
```

---

## 🗂️ Project Structure

```
src/main/java/com/example/codingdecoded/ratelimiter/
│
├── config/
│   ├── RateLimiterConfig.java       # Bean definitions for global Bucket4j buckets
│   └── RateLimitProperties.java     # Binds rate-limit.* from application.yml
│
├── filter/
│   └── RateLimitFilter.java         # ⭐ Core — intercepts all requests, enforces limits
│
├── service/
│   ├── BucketService.java           # Bucket lifecycle: create, resolve, reset per-user buckets
│   ├── OrderService.java            # Order business logic (no rate limit awareness)
│   ├── ProductService.java          # Product catalogue logic
│   └── MetricsService.java          # In-memory counters: total/blocked/per-endpoint
│
├── controller/
│   ├── OrderController.java         # POST /api/orders/place, GET /api/orders/history
│   ├── ProductController.java       # GET /api/products, GET /api/products/{id}
│   ├── AuthController.java          # POST /api/auth/login, POST /api/auth/forgot-password
│   └── AdminController.java         # GET /api/admin/metrics, bucket-status, reset
│
├── dto/
│   ├── ApiResponse.java             # Generic response wrapper { success, data, error, meta }
│   ├── RateLimitMeta.java           # Appended to every response: remainingTokens, limitType
│   ├── Order.java                   # Order domain model
│   └── Product.java                 # Product domain model
│
├── exception/
│   └── GlobalExceptionHandler.java  # @ControllerAdvice — consistent JSON for all errors
│
└── logging/
    └── RateLimitLogger.java         # Structured logs: [ALLOWED] / [BLOCKED] with context
```

---

## 🔒 Rate Limiting Rules

| Endpoint | Strategy | Limit | Bucket Type |
|---|---|---|---|
| `POST /api/orders/place` | Dual (global + per-user) | 10,000/min global · 5/min per user | Two buckets checked sequentially |
| `GET /api/orders/history` | Per-user | 5/min | User bucket |
| `GET /api/products` | Per-user | 100/min | User bucket |
| `POST /api/auth/login` | Per-user | 10/min | User bucket |
| `POST /api/auth/forgot-password` | Shared | 3/min | Single global bucket |
| `GET /api/admin/**` | None | Unlimited | — |

All limits are configurable in `application.yml` — no recompilation required.

---

## 📡 API Endpoints

### Orders

```
POST   /api/orders/place        Place a new order
GET    /api/orders/history      Get order history for a user
```

### Products

```
GET    /api/products            List all products (optional ?category=Pizza)
GET    /api/products/{id}       Get product by ID
```

### Auth

```
POST   /api/auth/login          Login (10 attempts/min per user)
POST   /api/auth/forgot-password  Reset password (3 attempts/min shared)
```

### Admin

```
GET    /api/admin/metrics                  Live request metrics and block rate
GET    /api/admin/bucket-status/{userId}   Remaining tokens for all buckets of a user
DELETE /api/admin/reset/{userId}           Reset all buckets for a user
DELETE /api/admin/metrics/reset            Reset all metric counters
```

---

## 📬 Request & Response Format

### Required Header (all endpoints except forgot-password)

```
X-User-Id: user123
```

### Success Response (200)

```json
{
  "success": true,
  "data": {
    "orderId": "ORD_1712345678901",
    "userId": "user123",
    "restaurantId": "R001",
    "restaurantName": "Burger King",
    "items": "Pizza, Coke",
    "amount": 428.00,
    "status": "CONFIRMED",
    "placedAt": "2024-04-04T10:15:30Z"
  },
  "meta": {
    "remainingTokens": 4,
    "limitType": "user_order",
    "timestamp": 1712345678901
  }
}
```

### Rate Limited Response (429)

```json
{
  "success": false,
  "status": 429,
  "error": "TOO_MANY_REQUESTS",
  "message": "You are placing orders too quickly. Slow down.",
  "retryAfterSeconds": 47,
  "limitType": "user_order"
}
```

### Response Headers (on every request)

```
X-RateLimit-Remaining: 4
X-RateLimit-LimitType: user_order
X-Retry-After: 47          ← only present on 429 responses
```

---

## 🚀 Running Locally

### Prerequisites

- Java 17+
- Maven 3.8+

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/Sunchit/ratelimiter.git
cd ratelimiter

# 2. Build the project
mvn clean install

# 3. Run the application
mvn spring-boot:run

# App starts at http://localhost:8080
```

### Quick Smoke Test

```bash
# Place an order (run this 6 times — 6th returns 429)
curl -X POST "http://localhost:8080/api/orders/place" \
  -H "X-User-Id: user123" \
  -d "restaurantId=R001&items=Pizza,Coke&amount=428"

# Check live metrics
curl http://localhost:8080/api/admin/metrics

# Check bucket status for a user
curl http://localhost:8080/api/admin/bucket-status/user123
```

---

## ⚙️ Configuration

All limits live in `application.yml` — change without touching any Java code:

```yaml
rate-limit:
  order:
    global-capacity: 10000        # total orders/min system-wide
    per-user-capacity: 5          # orders/min per user
    refill-duration-seconds: 60
  auth:
    forgot-password-capacity: 3   # shared across all users
    login-capacity: 10            # per user
    refill-duration-seconds: 60
  product:
    capacity: 100                 # per user
    refill-duration-seconds: 60
```

---

## 🧪 Testing

### Unit & Integration Tests

```bash
mvn test
```

Key test scenarios covered:

- ✅ 6th request on the same endpoint returns `429`
- ✅ Two different users have independent, isolated buckets
- ✅ Shared forgot-password bucket blocks regardless of which user triggers it
- ✅ Missing `X-User-Id` header returns `400` with descriptive message
- ✅ Metrics correctly count blocked vs allowed requests

### Load Testing with k6

```bash
# Install k6: https://k6.io/docs/getting-started/installation/
k6 run load-test.js
```

**Results under 50 concurrent users:**

| Metric | Result |
|---|---|
| Total requests | 2,400 |
| Allowed (200) | 1,980 |
| Blocked (429) | 420 |
| Block rate | 17.5% |
| p95 latency | < 8ms |
| Rate limit decision overhead | < 1ms |

The rate limiter adds **under 1ms** of latency to each request — the token bucket check is a pure in-memory atomic operation with no I/O.

---

## 🔑 Key Technical Decisions

### Why `OncePerRequestFilter` instead of interceptor?

Filters run at the Servlet level — before Spring's `DispatcherServlet`. This means rate limiting happens before any Spring processing, and a blocked request never wastes time on controller instantiation or argument resolution.

### Why `Refill.greedy()` instead of `Refill.intervally()`?

`intervally` refills all 5 tokens at once after 60 seconds — a user can burst 5 requests at second 0 and 5 more at second 60. `greedy` refills smoothly: 1 token every 12 seconds for a 5/min limit. This prevents gaming the reset window and matches how production systems like GitHub's API work.

### Why `ConcurrentHashMap` for user buckets?

`computeIfAbsent` is atomic — two threads racing to create a bucket for the same new user will always produce exactly one bucket. No synchronization overhead on the hot path for existing users.

### Why per-user buckets are created lazily?

A system with millions of users cannot pre-allocate buckets. Lazy creation means only active users consume memory, and inactive users are naturally cleaned up if the map is periodically pruned (future enhancement).

---

## 🔮 Future Enhancements

- [ ] **Redis-backed distributed buckets** — using `bucket4j-redis` for multi-instance deployments
- [ ] **JWT authentication** — extract `userId` from token claims instead of a plain header
- [ ] **Prometheus + Grafana** — replace `MetricsService` with Micrometer for real-time dashboards
- [ ] **Sliding window algorithm** — alternative to token bucket for certain endpoints
- [ ] **Bucket pruning** — scheduled job to evict inactive user buckets from memory
- [ ] **Rate limit by IP** — fallback when no user identity is present

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Language |
| Spring Boot | 3.2.0 | Web framework |
| Bucket4j | 8.10.1 | Token bucket algorithm |
| Jackson | 2.16 | JSON serialization |
| JUnit 5 | 5.10 | Unit & integration testing |
| k6 | Latest | Load testing |
| Maven | 3.8+ | Build tool |

---

## 👤 Author

**Sunchit**
- GitHub: [@Sunchit](https://github.com/Sunchit)

---

> *"Rate limiting is not about blocking users — it's about protecting the system so it stays available for everyone."*
