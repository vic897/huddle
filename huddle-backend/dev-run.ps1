# dev-run.ps1 — Local development launcher for Huddle Backend
# Sets required environment variables then starts the Spring Boot app.
#
# Usage:
#   cd huddle-backend
#   .\dev-run.ps1
#
# This file is committed as documentation. It does NOT contain secrets;
# fill in your actual values in a .env.local file or set them here locally.
# DO NOT commit real secrets into this file.

# ── Google OAuth ──────────────────────────────────────────────────────────────
$env:GOOGLE_CLIENT_ID = "73151215086-a5k8cdrq1qipb0duqbra4jdfurpjaepk.apps.googleusercontent.com"

# ── Database (defaults match application.yml; override here if different) ─────
# $env:DB_USERNAME = "postgres"
# $env:DB_PASSWORD = "123456789"

# ── JWT Secret (use application.yml default for local dev only) ───────────────
# $env:JWT_SECRET = "change-this-to-a-real-secret-before-any-deploy"

# ── Allowed institution domains ───────────────────────────────────────────────
# $env:ALLOWED_EMAIL_DOMAINS = "cityengineeringcollege.ac.in"

Write-Host "Starting Huddle backend with local dev environment..." -ForegroundColor Cyan
./mvnw spring-boot:run
