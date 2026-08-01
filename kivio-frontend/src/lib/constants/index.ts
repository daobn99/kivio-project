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
