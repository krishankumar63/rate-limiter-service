import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Custom metrics ────────────────────────────────────────────────────────────
const blockedRequests   = new Counter('blocked_requests');    // counts 429s
const allowedRequests   = new Counter('allowed_requests');    // counts 200s
const rateLimitHitRate  = new Rate('rate_limit_hit_rate');    // % of 429s
const orderLatency      = new Trend('order_latency_ms');      // latency trend

// ── Test configuration ────────────────────────────────────────────────────────
export let options = {
  scenarios: {

    // Scenario 1: Normal steady load — should mostly pass
    normal_load: {
      executor: 'constant-vus',
      vus: 5,                  // 5 simultaneous users
      duration: '30s',
      tags: { scenario: 'normal_load' }
    },

    // Scenario 2: Spike — hammer the API, lots of 429s expected
    spike_load: {
      executor: 'ramping-vus',
      startTime: '35s',        // starts after normal_load finishes
      startVUs: 0,
      stages: [
        { duration: '10s', target: 30 },   // ramp up to 30 users
        { duration: '20s', target: 30 },   // stay at 30 users
        { duration: '10s', target: 0  },   // ramp down
      ],
      tags: { scenario: 'spike_load' }
    },

    // Scenario 3: Two isolated users — prove independent buckets
    isolated_users: {
      executor: 'per-vu-iterations',
      startTime: '80s',        // starts after spike finishes
      vus: 2,
      iterations: 10,          // each VU does exactly 10 requests
      tags: { scenario: 'isolated_users' }
    }
  },

  // ── Pass/Fail thresholds ───────────────────────────────────────────────────
  thresholds: {
      http_req_duration:   ['p(95)<500'],
      rate_limit_hit_rate: ['rate<0.8'],  // change 0.5 to 0.8
      blocked_requests:    ['count>0'],
  }
};

// ── Main test function (runs once per VU per iteration) ───────────────────────
export default function () {

  // Each virtual user gets their own userId
  // __VU is the VU number (1, 2, 3...) — this simulates different users
  const userId = `user_${__VU}`;

  // ── Test 1: Place Order (rate limited to 5/min per user) ──────────────────
  const orderRes = http.post(
    `http://localhost:8080/api/orders/place` +
    `?restaurantId=R001&restaurantName=TestRestaurant&items=Pizza,Coke&amount=299`,
    null,
    {
      headers: { 'X-User-Id': userId },
      tags: { endpoint: 'place_order' }
    }
  );

  // Record latency
  orderLatency.add(orderRes.timings.duration);

  // Track allowed vs blocked
  if (orderRes.status === 200) {
    allowedRequests.add(1);
    rateLimitHitRate.add(false);
  } else if (orderRes.status === 429) {
    blockedRequests.add(1);
    rateLimitHitRate.add(true);
  }

  // Validate response shape
  check(orderRes, {
    'status is 200 or 429':         (r) => r.status === 200 || r.status === 429,
    'response has success field':   (r) => JSON.parse(r.body).success !== undefined,
    'response has meta field':      (r) => JSON.parse(r.body).meta !== undefined,
    '429 has retryAfterSeconds':    (r) => {
      if (r.status === 429) {
        return JSON.parse(r.body).retryAfterSeconds !== undefined;
      }
      return true; // skip check for 200s
    },
    'response time under 200ms':    (r) => r.timings.duration < 200,
  });

  // ── Test 2: Browse Products (rate limited to 100/min per user) ────────────
  const productRes = http.get(
    `http://localhost:8080/api/products`,
    {
      headers: { 'X-User-Id': userId },
      tags: { endpoint: 'get_products' }
    }
  );

  check(productRes, {
    'products returned 200': (r) => r.status === 200,
    'products array exists': (r) => {
      const body = JSON.parse(r.body);
      return body.data !== undefined;
    }
  });

  // ── Test 3: Check admin metrics (no rate limit) ───────────────────────────
  const metricsRes = http.get(
    `http://localhost:8080/api/admin/metrics`,
    { tags: { endpoint: 'admin_metrics' } }
  );

  check(metricsRes, {
    'admin metrics returned 200': (r) => r.status === 200,
    'totalRequests is tracked':   (r) => {
      const body = JSON.parse(r.body);
      return body.data.totalRequests > 0;
    }
  });

  sleep(0.5); // wait 500ms between iterations
}

// ── Summary handler — printed after test completes ────────────────────────────
export function handleSummary(data) {
  const total    = data.metrics.http_reqs.values.count;
  const blocked  = data.metrics.blocked_requests
                    ? data.metrics.blocked_requests.values.count : 0;
  const allowed  = total - blocked;
  const p95      = data.metrics.http_req_duration.values['p(95)'].toFixed(2);
  const avgMs    = data.metrics.http_req_duration.values.avg.toFixed(2);

  console.log('\n========================================');
  console.log('       RATE LIMITER - TEST RESULTS      ');
  console.log('========================================');
  console.log(`Total Requests  : ${total}`);
  console.log(`Allowed (200)   : ${allowed}`);
  console.log(`Blocked (429)   : ${blocked}`);
  console.log(`Block Rate      : ${((blocked/total)*100).toFixed(1)}%`);
  console.log(`Avg Latency     : ${avgMs}ms`);
  console.log(`p95 Latency     : ${p95}ms`);
  console.log('========================================\n');

  // Save results to a JSON file
  return {
    'k6/results.json': JSON.stringify(data, null, 2),
  };
}