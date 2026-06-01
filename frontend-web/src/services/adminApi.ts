import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 디버깅을 위해 임시로 리다이렉트 비활성화
    // if (error.response?.status === 401) {
    //   localStorage.removeItem('token');
    //   window.location.href = '/';
    // }
    console.error('Admin API Error:', error.response?.status, error.response?.data);
    return Promise.reject(error);
  }
);

// ==================== Market Admin APIs ====================

export interface Asset {
  symbol: string;
  name: string;
  country: string;
  currency: string;
  assetType: string;
  marketCap?: number;
  active?: boolean;
  delistedDate?: string;
  currentPrice?: number;
  latestDataDate?: string;
}

export interface BulkDeleteResponse {
  requested: number;
  deleted: number;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export interface CompanyOverview {
  symbol: string;
  name: string;
  country: string;
  currency: string;
  assetType: string;
  exchange: string;
  sector: string;
  industry: string;
  marketCap: number;
  description: string;
}

export interface CreateAssetRequest {
  symbol: string;
  name?: string;
  country?: string;
  currency?: string;
  assetType?: string;
  collectData: boolean;
  fromDate: string;
  toDate: string;
  includeDividends: boolean;
}

export const marketAdminApi = {
  searchAssets: async (keyword: string): Promise<Asset[]> => {
    const { data } = await api.get<Asset[]>('/admin/market/assets/search', {
      params: { keyword },
    });
    return data;
  },

  previewAsset: async (symbol: string): Promise<CompanyOverview> => {
    const { data } = await api.get<CompanyOverview>(`/admin/market/assets/preview/${symbol}`);
    return data;
  },

  getAllAssets: async (): Promise<Asset[]> => {
    const { data } = await api.get<Asset[]>('/admin/market/assets');
    return data;
  },

  getAllAssetSummaries: async (
    page = 0,
    size = 20,
    sortBy = 'symbol',
    direction = 'asc'
  ): Promise<PageResponse<Asset>> => {
    const { data } = await api.get<PageResponse<Asset>>('/admin/market/assets/summary', {
      params: { page, size, sortBy, direction },
    });
    return data;
  },

  createAsset: async (request: CreateAssetRequest): Promise<void> => {
    await api.post('/admin/market/assets', request);
  },

  deleteAsset: async (symbol: string): Promise<void> => {
    await api.delete(`/admin/market/assets/${symbol}`);
  },

  bulkDeleteAssets: async (symbols: string[]): Promise<BulkDeleteResponse> => {
    const { data } = await api.post<BulkDeleteResponse>(
      '/admin/market/assets/bulk-delete',
      { symbols }
    );
    return data;
  },

  updateAssetPrice: async (symbol: string): Promise<void> => {
    await api.post(`/admin/market/assets/${symbol}/update-price`);
  },

  updateAssetMarketCap: async (symbol: string): Promise<void> => {
    await api.post(`/admin/market/assets/${symbol}/update-market-cap`);
  },

  updateAllPrices: async (): Promise<void> => {
    await api.post('/admin/market/update/candles');
  },

  updateAllMarketCaps: async (): Promise<void> => {
    await api.post('/admin/market/update/market-cap');
  },
};

// ==================== User Admin APIs ====================

export interface UserSummary {
  userId: number;
  email: string;
  username: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface UserStats {
  totalUsers: number;
  activeUsers: number;
  inactiveUsers: number;
  adminUsers: number;
}

export interface AccountSummary {
  accountId: number;
  accountName: string;
  balanceKrw: number;
  balanceUsd: number;
  createdAt: string;
}

export interface UserDetail {
  userId: number;
  email: string;
  username: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
  lastLoginAt: string | null;
  accounts: AccountSummary[];
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export const userAdminApi = {
  getUsers: async (page = 0, size = 20, sort = 'createdAt,desc'): Promise<PageResponse<UserSummary>> => {
    const { data } = await api.get<PageResponse<UserSummary>>('/admin/users', {
      params: { page, size, sort },
    });
    return data;
  },

  getUserDetail: async (userId: number): Promise<UserDetail> => {
    const { data } = await api.get<UserDetail>(`/admin/users/${userId}`);
    return data;
  },

  getUserStats: async (): Promise<UserStats> => {
    const { data } = await api.get<UserStats>('/admin/users/stats');
    return data;
  },

  updateUserRole: async (userId: number, role: 'USER' | 'ADMIN'): Promise<void> => {
    await api.patch(`/admin/users/${userId}/role`, { role });
  },

  updateUserEnabled: async (userId: number, enabled: boolean): Promise<void> => {
    await api.patch(`/admin/users/${userId}/enabled`, { enabled });
  },

  // 강제 닉네임 변경
  updateNickname: async (userId: number, nickname: string): Promise<UserSummary> => {
    const { data } = await api.patch<UserSummary>(`/admin/users/${userId}/nickname`, { nickname });
    return data;
  },

  // 강제 비밀번호 변경
  updatePassword: async (userId: number, newPassword: string): Promise<void> => {
    await api.patch(`/admin/users/${userId}/password`, { newPassword });
  },

  // 이메일 강제 인증
  verifyEmail: async (userId: number): Promise<UserSummary> => {
    const { data } = await api.patch<UserSummary>(`/admin/users/${userId}/verify-email`);
    return data;
  },

  // 강제 탈퇴
  deleteUser: async (userId: number): Promise<void> => {
    await api.delete(`/admin/users/${userId}`);
  },
};

export default api;
