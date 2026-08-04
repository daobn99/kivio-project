/** バックエンドの PageResponse<T> と同形のページネーションレスポンス */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}
