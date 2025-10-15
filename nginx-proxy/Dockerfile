FROM nginx:1.25-alpine

# 메타데이터
LABEL maintainer="GGeolmuse Team"
LABEL version="1.5.0"
LABEL description="Nginx Reverse Proxy for GGeolmuse Microservices"

# Nginx 설정 복사
COPY nginx.conf /etc/nginx/nginx.conf

# 로그 디렉토리 생성 및 권한 설정
RUN mkdir -p /var/log/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    chmod -R 755 /var/log/nginx

# health check를 위한 curl 설치
RUN apk add --no-cache curl

# 포트 노출
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Nginx를 포어그라운드로 실행
CMD ["nginx", "-g", "daemon off;"]