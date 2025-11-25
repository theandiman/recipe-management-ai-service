# GitHub Actions Setup Guide

This guide explains how to set up GitHub Actions for the Recipe Management AI Service CI/CD pipeline.

## Prerequisites

1. **GitHub Repository**: Ensure this repository has GitHub Actions enabled
2. **Google Cloud Project**: You need a GCP project with the following APIs enabled:
   - Cloud Build API
   - Cloud Run API
   - Artifact Registry API
   - Cloud Resource Manager API

## Required Secrets

Set these secrets in your GitHub repository (Settings → Secrets and variables → Actions):

### GCP_PROJECT_ID
- **Value**: Your Google Cloud Project ID (e.g., `recipe-mgmt-dev`)
- **Purpose**: Identifies the GCP project for deployments

### GCP_SA_KEY
- **Value**: JSON content of a GCP Service Account key
- **Purpose**: Authenticates GitHub Actions with Google Cloud
- **How to create**:
  1. Go to GCP Console → IAM & Admin → Service Accounts
  2. Create a new service account (e.g., `github-actions-deployer`)
  3. Grant these roles:
     - `Cloud Run Admin`
     - `Artifact Registry Writer`
     - `Storage Admin` (for gsutil operations)
     - `Cloud Build Service Account` (if needed)
  4. Create a JSON key for the service account
  5. Copy the entire JSON content as the secret value

## Service Account Permissions

The service account needs these minimum permissions:

```yaml
roles:
  - roles/run.admin
  - roles/artifactregistry.writer
  - roles/storage.admin
  - roles/cloudbuild.builds.editor
```

## Workflow Features

The GitHub Actions workflow (`.github/workflows/ci-cd.yml`) provides:

### ✅ Concurrency Control
- Only one build runs on main branch at a time
- Prevents race conditions and deployment conflicts

### ✅ Automated Versioning
- Feature branches: SNAPSHOT versions
- Main branch: Release versions with automatic increment

### ✅ Comprehensive Testing
- Unit tests
- Integration tests
- Linting (Checkstyle, PMD)
- Security scanning

### ✅ Docker & Deployment
- Multi-stage Docker builds
- Artifact Registry publishing
- Cloud Run deployments

### ✅ Observability
- SonarQube code quality
- Snyk security scanning
- Semgrep security analysis

## Branch Protection Rules

Configure these branch protection rules for the `main` branch:

1. **Require pull request reviews**
2. **Require status checks to pass**:
   - `build-and-deploy (sonarcloud)`
   - `build-and-deploy (security/snyk)`
   - `build-and-deploy (semgrep-cloud-platform)`
3. **Require branches to be up to date**
4. **Include administrators**

## Migration from Cloud Build

If migrating from Cloud Build:

1. **Disable Cloud Build triggers** for this repository
2. **Set up GitHub Actions secrets** as described above
3. **Update any documentation** referencing Cloud Build
4. **Test the workflow** with a feature branch PR first

## Troubleshooting

### Build Fails with Authentication Error
- Verify `GCP_SA_KEY` secret contains valid JSON
- Check service account has required permissions
- Ensure GCP project ID is correct

### Docker Push Fails
- Verify Artifact Registry API is enabled
- Check service account has `artifactregistry.writer` role
- Ensure repository exists in Artifact Registry

### Deployment Fails
- Verify Cloud Run API is enabled
- Check service account has `run.admin` role
- Ensure Cloud Run service exists

### Version Bumping Issues
- Check that `version.sh` script is executable
- Verify git permissions for pushing version changes
- Ensure no concurrent builds are interfering

## Monitoring

Monitor your GitHub Actions:
- **Actions Tab**: View workflow runs and logs
- **Settings → Branches**: Configure branch protection
- **Settings → Secrets and variables**: Manage secrets

## Cost Considerations

GitHub Actions provides:
- 2,000 minutes/month for free accounts
- Additional minutes at $0.008/minute
- No additional GCP costs for the actions themselves

GCP costs will include:
- Cloud Run instance hours
- Artifact Registry storage
- Network egress for deployments