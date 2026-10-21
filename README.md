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

## Tests

```bash
./mvnw test
```

The current test suite includes unit and Spring MVC integration tests.

## Project status

This project is being refactored from an academic application into a portfolio project.

The live Microsoft Graph / Exchange Online integration has not yet been fully reverified. A standalone demo mode is planned.