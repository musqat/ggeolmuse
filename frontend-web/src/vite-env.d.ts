interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_KEYCLOAK_URL: string
  readonly VITE_KEYCLOAK_REALM: string
  readonly VITE_KEYCLOAK_CLIENT_ID: string
  // 개발 서버 · 테스트면 true(Vite 가 넣음)
  readonly DEV: boolean
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// CSS 모듈 타입 정의
declare module "*.css" {
  const content: { [className: string]: string };
  export default content;
}
