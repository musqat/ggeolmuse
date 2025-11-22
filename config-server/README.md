# Config Server

Spring Cloud Config 중앙 설정 관리 서비스

## 주요 기능

- 모든 마이크로서비스의 설정 중앙 관리
- 환경별 설정 분리 (dev, prod)
- Git 기반 설정 버전 관리
- 설정 변경 시 재배포 불필요

## 시스템 내 역할

**핵심 기능:**
- 6개 마이크로서비스에 환경별 설정 제공
- 설정 우선순위: common < environment < service
- Git 저장소 자동 동기화

**의존 서비스:** [ggeolmuse-config](https://github.com/musqat/ggeolmuse-config) (Git Repository)

## 설정 구조

**설정 로딩 순서:**
```
common/application.yml  →  {env}/application.yml  →  {env}/{service}.yml
```

**최종 설정** = 공통 설정 + 환경별 설정 + 서비스별 설정
