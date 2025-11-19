#!/bin/bash
# Recipe AI Service Startup Script with Honeycomb Observability
# This script starts the service with OpenTelemetry agent for Honeycomb integration

set -euo pipefail

# Service configuration
SERVICE_NAME="Recipe AI Service"
MAIN_CLASS="com.recipe.ai.RecipeAiApplication"
JAR_FILE="target/recipe-ai-service-0.0.1-SNAPSHOT.jar"

# OpenTelemetry agent configuration
OTEL_AGENT_VERSION="${OTEL_AGENT_VERSION:-v2.21.0}"
OTEL_AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"
OTEL_AGENT_JAR="opentelemetry-javaagent.jar"

# Default environment variables for observability
export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-recipe-ai-service}"
export OTEL_SERVICE_VERSION="${SERVICE_VERSION:-0.0.1-SNAPSHOT}"
export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-otlp}"
export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-otlp}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-https://api.honeycomb.io:443}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-service.instance.id=${HOSTNAME}}"

echo "🚀 Starting ${SERVICE_NAME} with Honeycomb observability..."
echo "📊 Service: ${OTEL_SERVICE_NAME}"
echo "🏷️  Version: ${OTEL_SERVICE_VERSION}"
echo "📡 Endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT}"
echo "🔧 Protocol: ${OTEL_EXPORTER_OTLP_PROTOCOL}"

# Check if Honeycomb API key is set
if [[ -z "${HONEYCOMB_API_KEY:-}" ]]; then
    echo "⚠️  Warning: HONEYCOMB_API_KEY environment variable not set"
    echo "🔑 Set your Honeycomb API key:"
    echo "   export HONEYCOMB_API_KEY=your_api_key_here"
    echo ""
    echo "📖 Get your API key from: https://ui.honeycomb.io/account"
    echo ""
fi

# Check if OpenTelemetry agent JAR exists, download if missing
if [[ ! -f "${OTEL_AGENT_JAR}" ]]; then
    echo "⬇️  Downloading OpenTelemetry Java agent ${OTEL_AGENT_VERSION}..."
    if ! curl -L -o "${OTEL_AGENT_JAR}" "${OTEL_AGENT_URL}"; then
        echo "❌ Failed to download OpenTelemetry agent"
        exit 1
    fi
    echo "✅ OpenTelemetry agent downloaded successfully"
fi

# Verify the agent JAR is valid
if [[ ! -s "${OTEL_AGENT_JAR}" ]]; then
    echo "❌ OpenTelemetry agent JAR is empty or corrupted"
    exit 1
fi

# Check if JAR file exists
if [[ ! -f "${JAR_FILE}" ]]; then
    echo "❌ JAR file not found: ${JAR_FILE}"
    echo "🏗️  Build the application first:"
    echo "   mvn clean package -DskipTests"
    exit 1
fi

# Set OTLP headers with Honeycomb API key
if [[ -n "${HONEYCOMB_API_KEY:-}" ]]; then
    export OTEL_EXPORTER_OTLP_HEADERS="api-key=${HONEYCOMB_API_KEY}"
    echo "🔐 Honeycomb API key configured"
else
    echo "⚠️  Running without Honeycomb API key - no observability data will be sent"
fi

echo ""
echo "🔍 Starting service with observability enabled..."
echo "📈 Traces, metrics, and logs will be sent to Honeycomb"
echo "🌐 View your data at: https://ui.honeycomb.io/"
echo ""

# Start the application with OpenTelemetry agent
exec java \
    -javaagent:"${OTEL_AGENT_JAR}" \
    -jar "${JAR_FILE}" \
    "$@"