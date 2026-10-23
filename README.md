# Conference Room Reservation System

A Java backend application for managing conference room reservations, built with Spring Boot, MySQL and Microsoft Graph API integration.

The project provides two operating modes:

- **Standalone Demo** — local authentication and database-backed room reservations without external Microsoft services.
- **Microsoft Graph** — Microsoft Entra ID authentication and Outlook calendar integration.

Originally developed as an academic project, the application has been refactored with a focus on backend reliability, transaction management, security, automated testing and reproducible development environments.

## Technology Stack

| Area | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.4.3 |
| Web | Spring MVC, Thymeleaf, JavaScript, CSS |
| Security | Spring Security, OAuth2 / OpenID Connect |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8.4 |
| External integration | Microsoft Graph API |
| Build | Maven, Maven Wrapper |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Integration testing | Maven Failsafe, MySQL |
| Infrastructure | Docker, Docker Compose |
| CI | GitHub Actions |

## Features

### Room Management

- Browse available conference rooms.
- Select a room and view its reservations.
- Create reservations for selected date and time ranges.
- Cancel reservations subject to ownership verification.

### Reservation Reliability

- Database transactions for reservation operations.
- Pessimistic locking to coordinate concurrent requests.
- Conflict detection for overlapping reservations.
- Reservation status and external integration state tracking.
- Persistence of uncertain Microsoft Graph operations for subsequent reconciliation.
- Isolation of standalone demo reservations from historical Graph reservations.

### Security

- Local accounts for standalone demo mode.
- Microsoft Entra ID authentication in Graph mode.
- Reservation ownership checks.
- CSRF protection for state-changing requests.
- Input validation.

## Architecture

The application follows a layered Spring Boot architecture:

- **Controllers** handle HTTP requests and views.
- **Services** implement reservation and integration logic.
- **Repositories** provide persistence through Spring Data JPA.
- **Entities** model users, rooms, buildings and reservations.
- **Security configuration** selects authentication behavior according to the operating mode.
- **Graph integration services** communicate with Microsoft identity and calendar APIs.

The demo and Graph reservation paths share the underlying reservation domain while using different integration behavior.

## Requirements

For the Docker-based setup:

- Docker Desktop with Docker Compose
- Git

For running the application outside Docker:

- Java 17
- MySQL 8.4 or a compatible version

The repository includes Maven Wrapper scripts, so a separate Maven installation is optional.

## Quick Start — Standalone Demo

Standalone demo mode does not require a working Microsoft Entra tenant or access to Outlook calendars.

**Use a fresh Docker environment with no valuable data.** Docker Compose creates a persistent MySQL volume. Never reuse a volume containing real reservations for a disposable demo.

### 1. Clone the repository

```bash
git clone https://github.com/jbialek99/conference-room-reservation-system.git
cd conference-room-reservation-system
```

Use the branch containing the standalone demo changes until they have been merged into the default branch.

### 2. Prepare environment variables

Copy the environment template.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux or macOS:

```bash
cp .env.example .env
```

Edit `.env` and configure:

```dotenv
DB_USER=conference_user
DB_PASSWORD=replace_with_a_local_database_password
DB_ROOT_PASSWORD=replace_with_a_different_root_password

BOOKING_MODE=demo
DEMO_PASSWORD=replace_with_a_strong_local_demo_password

AZURE_CLIENT_ID=00000000-0000-0000-0000-000000000001
AZURE_CLIENT_SECRET=demo_placeholder
AZURE_TENANT_ID=00000000-0000-0000-0000-000000000002
AZURE_REDIRECT_URI=http://localhost:8081/login/oauth2/code/azure
```

Use your own local passwords. The Microsoft identity values above are placeholders for demo mode, not working Microsoft credentials.

Do not commit `.env`.

### 3. Start Docker Compose

On a fresh checkout with ports `8081` and `3310` available:

```bash
docker compose up -d --build
```

The application is available at:

```text
http://localhost:8081/login
```

MySQL is exposed only on the local interface at port `3310`.

The default application configuration uses Hibernate schema updates. This is convenient for a fresh local demo, but should not replace versioned database migrations in a production deployment.

### 4. Load sample conference rooms

On the first startup, let the application initialize its database schema.

Then execute the SQL seed file against the running MySQL service.

Windows PowerShell:

```powershell
Get-Content db/demo-data.sql -Raw |
    docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
```

The seed script creates sample location and building data together with three rooms:

| Room | Capacity |
| --- | --- |
| Sala Alfa | 8 |
| Sala Beta | 16 |
| Sala Gamma | 4 |

The seed script checks whether its sample records already exist before inserting them.

### 5. Sign in

Open:

```text
http://localhost:8081/login
```

Available demo usernames:

- `demo1`
- `demo2`

Both accounts use the password configured in `DEMO_PASSWORD`.

Use the two accounts to verify that users can create reservations and that cancellation is restricted to the reservation owner.

### 6. Stop the application

```bash
docker compose down
```

This preserves the database volume.

Do not use `docker compose down -v` unless you intentionally want to remove the data stored in the Compose-managed volume.

## Microsoft Graph Mode

The Microsoft Graph integration is designed to use Microsoft Entra ID authentication and Outlook calendar APIs.

Configuration requires a valid Microsoft Entra application registration, supported permissions and a working tenant.

Set:

```dotenv
BOOKING_MODE=graph
```

Configure the appropriate Microsoft identity variables in `.env`.

Depending on the operation, the application uses delegated authorization or application credentials to interact with Microsoft Graph.

**Integration status:** the Graph integration exists in the application, but it has not yet been fully reverified against a current Microsoft Entra / Exchange Online environment. The standalone demo can be tested independently.

## Reservation Integration States

The application distinguishes the following states:

| State | Meaning |
| --- | --- |
| `CREATING` | External reservation creation is in progress |
| `LINKED` | Reservation is linked to an external calendar event |
| `UNCERTAIN` | External creation outcome requires reconciliation |
| `DEMO` | Reservation was created locally in standalone demo mode |
| `FAILED` | External creation failed |

For existing databases, review the migration script before deployment:

```text
db/manual/20261023_booking_integration.sql
```

Do not apply migrations blindly to a database containing valuable reservations. Back up data before changing the schema.

## Tests

### Unit and Spring Tests

Windows:

```powershell
.\mvnw.cmd test
```

Linux or macOS:

```bash
./mvnw test
```

Tests that require MySQL must be provided with their dedicated test database configuration.

The repository also contains a real MySQL concurrency integration test that verifies two concurrent attempts to reserve the same room and time interval cannot both succeed.

### Integration Tests

The integration test uses Maven Failsafe and requires an isolated database with the expected schema and sample records.

It is configured to reject unexpected database names as an additional safeguard.

The automated CI workflow prepares the test database and runs:

```bash
mvn -B failsafe:integration-test failsafe:verify
```

Never point destructive tests or schema-generation settings at production or other valuable databases.

## Continuous Integration

Two GitHub Actions workflows provide automated verification:

| Workflow | Responsibility |
| --- | --- |
| `Java Tests` | Unit tests and Spring application tests |
| `MySQL Integration Tests` | Concurrency test against a real MySQL 8.4 instance |

The workflows run for pushes to configured branches and for pull requests.

View workflow results in the repository's [GitHub Actions](https://github.com/jbialek99/conference-room-reservation-system/actions) tab.

## Project Structure

```text
src/
  main/
    java/
    resources/
      templates/
      static/
  test/
    java/
    resources/
db/
  demo-data.sql
  manual/
.github/
  workflows/
compose.yaml
Dockerfile
pom.xml
```

## Security Notes

- Keep environment files, passwords, client secrets and tokens out of version control.
- Use separate databases for development, integration tests and real deployments.
- Demo accounts are intended for local development, not public production access.
- The local demo has its own authentication mode and should not be exposed publicly without further security review.
- Validate database migrations and external API permissions before deploying Graph mode.

## Project Status

**Standalone demo:** implemented and tested locally.

**Automated testing:** GitHub Actions workflows for application tests and MySQL concurrency testing.

**Microsoft Graph integration:** implemented, but live connectivity and current permissions require revalidation.

This project is maintained as a Java backend portfolio project focused on reliable reservation workflows, database transactions and external API integration.