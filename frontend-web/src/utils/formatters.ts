export const formatPrice = (price?: number): string => {
  if (!price || price === 0) {
    return 'N/A';
  }
  return price.toFixed(4);
};

export const formatPriceShort = (price: number): string => {
  return price.toFixed(2);
};

export const formatChange = (change?: number): string => {
  if (!change && change !== 0) {
    return 'N/A';
  }
  const formatted = change.toFixed(2);
  return change >= 0 ? `+${formatted}` : formatted;
};

export const formatPercentage = (percentage?: number): string => {
  if (!percentage && percentage !== 0) {
    return 'N/A';
  }
  const formatted = percentage.toFixed(2);
  return percentage >= 0 ? `+${formatted}%` : `${formatted}%`;
};

export const formatVolume = (volume: number): string => {
  if (volume >= 1000000) {
    return `${(volume / 1000000).toFixed(1)}M`;
  } else if (volume >= 1000) {
    return `${(volume / 1000).toFixed(1)}K`;
  }
  return volume.toLocaleString();
};

export const formatMarketCap = (marketCap?: number): string => {
  if (!marketCap || marketCap === 0) {
    return 'N/A';
  }

  if (marketCap >= 1000000000000) {
    return `$${(marketCap / 1000000000000).toFixed(2)}T`;
  } else if (marketCap >= 1000000000) {
    return `$${(marketCap / 1000000000).toFixed(2)}B`;
  } else if (marketCap >= 1000000) {
    return `$${(marketCap / 1000000).toFixed(2)}M`;
  } else if (marketCap >= 1000) {
    return `$${(marketCap / 1000).toFixed(2)}K`;
  }
  return `$${marketCap.toLocaleString()}`;
};