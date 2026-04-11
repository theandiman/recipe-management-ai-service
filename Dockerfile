# Runtime stage (distroless)
FROM debian:bookworm-slim AS runtime

# OpenTelemetry agent version - update this when upgrading
ARG OTEL_AGENT_VERSION=v2.21.0
ENV OTEL_AGENT_VERSION=${OTEL_AGENT_VERSION}

RUN useradd --system --uid 1000 --create-home --home-dir /home/appuser appuser
WORKDIR /home/appuser

# Download OpenTelemetry Java agent for Honeycomb integration
RUN apt-get update && apt-get install -y wget ca-certificates && \
    wget -O opentelemetry-javaagent.jar \
         https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar && \
    # Verify the download was successful and has content
    [ -s opentelemetry-javaagent.jar ] && echo "✅ OpenTelemetry agent downloaded successfully" || (echo "❌ Failed to download OpenTelemetry agent" && exit 1) && \
    apt-get remove -y wget ca-certificates && apt-get autoremove -y && apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy build output and resolve application jar regardless of extraction path
COPY . /tmp/build-context
RUN set -e && \
    jar_path="$(find /tmp/build-context -maxdepth 4 -type f -name 'recipe-generator-service-*.jar' | head -n 1)" && \
    [ -n "$jar_path" ] && \
    cp "$jar_path" /home/appuser/app.jar && \
    rm -rf /tmp/build-context && \
    chown appuser:appuser /home/appuser/app.jar /home/appuser/opentelemetry-javaagent.jar && \
    chmod 500 /home/appuser/app.jar && \
    chmod 400 /home/appuser/opentelemetry-javaagent.jar

FROM gcr.io/distroless/java21-debian12
WORKDIR /app
# Copy the jar and agent from the intermediate runtime image; they already have correct ownership
COPY --from=runtime /home/appuser/app.jar /app/app.jar
COPY --from=runtime /home/appuser/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
EXPOSE 8080
USER 1000:1000
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
