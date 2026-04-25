# storage-service

Personal self-hosted storage API service built with Java 21 + Spring Boot 3.x.

## Quick Start (Dev)

```bash
mvn spring-boot:run
```

App starts on http://localhost:8080

Health check: `curl http://localhost:8080/health`

## Phases
- Phase 1: Project setup & database layer
- Phase 2: File upload/download
- Phase 3: File management & error handling
- Phase 4: Security hardening & rate limiting
- Phase 5: Docker deployment & docs
