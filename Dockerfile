FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/notificator-backend.jar .

EXPOSE 8080

CMD ["java", "-jar", "notificator-backend.jar"]