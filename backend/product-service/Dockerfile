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
RUN ./gradlew :product-service:dependencies --no-daemon

# Only this service's source enters its build stage.
COPY backend/product-service/src/ product-service/src/
RUN ./gradlew :product-service:bootJar --no-daemon

# Stage 2: Runtime
FROM amazoncorretto:25-alpine AS runtime
WORKDIR /app

ARG NATIART_STORAGE_ROOT=/var/lib/natiart/product-images
ENV NATIART_STORAGE_ROOT=${NATIART_STORAGE_ROOT}

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p "${NATIART_STORAGE_ROOT}" && \
    chown -R appuser:appgroup "${NATIART_STORAGE_ROOT}"
COPY --chmod=755 backend/docker/entrypoint.sh /app/entrypoint.sh
USER appuser

# Copy only the built JAR from the build stage
COPY --from=build --chown=appuser:appgroup /app/product-service/build/libs/product-service.jar app.jar

# Expose the application port
EXPOSE 8082

# Optimize JVM flags for Spring Boot in a container
ENTRYPOINT ["/app/entrypoint.sh"]

# Fixed catalog files enter only the explicit QA target.
FROM python:3.13-alpine AS qa-assets
WORKDIR /qa
COPY qa/prepare_images.py qa/manifest.json qa/artwork.json /qa/
COPY qa/assets /qa/assets
RUN python prepare_images.py /prepared-images

FROM runtime AS qa
COPY --from=qa-assets /prepared-images /app/qa-images
ENV NATIART_STORAGE_ROOT=/tmp/natiart-qa-product-images
ENV NATIART_QA_RESET_STORAGE=true

FROM runtime AS production
