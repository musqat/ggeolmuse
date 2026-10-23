// 떠 있는 요소의 쌓임 순서(큰 값이 위, 헤더는 z-50 클래스)
export const LAYER = {
  chatButton: 9000, // AI 채팅 버튼
  popover: 9050, // 달력 · 지표 시트
  modal: 9999, // 창(로그인 · 마이페이지 · 계좌 · 관리자 · 검색 · AI 채팅)
  confirm: 10000, // 확인 창, 창 안에서 띄워도 그 위
  toast: 10001, // 알림, 확인 창 위
} as const;
