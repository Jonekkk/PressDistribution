# Press Distribution

## Configuration Status

- ✅ **MySQL for Flyway** - `flyway-mysql` dependency in pom.xml
- ✅ **Spring Boot Configuration** - `application.yaml` uses environment variables
- ✅ **Test Profile** - H2 in-memory database for unit tests
- ✅ **Docker Compose** - MySQL 8.4 + Spring Boot application
- ✅ **.env** - Configuration for Docker and local environments

## Quick Start

### Using Docker Compose

```bash
docker compose up --build
```

Application: http://localhost:8080  
MySQL: localhost:3306

### Local Development (without Docker)

The `.env` file is loaded via `spring.config.import`, so simply run:

```bash
./mvnw spring-boot:run
```

Or override environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/press_distribution
export SPRING_DATASOURCE_USERNAME=press_user
export SPRING_DATASOURCE_PASSWORD=press_password
./mvnw spring-boot:run
```

### Local Requirements

- MySQL 8.4 or higher running locally
- Database `press_distribution` and user `press_user`

### Setup Local MySQL

```bash
# macOS with Homebrew
brew services start mysql

# Or Linux/other systems
mysqld --user=mysql &
```

Initialize database:

```sql
CREATE DATABASE press_distribution;
CREATE USER 'press_user'@'localhost' IDENTIFIED BY 'press_password';
GRANT ALL PRIVILEGES ON press_distribution.* TO 'press_user'@'localhost';
FLUSH PRIVILEGES;
```

## Testing

```bash
./mvnw test
```

Tests use H2 in-memory database and don't require MySQL.



