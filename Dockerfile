# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy the pom.xml and download dependencies (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user and group for security
RUN addgroup -S storagegroup && adduser -S storageuser -G storagegroup

# Create the storage directory and set permissions
RUN mkdir -p /var/storage && chown -R storageuser:storagegroup /var/storage

# Copy the built jar from the builder stage
COPY --from=builder /app/target/storage-service-0.0.1-SNAPSHOT.jar app.jar
RUN chown storageuser:storagegroup app.jar

# Switch to the non-root user
USER storageuser

# Expose the port the app runs on
EXPOSE 8080

# Environment variables that can be overridden at runtime
ENV STORAGE_BASE_PATH=/var/storage
ENV SPRING_PROFILES_ACTIVE=prod

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
