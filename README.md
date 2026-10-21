# Nook — Full-Stack E-Commerce Application

Nook is a full-stack e-commerce portfolio project built with **React, Java, Spring Boot, and MySQL**. It provides a storefront with product browsing, shopping cart and checkout flows, order management, role-based access control, and simulated payment and refund workflows.

> **Payment notice:** Payments and refunds are simulated using local gateway implementations. The application does not connect to a real payment provider or process real money.

## Features

- **Authentication and authorization:** User registration and login, BCrypt password hashing, JWT-based authentication, and customer/admin role restrictions.
- **Product catalogue:** Product listing and details, name/category/price filtering, sorting, pagination, and product management.
- **Category management:** Category listing and management, including retrieving products associated with a category.
- **Shopping cart and checkout:** Add, update, and remove cart items; checkout validates inventory and creates orders.
- **Inventory consistency:** Transactional checkout, conditional stock updates, and database locking where required.
- **Order management:** Customer order history and details, order-status rules, order cancellation, and inventory restoration.
- **Simulated payments and refunds:** Payment attempts, idempotency-key handling, payment history, refund outcomes, and failure-path handling.
- **Admin workflows:** Product and category management, along with paginated order management.
- **Containerized deployment:** Docker Compose orchestrates MySQL, the Spring Boot API, and the Nginx-served React frontend.

## Technology Stack

| Layer | Technologies |
|---|---|
| Backend | Java 21, Spring Boot, Spring MVC |
| Security | Spring Security, JWT (JJWT), BCrypt |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8.4 |
| Frontend | React 19, JavaScript, Vite, CSS |
| Testing | JUnit 5, Spring MVC testing, Mockito |
| Containerization | Docker, Docker Compose |
| Web server | Nginx |

## Architecture

```text
Browser
   |
   v
React application (served by Nginx)
   |
   | /api/* requests
   | Nginx removes the /api prefix
   v
Spring Boot REST API (port 8080)
   |
   v
Spring Data JPA / Hibernate
   |
   v
MySQL 8.4 (Docker volume)
```

The backend follows a layered architecture consisting of controllers, services, repositories, entities, DTOs, security components, and centralized exception handling.

## Getting Started with Docker Compose

### Prerequisites

- Docker Desktop or Docker Engine
- Docker Compose
- A terminal

### 1. Obtain the project

Clone the GitHub repository or download the source code. Open a terminal in the project root, which contains `docker-compose.yml`.

### 2. Configure environment variables

Create a `.env` file in the project root:

```dotenv
DB_PASSWORD=replace_with_a_strong_local_database_password
JWT_SECRET=replace_with_base64_encoded_secret
```

Generate a JWT secret:

```bash
openssl rand -base64 32
```

Copy the generated value into `JWT_SECRET`. Set `DB_PASSWORD` to your chosen local database password.

Keep `.env` private and do not commit it to Git. The Compose configuration should pass the required variables to the relevant containers.

The application requires a non-empty `JWT_SECRET` to start.

### 3. Build and start the application

Run these commands from the project root:

```bash
docker compose up --build -d
```

Check the service status:

```bash
docker compose ps
```

Inspect the backend logs:

```bash
docker compose logs -f backend
```

The initial build may take several minutes while Docker downloads base images and dependencies. The backend should start after the MySQL health check succeeds.

### 4. Open the application

- **Frontend:** [http://localhost](http://localhost)
- **Backend:** [http://localhost:8080](http://localhost:8080)

The frontend's Nginx configuration proxies requests beginning with `/api/` to the backend and removes the `/api` prefix. Browser-facing API requests therefore use `/api`, while Spring controllers expose paths such as `/auth`, `/products`, `/cart`, and `/orders`.

### 5. Stop the application

Stop the containers while retaining the database volume:

```bash
docker compose down
```

To remove the containers and the named `mysql_data` volume, including its database data:

```bash
docker compose down -v
```

**Warning:** The second command permanently deletes the data stored in that Docker volume. Back up any data you need before running it.

## Common Setup Issues

- **Port 80 is already in use:** Stop the conflicting service or change the frontend port mapping in `docker-compose.yml`.
- **Port 8080 is already in use:** Stop the conflicting service or change the backend port mapping.
- **Backend cannot connect to MySQL:** Inspect the MySQL and backend logs, and verify that the database credentials match.
- **JWT secret configuration error:** Verify that `.env` contains a non-empty `JWT_SECRET`, then recreate the backend container after changing the configuration.
- **Database schema:** The current configuration uses Hibernate `ddl-auto=update`. This is convenient for local development but does not replace a versioned database migration strategy.

## Development Setup Without Docker

This setup is intended for local development.

### Prerequisites

- Java 21
- Node.js 22 and npm
- MySQL 8.4

### 1. Configure the database

Create a local database named `ecommerce`.

### 2. Configure environment variables

Export the variables in the terminal session used to start the backend:

```bash
export DB_URL="jdbc:mysql://localhost:3306/ecommerce"
export DB_USERNAME="your_local_mysql_username"
export DB_PASSWORD="your_local_mysql_password"
export JWT_SECRET="$(openssl rand -base64 32)"
```

Replace the database username and password with your local MySQL credentials.

The generated JWT secret is temporary for that shell session. If you generate a new secret, previously issued JWTs signed with the old secret will no longer validate. For persistent local development, use a securely stored, stable development secret.

### 3. Start the backend

From the project root:

```bash
./mvnw spring-boot:run
```

### 4. Start the frontend

Open a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open the Vite development URL shown in the terminal, normally [http://localhost:5173](http://localhost:5173).

The Vite configuration proxies `/api` requests to `http://localhost:8080`.

## API Overview

The following table summarizes the main API route groups.

| Area | Route group | Example endpoints |
|---|---|---|
| Authentication | `/auth` | `POST /auth/register`, `POST /auth/login` |
| Products | `/products` | `GET /products`, `GET /products/{id}`, `POST /products`, `PATCH /products/{id}`, `DELETE /products/{id}` |
| Categories | `/categories` | `GET /categories`, `GET /categories/{id}`, `GET /categories/{id}/products` |
| Cart | `/cart` | `GET /cart`, `POST /cart/items`, `PUT /cart/items/{productId}`, `DELETE /cart/items/{productId}` |
| Orders | `/orders` | `POST /orders/checkout`, `GET /orders`, `GET /orders/{orderId}`, `POST /orders/{orderId}/cancel` |
| Payments | `/payments` | `POST /payments/orders/{orderId}`, `GET /payments/orders/{orderId}` |
| Admin orders | `/admin/orders` | `GET /admin/orders` with pagination and sorting |

Protected endpoints require valid authentication and the appropriate permissions under the application's Spring Security configuration.

For payment requests, include an `Idempotency-Key` header. Reusing a key for the same order returns the stored result instead of creating another payment attempt. A failed payment retry should use a new key.

These behaviors apply to the application's simulated payment gateway, not a real payment processor.

## Testing

### Backend tests

Run backend tests from the project root:

```bash
./mvnw test
```

The test suite includes coverage for security and ownership rules, product search and pagination, payment attempts and retries, and payment/refund failure paths.

Some integration tests may require a configured test database, particular records, or test fixtures. Inspect the test setup before running them against a database. Always use a dedicated test database and never point integration tests at a production database or valuable personal data.

### Frontend checks

Run the frontend checks from the `frontend` directory:

```bash
npm ci
npm run lint
npm run build
```

- `npm ci` installs dependencies from the lockfile.
- `npm run lint` checks the source code for linting issues.
- `npm run build` creates the production frontend bundle.

## Security and Configuration

- Never commit `.env`, database passwords, JWT secrets, or other credentials.
- Public registration assigns the customer role. Admin account provisioning is a manual database operation; do not store admin credentials in source control.
- JWTs expire after one hour in the documented implementation.
- Hibernate schema auto-update is enabled for local development. A production deployment should use a reviewed database migration strategy.
- Production deployments also require appropriate secret management and additional operational and security hardening.
- This repository documents local Docker Compose deployment and does not claim that the application is publicly hosted.

## Project Structure

```text
.
├── src/
│   ├── main/java/com/Sambhav/ecommerce/
│   │   ├── config/          # Spring Security configuration
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Request and response DTOs
│   │   ├── exception/       # Exceptions and API error handling
│   │   ├── filter/          # JWT authentication filter
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Spring Data repositories
│   │   ├── service/         # Business logic and simulated gateways
│   │   └── enums/           # Order, payment, and refund states
│   └── test/java/           # Backend tests
├── frontend/
│   ├── src/                 # React application
│   ├── Dockerfile
│   └── nginx.conf
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Current Limitations

- Payment and refund processing is simulated; there is no real payment-provider integration.
- The application uses Hibernate schema auto-update rather than versioned database migrations.
- Deployment instructions cover local Docker Compose use; a public cloud deployment is not claimed.
