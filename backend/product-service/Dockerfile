# Dockerfile for product-service
FROM gradle:jdk21 AS build
WORKDIR /app
# Copy the entire backend directory
COPY backend .
# Change to the product-service directory
WORKDIR /app/product-service
# Run the build for the product-service subproject
RUN gradle build -x test

FROM amazoncorretto:21-alpine
VOLUME /tmp
# Create a directory for the JAR
RUN mkdir -p /app
# Copy the JAR file(s) to the /app directory
COPY --from=build /app/product-service/build/libs/*.jar /app/
# Set the working directory
WORKDIR /app
# Use environment variable to specify the JAR file
ENV JAR_FILE=product-service.jar
# Run the JAR file
ENTRYPOINT ["sh", "-c", "java -jar ${JAR_FILE}"]
