import { setupServer } from 'msw/node'
import { authHandlers } from './handlers/auth'
import { userHandlers } from './handlers/users'
import { addressHandlers } from './handlers/addresses'
import { sellerApplicationHandlers } from './handlers/sellerApplications'
// 機能の実装に合わせてハンドラを追加する（products / cart 等は当該フェーズで追加）

export const server = setupServer(
  ...authHandlers,
  ...userHandlers,
  ...addressHandlers,
  ...sellerApplicationHandlers,
)
