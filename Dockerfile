# ---- Build Stage ----
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (layer cache)
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create uploads directory
RUN mkdir -p /app/uploads

# Copy the built jar
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

