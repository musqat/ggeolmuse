// keycloak-check 는 realm 파일의 로그인 · 사용자 관리 · 서비스 클라이언트가 가진 흐름과 역할,
// 렌더한 매니페스트에서 Keycloak 클라이언트 secret 을 받는 워크로드를 대조한다
package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"sort"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	utilyaml "k8s.io/apimachinery/pkg/util/yaml"
	"sigs.k8s.io/yaml"
)

const (
	loginClient     = "ggeolmuse-client"
	userAdminClient = "ggeolmuse-user-admin"
	serviceClient   = "ggeolmuse-service"
	realmManagement = "realm-management"
	manageUsers     = "manage-users"
	serviceRole     = "service"
	keycloakName    = "keycloak"
)

// secret 키마다 받는 워크로드(첫 워크로드는 반드시 받음, 목록 밖 워크로드가 받으면 실패)
var secretOwners = []struct {
	key    string
	owners []string
}{
	{"KEYCLOAK_SECRET", []string{"user-service"}},
	{"KEYCLOAK_ADMIN_CLIENT_SECRET", []string{"user-service", keycloakName}},
}

// Keycloak 이 realm 을 새로 만들 때만 쓰는 클라이언트 secret(없어도 기동)
var keycloakOptional = []string{"KEYCLOAK_SERVICE_CLIENT_SECRET", "KEYCLOAK_ADMIN_CLIENT_SECRET"}

type client struct {
	ClientID                  string   `json:"clientId"`
	PublicClient              bool     `json:"publicClient"`
	StandardFlowEnabled       *bool    `json:"standardFlowEnabled"`
	ImplicitFlowEnabled       bool     `json:"implicitFlowEnabled"`
	DirectAccessGrantsEnabled bool     `json:"directAccessGrantsEnabled"`
	ServiceAccountsEnabled    bool     `json:"serviceAccountsEnabled"`
	RedirectURIs              []string `json:"redirectUris"`
	WebOrigins                []string `json:"webOrigins"`
}

type user struct {
	Username               string              `json:"username"`
	ServiceAccountClientID string              `json:"serviceAccountClientId"`
	RealmRoles             []string            `json:"realmRoles"`
	ClientRoles            map[string][]string `json:"clientRoles"`
}

type scopeMapping struct {
	Client string   `json:"client"`
	Roles  []string `json:"roles"`
}

type realm struct {
	Clients             []client                  `json:"clients"`
	Users               []user                    `json:"users"`
	ClientScopeMappings map[string][]scopeMapping `json:"clientScopeMappings"`
}

// 워크로드가 앱 secret 에서 받는 키 하나(볼륨 항목 · 환경변수)
type secretUse struct {
	workload string
	key      string
	optional bool
}

// ok · FAIL 줄과 종료 코드
type report struct {
	w    io.Writer
	code int
}

func (r *report) check(pass bool, format string, a ...any) {
	if pass {
		fmt.Fprintf(r.w, "ok    "+format+"\n", a...)
		return
	}
	fmt.Fprintf(r.w, "FAIL  "+format+"\n", a...)
	r.code = 1
}

func main() {
	if len(os.Args) < 3 {
		fmt.Println("FAIL  사용법: keycloak-check rendered.yaml realm.json [realm.json ...]")
		os.Exit(1)
	}
	manifest, err := os.Open(os.Args[1])
	if err != nil {
		fmt.Println("FAIL  읽기 실패:", err)
		os.Exit(1)
	}
	defer manifest.Close()
	realms := map[string][]byte{}
	for _, path := range os.Args[2:] {
		data, err := os.ReadFile(path)
		if err != nil {
			fmt.Println("FAIL  읽기 실패:", err)
			os.Exit(1)
		}
		realms[filepath.Base(path)] = data
	}
	os.Exit(run(manifest, realms, os.Stdout))
}

// realm 파일마다 클라이언트 · 역할을, 렌더 결과에서 secret 을 받는 워크로드를 대조해 결과를 w 에 쓰고 종료 코드를 돌려줌
func run(manifest io.Reader, realms map[string][]byte, w io.Writer) int {
	r := &report{w: w}
	names := make([]string, 0, len(realms))
	for name := range realms {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		checkRealm(name, realms[name], r)
	}

	uses, err := load(manifest)
	if err != nil {
		r.check(false, "매니페스트 읽기 실패: %v", err)
		return r.code
	}
	checkSecrets(uses, r)
	return r.code
}

func checkRealm(name string, data []byte, r *report) {
	var rl realm
	if err := json.Unmarshal(data, &rl); err != nil {
		r.check(false, "%s 읽기 실패: %v", name, err)
		return
	}
	clients := map[string]client{}
	for _, c := range rl.Clients {
		clients[c.ClientID] = c
	}
	accounts := map[string]user{}
	for _, u := range rl.Users {
		if u.ServiceAccountClientID != "" {
			accounts[u.ServiceAccountClientID] = u
		}
	}

	login, found := clients[loginClient]
	r.check(found && !login.ServiceAccountsEnabled && isFalse(login.StandardFlowEnabled) && !login.ImplicitFlowEnabled &&
		len(login.RedirectURIs) == 0 && len(login.WebOrigins) == 0,
		"%s · %s 비밀번호 로그인만(서비스 계정 · 브라우저 흐름 · 리다이렉트 주소 없음)", name, loginClient)
	_, loginAccount := accounts[loginClient]
	r.check(!loginAccount, "%s · %s 서비스 계정 사용자 없음", name, loginClient)

	for _, id := range []string{userAdminClient, serviceClient} {
		c, found := clients[id]
		r.check(found && serviceAccountOnly(c), "%s · %s 서비스 계정만", name, id)
	}
	admin := accounts[userAdminClient]
	r.check(len(admin.RealmRoles) == 0 && sameRoles(admin.ClientRoles, map[string][]string{realmManagement: {manageUsers}}),
		"%s · %s 역할은 %s %s 하나", name, userAdminClient, realmManagement, manageUsers)
	service := accounts[serviceClient]
	r.check(slices.Equal(service.RealmRoles, []string{serviceRole}) && len(service.ClientRoles) == 0,
		"%s · %s 역할은 realm 역할 %s 하나", name, serviceClient, serviceRole)

	// realm-management 역할은 사용자 관리 클라이언트의 manage-users 만
	granted := 0
	for _, u := range rl.Users {
		for _, role := range u.ClientRoles[realmManagement] {
			if u.ServiceAccountClientID != userAdminClient || role != manageUsers {
				r.check(false, "%s · %s %s 역할 %s", name, u.Username, realmManagement, role)
				granted++
			}
		}
	}
	for _, m := range rl.ClientScopeMappings[realmManagement] {
		for _, role := range m.Roles {
			if m.Client != userAdminClient || role != manageUsers {
				r.check(false, "%s · %s 범위 %s 역할 %s", name, m.Client, realmManagement, role)
				granted++
			}
		}
	}
	if granted == 0 {
		r.check(true, "%s · %s 역할은 %s 의 %s 만", name, realmManagement, userAdminClient, manageUsers)
	}

	// realm 역할 service 는 서비스 클라이언트의 서비스 계정만
	others := 0
	for _, u := range rl.Users {
		if u.ServiceAccountClientID != serviceClient && slices.Contains(u.RealmRoles, serviceRole) {
			r.check(false, "%s · %s realm 역할 %s", name, u.Username, serviceRole)
			others++
		}
	}
	if others == 0 {
		r.check(true, "%s · realm 역할 %s 는 %s 서비스 계정만", name, serviceRole, serviceClient)
	}
}

// 비밀번호 로그인 · 브라우저 흐름 없이 client_credentials 로만 토큰을 받는 클라이언트(standardFlowEnabled 는 비면 켬)
func serviceAccountOnly(c client) bool {
	return c.ServiceAccountsEnabled && !c.PublicClient && isFalse(c.StandardFlowEnabled) && !c.ImplicitFlowEnabled &&
		!c.DirectAccessGrantsEnabled
}

func isFalse(p *bool) bool {
	return p != nil && !*p
}

func sameRoles(got, want map[string][]string) bool {
	if len(got) != len(want) {
		return false
	}
	for k, roles := range want {
		g := slices.Clone(got[k])
		slices.Sort(g)
		w := slices.Clone(roles)
		slices.Sort(w)
		if !slices.Equal(g, w) {
			return false
		}
	}
	return true
}

func checkSecrets(uses []secretUse, r *report) {
	for _, s := range secretOwners {
		var holders []string
		for _, u := range uses {
			if u.key == s.key && !slices.Contains(holders, u.workload) {
				holders = append(holders, u.workload)
			}
		}
		pass := true
		for _, h := range holders {
			if !slices.Contains(s.owners, h) {
				r.check(false, "%s · %s 받음(받는 곳은 %v 만)", h, s.key, s.owners)
				pass = false
			}
		}
		if !slices.Contains(holders, s.owners[0]) {
			r.check(false, "%s · %s 없음", s.owners[0], s.key)
			pass = false
		}
		if pass {
			r.check(true, "%s 받는 곳 %v", s.key, holders)
		}
	}
	for _, key := range keycloakOptional {
		pass := true
		for _, u := range uses {
			if u.workload == keycloakName && u.key == key && !u.optional {
				r.check(false, "%s · %s 를 반드시 있는 secret 으로 받음(키가 없으면 기동 실패)", keycloakName, key)
				pass = false
			}
		}
		if pass {
			r.check(true, "%s · %s 없어도 기동", keycloakName, key)
		}
	}
}

func load(r io.Reader) ([]secretUse, error) {
	var uses []secretUse
	reader := utilyaml.NewYAMLReader(bufio.NewReader(r))
	for {
		doc, err := reader.Read()
		if errors.Is(err, io.EOF) {
			return uses, nil
		}
		if err != nil {
			return nil, err
		}

		var head struct {
			Kind string `json:"kind"`
		}
		if err := yaml.Unmarshal(doc, &head); err != nil {
			return nil, err
		}
		switch head.Kind {
		case "Deployment":
			var d appsv1.Deployment
			if err := yaml.Unmarshal(doc, &d); err != nil {
				return nil, err
			}
			uses = append(uses, secretUses(d.Name, d.Spec.Template.Spec)...)
		case "StatefulSet":
			var s appsv1.StatefulSet
			if err := yaml.Unmarshal(doc, &s); err != nil {
				return nil, err
			}
			uses = append(uses, secretUses(s.Name, s.Spec.Template.Spec)...)
		}
	}
}

// secret 볼륨 항목과 secretKeyRef 환경변수로 받는 키
func secretUses(name string, spec corev1.PodSpec) []secretUse {
	var uses []secretUse
	for _, v := range spec.Volumes {
		if v.Secret == nil {
			continue
		}
		optional := v.Secret.Optional != nil && *v.Secret.Optional
		for _, item := range v.Secret.Items {
			uses = append(uses, secretUse{name, item.Key, optional})
		}
	}
	for _, c := range append(slices.Clone(spec.InitContainers), spec.Containers...) {
		for _, e := range c.Env {
			if e.ValueFrom == nil || e.ValueFrom.SecretKeyRef == nil {
				continue
			}
			ref := e.ValueFrom.SecretKeyRef
			uses = append(uses, secretUse{name, ref.Key, ref.Optional != nil && *ref.Optional})
		}
	}
	return uses
}
