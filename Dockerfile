# Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Run
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 shopbot
COPY --from=build /app/target/telegram-shop-bot-*.jar app.jar
USER shopbot
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
