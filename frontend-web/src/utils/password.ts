// 서버와 같은 비밀번호 규칙(영문 · 숫자 · 특수문자 하나 이상, 8 ~ 100자)
export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,100}$/;

export const PASSWORD_RULE_MESSAGE = '비밀번호는 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.';

// 입력 칸 안내 문구
export const PASSWORD_HINT = '영문, 숫자, 특수문자 포함 8자 이상';
