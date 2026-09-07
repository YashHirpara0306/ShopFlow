// k6 load test: the write path - login, add to cart, place order, measure accept latency.
// Requires a product id with stock. Pass PRODUCT_ID (an already-stocked product) and CUSTOMER creds,
// or the script registers a fresh customer per VU iteration.
//   k6 run -e GATEWAY=http://localhost:8080 -e PRODUCT_ID=<uuid> scripts/load/place-order.js
import http from "k6/http";
import { check, sleep } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const GW = __ENV.GATEWAY || "http://localhost:8080";
const PRODUCT_ID = __ENV.PRODUCT_ID;

export const options = {
  stages: [
    { duration: "30s", target: 10 },
    { duration: "1m", target: 25 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.02"],
    "http_req_duration{name:createOrder}": ["p(95)<500"],
  },
};

export default function () {
  if (!PRODUCT_ID) {
    throw new Error("set -e PRODUCT_ID=<stocked product uuid>");
  }
  const email = `k6+${uuidv4()}@shopflow.local`;
  const json = { headers: { "Content-Type": "application/json" } };

  http.post(`${GW}/api/auth/register`, JSON.stringify({ email, password: "password1", displayName: "K6" }), json);
  const login = http.post(`${GW}/api/auth/login`, JSON.stringify({ email, password: "password1" }), json);
  const token = login.json("accessToken");
  const authed = { headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` } };

  http.post(`${GW}/api/cart/items`, JSON.stringify({ productId: PRODUCT_ID, quantity: 1 }), authed);

  const create = http.post(`${GW}/api/orders`, null, {
    headers: { ...authed.headers, "Idempotency-Key": uuidv4() },
    tags: { name: "createOrder" },
  });
  check(create, { "order 202": (r) => r.status === 202 });
  sleep(1);
}
