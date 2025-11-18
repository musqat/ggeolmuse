# Config Server

Spring Cloud Config 중앙 설정 관리 서비스

## 주요 기능

- 모든 마이크로서비스의 설정 중앙 관리
- 환경별 설정 분리 (dev, prod)
- Git 기반 설정 버전 관리
- 설정 변경 시 재배포 불필요

## 시스템 내 역할

**책임:**
- 모든 서비스의 application.yml 제공
- 환경별 설정 관리 (dev/prod)
- 설정 우선순위 관리: common < environment < service
- Git 저장소와 자동 동기화

## 설정 구조

```
ggeolmuse-config/ (Git 저장소)
├── common/
│   └── application.yml              # 전체 서비스 공통 설정
├── dev/                             # 개발 환경
│   ├── application.yml
│   ├── user-service.yml
│   ├── trade-service.yml
│   ├── market-data-service.yml
│   └── backtest-service.yml
└── prod/                            # 운영 환경
    ├── application.yml
    ├── user-service.yml
    ├── trade-service.yml
    ├── market-data-service.yml
    └── backtest-service.yml
```

**설정 로딩 순서:**
```
1. configs/common/application.yml         (전체 서비스 공통)
2. configs/{profile}/application.yml      (환경별 공통)
3. configs/{profile}/{service}.yml        (서비스별)
```

**최종 설정** = common + profile + service
