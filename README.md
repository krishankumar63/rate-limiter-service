# ⚡ Rate Limiter Service

> A production-grade API rate limiting system built with **Java + Spring Boot + Bucket4j**, demonstrating real-world traffic control patterns used at scale by companies like Zomato, Swiggy, and Razorpay.

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Bucket4j](https://img.shields.io/badge/Bucket4j-8.10.1-0052CC?style=for-the-badge)
![Tests](https://img.shields.io/badge/Tests-19%20Passing-brightgreen?style=for-the-badge&logo=junit5)
![k6](https://img.shields.io/badge/Load%20Tested-k6-7D64FF?style=for-the-badge&logo=k6)

---

## 📌 What This Project Is

Most college projects show basic CRUD. This project demonstrates something interviewers actually care about — **how do you protect a backend system at scale?**

This is a complete **e-commerce backend API** (orders, products, auth) with rate limiting as its core infrastructure layer. The rate limiter is built the way it's done in real production systems — not inside controllers, but as a **servlet filter that intercepts every request** before any business logic runs.

**The core question this project answers:**
*"How do you ensure one user can't flood your system and ruin the experience for everyone else?"*

---

## 🏗️ Architecture

```
Incoming HTTP Request
        │
        ▼
┌─────────────────────┐
│   RateLimitFilter   │  ← OncePerRequestFilter
│                     │    Runs BEFORE every controller
│  1. Extract userId  │    Falls back to IP for anonymous users
│  2. Pick bucket     │    Routes by endpoint path
│  3. tryConsume(1)   │    Atomic, thread-safe token check
│                     │
│  ✅ Allowed ──────────────────────────────────────────┐
│  ❌ Blocked → 429 written here, chain stops           │
└─────────────────────┘                                 │
                                                        ▼
                                           ┌────────────────────┐
                                           │   Controller Layer  │
                                           │  zero RL knowledge  │
                                           └─────────┬──────────┘
                                                     │
                                           ┌─────────▼──────────┐
                                           │   Service Layer     │
                                           │  pure business logic│
                                           └────────────────────┘
```

### Dual-Layer Protection on Order Placement

```
POST /api/orders/place
        │
        ▼
Global Bucket (10,000/min) ──❌──▶ 429 "System at capacity"
        │
       ✅
        │
        ▼
User Bucket (5/min) ─────────❌──▶ 429 "You are ordering too fast"
        │
       ✅
        │
        ▼
  Order confirmed ✅
```

---

## 🗂️ Project Structure

```
src/
├── main/java/com/dev/rate_limiter_service/
│   ├── config/
│   │   ├── RateLimiterConfig.java        # Global Bucket4j bean definitions
│   │   └── RateLimitProperties.java      # Binds rate-limit.* from application.yml
│   │
│   ├── filter/
│   │   └── RateLimitFilter.java          # ⭐ Core — intercepts ALL requests
│   │
│   ├── service/
│   │   ├── BucketService.java            # Bucket lifecycle management
│   │   ├── OrderService.java             # Order business logic
│   │   ├── ProductService.java           # Product catalogue logic
│   │   └── MetricsService.java           # In-memory request counters
│   │
│   ├── controller/
│   │   ├── OrderController.java          # POST /api/orders/place, GET /api/orders/history
│   │   ├── ProductController.java        # GET /api/products, GET /api/products/{id}
│   │   ├── AuthController.java           # POST /api/auth/login, /forgot-password
│   │   └── AdminController.java          # Live metrics, bucket status, reset tools
│   │
│   ├── dto/
│   │   ├── ApiResponse.java              # Generic wrapper { success, data, error, meta }
│   │   ├── RateLimitMeta.java            # remainingTokens + limitType on every response
│   │   ├── Order.java
│   │   └── Product.java
│   │
│   ├── exception/
│   │   └── GlobalExceptionHandler.java   # Consistent JSON for all errors
│   │
│   └── logging/
│       └── RateLimitLogger.java          # Structured [ALLOWED] / [BLOCKED] logs
│
└── test/java/com/dev/rate_limiter_service/
    ├── service/
    │   ├── BucketServiceTest.java              # 6 unit tests — bucket isolation & lifecycle
    │   └── MetricsServiceTest.java             # 3 unit tests — counter accuracy
    ├── filter/
    │   └── RateLimitFilterTest.java            # 7 tests — filter interception & headers
    └── controller/
        └── OrderControllerIntegrationTest.java # 3 full stack integration tests
```

---

## 🔒 Rate Limiting Rules

| Endpoint | Strategy | Limit | Who Is Affected |
|---|---|---|---|
| `POST /api/orders/place` | **Dual** (global + per-user) | 10,000/min global · 5/min per user | Both limits must pass |
| `GET /api/orders/history` | Per-user | 5/min | Only that user |
| `GET /api/products` | Per-user | 100/min | Only that user |
| `POST /api/auth/login` | Per-user | 10/min | Only that user |
| `POST /api/auth/forgot-password` | **Shared** | 3/min | All users combined |
| `GET /api/admin/**` | None | Unlimited | — |

### User Identity Resolution

| Caller Type | Identifier Used | How |
|---|---|---|
| Logged-in user | `X-User-Id` header value | `X-User-Id: user123` |
| Anonymous visitor | IP address | Automatic fallback in filter |
| Behind a proxy | First IP in `X-Forwarded-For` | Proxy-aware extraction |

---

## 📡 API Reference

### Orders
```
POST   /api/orders/place              Place a new order
GET    /api/orders/history            Get order history for a user
```

### Products
```
GET    /api/products                  List all products (optional: ?category=Pizza)
GET    /api/products/{id}             Get product by ID
```

### Auth
```
POST   /api/auth/login                Login — 10 attempts/min per user
POST   /api/auth/forgot-password      Password reset — 3 attempts/min shared
```

### Admin (Monitoring & Demo)
```
GET    /api/admin/metrics                   Live request stats and block rate
GET    /api/admin/bucket-status/{userId}    Remaining tokens for all buckets of a user
DELETE /api/admin/reset/{userId}            Reset all buckets for a user
DELETE /api/admin/metrics/reset             Reset all metric counters
```

---

## 📬 Request & Response Format

### Required Header
```
X-User-Id: user123
```
Not required on `/api/auth/forgot-password` and `/api/products` — these fall back to IP-based limiting automatically.

### Success Response `200`
```json
{
  "success": true,
  "message": "Order placed successfully!",
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

### Rate Limited Response `429`
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

# 2. Build
mvn clean install

# 3. Run
mvn spring-boot:run
# App starts at http://localhost:8080
```

### Quick Smoke Test

```bash
# Run this 6 times — first 5 return 200, 6th returns 429
curl -X POST "http://localhost:8080/api/orders/place" \
  -H "X-User-Id: user123" \
  -d "restaurantId=R001&items=Pizza,Coke&amount=428"

# Check live metrics
curl http://localhost:8080/api/admin/metrics

# Check bucket status for a user
curl http://localhost:8080/api/admin/bucket-status/user123

# Reset user bucket (for re-testing)
curl -X DELETE http://localhost:8080/api/admin/reset/user123
```

### Postman Collection
Import `/postman/RateLimiter.postman_collection.json` — all endpoints pre-configured with headers, params, and example values. Ready to run in 30 seconds.

---

## ⚙️ Configuration

All limits live in `application.yml` — no recompilation needed to change them:

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

## 🧪 Test Results

### JUnit — 19 Tests, 0 Failures

```
BucketServiceTest                (6 tests)  ✅ PASS
MetricsServiceTest               (3 tests)  ✅ PASS
RateLimitFilterTest              (7 tests)  ✅ PASS
OrderControllerIntegrationTest   (3 tests)  ✅ PASS

Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Test Class | What It Proves |
|---|---|
| `BucketServiceTest` | User bucket isolation, lazy creation, reset lifecycle |
| `MetricsServiceTest` | Counter accuracy, blocked vs allowed tracking |
| `RateLimitFilterTest` | Filter intercepts correctly, 429 on 6th request, correct headers |
| `OrderControllerIntegrationTest` | Full stack — real HTTP through filter → controller → response |

### k6 Load Test — 3 Scenarios

```bash
k6 run k6/load-test.js
```

| Scenario | VUs | Duration | Purpose |
|---|---|---|---|
| `normal_load` | 5 | 30s | Steady traffic — limits enforced correctly |
| `spike_load` | 0 → 30 → 0 | 40s | Traffic spike — system stays stable |
| `isolated_users` | 2 | 10 iters each | Proves independent per-user buckets |

**Results:**

| Metric | Result |
|---|---|
| Total Requests | 6,324 |
| Allowed (200) | 4,470 |
| Blocked (429) | 1,854 |
| Block Rate | 29.3% |
| **Avg Latency** | **1.73ms** |
| **p95 Latency** | **3.19ms** |

> The rate limiter adds **under 2ms** of average latency per request. The token bucket check is a pure in-memory atomic CAS operation — no database, no network, no I/O.

---

## 🔑 Key Technical Decisions

### Why `OncePerRequestFilter` instead of a controller interceptor?

Filters run at the **Servlet level** — before Spring's `DispatcherServlet`. A blocked request never wastes CPU on controller instantiation, argument resolution, or service calls. The request is rejected at the earliest possible point in the lifecycle.

### Why `Refill.greedy()` instead of `Refill.intervally()`?

`intervally` refills all tokens at once after the window — a user can burst 5 requests at second 0, then 5 more at second 60, effectively doubling their limit at the boundary. `greedy` refills smoothly at **1 token every 12 seconds** for a 5/min limit. This prevents window-boundary exploitation and matches how GitHub and Stripe implement their rate limiting.

### Why `ConcurrentHashMap` with `computeIfAbsent`?

`computeIfAbsent` is atomic — two threads racing to create a bucket for the same new user will always produce exactly **one bucket**, with no explicit locking. Zero synchronization overhead on the hot path for returning users.

### Why lazy bucket creation?

Pre-allocating buckets for all users at startup is impossible at scale. Lazy creation means only **currently active users** consume memory. A system with millions of registered users doesn't need millions of buckets in RAM.

### Known trade-off — global token not refunded on user block

In dual-check mode: if the global bucket passes but the user bucket fails, the consumed global token is not returned. Bucket4j has no rollback mechanism. At very high traffic, the global count depletes slightly faster than the true confirmed order count. This is documented in the code with a comment.

---

## 🔮 Future Enhancements

- [ ] **Redis-backed distributed buckets** — `bucket4j-redis` so multiple service instances share the same bucket state
- [ ] **JWT authentication** — extract `userId` from token claims instead of a plain header, eliminating spoofing
- [ ] **Sliding window algorithm** — more accurate than token bucket for certain anti-abuse scenarios
- [ ] **Micrometer + Prometheus + Grafana** — replace in-memory `MetricsService` with a production observability stack
- [ ] **Bucket eviction** — scheduled job to remove inactive user buckets and prevent unbounded memory growth
- [ ] **Tiered limits** — premium users get higher quotas, configurable per user tier

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Language |
| Spring Boot | 4.0.5 | Web framework |
| Bucket4j | 8.10.1 | Token bucket algorithm |
| Jackson | 2.x | JSON serialization |
| JUnit 5 | 5.x | Unit and integration testing |
| MockMvc | — | HTTP layer testing without a running server |
| k6 | Latest | Load and performance testing |
| Maven | 3.8+ | Build tool |

---

## 👤 Author

**Sunchit**
- GitHub: [@Sunchit](https://github.com/Sunchit)

---

> *"Rate limiting is not about blocking users — it's about protecting the system so it stays available for everyone."*
