# Nexus Quiz Portal

A full-stack school quiz and performance-tracking web application for
**Nexus Ranaji English Medium School**.

---

## Prerequisites

| Tool | Minimum version |
|------|-----------------|
| JDK  | 21 |
| Maven | 3.9+ (or use the included `mvnw` wrapper) |
| Node.js | 18+ |
| MySQL | 8.0 (local installation) |

---

## Local Database Setup

This project uses a **local MySQL 8** installation (not Docker).

1. Make sure MySQL is running on `localhost:3306`.
2. Create the database (you only need to do this once):

```sql
CREATE DATABASE nexus_quiz CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. Create a dedicated MySQL user (optional but recommended):

```sql
CREATE USER 'nexus_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON nexus_quiz.* TO 'nexus_user'@'localhost';
FLUSH PRIVILEGES;
```

4. Copy `.env.example` to `.env` inside the `backend/` folder and fill in your credentials:

```
DB_URL=jdbc:mysql://localhost:3306/nexus_quiz?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=nexus_user
DB_PASSWORD=your_password
```

Spring Boot loads `backend/.env` as a properties file when the backend is launched
from the `backend/` folder. Keep the file in `KEY=value` format; it is excluded
from Git.

Flyway will automatically apply all migrations (`V1__init_schema.sql`,
`V2__seed_badges.sql`, …) on the first startup.

---

## Running the Backend

```powershell
cd backend

# Copy and fill environment variables
copy .env.example .env

# Run (the .env file is loaded from this backend folder)
# PowerShell needs the -D flag quoted as a single token when setting the profile
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The API will be available at **http://localhost:8080**.

### First login

| Field    | Value                              |
|----------|------------------------------------|
| Username | value of `ADMIN_DEFAULT_USERNAME` in your `.env` (default: `admin`) |
| Password | value of `ADMIN_DEFAULT_PASSWORD` in your `.env` (default: `Admin@1234`) |

> You will be forced to change the password on first login.

---

## Running the Frontend

```bash
cd frontend

# Install dependencies
npm install

# Copy and fill environment variables
copy .env.example .env.local   # Windows
# cp .env.example .env.local   # Mac/Linux

# Start dev server
npm run dev
```

The SPA will be available at **http://localhost:5173**.

### Admin setup order

Create admin records in dependency order: **academic year → class → subject → faculty user → student user (with a class) → faculty assignment → parent user → parent link**. Users are created from **Admin → Users**; classes and academic years are managed under **Admin → Classes**.

---

## Swagger UI

Once the backend is running, open:

```
http://localhost:8080/swagger-ui/index.html
```

Authenticate with the admin account to explore all endpoints.

---

## Postman Collection

1. Open Postman → **Import** → choose the file `postman/Nexus_Quiz_Portal.postman_collection.json`.
2. Set the `base_url` collection variable to `http://localhost:8080/api/v1`.
3. Run the **Auth / Login** request first to populate the `access_token` variable.

---

## Building for Production

```bash
# Backend fat JAR
cd backend
mvn clean package -DskipTests

# Run the JAR
java -Dspring.profiles.active=prod \
     -Duser.timezone=UTC \
     -jar target/nexus-quiz-portal-0.0.1-SNAPSHOT.jar

# Frontend static bundle
cd frontend
npm run build
# Output in frontend/dist/
```

---

## Daily Database Backup

```bash
# Dump (replace credentials as needed)
mysqldump -u nexus_user -p nexus_quiz > backup_$(date +%Y%m%d).sql

# Restore
mysql -u nexus_user -p nexus_quiz < backup_20260101.sql
```

Schedule this command with Task Scheduler (Windows) or cron (Linux/macOS).

---

## Project Structure

```
nexus-quiz-portal/
├── README.md
├── .env.example            ← copy to backend/.env (never commit the real one)
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/nexus/quiz/
│       │   ├── config/         Spring Security, CORS, OpenAPI, schedulers
│       │   ├── controller/     REST controllers
│       │   ├── dto/            Request / response DTOs
│       │   ├── entity/         JPA entities
│       │   ├── exception/      Custom exceptions + global handler
│       │   ├── repository/     Spring Data JPA repos
│       │   ├── scheduler/      Background jobs
│       │   ├── security/       JWT filter, token service, user details
│       │   ├── service/        Business logic
│       │   └── util/           DateTimeUtil, PasswordUtil, CsvUtil, PdfUtil
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/migration/   Flyway SQL scripts
└── frontend/
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    └── src/
        ├── api/
        ├── assets/badges/      SVG badge icons
        ├── components/
        ├── context/
        ├── hooks/
        ├── layouts/
        ├── pages/
        ├── routes/
        ├── theme.js
        └── utils/
```

---

## Versions Used

| Technology | Version |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.3.4 |
| Flyway | managed by Spring Boot 3.3.4 |
| MySQL Connector/J | managed by Spring Boot |
| React | 18.3.1 |
| Vite | 5.x (latest) |
| Tailwind CSS | 3.4.10 |
| React Router | 6.26.1 |
| Axios | 1.7.4 |
