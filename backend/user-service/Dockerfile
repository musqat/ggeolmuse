# 빌드 스테이지 (빌드 컨텍스트: 레포 루트)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Central 미러(keycloak 부모 pom 이 끌고 오는 jboss 저장소가 응답 없음)
COPY .mvn/ci-settings.xml /root/.m2/settings.xml
# Central 이 잠깐 5xx 를 줘도 다시 받음(기본은 429 · 503 만 재시도)
ENV MAVEN_ARGS="-Daether.connector.http.retryHandler.count=3 -Daether.connector.http.retryHandler.serviceUnavailable=429,500,502,503,504"

# ggeolmuse-bom(의존성 버전) · ggeolmuse-common(공통 코드) 설치
COPY backend/ggeolmuse-bom/pom.xml /bom/pom.xml
RUN mvn -f /bom/pom.xml install -q
COPY backend/ggeolmuse-common/pom.xml /common/pom.xml
COPY backend/ggeolmuse-common/src /common/src
RUN mvn -f /common/pom.xml install -DskipTests -q

# messaging 모듈 설치
COPY backend/messaging/pom.xml /messaging/pom.xml
COPY backend/messaging/src /messaging/src
RUN mvn -f /messaging/pom.xml install -DskipTests -q

# 서비스 빌드
COPY backend/user-service/pom.xml .
RUN mvn -B dependency:go-offline -DskipTests -q
COPY backend/user-service/src ./src
RUN mvn -B clean package -DskipTests -q

# 런타임 스테이지
FROM eclipse-temurin:21-jre-alpine

# 베이스 이미지가 늦게 따라오는 알파인 보안 패치를 빌드 때 받음
RUN apk upgrade --no-cache

WORKDIR /app

ADD --checksum=sha256:cc4af5966ab72109cacc962ba3b9f99b3e88caf064c3144a451bcfe0f4950f19 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.26.1/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
RUN chmod 644 opentelemetry-javaagent.jar

COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
