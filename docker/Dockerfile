# Docker is intentionally separate from the offline portable package: it builds
# with online base images and Maven/npm dependencies, then runs one same-origin app.
FROM node:24-bookworm-slim AS frontend
WORKDIR /src
COPY stu-frontend/package.json stu-frontend/package-lock.json ./
RUN npm ci
COPY stu-frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY stu-backend/ ./
COPY --from=frontend /src/dist ./src/main/resources/static
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend /build/target/*.jar /app/stu-manage.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/stu-manage.jar"]
