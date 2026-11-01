import React, { useState } from 'react';

// FMP 그림 주소(점 · 빗금이 든 티커는 FMP 식 하이픈으로, BRK.B → BRK-B)
const logoUrl = (symbol: string) =>
  `https://financialmodelingprep.com/image-stock/${encodeURIComponent(symbol.replace(/[./]/g, '-'))}.png`;

// 종목 로고(못 받으면 첫 글자 상자)
export const StockLogo: React.FC<{ symbol: string }> = ({ symbol }) => {
  const [failed, setFailed] = useState(false);

  if (failed) {
    return (
      <div className="w-6 h-6 bg-brand-bg rounded flex items-center justify-center">
        <span className="text-xs font-bold text-brand">{symbol.charAt(0)}</span>
      </div>
    );
  }

  return (
    <img
      src={logoUrl(symbol)}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      className="w-6 h-6 rounded"
      onError={() => setFailed(true)}
    />
  );
};
