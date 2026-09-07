-- Runs once, on first startup of the Postgres container (docker-entrypoint-initdb.d).
--
-- Each microservice owns a logically separate database on the same instance. No service
-- has credentials for another service's database; there are no cross-database joins.
-- cart-service is intentionally absent: it is Redis-only.

CREATE DATABASE shopflow_user;
CREATE DATABASE shopflow_product;
CREATE DATABASE shopflow_order;
CREATE DATABASE shopflow_inventory;
CREATE DATABASE shopflow_payment;
CREATE DATABASE shopflow_notification;
