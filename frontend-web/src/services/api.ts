import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { Stock, StockPrice } from '../types/stock';
import type { Portfolio } from '../types/portfolio';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8070';

const apiClient = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
});

// 요청 인터셉터 (JWT 토큰 추가)
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const stockApi = {
  getCurrentPrice: (symbol: string) =>
    apiClient.get<ApiResponse<StockPrice>>(`/api/market/price/${symbol}`),

  getOHLCData: (symbol: string, startDate?: string, endDate?: string) =>
    apiClient.get(`/api/market/ohlc/${symbol}`, {
      params: { startDate, endDate }
    }),
};

export const portfolioApi = {
  getPortfolio: () =>
    apiClient.get<ApiResponse<Portfolio>>('/api/portfolio'),
};