FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

RUN mvn -B -f backend/pom.xml clean package


FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/backend/target/backend-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
