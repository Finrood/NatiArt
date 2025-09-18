# Stage 1: Build
FROM gradle:9.1-jdk25-alpine AS build
WORKDIR /app

# Copy Gradle wrapper and build files first for caching
COPY backend/gradle/ gradle/
COPY backend/gradlew gradlew
COPY backend/settings.gradle.kts settings.gradle.kts
COPY backend/build.gradle.kts build.gradle.kts

# Download dependencies to cache them
RUN ./gradlew --no-daemon dependencies

# Copy source code
COPY backend/directory-service directory-service/

# Build the application, skipping tests
WORKDIR /app/directory-service
RUN ../gradlew build -x test --no-daemon

# Stage 2: Runtime
FROM amazoncorretto:25-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the built JAR from the build stage
COPY --from=build --chown=appuser:appgroup /app/directory-service/build/libs/*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Optimize JVM flags for Spring Boot in a container
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]