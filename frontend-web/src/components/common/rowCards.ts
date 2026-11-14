// 휴대폰 너비(sm 아래)에서는 줄마다 카드, sm 부터는 표 그대로인 클래스(카드 안 칸 이름은 칸의 data-label, 글자 크기 · 줄 사이 선 · 칸 여백은 표마다 sm: 로)

export const ROW_CARD_TABLE = 'block w-full sm:table';

export const ROW_CARD_HEAD = 'hidden sm:table-header-group';

export const ROW_CARD_BODY = 'block space-y-2 sm:table-row-group sm:space-y-0';

export const ROW_CARD_ROW = 'block rounded-lg border border-line/70 px-3 py-2 sm:table-row sm:rounded-none sm:border-0 sm:p-0';

// 카드 제목 칸(첫 칸)
export const ROW_CARD_TITLE = 'block pb-1 text-left sm:table-cell';

// 카드에서는 왼쪽에 칸 이름 · 오른쪽에 값
export const ROW_CARD_CELL =
  'flow-root py-0.5 text-right before:float-left before:mr-3 before:text-tx-2 before:content-[attr(data-label)] sm:table-cell sm:before:content-none';

// 카드 테두리 없는 줄 · 칸(줄이 없을 때 안내 등)
export const ROW_CARD_PLAIN_ROW = 'block sm:table-row';
export const ROW_CARD_PLAIN_CELL = 'block sm:table-cell';
