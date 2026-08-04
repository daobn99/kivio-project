# Git ワークフロー

## ブランチ戦略

| ブランチ | 役割 | 直接プッシュ |
|---|---|---|
| `main` | 本番相当。タグ付きリリースのみ反映 | 禁止 |
| `develop` | 結合ブランチ。全 feature の統合先 | 禁止 |
| `feature/<name>` | 機能開発。1 タスク = 1 ブランチ | 作業者のみ |

```
main
 └── develop
      ├── feature/auth
      ├── feature/catalog
      └── feature/user-profile
```

## ブランチ命名規則

```
feature/<kebab-case-name>   # 機能開発
fix/<kebab-case-name>       # バグ修正
chore/<kebab-case-name>     # 設定・依存関係・ドキュメント
```

例:
- `feature/auth`
- `feature/product-search`
- `fix/cart-quantity-overflow`
- `chore/update-dependencies`

## PR ルール

- **マージ先は `develop` のみ** — `main` への直接 PR 禁止
- **セルフマージ禁止** — 必ず 1 名以上のレビュアーの Approve を得る
- **CI グリーン必須** — GitHub Actions が全ステップ通過するまでマージ不可
- PR タイトルは Conventional Commits 形式に準拠する（後述）
- WIP の場合は Draft PR として作成する

### `develop` → `main` マージ
リリース時はシニアが実施。通常の開発フローでは操作しない。

## コミットメッセージ規約（Conventional Commits）

```
<type>(<scope>): <subject>
```

### type 一覧

| type | 用途 |
|---|---|
| `feat` | 新機能 |
| `fix` | バグ修正 |
| `chore` | ビルド・依存関係・設定変更（プロダクションコードに影響なし） |
| `docs` | ドキュメントのみの変更 |
| `style` | フォーマット・セミコロン等（ロジック変更なし） |
| `refactor` | リファクタリング（機能追加・バグ修正なし） |
| `test` | テストの追加・修正 |
| `ci` | CI/CD 設定の変更 |

### scope（任意）

対象ドメインまたはレイヤーを小文字で記述する。

例: `auth`, `catalog`, `order`, `cart`, `frontend`, `db`

### 例

```
feat(auth): JWTアクセストークン発行エンドポイントを追加
fix(cart): 数量更新時のオーバーフローを修正
chore(deps): TanStack Query v5 をインストール
docs: GIT_WORKFLOW.md を追加
test(auth): リフレッシュトークンローテーションのテストを追加
```

### ルール

- subject は命令形・現在形で記述（「追加した」ではなく「追加」）
- subject の末尾にピリオド不要
- 1 コミット = 1 つの論理的変更
- 自動検証ツールは導入しない（一人開発のため）。本規約を意識して手動で準拠する

## ブランチ操作フロー

```bash
# feature ブランチを develop から切る
git switch develop
git pull origin develop
git switch -c feature/<name>

# 作業・コミット
git add <files>
git commit -m "feat(<scope>): <subject>"

# PR を出す前に develop の最新を取り込む
git fetch origin
git rebase origin/develop

# GitHub で PR を作成（base: develop）
```

## バーティカルスライス タスク一覧

フェーズ 0〜4 完了後、以下の単位で feature ブランチを切る。

| # | ブランチ名 | 概要 | Phase |
|---|---|---|---|
| 1 | `feature/auth` | 登録・ログイン・Google OAuth | 2 |
| 2 | `feature/user-profile` | プロフィール・住所管理 | 2 |
| 3 | `feature/seller-application` | 出品者申請 | 2 |
| 4 | `feature/catalog` | ショップ・商品 CRUD | 2 |
| 5 | `feature/product-search` | 商品検索・一覧 | 3 |
| 6 | `feature/cart` | カート操作 | 3 |
| 7 | `feature/checkout` | チェックアウト・注文・Stripe | 3 |
| 8 | `feature/orders` | 注文履歴・ステータス | 3 |
| 9 | `feature/chat` | WebSocket チャット | 4 |
| 10 | `feature/notification` | 通知 | 4 |
| 11 | `feature/admin` | 管理者機能 | 4 |
| 12 | `feature/review-wishlist` | レビュー・ウィッシュリスト | 5 |

> **優先順位:** タスク 1〜4 を Phase 2 の MVP として先行する。
