import type { NextConfig } from 'next'

// `/api/v1/*` は BFF Route Handler（src/app/api/v1/[...path]）が処理する。
// 認証 Cookie → Bearer の詰め替え・トークンの Cookie 化が必要なため、透過 rewrite は使わない。
const nextConfig: NextConfig = {
  output: 'standalone',
  images: {
    dangerouslyAllowSVG: true,
    contentSecurityPolicy: "default-src 'self'; script-src 'none'; sandbox;",
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'res.cloudinary.com',
        pathname: '/**',
      },
    ],
  },
}

export default nextConfig
