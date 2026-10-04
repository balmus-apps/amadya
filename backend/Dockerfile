# Build context: repository root (the OpenAPI spec in api/ is a build input).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties backend/
COPY backend/gradle backend/gradle
RUN cd backend && ./gradlew --no-daemon -q help
COPY api api
COPY backend/src backend/src
RUN cd backend && ./gradlew --no-daemon -q bootJar -x test

FROM eclipse-temurin:25-jre
RUN groupadd --system amadya && useradd --system --gid amadya amadya
WORKDIR /app
COPY --from=build /src/backend/build/libs/*.jar app.jar
USER amadya
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
