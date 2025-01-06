# Stage 1: Build
FROM gradle:8.12.0-jdk21-alpine AS build
WORKDIR /app

# Copy only the necessary files for Gradle build (gradle wrapper + build files)
COPY backend/gradle gradle
COPY backend/gradlew gradlew
COPY backend/settings.gradle.kts settings.gradle.kts
COPY backend/build.gradle.kts build.gradle.kts
COPY backend/product-service product-service

# Make sure gradlew is executable
RUN chmod +x gradlew

# Run the build, skipping tests
WORKDIR /app/product-service
RUN ../gradlew build -x test && rm -rf ~/.gradle/caches

# Stage 2: Runtime
FROM amazoncorretto:21-alpine
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/product-service/build/libs/*.jar /app/

# Run the application
ENTRYPOINT ["java", "-jar", "/app/product-service.jar"]
