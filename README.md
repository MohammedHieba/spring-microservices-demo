# Event Driven Saga Microservices

![Java](https://img.shields.io/badge/Java-17-orange)

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)

![Kafka](https://img.shields.io/badge/Apache-Kafka-black)

![Architecture](https://img.shields.io/badge/Microservices-Architecture-blue)

![Pattern](https://img.shields.io/badge/Saga-Orchestration-red)

A **production-style Spring Boot microservices architecture** demonstrating **event-driven communication using Apache Kafka**, **distributed transactions using the Saga Pattern**, and **reliable event publishing using the Transactional Outbox Pattern with Debezium CDC**.

This project demonstrates several **real-world distributed system patterns** used in modern **microservices architectures**, including **secure service-to-service communication using Keycloak** and **reliable event-driven workflows**.

---

# Architecture Overview

```

                  +----------------------+

                  |      API Gateway     |

                  |   Spring Cloud GW    |

                  +----------+-----------+

                             |

                    ---------------------

                    |                   |

              +-----------+       +-----------+

              |  Order    |       | Inventory |

              |  Service  |       |  Service  |

              +-----+-----+       +-----+-----+

                    |                   |

                    -----------+--------

                                |

                           +---------+

                           | Kafka   |

                           +---------+

                        +----------------+

                        |  Debezium CDC  |

                        +----------------+

                                |

                           Outbox Tables

                +-----------------------------+

                |  Eureka Service Discovery   |

                +-----------------------------+

```

---

# Microservices

This system currently consists of **two core microservices** designed to demonstrate **event-driven distributed transactions**.

## Order Service

The **Order Service acts as the Saga Orchestrator** and manages the lifecycle of orders.

Responsibilities:

- Create orders

- Publish `OrderCreatedEvent`

- Handle `InventoryResponseEvent`

- Decide final order state

- Publish:

  - `OrderApprovedEvent`

  - `OrderRejectedEvent`

The Order Service controls the distributed transaction workflow across services.

---

## Inventory Service

The **Inventory Service** is responsible for validating product availability and managing reserved stock.

Responsibilities:

- Validate product stock

- Reserve inventory

- Release reserved stock when order is rejected

- Finalize inventory deduction when order approved

- Publish `InventoryResponseEvent`

---

# Transactional Outbox Pattern

This project implements the **Transactional Outbox Pattern** to guarantee reliable event publishing.

### Problem

Dual Write Problem (Database + Kafka publish inconsistency)

### Solution

Events are written to an **Outbox table within the same database transaction** as the business operation.

Example flow:

Order Created → Order saved in database → Outbox event stored in same transaction

---

# Debezium Change Data Capture (CDC)

Instead of publishing events directly to Kafka, **Debezium monitors the Outbox table** and streams events automatically to Kafka.

Flow:

Service Transaction → Outbox Table → Debezium CDC → Kafka Topic

Benefits:

- Guaranteed event delivery

- No dual write problem

- Reliable event streaming

- Clean separation between business logic and messaging

---

# Debezium Connector Configuration

The following configuration streams **Outbox events from MySQL to Kafka topics** using the **Debezium Outbox Event Router**.

```json

{

  "name": "microservices-outbox-connector",

  "config": {

    "connector.class": "io.debezium.connector.mysql.MySqlConnector",

    "database.hostname": "host.docker.internal",

    "database.port": "3306",

    "database.user": "root",

    "database.password": "12345",

    "database.server.id": "184054",

    "database.server.name": "microservices",

    "database.include.list": "orderservice,inventoryservice",

    "table.include.list": "orderservice.outbox,inventoryservice.outbox",

    "topic.prefix": "dbserver1",

    "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",

    "schema.history.internal.kafka.topic": "schema-changes.microservices",

    "transforms": "outbox",

    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",

    "transforms.outbox.route.by.field": "topic",

    "transforms.outbox.table.field.event.id": "event_id",

    "transforms.outbox.table.field.event.key": "aggregate_id",

    "transforms.outbox.table.field.event.payload": "payload",

    "transforms.outbox.table.expand.json.payload": "true",

    "transforms.outbox.route.topic.replacement": "${routedByValue}",

    "value.converter": "org.apache.kafka.connect.json.JsonConverter",

    "value.converter.schemas.enable": "false",

    "key.converter": "org.apache.kafka.connect.storage.StringConverter"

  }

}

```

---

# Kafka Event Communication

Microservices communicate asynchronously using **Kafka topics**.

Example topics:

- order-created-topic

- inventory-response-topic

- order-approved-topic

- order-rejected-topic

Benefits:

- Loose coupling

- High scalability

- Fault tolerance

- Asynchronous processing

---

# Saga Pattern (Distributed Transactions)

The system uses **Saga Orchestration** to maintain consistency across services.

The **Order Service orchestrates the workflow**.

Workflow:

Client → API Gateway → Order Service

Order Service publishes **OrderCreatedEvent**

Inventory Service processes event and publishes **InventoryResponseEvent**

Order Service decides:

If success → Publish **OrderApprovedEvent**

If failure → Publish **OrderRejectedEvent**

Inventory Service then:

- Finalizes inventory on approval

- Releases reserved stock on rejection

This guarantees **eventual consistency across microservices**.

---

# Dead Letter Topics (DLT)

Kafka **Dead Letter Topics** are implemented to handle failed message processing.

Example topics:

- order-created-topic-dlt

- order-approved-topic-dlt

- order-rejected-topic-dlt

- inventory-response-topic-dlt

Purpose:

- Handle poison messages

- Prevent consumer crashes

- Enable retry and manual recovery

---

# Security (Keycloak)

Service-to-service communication is secured using **Keycloak**.

Features:

- OAuth2 authentication

- Token-based authorization

- Secure service-to-service communication

- Centralized identity management

---

# Shared Event Contracts

A shared module provides **event contracts** used by all services.

microservices-events

Contains:

- Event DTOs

- Kafka message contracts

- Shared models

This prevents serialization mismatches between services.

---

# Technology Stack

## Core

- Java 17

- Spring Boot 3

- Spring Cloud

- Spring Data JPA

- Apache Kafka

## Infrastructure

- Kafka

- Zookeeper

- Debezium

- Docker

- MySQL

## Security

- Keycloak

- OAuth2

## Architecture Patterns

- Microservices Architecture

- Event Driven Architecture

- Saga Pattern (Orchestration)

- Transactional Outbox Pattern

- Change Data Capture (Debezium)

- Dead Letter Topic Strategy

- Eventual Consistency

---

# Running the Project

## 1 Start Infrastructure

```

docker-compose up -d

```

This starts:

- Kafka

- Zookeeper

- Debezium

- Databases

---

## 2 Start Service Discovery

Run:

service-discovery

Eureka dashboard:
http://localhost:8761

---

## 3 Start Microservices

Run services in this order:

inventory-service

order-service

api-gateway

---

# Project Structure

```

event-driven-saga-microservices

│

├── api-gateway

├── order-service

├── inventory-service

├── service-discovery

└── microservices-events

```

---

# Implemented Microservices Patterns

This project demonstrates:

- Event Driven Architecture

- Saga Pattern (Orchestration)

- Transactional Outbox Pattern

- Debezium Change Data Capture

- Dead Letter Topic Strategy

- Service Discovery Pattern

- API Gateway Pattern

- Asynchronous Messaging

- Eventual Consistency

---

# Future Improvements

Planned production enhancements:

- Idempotent Kafka Consumers

- Rate Limiting

- Circuit Breaker (Resilience4j)

- Distributed Tracing (Zipkin / Jaeger)

- Centralized Logging (ELK Stack)

- Monitoring (Prometheus + Grafana)

---

# Author

**Mohamed Hieba**

Java Backend Engineer
 