# Convenience targets. On Windows without `make`, run the underlying commands directly
# (see README "How to run"). `MVN` falls back to the wrapper if it exists, else system Maven.

MVN := $(if $(wildcard ./mvnw),./mvnw,mvn)

.PHONY: help build test verify clean infra-up infra-down infra-logs

help:
	@echo "build       - compile + package all modules (skip tests)"
	@echo "test        - run unit tests"
	@echo "verify      - full build: unit + integration tests"
	@echo "clean       - remove build output"
	@echo "infra-up    - start Postgres + Redis + Kafka via Docker Compose"
	@echo "infra-down  - stop infrastructure (keep data)"
	@echo "infra-logs  - tail infrastructure logs"

build:
	$(MVN) -q -B -ntp -DskipTests package

test:
	$(MVN) -q -B -ntp test

verify:
	$(MVN) -B -ntp verify

clean:
	$(MVN) -q -B clean

infra-up:
	docker compose up -d

infra-down:
	docker compose down

infra-logs:
	docker compose logs -f
