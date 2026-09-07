# Kubernetes

Minimal manifests to run the whole system on a local cluster (`kind` / `minikube`). Not
production-grade — no persistence, single-replica infra, placeholder secrets.

## Concepts (as used here)

| Object | What it is | Here |
|---|---|---|
| **Pod** | one or more containers, the smallest deployable unit | each service runs one container per Pod |
| **Deployment** | declares *N* identical Pods and replaces them on change/crash | one per service; `replicas: 2` for gateway + order-service |
| **Service** | a stable in-cluster DNS name + virtual IP load-balancing to a Deployment's Pods | `http://order-service:8084` etc. — this is the "service discovery" (ADR-0003) |
| **ConfigMap** | non-secret key/value config, mounted as env or files | `shopflow-config` (hosts, URIs, profile) |
| **Secret** | base64 key/value for sensitive data | `shopflow-secrets` (`JWT_SECRET`, DB password) — from a sealed-secret / external store in reality |
| **Ingress** | HTTP routing from outside the cluster to a Service | all `/` → `api-gateway` |
| **HorizontalPodAutoscaler** | adds/removes Pods based on CPU (or custom metrics) | `order-service` 2→6, `api-gateway` 2→5 at 70% CPU |

## Apply (kind)

```bash
kind create cluster
kubectl apply -k https://kubernetes.github.io/ingress-nginx ...   # or your ingress controller

# build + load images
mvn -DskipTests package
for s in api-gateway user-service product-service cart-service order-service inventory-service payment-service notification-service; do
  docker build -f services/$s/Dockerfile -t shopflow/$s:latest .
  kind load docker-image shopflow/$s:latest
done
# (then set every image newTag back to `latest` in kustomization.yaml, or use `latest` directly)

kubectl apply -k deploy/k8s/base
kubectl -n shopflow get pods -w            # wait for Ready
echo "127.0.0.1 shopflow.local" | sudo tee -a /etc/hosts
curl http://shopflow.local/actuator/health
```

## Production notes

- Replace `infra.yaml` with managed Postgres (RDS), Redis (ElastiCache), Kafka (MSK) and point the
  ConfigMap/Secret at those endpoints.
- Use `PodDisruptionBudget`s, `topologySpreadConstraints`, real `PersistentVolumeClaim`s (if any
  stateful workload stays in-cluster), resource `limits` on CPU, and network policies.
- Secrets from AWS Secrets Manager via the Secrets Store CSI driver or External Secrets Operator.
- The Deployments are stateless (state is in Postgres/Redis/Kafka) so they scale and roll freely.
