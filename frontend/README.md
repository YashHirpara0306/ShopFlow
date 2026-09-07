# ShopFlow storefront (React)

A **Rebt**-branded storefront for the ShopFlow backend — Vite + React + TypeScript, TanStack Query,
React Router. Design tokens follow the brand style guide (Inter + Newsreader; Viridian Green
`#1FB2AA`, Desert Sand `#E8C9A6`, Onyx `#2F3536`).

## Run in dev

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173  (Vite proxies /api -> http://localhost:8080)
```

Point the proxy elsewhere with `VITE_GATEWAY_URL=http://host:8080 npm run dev`, or set
`VITE_API_BASE` for a non-proxied (CORS) setup.

Bring the backend up first: from the repo root, `mvn -DskipTests package && docker compose up -d`
(and `./scripts/smoke.sh` to seed a product + stock, or use the Admin area in the UI).

## Build

```bash
npm run build          # tsc --noEmit + vite build -> dist/
npm run preview
```

## In Docker Compose

The repo's `docker-compose.yml` has a `frontend` service (nginx serving the built SPA and proxying
`/api` to `api-gateway`). `docker compose up -d --build` then open `http://localhost:3000`.

## What's wired to the backend

| Screen | Endpoints |
|---|---|
| Home / Products / Product detail | `GET /api/products`, `/api/products/{id}`, `/api/categories` |
| Cart | client-side (localStorage); staged to `/api/cart` at checkout |
| Checkout → Order status | `DELETE /api/cart`, `POST /api/cart/items`, `POST /api/orders` (Idempotency-Key), `GET /api/orders/{id}` (polls until terminal) |
| My orders | `GET /api/orders` |
| Auth | `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh` (silent, on 401), `/api/auth/logout` |
| Admin | `POST /api/categories`, `POST /api/products`, `POST /api/inventory/stock`, `GET /api/inventory/{id}` |

Access token is kept in memory; the refresh token in `localStorage`; an Axios interceptor performs a
silent refresh + retry on a 401. The Admin area is gated on the `ADMIN` role
(`shopflow.security.bootstrap-admin-emails` on the backend makes an email an admin at registration).

Try ordering something priced with `.13` cents (e.g. `120.13`) to watch the Saga decline payment and
release the reservation on the order-status timeline.
