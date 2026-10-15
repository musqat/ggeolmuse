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

export interface StartConversationResponse {
  conversation_id: string;
  symbol: string;
  name: string | null;
  remaining: number;
}

export interface AskResponse {
  answer: string;
  remaining: number;
  conversation_id: string;
  symbol: string;
  // 거절 이유(off_topic · other_symbol · insufficient_data). 답이면 null
  rejected: string | null;
}

export interface QuotaResponse {
  remaining: number;
}

export const aiChatApi = {
  // 종목을 확인하고 대화를 연다. 한도는 쓰지 않는다
  startConversation: (symbol: string) =>
    client.post<StartConversationResponse>('/chat/conversations', { symbol }),
  ask: (conversationId: string, message: string) =>
    client.post<AskResponse>('/chat', { conversation_id: conversationId, message }),
  // 오늘 남은 질문 수. 한도는 쓰지 않는다
  quota: () => client.get<QuotaResponse>('/chat/quota'),
};
