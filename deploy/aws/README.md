# AWS deployment

Two paths. Both keep development free (everything runs locally) and demo cost low.

## Path A — lowest cost (~$0 with free tier, ~$15/mo after): one EC2 box

For a portfolio demo, not real load.

| Component | Choice |
|---|---|
| Compute | one `t3.small` (2 vCPU / 2 GiB) or `t4g.small` EC2, Amazon Linux 2023 + Docker |
| Run | `git clone`, `mvn -DskipTests package`, `docker compose up -d` (the repo's Compose file) |
| TLS / DNS | Caddy or nginx on the box for HTTPS + a Route 53 A record (or just the public IP) |
| Backups | `pg_dump` cron to an S3 bucket |
| Cost control | stop the instance when not demoing; `t4g` (Graviton) is cheapest |

Trade-off: single point of failure, no autoscaling, data on the instance's EBS volume.

## Path B — managed, still cheap (~$40–70/mo): ECS Fargate + RDS

| Concern | Service | Sizing |
|---|---|---|
| Containers | **ECS Fargate** — one service per task definition, images from **ECR** (push from the `docker-publish` workflow, retargeted to ECR) | 0.25 vCPU / 0.5 GB each; gateway + order-service `desiredCount: 2`, rest `1` |
| Ingress | **Application Load Balancer** → the `api-gateway` target group only; the other services are private (security-group locked to the ALB / gateway task) | 1 ALB |
| SQL | **RDS PostgreSQL** `db.t4g.micro`, single-AZ, 20 GB gp3; one instance, six databases (or six schemas) | ~$13/mo |
| Cache | **ElastiCache** `cache.t4g.micro` Redis, or Redis on a tiny EC2 to save money | ~$12/mo or ~$4/mo |
| Events | **MSK Serverless** (pay per GB) for low volume, or a single-broker Kafka on a `t4g.small` EC2 for a fixed ~$15/mo | volume-dependent |
| Objects | **S3** bucket for product images (`imageUrl` upload) and archived logs | pennies |
| Secrets | **AWS Secrets Manager** (`JWT_SECRET`, DB creds); injected into tasks via `secrets:` in the task def | ~$0.40/secret/mo |
| Identity | **IAM** task roles, least-privilege: each task role gets only its own Secrets Manager entries + (Product) `s3:PutObject` on the images prefix + `logs:CreateLogStream/PutLogEvents` | — |
| Logs/metrics | **CloudWatch Logs** (awslogs driver per task) + CloudWatch alarms on ALB 5xx, RDS CPU/connections, ECS task restarts; scrape `/actuator/prometheus` with an ADOT sidecar or run Prometheus/Grafana on the same box as Path A | — |
| Scaling | ECS Service Auto Scaling on CPU (target 60%) for `api-gateway` and `order-service` | — |

### Component → where

```
Internet ─▶ ALB (public) ─▶ api-gateway (Fargate, public subnet ENI, SG: 443 from ALB)
                                  │  (internal, SG: from gateway SG only)
        ┌─────────────────────────┼───────────────────────────────────┐
        ▼            ▼            ▼            ▼            ▼           ▼
   user-svc    product-svc    cart-svc    order-svc    inventory-svc  payment/notif
        │            │            │            │            │
        └──────────▶ RDS PostgreSQL (private subnets, SG: 5432 from the svc SGs)
   product/cart ───▶ ElastiCache Redis (private, SG: 6379 from svc SGs)
   order/inv/pay/notif/user ─▶ MSK / Kafka (private, SG: 9092 from svc SGs)
   product ───▶ S3 (VPC gateway endpoint)
```

## Dry-run checklist (do once, then tear down)

1. `aws ecr create-repository` × 8; retag + push images (or point `docker-publish.yml` at ECR).
2. VPC with 2 public + 2 private subnets, one NAT gateway (or VPC endpoints to skip NAT cost).
3. RDS `db.t4g.micro`; create the 6 databases; put the master password in Secrets Manager.
4. ElastiCache `cache.t4g.micro` (or Redis on EC2).
5. Kafka: MSK Serverless cluster, or Kafka container on a `t4g.small`.
6. 8 ECS task definitions (env from ConfigMap-equivalent + `secrets` from Secrets Manager), 8 ECS
   services in one cluster; only `api-gateway` registered with the ALB target group.
7. ALB + HTTPS listener (ACM cert) + Route 53 record.
8. CloudWatch alarms; ECS Service Auto Scaling policies.
9. Smoke test: `GATEWAY_URL=https://<domain> ./scripts/smoke.sh`.
10. **Tear down**: delete ECS services + cluster, ALB, RDS (skip final snapshot for a demo),
    ElastiCache/MSK, NAT gateway, ECR repos. NAT gateway and idle RDS/MSK are the things that
    quietly cost money.
