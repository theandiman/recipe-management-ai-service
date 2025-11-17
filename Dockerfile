# Runtime stage (distroless)
FROM debian:bookworm-slim AS runtime
RUN useradd --system --uid 1000 --create-home --home-dir /home/appuser appuser
WORKDIR /home/appuser
# Copy jar and set ownership to non-root user
COPY /workspace/target/*.jar /home/appuser/app.jar
RUN chown appuser:appuser /home/appuser/app.jar && chmod 500 /home/appuser/app.jar

FROM gcr.io/distroless/java21-debian12
WORKDIR /app
# Copy the jar from the intermediate runtime image; it already has correct ownership
COPY --from=runtime /home/appuser/app.jar /app/app.jar
EXPOSE 8080
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
