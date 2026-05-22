FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon && \
    cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /tmp/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /tmp/app.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
