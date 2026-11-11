// 범례 항목 하나(선 · 점선 · 점 모양과 색)
export interface LegendItem {
  name: string;
  color: string;
  shape: 'line' | 'dashed' | 'dot';
}

const SWATCH: Record<LegendItem['shape'], string> = {
  line: 'inline-block w-4 h-0.5',
  dashed: 'inline-block w-4 border-t-2 border-dashed',
  dot: 'inline-block w-2.5 h-2.5 rounded-full',
};

// 차트 아래 범례 한 줄(좁은 화면은 줄바꿈)
export const ChartLegend = ({ items }: { items: LegendItem[] }) => (
  <ul aria-label="차트 범례" className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-tx-2">
    {items.map((item) => (
      <li key={item.name} className="flex items-center gap-1.5">
        <span
          aria-hidden="true"
          className={SWATCH[item.shape]}
          style={item.shape === 'dashed' ? { borderColor: item.color } : { backgroundColor: item.color }}
        />
        {item.name}
      </li>
    ))}
  </ul>
);
