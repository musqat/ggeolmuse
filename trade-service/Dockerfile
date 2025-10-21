FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar
COPY opentelemetry-javaagent.jar opentelemetry-javaagent.jar

ENTRYPOINT ["sh", "-c", "java -javaagent:opentelemetry-javaagent.jar $JAVA_OPTS -jar app.jar"]