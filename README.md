# 🗄️ Tiny Vault (Secure Storage API)

A lightweight, secure, and self-hosted personal storage API built with Spring Boot 3 and PostgreSQL. It acts as your own private "S3 bucket," designed specifically for integrating with your Next.js, Laravel, or any other web applications.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-success.svg)]()
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)]()

## ✨ Features

- **Public & Private Files**: Upload files privately (accessible only via API key) or publicly (generates a `publicUrl` to embed directly in HTML `<img>` or `<video>` tags).
- **Hardcore Security**: 
  - Token-bucket rate limiting (Bucket4j) to prevent abuse.
  - "Magic bytes" MIME-type detection (prevents malicious `.php` disguised as `.jpg`).
  - Strict security headers (CSP, X-Frame-Options, No-Sniff).
- **Stateless Authentication**: Fast and secure `X-API-Key` and `X-API-Secret` header authentication using BCrypt hashing.
- **Docker Ready**: Easy multi-stage Docker build for seamless deployment to any VPS.
- **Swagger Documentation**: Interactive OpenAPI documentation built-in.

## 🚀 Quick Start (Docker)

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/tiny-vault.git
   cd tiny-vault
   ```

2. Set up your environment variables in `.env` or directly in `docker-compose.yml`:
   ```env
   DB_URL=jdbc:postgresql://your-neon-or-local-db:5432/db
   DB_USERNAME=postgres
   DB_PASSWORD=secret
   APP_BASE_URL=http://localhost:8080
   CORS_ORIGINS=*
   ```

3. Spin it up:
   ```bash
   docker compose up -d --build
   ```

## 📚 API Documentation

Once the app is running, visit the interactive Swagger UI to explore and test the endpoints:
**`http://localhost:8080/swagger-ui/index.html`**

### Core Endpoints:
- `POST /api/v1/upload` - Upload a file (use `?public=true` to get an embeddable link).
- `GET /api/v1/files` - List your uploaded files.
- `GET /api/v1/download/{filename}` - Download a private file securely.
- `GET /api/v1/public/{filename}` - Serve a public file (No authentication required!).
- `PATCH /api/v1/files/{filename}/visibility` - Toggle a file between public/private.

## 💻 Integration Examples

Check the `examples/` directory for code snippets on how to use Tiny Vault in your projects!
- [Next.js / TypeScript Example](examples/nextjs-integration.ts)
- [Laravel / PHP Example](examples/laravel-integration.php)

## 🛡️ License

This project is open-sourced under the [MIT License](LICENSE).
