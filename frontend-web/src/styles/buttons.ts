// 흰 글자를 올리는 버튼 바탕 색(테마의 danger · success 토큰은 어두운 테마에서 밝아 흰 글자 대비가 모자람)
export const SOLID_FILL = {
  danger: 'bg-red-600 text-white',
  confirm: 'bg-green-700 text-white',
  info: 'bg-blue-600 text-white',
  accent: 'bg-purple-600 text-white',
} as const;

// 위 바탕 색 버튼(마우스를 올리면 한 단계 진하게)
export const SOLID_BUTTON = {
  danger: `${SOLID_FILL.danger} hover:bg-red-700`,
  confirm: `${SOLID_FILL.confirm} hover:bg-green-800`,
  info: `${SOLID_FILL.info} hover:bg-blue-700`,
  accent: `${SOLID_FILL.accent} hover:bg-purple-700`,
} as const;
