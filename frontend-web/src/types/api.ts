export interface ApiResponse<T> {
  statusCode: number;
  statusMsg: string;
  data: T;
}

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
}

export interface PaginationParams {
  page: number;
  size: number;
}