export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

export const ROUTES = {
  home: '/',
  search: '/search',
  product: (id: string) => `/products/${id}`,
  shop: (id: string) => `/shops/${id}`,

  auth: {
    login: '/auth/login',
    register: '/auth/register',
  },

  buyer: {
    cart: '/cart',
    checkout: '/checkout',
    orders: '/orders',
    order: (id: string) => `/orders/${id}`,
    wishlist: '/wishlist',
  },

  seller: {
    dashboard: '/seller/dashboard',
    /** 申請フォーム兼・審査状況の確認画面（1 URL で 4 状態を出し分ける） */
    applicationNew: '/seller/applications/new',
    /**
     * 申請が不要なユーザー（SELLER / ADMIN / 承認済み）の送り先。
     * `/seller/dashboard` が未実装のため暫定でトップページ。実装後はこの 1 箇所を
     * `dashboard` に差し替えれば 3 経路すべてが切り替わる。
     */
    applicationRedirect: '/',
    products: '/seller/products',
    newProduct: '/seller/products/new',
    editProduct: (id: string) => `/seller/products/${id}/edit`,
    orders: '/seller/orders',
  },

  admin: {
    users: '/admin/users',
    sellerApplications: '/admin/seller-applications',
  },
} as const
