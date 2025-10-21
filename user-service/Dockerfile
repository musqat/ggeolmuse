FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# OpenTelemetry Java Agent 다운로드
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
RUN chmod 644 opentelemetry-javaagent.jar

COPY target/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java -javaagent:opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]