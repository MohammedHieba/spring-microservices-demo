# Spring Boot Microservices Architecture (Kafka + Saga Pattern)

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![Kafka](https://img.shields.io/badge/Apache-Kafka-black)
![Architecture](https://img.shields.io/badge/Microservices-Architecture-blue)
![Pattern](https://img.shields.io/badge/Saga-Pattern-red)

A **Spring Boot Microservices architecture** demonstrating **event-driven communication using Apache Kafka** and **distributed transaction management using the Saga Pattern**.

The system showcases real-world **microservices architecture patterns** including:

- API Gateway
- Service Discovery
- Event-Driven Communication
- Distributed Transactions
- Eventual Consistency

---

# Architecture Overview

```
                        +----------------------+
                        |      API Gateway     |
                        |   Spring Cloud GW    |
                        +----------+-----------+
                                   |
                    -----------------------------------
                    |                |                |
            +---------------+ +---------------+ +---------------+
            | Product       | | Order         | | Inventory     |
            | Service       | | Service       | | Service       |
            +-------+-------+ +-------+-------+ +-------+-------+
                    |                 |                 |
                    -------------------------------------
                                   |
                            +-------------+
                            |   Apache    |
                            |    Kafka    |
                            +-------------+

                     +---------------------------+
                     |   Eureka Service Discovery|
                     +---------------------------+
```

---

# Microservices

### Product Service
Responsible for managing the product catalog.

Features:
- Create product
- Retrieve products
- Publish product events

---

### Order Service
Responsible for creating orders and orchestrating the **Saga workflow**.

Features:

- Create orders
- Publish `OrderCreatedEvent`
- Handle order state transitions

---

### Inventory Service
Responsible for validating product availability.

Features:

- Check inventory
- Reserve stock
- Publish result events

---

### API Gateway

Single entry point for all clients.

Responsibilities:

- Request routing
- Load balancing
- Integration with service discovery

---

### Service Discovery (Eureka)

All microservices register themselves with **Eureka Server**.

Benefits:

- Dynamic service discovery
- Load balancing
- Loose coupling

---

### Microservices Events Module

Shared module containing:

- Event DTOs
- Kafka message contracts
- Shared models used across services

---

# Technology Stack

### Core

- Java **17**
- Spring Boot
- Spring Cloud
- Spring Cloud Gateway
- Spring Data JPA
- Apache Kafka

### Architecture

- Microservices Architecture
- Event Driven Architecture
- Saga Pattern
- Service Discovery Pattern
- API Gateway Pattern
- Eventual Consistency

### Infrastructure

- Docker
- Kafka
- Zookeeper
- MySQL / PostgreSQL

---

# Kafka Event Communication

Services communicate asynchronously using **Kafka topics**.

Example topics:

```
order-created
inventory-checked
order-confirmed
order-rejected
```

Benefits:

- Loose coupling
- High scalability
- Fault tolerance
- Asynchronous processing

---

# Saga Pattern (Distributed Transaction)

This project demonstrates **Saga Pattern using event orchestration**.

Flow:

```
Client
   |
   v
API Gateway
   |
   v
Order Service
   |
   | Publish Event
   v
OrderCreatedEvent
   |
   v
Inventory Service

If stock available:
    -> OrderConfirmedEvent

If stock unavailable:
    -> OrderRejectedEvent
```

This ensures **eventual consistency between microservices**.

---

# Running the Project

## 1 Start Infrastructure

Start Kafka and required services:

```bash
docker-compose up -d
```

---

## 2 Start Service Discovery

Run:

```
service-discovery
```

Eureka dashboard:

```
http://localhost:8761
```

---

## 3 Start Microservices

Run services in this order:

```
product-service
inventory-service
order-service
api-gateway
```

---

# API Examples

### Create Product

```
POST /api/products
```

---

### Create Order

```
POST /api/orders
```

---

### Get Products

```
GET /api/products
```

All requests go through:

```
http://localhost:8080
```

---

# Project Structure

```
spring-microservices-demo
│
├── api-gateway
├── product-service
├── order-service
├── inventory-service
├── service-discovery
└── microservices-events
```

---

# Implemented Microservices Patterns

This project demonstrates the implementation of:

- Event Driven Architecture
- Saga Pattern
- Service Discovery Pattern
- API Gateway Pattern
- Asynchronous Messaging
- Distributed Transactions
- Eventual Consistency

---

# Future Improvements

Possible production enhancements:

- Circuit Breaker (Resilience4j)
- Distributed Tracing (Zipkin / Jaeger)
- Centralized Logging (ELK Stack)
- Monitoring (Prometheus + Grafana)
- Authentication (OAuth2 / Keycloak)

---

# Author

Mohamed  
Java Backend Engineer
