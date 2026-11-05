FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src/main ./src/main
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /build/build/libs/sv_vehicle-*.jar /app/app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
