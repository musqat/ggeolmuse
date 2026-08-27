# 빌드 스테이지 (빌드 컨텍스트: 레포 루트)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# ggeolmuse-bom (common-lib 포함) 설치
COPY ggeolmuse-bom/pom.xml /bom/pom.xml
COPY ggeolmuse-bom/src /bom/src
RUN mvn -f /bom/pom.xml install -DskipTests -q

# 서비스 빌드
COPY gateway-server/pom.xml .
RUN mvn -B dependency:go-offline -DskipTests -q
COPY gateway-server/src ./src
RUN mvn -B clean package -DskipTests -q

# 런타임 스테이지
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
RUN chmod 644 opentelemetry-javaagent.jar

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
