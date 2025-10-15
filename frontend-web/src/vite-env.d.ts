interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_KEYCLOAK_URL: string
  readonly VITE_KEYCLOAK_REALM: string
  readonly VITE_KEYCLOAK_CLIENT_ID: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// CSS 모듈 타입 정의
declare module "*.css" {
  const content: { [className: string]: string };
  export default content;
}
