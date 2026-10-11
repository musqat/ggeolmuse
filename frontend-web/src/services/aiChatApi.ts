import axios from 'axios';
import { attachTokenRefresh } from './api';

const API_BASE = (import.meta.env.VITE_API_URL ?? '').trim() || '';

const client = axios.create({ baseURL: API_BASE, timeout: 60000 });

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 401 이면 메인 클라이언트와 같은 방식으로 토큰을 갱신하고 다시 보낸다
attachTokenRefresh(client);

interface ChatResponse {
  answer: string;
  remaining: number;
  symbol: string | null;
}

export const aiChatApi = {
  // symbol 지정 시(차트 분석) 백엔드가 mini 추출 생략하고 그 종목 분석
  sendMessage: (message: string, symbol?: string) =>
    client.post<ChatResponse>('/chat', symbol ? { message, symbol } : { message }),
};
