package main

import (
	"strings"
	"testing"
)

// 로그인 · 사용자 관리 · 서비스 클라이언트를 나눈 realm
const goodRealm = `{"clients":[
{"clientId":"ggeolmuse-client","publicClient":false,"standardFlowEnabled":false,"directAccessGrantsEnabled":true,"serviceAccountsEnabled":false,"redirectUris":[],"webOrigins":[]},
{"clientId":"ggeolmuse-user-admin","publicClient":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":true},
{"clientId":"ggeolmuse-frontend","publicClient":true,"standardFlowEnabled":true},
{"clientId":"ggeolmuse-service","publicClient":false,"standardFlowEnabled":false,"implicitFlowEnabled":false,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":true}],
"clientScopeMappings":{"realm-management":[{"client":"ggeolmuse-user-admin","roles":["manage-users"]}]},
"users":[
{"username":"service-account-ggeolmuse-user-admin","serviceAccountClientId":"ggeolmuse-user-admin","clientRoles":{"realm-management":["manage-users"]}},
{"username":"service-account-ggeolmuse-service","serviceAccountClientId":"ggeolmuse-service","realmRoles":["service"]}]}`

// Deployment(컨테이너 env · volumes 는 YAML 흐름 표기)
func deploy(name, env, volumes string) string {
	return "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: " + name + "\nspec:\n  template:\n    spec:\n      containers: [{name: app, env: " + env +
		"}]\n      volumes: " + volumes + "\n---\n"
}

// 앱 secret 에서 키만 골라 넣는 secret 볼륨
func secretVol(name string, optional bool, keys ...string) string {
	items := make([]string, len(keys))
	for i, k := range keys {
		items[i] = "{key: " + k + ", path: " + k + "}"
	}
	opt := ""
	if optional {
		opt = ", optional: true"
	}
	return "{name: " + name + ", secret: {secretName: app-secrets" + opt + ", items: [" + strings.Join(items, ", ") + "]}}"
}

// 앱 secret 키를 읽는 환경변수
func secretEnv(key string, optional bool) string {
	opt := ""
	if optional {
		opt = ", optional: true"
	}
	return "{name: " + key + ", valueFrom: {secretKeyRef: {name: app-secrets, key: " + key + opt + "}}}"
}

var (
	userSvc  = deploy("user-service", "[]", "["+secretVol("secrets", false, "KEYCLOAK_SECRET", "KEYCLOAK_ADMIN_CLIENT_SECRET", "KEYCLOAK_SERVICE_CLIENT_SECRET")+"]")
	tradeSvc = deploy("trade-service", "[]", "["+secretVol("secrets", false, "KEYCLOAK_SERVICE_CLIENT_SECRET")+"]")
	keycloak = deploy("keycloak", "[]", "["+secretVol("secrets", false, "KEYCLOAK_CLIENT_SECRET")+", "+
		secretVol("client-secrets", true, "KEYCLOAK_SERVICE_CLIENT_SECRET", "KEYCLOAK_ADMIN_CLIENT_SECRET")+"]")
	goodManifest = userSvc + tradeSvc + keycloak
)

func TestRun(t *testing.T) {
	realm := func(from, to string) string {
		if !strings.Contains(goodRealm, from) {
			t.Fatalf("realm 에 %q 없음", from)
		}
		return strings.Replace(goodRealm, from, to, 1)
	}
	cases := []struct {
		name     string
		manifest string
		realm    string
		code     int
		want     string
	}{
		{"나눈 realm 과 로그인 · 사용자 관리 secret 을 user-service · Keycloak 만 받으면 통과", goodManifest, goodRealm, 0, "ok    realm.json · ggeolmuse-client 비밀번호 로그인만"},
		{"로그인 클라이언트의 브라우저 흐름이 켜지면 실패", goodManifest,
			realm(`"clientId":"ggeolmuse-client","publicClient":false,"standardFlowEnabled":false`, `"clientId":"ggeolmuse-client","publicClient":false,"standardFlowEnabled":true`),
			1, "FAIL  realm.json · ggeolmuse-client 비밀번호 로그인만"},
		{"로그인 클라이언트에 리다이렉트 주소가 있으면 실패", goodManifest,
			realm(`"redirectUris":[]`, `"redirectUris":["https://example.test/*"]`), 1, "FAIL  realm.json · ggeolmuse-client 비밀번호 로그인만"},
		{"로그인 클라이언트의 서비스 계정 사용자가 있으면 실패", goodManifest,
			realm(`"users":[`, `"users":[{"username":"service-account-ggeolmuse-client","serviceAccountClientId":"ggeolmuse-client"},`),
			1, "FAIL  realm.json · ggeolmuse-client 서비스 계정 사용자 없음"},
		{"사용자 관리 클라이언트에 클라이언트 관리 역할이 더해지면 실패", goodManifest,
			realm(`"clientRoles":{"realm-management":["manage-users"]}`, `"clientRoles":{"realm-management":["manage-users","manage-clients"]}`),
			1, "FAIL  realm.json · service-account-ggeolmuse-user-admin realm-management 역할 manage-clients"},
		{"로그인 클라이언트로 realm-management 범위를 넘기면 실패", goodManifest,
			realm(`{"client":"ggeolmuse-user-admin","roles":["manage-users"]}`, `{"client":"ggeolmuse-client","roles":["manage-users"]}`),
			1, "FAIL  realm.json · ggeolmuse-client 범위 realm-management 역할 manage-users"},
		{"서비스 클라이언트의 브라우저 흐름 값이 없으면(기본값 켬) 실패", goodManifest,
			realm(`{"clientId":"ggeolmuse-service","publicClient":false,"standardFlowEnabled":false,`, `{"clientId":"ggeolmuse-service","publicClient":false,`),
			1, "FAIL  realm.json · ggeolmuse-service 서비스 계정만"},
		{"서비스 계정에 admin realm 역할이 더해지면 실패", goodManifest,
			realm(`"realmRoles":["service"]`, `"realmRoles":["service","admin"]`), 1, "FAIL  realm.json · ggeolmuse-service 역할은 realm 역할 service 하나"},
		{"사람 사용자에게 realm 역할 service 가 있으면 실패", goodManifest,
			realm(`"users":[`, `"users":[{"username":"tester","realmRoles":["user","service"]},`), 1, "FAIL  realm.json · tester realm 역할 service"},
		{"trade-service 가 로그인 클라이언트 secret 을 받으면 실패",
			userSvc + deploy("trade-service", "[]", "["+secretVol("secrets", false, "KEYCLOAK_SECRET")+"]") + keycloak, goodRealm,
			1, "FAIL  trade-service · KEYCLOAK_SECRET 받음(받는 곳은 [user-service] 만)"},
		{"trade-service 가 환경변수로 사용자 관리 secret 을 받아도 실패",
			userSvc + deploy("trade-service", "["+secretEnv("KEYCLOAK_ADMIN_CLIENT_SECRET", false)+"]", "[]") + keycloak, goodRealm,
			1, "FAIL  trade-service · KEYCLOAK_ADMIN_CLIENT_SECRET 받음"},
		{"user-service 가 사용자 관리 secret 을 받지 않으면 실패",
			deploy("user-service", "[]", "["+secretVol("secrets", false, "KEYCLOAK_SECRET")+"]") + tradeSvc + keycloak, goodRealm,
			1, "FAIL  user-service · KEYCLOAK_ADMIN_CLIENT_SECRET 없음"},
		{"Keycloak 이 사용자 관리 secret 을 반드시 있는 볼륨으로 받으면 실패",
			userSvc + tradeSvc + deploy("keycloak", "[]", "["+secretVol("secrets", false, "KEYCLOAK_CLIENT_SECRET", "KEYCLOAK_ADMIN_CLIENT_SECRET")+"]"), goodRealm,
			1, "FAIL  keycloak · KEYCLOAK_ADMIN_CLIENT_SECRET 를 반드시 있는 secret 으로 받음"},
		{"Keycloak 이 서비스 클라이언트 secret 을 optional 아닌 환경변수로 받으면 실패",
			userSvc + tradeSvc + deploy("keycloak", "["+secretEnv("KEYCLOAK_SERVICE_CLIENT_SECRET", false)+"]", "[]"), goodRealm,
			1, "FAIL  keycloak · KEYCLOAK_SERVICE_CLIENT_SECRET 를 반드시 있는 secret 으로 받음"},
		{"Keycloak 이 optional 환경변수로 받으면 통과",
			userSvc + tradeSvc + deploy("keycloak", "["+secretEnv("KEYCLOAK_SERVICE_CLIENT_SECRET", true)+", "+secretEnv("KEYCLOAK_ADMIN_CLIENT_SECRET", true)+"]", "[]"),
			goodRealm, 0, "ok    keycloak · KEYCLOAK_ADMIN_CLIENT_SECRET 없어도 기동"},
		{"realm 파일이 JSON 이 아니면 실패", goodManifest, "{", 1, "FAIL  realm.json 읽기 실패"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var out strings.Builder
			code := run(strings.NewReader(c.manifest), map[string][]byte{"realm.json": []byte(c.realm)}, &out)
			if code != c.code {
				t.Fatalf("종료 코드 %d, 기대 %d\n%s", code, c.code, out.String())
			}
			if !strings.Contains(out.String(), c.want) {
				t.Fatalf("출력에 %q 없음\n%s", c.want, out.String())
			}
		})
	}
}
