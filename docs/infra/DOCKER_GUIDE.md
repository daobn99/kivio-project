# Docker ガイド

> 関連ファイル: `docker-compose.yml`、`kivio-backend/Dockerfile`、`kivio-frontend/Dockerfile`  
> 関連ドキュメント: [CI_CD.md](./CI_CD.md)

---

## 目次

1. [このプロジェクトでの Docker の使い方](#1-このプロジェクトでの-docker-の使い方)
2. [ローカル開発（docker-compose.yml）](#2-ローカル開発docker-composeyml)
3. [Backend Dockerfile](#3-backend-dockerfile)
4. [Frontend Dockerfile](#4-frontend-dockerfile)
5. [.dockerignore](#5-dockerignore)

---

## 1. このプロジェクトでの Docker の使い方

Docker を使う場面は 2 種類あります。

| 場面 | 使うもの | 目的 |
|---|---|---|
| ローカル開発 | `docker-compose.yml` | PostgreSQL・バックエンド・フロントエンドをまとめて起動する |
| CI/CD（本番イメージ作成） | `kivio-backend/Dockerfile`、`kivio-frontend/Dockerfile` | CD ワークフローが ghcr.io にプッシュするイメージを作成する |

`.devcontainer/` は VS Code / GitHub Codespaces の開発環境定義で、上記とは別の用途です。

---

## 2. ローカル開発（docker-compose.yml）

### サービス構成

```
kivio-net（内部ネットワーク）
  ├─ db        postgres:17-alpine     :5432
  ├─ backend   kivio-backend ビルド   :8080   ← db が healthy になってから起動
  └─ frontend  kivio-frontend ビルド  :3000   ← backend が healthy になってから起動
```

起動順序は `depends_on` + `condition: service_healthy` で制御しています。

- `db` の healthcheck: `pg_isready` が成功するまで backend は起動しない
- `backend` の healthcheck: `/api/v1/health` が 200 を返すまで frontend は起動しない

### よく使うコマンド

```bash
# 全サービスをビルドして起動（初回・Dockerfile 変更後）
docker compose up --build

# 全サービスを起動（イメージが既にある場合）
docker compose up

# バックグラウンドで起動
docker compose up -d

# DB のみ起動（バックエンドをローカルで直接起動したい場合）
docker compose up -d db

# ログを確認する（Ctrl+C で終了）
docker compose logs -f

# 特定サービスのログだけ見る
docker compose logs -f backend

# 停止（コンテナとネットワークを削除）
docker compose down

# 停止 + ボリューム（DB データ）も削除
docker compose down -v
```

### 環境変数

機密情報はデフォルト値の代わりに環境変数で上書きできます。本番環境では必ず上書きしてください。

```bash
# プロジェクトルートに .env ファイルを作成する（docker compose が自動で読み込む）
# .env は .gitignore に含まれており、リポジトリにはコミットされない
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
echo "AUTH_SECRET=$(openssl rand -base64 32)" >> .env
```

| 変数名 | デフォルト値 | 用途 |
|---|---|---|
| `JWT_SECRET` | `local-development-secret-at-least-32-bytes-long` | JWT 署名キー |
| `AUTH_SECRET` | `local-development-nextauth-secret` | Next Auth セッション暗号化キー |
| `GOOGLE_CLIENT_ID` | `dummy` | Google OAuth クライアント ID |
| `GOOGLE_CLIENT_SECRET` | `dummy` | Google OAuth クライアントシークレット |

### API_BASE_URL の設計

フロントエンドの API 呼び出しには 2 つの経路があります。

| 経路 | 環境変数 | 値 | 用途 |
|---|---|---|---|
| サーバーサイド（SSR / Server Actions） | `API_BASE_URL` | `http://backend:8080` | Docker 内部ネットワーク経由。コンテナ名 `backend` で解決できる |
| ブラウザ（クライアントサイド） | `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | ホストマシンから backend コンテナへのアクセス |

---

## 3. Backend Dockerfile

`kivio-backend/Dockerfile`

### マルチステージビルドの構成

```
builder ステージ（eclipse-temurin:25-jdk-noble）
  ├─ Gradle Wrapper・依存関係を先に COPY してキャッシュ層を作る
  ├─ ソースコードを COPY
  └─ ./gradlew bootJar でビルド → build/libs/*.jar を生成

runner ステージ（eclipse-temurin:25-jre-noble）← 最終イメージ
  ├─ curl をインストール（docker compose の healthcheck で使用）
  ├─ 専用ユーザー spring（UID 1001）を作成しルート権限で動かさない
  ├─ builder から JAR だけをコピー（ソースコード・ビルドツールは含まない）
  └─ java -jar で起動
```

### 2 つのステージを分ける理由

JDK（Java Development Kit）はコンパイルに必要ですが、ビルド済み JAR を実行するだけなら JRE（Java Runtime Environment）で十分です。

| | JDK ベースイメージ | JRE ベースイメージ |
|---|---|---|
| 用途 | ビルド（builder ステージ） | 本番実行（runner ステージ） |
| サイズ | 約 400MB | 約 200MB |

最終イメージには JRE だけが含まれるため、イメージサイズが削減でき、攻撃対象領域も小さくなります。

### 依存関係キャッシュの仕組み

```dockerfile
# ① 依存関係定義ファイルだけを先に COPY
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies ...   # ← このレイヤーはキャッシュされる

# ② ソースコードを COPY（変更が多い）
COPY src src
RUN ./gradlew bootJar ...
```

Docker はレイヤーを上から順にキャッシュします。`build.gradle.kts` を変更しない限り、依存関係ダウンロードのステップはキャッシュが使われ、ビルドが高速になります。

### 起動オプション

```dockerfile
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Duser.timezone=Asia/Tokyo", \
  "-jar", "/app/app.jar"]
```

| オプション | 目的 |
|---|---|
| `-Djava.security.egd=file:/dev/./urandom` | 乱数生成器をノンブロッキングにして起動時間を短縮する |
| `-Duser.timezone=Asia/Tokyo` | JVM のタイムゾーンを日本時間に固定する |

---

## 4. Frontend Dockerfile

`kivio-frontend/Dockerfile`

### マルチステージビルドの構成

```
deps ステージ（node:24-alpine）
  ├─ corepack enable で pnpm を有効化
  ├─ package.json / pnpm-lock.yaml / pnpm-workspace.yaml を COPY
  └─ pnpm install --frozen-lockfile で node_modules を作成

builder ステージ（node:24-alpine）
  ├─ deps から node_modules をコピー
  ├─ ソースコードをコピー
  └─ pnpm build で Next.js 本番ビルド（.next/standalone を生成）

runner ステージ（node:24-alpine）← 最終イメージ
  ├─ 専用ユーザー nextjs（UID 1001）を作成
  ├─ builder から standalone / static / public だけをコピー
  └─ node server.js で起動
```

### standalone 出力とは

`next.config.ts` に `output: "standalone"` を設定することで、Next.js は実行に必要なファイルだけを `.next/standalone/` に出力します。`node_modules` 全体をコピーする必要がなく、最終イメージを大幅に小さくできます。

最終イメージにコピーするのは以下の 3 つだけです。

| コピー元 | 内容 |
|---|---|
| `.next/standalone/` | サーバー起動に必要な最小限のファイル（`server.js` 含む） |
| `.next/static/` | CSS・JS・画像などの静的アセット |
| `public/` | `public/` ディレクトリ内の静的ファイル |

### deps ステージを分ける理由

`node_modules` のインストールは時間がかかります。`package.json` を変更しない限り deps ステージのキャッシュが使われるため、ソースコードのみを変更した場合のビルドが速くなります。

---

## 5. .dockerignore

各サービスの `.dockerignore` は Docker build コンテキスト（`docker build` に渡すファイル群）から不要なファイルを除外します。除外することで以下の効果があります。

- `docker build` 時の転送量を減らしてビルドを高速化する
- `.env` などの機密ファイルがイメージに混入するのを防ぐ

### kivio-backend/.dockerignore の主な除外対象

| 除外パターン | 理由 |
|---|---|
| `build/` | ビルド済み成果物（Dockerfile 内で再ビルドする） |
| `.gradle/` | Gradle のローカルキャッシュ |
| `**/*.class` | コンパイル済みクラスファイル |
| `.idea/`, `*.iml` | IDE の設定ファイル |

### kivio-frontend/.dockerignore の主な除外対象

| 除外パターン | 理由 |
|---|---|
| `node_modules/` | Dockerfile 内で `pnpm install` するため不要 |
| `.next/` | Dockerfile 内でビルドするため不要 |
| `.env`, `.env.*` | 機密情報の混入防止（`.env.example` は除外しない） |
| `tests/`, `e2e/`, `coverage/` | テスト関連ファイルは本番イメージに不要 |
