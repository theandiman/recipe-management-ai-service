# Deployment Guide

This document outlines the steps to deploy the Recipe Management AI Service to Google Cloud Run.

## Prerequisites Completed ✓

- [x] Backend repository created: `recipe-management-ai-service`
- [x] Code copied from recipe-generator-stack-a
- [x] Firebase config updated to `recipe-mgmt-dev`
- [x] CORS updated for new frontend URLs
- [x] Dockerfile ready for Cloud Run
- [x] README documentation complete

## Firebase Service Account Setup

### Download Service Account Key

1. Go to [Firebase Console](https://console.firebase.google.com/project/recipe-mgmt-dev/settings/serviceaccounts/adminsdk)
2. Click "Generate new private key"
3. Save the JSON file securely (do NOT commit to repository)

### For Local Development

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/recipe-mgmt-dev-serviceAccountKey.json
export GEMINI_API_KEY=your_gemini_api_key_here
export SPRING_PROFILES_ACTIVE=local  # Optional: disables auth for local testing
mvn spring-boot:run
```

## Infrastructure Setup

### 1. Add Service Account Key to Secret Manager

In the infrastructure repository:

```bash
cd ~/code/recipe-management-infrastructure

# Create secret for Firebase service account
gcloud secrets create firebase-service-account \
  --project=recipe-mgmt-dev \
  --data-file=/path/to/recipe-mgmt-dev-serviceAccountKey.json \
  --replication-policy="automatic"

# Create secret for Gemini API key
gcloud secrets create gemini-api-key \
  --project=recipe-mgmt-dev \
  --set-contents=/path/to/gemini-key.txt \
  --replication-policy="automatic"
```

### 2. Create Terraform Module for Backend

Create `terraform/modules/backend/main.tf`:

```hcl
resource "google_cloud_run_service" "recipe_ai_service" {
  name     = var.service_name
  location = var.region
  project  = var.project_id

  template {
    spec {
      service_account_name = google_service_account.backend_sa.email
      
      containers {
        image = var.image_url
        
        ports {
          container_port = 8080
        }
        
        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = var.environment
        }
        
        env {
          name  = "firebase.project.id"
          value = var.firebase_project_id
        }
        
        env {
          name = "GEMINI_API_KEY"
          value_from {
            secret_key_ref {
              name = google_secret_manager_secret.gemini_api_key.secret_id
              key  = "latest"
            }
          }
        }
        
        env {
          name = "GOOGLE_APPLICATION_CREDENTIALS"
          value = "/secrets/firebase-sa.json"
        }
        
        volume_mounts {
          name       = "firebase-sa"
          mount_path = "/secrets"
        }
        
        resources {
          limits = {
            cpu    = "2"
            memory = "1Gi"
          }
        }
      }
      
      volumes {
        name = "firebase-sa"
        secret {
          secret_name = google_secret_manager_secret.firebase_sa.secret_id
          items {
            key  = "latest"
            path = "firebase-sa.json"
          }
        }
      }
    }
    
    metadata {
      annotations = {
        "autoscaling.knative.dev/minScale" = var.min_instances
        "autoscaling.knative.dev/maxScale" = var.max_instances
      }
    }
  }
  
  traffic {
    percent         = 100
    latest_revision = true
  }
}

resource "google_service_account" "backend_sa" {
  project      = var.project_id
  account_id   = "${var.service_name}-sa"
  display_name = "Service Account for ${var.service_name}"
}

resource "google_cloud_run_service_iam_member" "public_access" {
  service  = google_cloud_run_service.recipe_ai_service.name
  location = google_cloud_run_service.recipe_ai_service.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_secret_manager_secret_iam_member" "backend_sa_firebase" {
  secret_id = google_secret_manager_secret.firebase_sa.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.backend_sa.email}"
}

resource "google_secret_manager_secret_iam_member" "backend_sa_gemini" {
  secret_id = google_secret_manager_secret.gemini_api_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.backend_sa.email}"
}

output "service_url" {
  value = google_cloud_run_service.recipe_ai_service.status[0].url
}
```

### 3. Add Backend to Dev Environment

In `terraform/environments/dev/main.tf`, add:

```hcl
module "recipe_ai_service" {
  source = "../../modules/backend"
  
  project_id          = var.project_id
  region              = var.region
  service_name        = "recipe-ai-service"
  environment         = "dev"
  firebase_project_id = var.firebase_project_id
  image_url           = "europe-west2-docker.pkg.dev/${var.project_id}/recipe-ai/recipe-ai-service:latest"
  min_instances       = 0
  max_instances       = 10
}

output "backend_url" {
  value = module.recipe_ai_service.service_url
}
```

### 4. Create Artifact Registry Repository

```bash
gcloud artifacts repositories create recipe-ai \
  --repository-format=docker \
  --location=europe-west2 \
  --project=recipe-mgmt-dev \
  --description="Docker repository for Recipe AI Service"
```

## CI/CD Setup

### GitHub Actions Workflow

Create `.github/workflows/deploy-backend.yml` in infrastructure repo:

```yaml
name: Deploy Backend to Cloud Run

on:
  push:
    branches:
      - main
    paths:
      - 'backend/**'
  workflow_dispatch:

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    
    steps:
      - name: Checkout infrastructure
        uses: actions/checkout@v4
        with:
          repository: theandiman/recipe-management-infrastructure
      
      - name: Checkout backend code
        uses: actions/checkout@v4
        with:
          repository: theandiman/recipe-management-ai-service
          path: backend-code
      
      - name: Authenticate to Google Cloud
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}
      
      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v2
      
      - name: Configure Docker for Artifact Registry
        run: gcloud auth configure-docker europe-west2-docker.pkg.dev
      
      - name: Build and push Docker image
        working-directory: backend-code
        run: |
          IMAGE_URL="europe-west2-docker.pkg.dev/recipe-mgmt-dev/recipe-ai/recipe-ai-service:${{ github.sha }}"
          IMAGE_LATEST="europe-west2-docker.pkg.dev/recipe-mgmt-dev/recipe-ai/recipe-ai-service:latest"
          
          docker build -t $IMAGE_URL -t $IMAGE_LATEST .
          docker push $IMAGE_URL
          docker push $IMAGE_LATEST
      
      - name: Deploy to Cloud Run
        run: |
          cd terraform/environments/dev
          terraform init
          terraform apply -auto-approve \
            -var="image_url=europe-west2-docker.pkg.dev/recipe-mgmt-dev/recipe-ai/recipe-ai-service:${{ github.sha }}"
### Using GCP Artifact Registry Maven Proxy (optional)

This project can be configured to use the GCP Artifact Registry Maven proxy that caches GitHub Packages dependencies in GCP.

Prerequisites:
- The infra project must create or have an existing Maven remote repo (e.g., `gh-remote-maven`) and a `gh-packages-pat` Secret Manager secret with a GitHub PAT.
- The infra PR must provide IAM access for the Cloud Build SA to the repo (roles/artifactregistry.reader) and the secret (roles/secretmanager.secretAccessor).
  
> Note: To add the GitHub Packages PAT to Secret Manager, use the infra repo's GitHub Actions workflow `Add GitHub Packages PAT to GCP Secret Manager` (manual). See `recipe-management-infrastructure` README for instructions — you can set `create_if_missing=true` to have the workflow create the secret object if needed.

If the infra repo has the above configuration, Cloud Build will automatically use the proxy when builds run (see `cloudbuild.yaml` step that writes `~/.m2/settings.xml` with a token).

This avoids needing to place GitHub credentials in the CI environment, and instead relies on GCP-managed access via Secret Manager and Artifact Registry.

```

## Testing Deployment

### 1. Local Testing

```bash
# With authentication disabled
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run

# Test recipe generation
curl -X POST http://localhost:8080/api/recipes/generate \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Quick pasta dinner",
    "pantryItems": ["tomatoes", "pasta", "garlic"],
    "dietaryPreferences": []
  }'
```

### 2. Cloud Run Testing

After deployment, get the service URL:

```bash
cd ~/code/recipe-management-infrastructure/terraform/environments/dev
terraform output backend_url
```

Test with Firebase ID token:

```bash
# Get ID token from frontend or Firebase CLI
BACKEND_URL="https://recipe-ai-service-xxx-ew.a.run.app"
ID_TOKEN="your_firebase_id_token"

curl -X POST $BACKEND_URL/api/recipes/generate \
  -H "Authorization: Bearer $ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Healthy breakfast",
    "pantryItems": ["eggs", "spinach", "cheese"],
    "dietaryPreferences": ["vegetarian"]
  }'
```

## Frontend Integration

### Update Frontend .env

In the frontend repository, update `.env`:

```bash
# Get the backend URL from Terraform output
cd ~/code/recipe-management-infrastructure/terraform/environments/dev
BACKEND_URL=$(terraform output -raw backend_url)

# Update frontend .env
cd ~/code/recipe-management-frontend
echo "VITE_API_URL=$BACKEND_URL" > .env

# Restart dev server
npm run dev
```

## Health Check

Verify the service is healthy:

```bash
curl https://recipe-ai-service-xxx-ew.a.run.app/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

## Troubleshooting

### Check Cloud Run Logs

```bash
gcloud logs read --project=recipe-mgmt-dev \
  --limit=50 \
  --format="table(timestamp, textPayload)" \
  --filter='resource.type="cloud_run_revision" AND resource.labels.service_name="recipe-ai-service"'
```

### Common Issues

1. **401 Unauthorized**: Check Firebase project ID matches between frontend and backend
2. **CORS errors**: Verify frontend URL in `CorsConfig.java`
3. **500 errors**: Check Gemini API key is set correctly
4. **Service account errors**: Verify service account has Secret Manager access

## Next Steps

1. [ ] Download Firebase service account key
2. [ ] Store secrets in Secret Manager
3. [ ] Create Terraform backend module
4. [ ] Create Artifact Registry repository
5. [ ] Set up GitHub Actions workflow
6. [ ] Deploy to Cloud Run
7. [ ] Update frontend .env with backend URL
8. [ ] Test end-to-end authentication
9. [ ] Monitor Cloud Run metrics
10. [ ] Set up production environment

## Related Documentation

- [Firebase Console](https://console.firebase.google.com/project/recipe-mgmt-dev)
- [Google Cloud Console](https://console.cloud.google.com/run?project=recipe-mgmt-dev)
- [Infrastructure Repo](https://github.com/theandiman/recipe-management-infrastructure)
- [Frontend Repo](https://github.com/theandiman/recipe-management-frontend)
