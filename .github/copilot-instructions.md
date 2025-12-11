# GitHub Copilot Instructions for Recipe Management AI Service

## Project Overview

This is a Spring Boot microservice that provides AI-powered recipe generation using Google's Gemini AI models. The service integrates with Firebase Authentication and is deployed on Google Cloud Run.

**Key Technologies:**
- **Framework**: Spring Boot 3.5.8
- **Java Version**: 21
- **Build Tool**: Maven 3.8+
- **AI Provider**: Google Gemini API (gemini-2.5-flash)
- **Authentication**: Firebase Admin SDK
- **Observability**: Honeycomb with OpenTelemetry
- **Deployment**: Google Cloud Run

## Repository Structure

```
src/
├── main/
│   ├── java/com/recipe/ai/
│   │   ├── config/          # Spring configuration classes
│   │   ├── controller/      # REST API controllers
│   │   ├── dto/            # Data transfer objects (Gemini API)
│   │   ├── model/          # Request/response models
│   │   ├── security/       # Authentication filters and config
│   │   ├── service/        # Business logic (RecipeService)
│   │   └── util/           # Utility classes
│   └── resources/
│       ├── application.properties
│       ├── application-local.properties
│       └── application-test.properties
└── test/
    ├── java/com/recipe/ai/
    │   ├── controller/     # Controller tests
    │   ├── security/       # Security tests
    │   └── service/        # Service tests (including integration)
    └── resources/
        └── fixtures/       # Test data fixtures
```

## Build and Test Commands

### Building the Project
```bash
# Clean build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build and run
mvn spring-boot:run
```

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RecipeServiceTest

# Run tests with coverage
mvn clean test jacoco:report
```

### Code Quality Checks
```bash
# Run Checkstyle (coding standards)
mvn checkstyle:check

# Run SpotBugs (static analysis)
mvn spotbugs:check

# Note: SpotBugs is automatically skipped on Java 18+ in the default profile
```

### Local Development
```bash
# Run with local profile (authentication disabled)
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run

# Run with authentication enabled
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
export GEMINI_API_KEY=your_api_key
mvn spring-boot:run

# Run with observability (Honeycomb)
export HONEYCOMB_API_KEY=your_honeycomb_key
./start-with-observability.sh
```

## Code Style and Conventions

### General Java Conventions
- Use **camelCase** for variables and methods
- Use **PascalCase** for classes
- Use **UPPER_SNAKE_CASE** for constants
- All classes should have proper Javadoc comments for public APIs
- Follow standard Spring Boot conventions and patterns

### Package Structure
- `controller/` - REST endpoints with `@RestController`, minimal business logic
- `service/` - Business logic with `@Service` annotation
- `model/` - Request/response DTOs for API endpoints
- `dto/` - Internal DTOs for external API communication (e.g., Gemini)
- `config/` - Spring configuration classes with `@Configuration`
- `security/` - Authentication and authorization logic

### Testing Conventions
- Test classes follow the naming pattern: `<ClassName>Test.java`
- Integration tests use `@SpringBootTest` with `WebEnvironment.RANDOM_PORT`
- Use MockWebServer for mocking external HTTP calls (Gemini API)
- Disable authentication in tests with `@TestPropertySource(properties = "auth.enabled=false")`
- Test files should be in the same package structure as the code they test

### Error Handling
- Use appropriate HTTP status codes (400, 401, 403, 500, etc.)
- Log errors with context using SLF4J Logger
- Return meaningful error messages in response bodies
- Handle WebClient exceptions for external API calls

### Security Best Practices
- **NEVER commit secrets** (API keys, credentials) to version control
- Use environment variables for sensitive configuration
- Always validate and sanitize user inputs
- Maintain Firebase authentication on production endpoints
- Use defensive copying for mutable objects (e.g., Lists in getters)

## Key Dependencies and Integrations

### Firebase Authentication
- Project ID: `recipe-mgmt-dev`
- Service account credentials via `GOOGLE_APPLICATION_CREDENTIALS`
- Authentication can be disabled via `auth.enabled=false` for local development
- Uses Firebase Admin SDK for token verification

### Gemini API Integration
- API key via `GEMINI_API_KEY` environment variable
- Text generation endpoint: `gemini-2.5-flash:generateContent`
- Image generation endpoint: `gemini-2.5-flash-image:generateContent`
- Image generation can be toggled via `gemini.image.enabled` property
- Uses WebClient for reactive HTTP calls
- Implements JSON schema-based response generation

### Observability (Honeycomb)
- OpenTelemetry Java agent for distributed tracing
- Automatic instrumentation for HTTP requests, database calls, etc.
- Service name: `recipe-ai-service`
- Requires `HONEYCOMB_API_KEY` environment variable
- Exports traces, metrics, and logs via OTLP protocol

## Configuration Management

### Application Properties
- Base config: `application.properties`
- Local development: `application-local.properties` (auth disabled, verbose logging)
- Test environment: `application-test.properties`
- Use `SPRING_PROFILES_ACTIVE` to switch between profiles

### Environment Variables (Production)
- `GOOGLE_APPLICATION_CREDENTIALS` - Firebase service account path
- `GEMINI_API_KEY` - Gemini API authentication
- `HONEYCOMB_API_KEY` - Honeycomb observability
- `SERVICE_VERSION` - Service version for tagging
- `API_KEYS` - Comma-separated API keys for server-to-server auth

## API Design Guidelines

### REST Endpoints
- Base path: `/api/recipes`
- Use proper HTTP methods (POST for generation endpoints)
- Include `Authorization: Bearer <token>` header for authenticated requests
- Content-Type: `application/json`
- Follow RESTful naming conventions

### Request/Response Models
- Use validation annotations (`@NotNull`, `@Size`, etc.)
- Provide sensible defaults (e.g., `METRIC` for units)
- Include defensive copying in getters returning mutable collections
- Use `@JsonIgnoreProperties(ignoreUnknown = true)` for external API DTOs

## Development Workflow

### Feature Development
1. Create feature branch from `main`
2. Use SNAPSHOT versions (e.g., `1.0.1-SNAPSHOT`)
3. Write tests for new functionality
4. Run code quality checks (`mvn checkstyle:check`)
5. Ensure all tests pass (`mvn test`)
6. Create pull request for review

### Version Management
- Semantic versioning: `MAJOR.MINOR.PATCH`
- Main branch uses release versions (e.g., `1.0.1`)
- Feature branches use SNAPSHOT versions
- Version bumping is automated in CI/CD
- Use `./version.sh` for manual version operations

### CI/CD Pipeline
- Automated builds on all branches
- Checkstyle and SpotBugs run on CI
- Tests must pass before merge
- Main branch deploys to Google Cloud Run
- Automatic version tagging on successful deploys

## Common Pitfalls to Avoid

1. **API Key Management**: Never hardcode API keys; always use environment variables
2. **Authentication Bypass**: Don't disable `auth.enabled` in production
3. **Mutable Collections**: Always use defensive copying for collection getters
4. **Error Handling**: Don't swallow exceptions; log with context
5. **Test Isolation**: Use `@DynamicPropertySource` for test-specific configuration
6. **External API Mocking**: Use MockWebServer, not real API calls in tests
7. **Memory Limits**: Be mindful of WebClient buffer sizes for large responses

## Related Repositories

- [recipe-management-frontend](https://github.com/theandiman/recipe-management-frontend) - React frontend application
- [recipe-management-infrastructure](https://github.com/theandiman/recipe-management-infrastructure) - Terraform infrastructure as code
- [recipe-management-shared](https://github.com/theandiman/recipe-management-shared) - Shared models and schemas

## Additional Resources

- [API Documentation](https://theandiman.github.io/recipe-management-ai-service/) - Swagger UI
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Gemini API Documentation](https://ai.google.dev/docs)
- [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
- [Honeycomb Documentation](https://docs.honeycomb.io/)

## Task Suitability

This repository is well-suited for GitHub Copilot coding agent tasks such as:
- ✅ Bug fixes in service logic or controllers
- ✅ Adding new test cases or improving test coverage
- ✅ Refactoring code for better readability or performance
- ✅ Updating dependencies (with proper testing)
- ✅ Documentation improvements
- ✅ Adding new API endpoints following existing patterns
- ✅ Security vulnerability fixes

Tasks that require human oversight:
- ⚠️ Changes to authentication/security logic
- ⚠️ Database schema migrations (if added in future)
- ⚠️ Major architectural changes
- ⚠️ Production configuration changes
- ⚠️ Gemini API prompt engineering (requires domain expertise)
