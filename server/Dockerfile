FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache pandoc

COPY build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dserver.port=8080", "app.jar"]
