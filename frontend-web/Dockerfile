# 프론트엔드 Dockerfile
FROM node:20 AS build

# K8s 환경용 기본값이 있는 빌드 인자
ARG VITE_API_URL=/api
ARG VITE_KEYCLOAK_URL=http://app.localhost/auth
ARG VITE_KEYCLOAK_REALM=muscathan
ARG VITE_KEYCLOAK_CLIENT_ID=ggeolmuse-frontend

# 빌드용 환경 변수
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL
ENV VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM
ENV VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

WORKDIR /app

# package.json과 package-lock.json 복사
COPY package*.json ./

# 의존성 설치
RUN npm install

# 소스 코드 복사
COPY . .

# 빌드
RUN npm run build

# Nginx 서버
FROM nginx:alpine

# 빌드된 파일을 nginx로 복사
COPY --from=build /app/dist /usr/share/nginx/html

# nginx 설정 파일 복사
COPY nginx.conf /etc/nginx/nginx.conf

RUN chown -R 10001:10001 /var/cache/nginx

USER 10001

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]