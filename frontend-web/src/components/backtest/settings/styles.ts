// 백테스트 설정 칸의 고르기 단추 모양(주황은 모드 · 실행 단추만, 나머지 고른 상태는 중립색)

// 모드 고르기
export const MODE_OPTION = (checked: boolean) =>
  `min-h-10 sm:min-h-0 rounded-full border px-4 py-[7px] text-sm transition-colors ${
    checked ? 'border-brand bg-brand-bg text-brand font-semibold' : 'border-line-strong text-tx-2 hover:text-tx-1'
  }`;

// 칸 이름 줄 오른쪽의 작은 고르기 묶음(금액 | 주식 수, 주 | 달 | 년)
export const SEG_GROUP = 'inline-flex shrink-0 overflow-hidden rounded-full border border-line-strong';
export const SEG_OPTION = (checked: boolean) =>
  `min-h-9 sm:min-h-6 px-2.5 py-[3px] text-xs whitespace-nowrap ${
    checked ? 'bg-elevated text-tx-1 font-semibold' : 'text-tx-2 hover:text-tx-1'
  }`;

// 칸 안의 둥근 고르기 단추(주기 · 방향)
export const PILL = (checked: boolean) =>
  `inline-flex min-h-9 sm:min-h-7 items-center gap-1 rounded-full border px-3 py-[3px] text-[13px] transition-colors ${
    checked ? 'border-tx-2 bg-elevated text-tx-1 font-semibold' : 'border-line-strong text-tx-2 hover:text-tx-1'
  }`;

// 칸 안 고정 글자 · 단위
export const LEAD = 'text-tx-2';
export const UNIT = 'text-sm text-tx-2';

// 값 줄(입력 · 단위 · 오른쪽 읽기 쉬운 금액)
export const VALUE_ROW = 'flex min-w-0 flex-wrap items-center gap-[3px] text-base sm:text-[15px] text-tx-1';

// 칸 오른쪽 끝 읽기 쉬운 금액 · 회사 이름
export const AFTER_VALUE = 'ml-auto pl-2.5 text-[13px] text-tx-2 whitespace-nowrap';
