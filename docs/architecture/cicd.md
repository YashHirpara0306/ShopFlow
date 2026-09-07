# CI/CD

## Pipeline

```
git push / PR ──▶ .github/workflows/ci.yml
   job 1  compile-and-unit   : mvn -B -ntp -DskipITs verify        (fast, no Docker)
   job 2  integration        : mvn -B -ntp verify                  (needs job 1; Testcontainers: Postgres/Redis/Kafka)
                                └─ any test failure fails the pipeline

push to main / tag v* ──▶ .github/workflows/docker-publish.yml
   matrix over the 8 services: build image (context = repo root, per-service Dockerfile),
   push to ghcr.io/<owner>/<repo>/<service> with tags: branch, semver, short-sha; GHA layer cache.
```

## Why two CI jobs

`compile-and-unit` gives fast feedback (~2 min) on compile errors and logic bugs. `integration`
runs the Docker-backed tests (the Saga IT, the inventory concurrency IT, the refresh-reuse IT, …)
and only starts if job 1 passed, so a broken build does not spin up containers.

## Branch protection (configure on GitHub)

- `main` and `develop`: require the `compile-and-unit` and `integration` checks to pass before merge;
  require a PR; disallow force-push.
- Merge into `develop` from `feature/*`; promote `develop` → `main` for a release tag.

## Deploy

Deployment is manual in this project (portfolio scope):
- **Kubernetes:** `kubectl apply -k deploy/k8s/base` (see `deploy/k8s/README.md`) with images from GHCR.
- **AWS:** see `deploy/aws/README.md`.

A `deploy.yml` workflow (apply the K8s manifests on a tag, or `aws ecs update-service`) is the
natural next step and is intentionally left out to keep the repo runnable without cloud credentials.
