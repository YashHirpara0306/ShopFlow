// k6 load test: the read-heavy product browse path (cache-backed).
//   k6 run -e GATEWAY=http://localhost:8080 scripts/load/browse-products.js
import http from "k6/http";
import { check, sleep } from "k6";

const GW = __ENV.GATEWAY || "http://localhost:8080";

export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 50 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<300"],
  },
};

export default function () {
  const list = http.get(`${GW}/api/products?page=0&size=20`);
  check(list, { "list 200": (r) => r.status === 200 });

  const body = list.json();
  if (body.content && body.content.length) {
    const id = body.content[Math.floor(Math.random() * body.content.length)].id;
    const one = http.get(`${GW}/api/products/${id}`);
    check(one, { "detail 200": (r) => r.status === 200 });
  }
  sleep(1);
}
