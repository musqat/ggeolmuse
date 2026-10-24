// 서버와 같은 닉네임 규칙(2 ~ 20자, 영문 · 숫자 · 한글 · _)
export const NICKNAME_RULE = /^[a-zA-Z0-9가-힣_]{2,20}$/;

export const NICKNAME_RULE_MESSAGE = '닉네임은 2 ~ 20자의 영문, 숫자, 한글, _ 만 쓸 수 있습니다.';

// 입력 칸 안내 문구
export const NICKNAME_HINT = '2 ~ 20자, 영문 · 숫자 · 한글 · _';
