FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S -G app app
RUN mkdir -p /app/data/files && chown -R app:app /app/data
WORKDIR /app
COPY --from=build /workspace/target/crm-backend-0.1.0.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=10 CMD sh -c 'test "$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/v1/auth/me)" = "401"'
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--XX:MaxRAMPercentage=75.0} -jar /app/app.jar"]


