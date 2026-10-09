# Stage 1: Build
FROM gradle:9.1-jdk25-alpine AS build
WORKDIR /app

# Copy Gradle wrapper and build files first for caching
COPY backend/gradle/ gradle/
COPY backend/gradlew gradlew
COPY backend/settings.gradle.kts settings.gradle.kts
COPY backend/build.gradle.kts build.gradle.kts
COPY backend/directory-service/build.gradle.kts directory-service/build.gradle.kts
COPY backend/product-service/build.gradle.kts product-service/build.gradle.kts

# Download dependencies to cache them
RUN ./gradlew :directory-service:dependencies --no-daemon

# Only this service's source enters its build stage.
COPY backend/directory-service/src/ directory-service/src/
RUN ./gradlew :directory-service:bootJar --no-daemon

# Stage 2: Runtime
FROM amazoncorretto:25-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --chmod=755 backend/docker/entrypoint.sh /app/entrypoint.sh
USER appuser

# Copy only the built JAR from the build stage
COPY --from=build --chown=appuser:appgroup /app/directory-service/build/libs/directory-service.jar app.jar

# Expose the application port
EXPOSE 8081

# Optimize JVM flags for Spring Boot in a container
ENTRYPOINT ["/app/entrypoint.sh"]
