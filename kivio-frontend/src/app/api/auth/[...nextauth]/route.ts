import { handlers } from '@/auth'

// NextAuth v5 の Route Handler（GET/POST）を `/api/auth/*` に公開する。
// これが無いと next-auth/react の signIn('google') が遷移先を見つけられず`/api/auth/error` で 404 になる。
export const { GET, POST } = handlers
