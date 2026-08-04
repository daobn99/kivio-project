export interface ProductListParams {
  q?: string
  categoryId?: string
  shopId?: string
  page?: number
  size?: number
  /** ソート指定。形式: `{field},{direction}`（例: `price,asc`）。未指定はバックエンドのデフォルト順 */
  sort?: string
}

export interface SellerProductListParams {
  page?: number
  size?: number
  status?: string
}

export interface OrderListParams {
  status?: string
  page?: number
  size?: number
}

export const queryKeys = {
  products: {
    all: ['products'] as const,
    list: (params?: ProductListParams) => ['products', 'list', params] as const,
    detail: (id: string) => ['products', 'detail', id] as const,
    reviews: (id: string) => ['products', 'detail', id, 'reviews'] as const,
  },
  shops: {
    all: ['shops'] as const,
    detail: (id: string) => ['shops', 'detail', id] as const,
  },
  cart: {
    detail: ['cart'] as const,
  },
  orders: {
    all: ['orders'] as const,
    list: (params?: OrderListParams) => ['orders', 'list', params] as const,
    detail: (id: string) => ['orders', 'detail', id] as const,
  },
  seller: {
    stats: ['seller', 'stats'] as const,
    products: (params?: SellerProductListParams) => ['seller', 'products', params] as const,
    orders: (params?: OrderListParams) => ['seller', 'orders', params] as const,
  },
  user: {
    me: ['user', 'me'] as const,
    addresses: ['user', 'me', 'addresses'] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    list: (params?: { page?: number; size?: number }) => ['notifications', 'list', params] as const,
  },
  wishlist: {
    all: ['wishlist'] as const,
  },
  sellerApplication: {
    me: ['seller-application', 'me'] as const,
  },
} as const
