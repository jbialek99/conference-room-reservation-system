# Conference Room Reservation System

A conference room reservation application built with Java, Spring Boot and Microsoft Graph API integration.

## Technology stack

- Java 17
- Spring Boot 3.4.3
- Spring MVC and Thymeleaf
- Spring Security and OAuth2 / OpenID Connect
- Spring Data JPA / Hibernate
- MySQL 8.4
- Microsoft Graph API
- Docker Compose
- Maven, JUnit 5 and Mockito

## Features

- Browsing buildings and conference rooms
- Creating room reservation requests
- Microsoft Entra ID authentication
- Outlook calendar integration
- Reservation status tracking
- Basic reservation ownership verification
- Input validation and CSRF protection

## Configuration

Copy `.env.example` to `.env` and configure database credentials and Microsoft Entra application settings.

Never commit `.env`, application secrets or access tokens.

## Database

Start the MySQL container:

```bash
docker compose up -d mysql
```

The database is exposed on localhost port 3310.

## Running the application

The application requires Java 17, MySQL and a configured Microsoft Entra application.

Run with Maven after exporting the environment variables from `.env`:

```bash
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd`.

## Standalone demo mode

The application supports a standalone demo mode without Microsoft Entra authentication or Microsoft Graph connectivity.

Demo mode features:
- Local authentication with `demo1` and `demo2`
- Conference room selection
- Reservation creation and cancellation
- Ownership verification
- Transactional conflict detection using MySQL

Required environment variables:
- `BOOKING_MODE=demo`
- `DEMO_PASSWORD` - local password for both demo accounts

Demo mode requires a dedicated MySQL database with the application schema and room data. Do not use a production database.

For existing databases, review and apply `db/manual/20261023_booking_integration.sql` before running the updated application.

## Booking reliability

Booking integration states: `CREATING`, `LINKED`, `UNCERTAIN`, `DEMO`, `FAILED`.

The application uses database transactions and pessimistic locking to prevent overlapping reservations. Uncertain Microsoft Graph operations are retained for recovery and reconciliation.

## Integration testing

Unit and application-context tests run through Maven Surefire. A separate MySQL concurrency integration test runs through Maven Failsafe.

Use isolated test databases. Integration tests must never run against a database containing valuable reservations.
## Tests

```bash
./mvnw test
```

The current test suite includes unit and Spring MVC integration tests.

## Project status

This project is being refactored from an academic application into a portfolio project.

The standalone demo mode has been implemented and verified locally. The live Microsoft Graph / Exchange Online integration has not yet been fully reverified.