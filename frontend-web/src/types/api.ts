export interface ApiResponse<T> {
  statusCode: number;
  statusMsg: string;
  data: T;
}
