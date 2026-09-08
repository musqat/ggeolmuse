# GGeolmuse Helm Chart

## 🔐 values 와 Secret

`values.yaml`(공통), `values-dev.yaml`, `values-prod.yaml` 은 전부 Git 에 있다.
prod 는 ArgoCD 가 master 의 `values-prod.yaml` 을 읽어 배포하므로 커밋해야 반영된다.

비밀값은 파일에 없다.

- dev: `values-secret.yaml.example` 을 `values-secret.yaml` 로 복사해 채운다. `.gitignore` 에 걸려 있다.
- prod: AWS Secrets Manager `ggeolmuse/production` 을 External Secrets 가 `ggeolmuse-secrets` 로 내려준다.

```bash
# dev
helm upgrade --install ggeolmuse . -f values.yaml -f values-dev.yaml -f values-secret.yaml

# prod (ArgoCD 가 한다)
helm upgrade --install ggeolmuse . -f values.yaml -f values-prod.yaml
```

##  DB 백업

prod 는 `dbBackup.enabled: true` 로 CronJob `db-backup` 이 매달 1일 09:00 KST 에
서비스 DB를 `pg_dump -Fc` 로 떠서
`s3://ggeolmuse-db-backup-apne2/YYYY/MM/` 에 올린다. 버킷과 EC2 쓰기 권한은
`terraform/db-backup.tf` 가 만든다.

1일이 주말이면 클러스터가 꺼져 있다. `startingDeadlineSeconds` 가 3일이라 월요일
07:00 부팅 때 놓친 실행을 따라잡는다.

손으로 한 번 돌리기:

```bash
kubectl create job --from=cronjob/db-backup db-backup-manual
kubectl logs -f job/db-backup-manual -c dump     # 덤프
kubectl logs -f job/db-backup-manual -c upload   # 업로드
```

RDS 자동 스냅샷(PITR)과 역할이 다르다. 스냅샷은 5분 단위 시점 복원, 이건 RDS 밖 사본이다.

##  업그레이드

```bash
helm upgrade ggeolmuse . -f values.yaml -f values-dev.yaml -f values-secret.yaml
```
