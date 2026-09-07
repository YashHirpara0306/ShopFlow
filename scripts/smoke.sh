#!/usr/bin/env bash
# End-to-end smoke test against a running `docker compose up` stack (all traffic via the gateway).
# Requires: curl, jq.  Usage: ./scripts/smoke.sh [GATEWAY_URL]
set -euo pipefail

GW="${1:-http://localhost:8080}"
ADMIN_EMAIL="admin@shopflow.local"     # must be in BOOTSTRAP_ADMIN_EMAILS
PW="password1"
CUST_EMAIL="customer+$(date +%s)@shopflow.local"

say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
post() { curl -fsS -X POST "$GW$1" -H 'Content-Type: application/json' "${@:3}" -d "$2"; }
auth() { curl -fsS "$@"; }

say "register admin (idempotent) + login"
curl -fsS -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\",\"displayName\":\"Admin\"}" >/dev/null 2>&1 || true
ADMIN_TOKEN=$(post /api/auth/login "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\"}" | jq -r .accessToken)
[ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != null ] || { echo "admin login failed (is $ADMIN_EMAIL in BOOTSTRAP_ADMIN_EMAILS?)"; exit 1; }

say "admin: create category + product"
CAT_ID=$(curl -fsS -X POST "$GW/api/categories" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Smoke Test"}' | jq -r .id)
SKU="SMOKE-$(date +%s)"
PROD_ID=$(curl -fsS -X POST "$GW/api/products" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"sku\":\"$SKU\",\"name\":\"Smoke Widget\",\"price\":199.00,\"categoryId\":\"$CAT_ID\"}" | jq -r .id)
echo "product $PROD_ID"

say "admin: stock the product (10 units)"
curl -fsS -X POST "$GW/api/inventory/stock" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"productId\":\"$PROD_ID\",\"quantity\":10}" | jq -c .

say "customer: register + login"
post /api/auth/register "{\"email\":\"$CUST_EMAIL\",\"password\":\"$PW\",\"displayName\":\"Cust\"}" >/dev/null
CUST_TOKEN=$(post /api/auth/login "{\"email\":\"$CUST_EMAIL\",\"password\":\"$PW\"}" | jq -r .accessToken)

say "customer: add to cart + read cart"
curl -fsS -X POST "$GW/api/cart/items" -H "Authorization: Bearer $CUST_TOKEN" \
  -H 'Content-Type: application/json' -d "{\"productId\":\"$PROD_ID\",\"quantity\":2}" | jq -c '{totalQuantity, subtotal}'

say "customer: place order (202 Accepted)"
ORDER_ID=$(curl -fsS -X POST "$GW/api/orders" -H "Authorization: Bearer $CUST_TOKEN" \
  -H "Idempotency-Key: $(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)" | jq -r .id)
echo "order $ORDER_ID"

say "poll order status (Saga runs asynchronously)"
for i in $(seq 1 30); do
  STATUS=$(curl -fsS "$GW/api/orders/$ORDER_ID" -H "Authorization: Bearer $CUST_TOKEN" | jq -r .status)
  echo "  [$i] $STATUS"
  case "$STATUS" in
    CONFIRMED) echo -e "\n\033[1;32mSMOKE TEST PASSED - order CONFIRMED\033[0m"; exit 0 ;;
    CANCELLED|FAILED) echo -e "\n\033[1;31morder ended $STATUS (payment sim may have declined; re-run)\033[0m"; exit 1 ;;
  esac
  sleep 2
done
echo "order did not reach a terminal state in time"; exit 1
