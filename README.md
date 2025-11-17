[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=theandiman_recipe-management-ai-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=theandiman_recipe-management-ai-service) [![Known Vulnerabilities](https://snyk.io/test/github/theandiman/recipe-management-ai-service/badge.svg)](https://snyk.io/test/github/theandiman/recipe-management-ai-service)

# Recipe Management AI Service

AI-powered recipe generation service using Spring Boot, Vertex AI/Gemini, and Firebase Auth.

## Overview

This microservice provides REST API endpoints for generating recipes and recipe images using Google's Gemini AI models. It integrates with Firebase Authentication for secure access control.

## Features

- **AI Recipe Generation**: Generate unique recipes based on user prompts, pantry items, and dietary preferences
- **AI Image Generation**: Automatically generate food images for recipes
- **Firebase Authentication**: Secure endpoints with Firebase ID token verification
- **CORS Support**: Configured for local development and Firebase hosting
- **Health Checks**: Actuator endpoints for Cloud Run health monitoring
- **Nutritional Information**: Accurate nutritional data based on USDA standards

## Architecture

- **Framework**: Spring Boot 3.4.10
- **Java Version**: 17
- **Build Tool**: Maven
- **AI Provider**: Google Gemini API (gemini-2.5-flash)
- **Authentication**: Firebase Admin SDK
- **Deployment**: Google Cloud Run

## Prerequisites

- Java 17+
- Maven 3.8+
- Firebase project with service account key
- Gemini API key

## Configuration

### Firebase Setup

1. Download service account key from Firebase Console (recipe-mgmt-dev project)
2. Set environment variable:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
   ```

### Gemini API Key

Set via environment variable:
```bash
export GEMINI_API_KEY=your_api_key_here
```

### Application Properties

Key configuration in `src/main/resources/application.properties`:

- `firebase.project.id=recipe-mgmt-dev` - Firebase project ID
- `auth.enabled=true` - Enable/disable authentication
- `gemini.api.key` - Gemini API key (set via env var)
- `gemini.image.enabled=true` - Enable image generation

## API Endpoints

### Generate Recipe

```bash
POST /api/recipes/generate
Authorization: Bearer <firebase_id_token>
Content-Type: application/json

{
  "prompt": "Quick healthy dinner",
  "pantryItems": ["chicken", "broccoli", "rice"],
  "dietaryPreferences": ["gluten-free"]
}
```

### Generate Image

```bash
POST /api/recipes/image/generate
Authorization: Bearer <firebase_id_token>
Content-Type: application/json

{
  "recipe": { ... }
}
```

## Local Development

1. Install dependencies:
   ```bash
   mvn clean install
   ```

2. Run with local profile (auth disabled):
   ```bash
   export SPRING_PROFILES_ACTIVE=local
   mvn spring-boot:run
   ```

3. Or run with authentication:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
   export GEMINI_API_KEY=your_api_key
   mvn spring-boot:run
   ```

4. Server runs on `http://localhost:8080`

## CORS Configuration

Allowed origins:
- `http://localhost:5173` (local frontend)
- `https://recipe-mgmt-dev.web.app` (deployed frontend)
- `https://recipe-mgmt-dev.firebaseapp.com`

## Deployment

This service is deployed to Google Cloud Run via Terraform in the [recipe-management-infrastructure](https://github.com/theandiman/recipe-management-infrastructure) repository.

## Environment Variables

Production environment variables:
- `GOOGLE_APPLICATION_CREDENTIALS` - Path to service account key
- `GEMINI_API_KEY` - Google Gemini API key
- `SPRING_PROFILES_ACTIVE` - Profile (production, local)
- `firebase.project.id` - Firebase project ID (recipe-mgmt-dev)
- `auth.enabled` - Enable authentication (true)

## Health Checks

- **Endpoint**: `/actuator/health`
- **Usage**: Used by Cloud Run for health monitoring

## Related Repositories

- [recipe-management-frontend](https://github.com/theandiman/recipe-management-frontend) - React frontend
- [recipe-management-infrastructure](https://github.com/theandiman/recipe-management-infrastructure) - Terraform infrastructure

## License

MIT
