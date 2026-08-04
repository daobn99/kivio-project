# デプロイ手順書（手動デプロイ / Phase 2）

> 関連ドキュメント: [CI_CD.md](./CI_CD.md)、[DOCKER_GUIDE.md](./DOCKER_GUIDE.md)、[REQUIREMENTS.md §6.3](../requirements/REQUIREMENTS.md)
> 対象: Phase 2 時点（Auth / User / SellerApplication まで実装済み）のアプリケーションを本番相当環境へ公開する

---

## 目次

1. [このドキュメントの位置づけ](#1-このドキュメントの位置づけ)
2. [環境戦略とデプロイ構成](#2-環境戦略とデプロイ構成)
3. [prod プロファイルの構成と事前確認](#3-prod-プロファイルの構成と事前確認)
4. [外部サービスのアカウント準備](#4-外部サービスのアカウント準備)
5. [環境変数一覧](#5-環境変数一覧)
6. [デプロイ手順](#6-デプロイ手順)
7. [スモークテスト](#7-スモークテスト)
8. [再デプロイ・ロールバック](#8-再デプロイロールバック)
9. [トラブルシューティング](#9-トラブルシューティング)
10. [既知の制約](#10-既知の制約)
11. [今後のロードマップ](#11-今後のロードマップ)

---

## 1. このドキュメントの位置づけ

### なぜ「手動」デプロイなのか

CD（`cd.yml`）は現在 **ghcr.io へのイメージ push までしか行っていません**。実際のデプロイ手順はコメントアウトされた雛形のみです。

自動化の前に一度手動で通すことには明確な意味があります。

- 自動化とは「手順の記述」である。手順が分かっていないものは自動化できない
- 初回デプロイは必ず何かが失敗する。CI 経由だと 1 回の試行に数分かかり、原因の切り分けが困難になる
- 本ドキュメント §6 の手順が、そのまま将来の CD 実装の仕様書になる

### 進め方の方針

**Phase 2 の現状のまま（ショップ・商品 CRUD の実装前に）デプロイします。**

Cloudinary 連携は Phase 2 の「4. ショップ・商品 CRUD」で追加されますが、それを待たずに先にデプロイします。理由は、動く状態のアプリを先に本番へ載せておけば、Cloudinary 追加時の障害切り分けが「Cloudinary の設定のみ」に限定されるためです。逆順にすると、DB 接続・CORS・環境変数・Cloudinary のどれが原因か分からなくなります。

---

## 2. 環境戦略とデプロイ構成

### 2-1. 環境戦略: デプロイ先は production のみ

個人開発プロジェクトであるため、**クラウド上に構築する環境は production 1 つだけ**とします。staging / dev 環境は構築しません。

| 環境 | 実行場所 | Spring プロファイル | 役割 |
|---|---|---|---|
| **ローカル** | devcontainer / `docker compose` | `dev` | **開発環境（開発環境の役割はこれが担う）**。Mailpit・Swagger UI・ローカル DB |
| **production** | Vercel + Render | `prod` | 公開環境 |
| （CI） | GitHub Actions | `test` | Testcontainers・`LogEmailSender`。デプロイ先ではない |

**staging を構築しない理由:**

- DB・Redis・アプリインスタンスをすべて二重に持つ必要があり、費用と設定管理が倍になる
- 二重管理された設定は必ず乖離する。乖離した時点で「production を模した環境」という staging の存在意義が失われる
- staging の本来の価値は「実ユーザーへ影響が出る前に検知する」ことだが、現段階では実ユーザーがいない

**staging の代替:**

- **フロントエンド**: Vercel の Preview Deployment が PR ごとに固有 URL を発行するため、マージ前の動作確認はこれで足りる
- **DB マイグレーションの検証**: Neon のブランチ機能で production のコピーを一時的に作成し、検証後に破棄する

> 実ユーザーが付いた時点、または production データのコピーに対してマイグレーションを検証する必要が生じた時点で、staging の構築を再検討します。

### 2-2. ブランチとリリースの関係

```
feature/*  ──PR──▶  develop  ──PR──▶  main  ──▶  CD がイメージ生成  ──▶  手動デプロイ
                    （結合・CI）        （リリース）
```

| ブランチ | 意味 |
|---|---|
| `develop` | 結合ブランチ。「完成したが、まだリリースしていないもの」が溜まる場所。CI は動くが CD は動かない |
| `main` | **production で動いているもの**。ここへのマージ = リリース。CD がイメージを生成する |

`main` を「本番で動いているコード」と一致させておくことで、障害時に `git log main` で稼働中のコードを特定でき、ロールバック先も明確になります。

### 2-3. デプロイ構成

`REQUIREMENTS.md §6.3` で採用した構成に従います。

```
                        ┌──────────────────────────┐
   ブラウザ ────────────▶│  Vercel                  │
                        │  Next.js (Frontend + BFF) │
                        └────────────┬─────────────┘
                                     │ サーバー間通信
                                     │ (API_BASE_URL)
                                     ▼
                        ┌──────────────────────────┐
                        │  Render                  │
                        │  Spring Boot (Backend)   │
                        └──┬────────────┬──────────┘
                           │            │
              ┌────────────▼───┐   ┌────▼─────────────┐
              │ Neon           │   │ Upstash          │
              │ PostgreSQL 17  │   │ Redis (OTP/登録)  │
              └────────────────┘   └──────────────────┘

              外部サービス: Resend（メール）/ Cloudinary（画像・Phase 2 後半）
                            Google OAuth（ソーシャルログイン）
```

### 構成上の重要な特性: BFF によりブラウザは Backend を直接呼ばない

フロントエンドは BFF パターン（`src/app/api/v1/[...path]` の Route Handler）を採用しています。クライアントコンポーネントの fetch は `/api/v1/...` という**相対パス**であり、同一オリジンの Next.js に対して発行されます（`src/lib/api/client/base.ts`）。

この結果、以下が成立します。

| 論点 | 通常構成（ブラウザ→Backend 直接） | 本構成（BFF 経由） |
|---|---|---|
| CORS | Vercel ドメインを許可する設定が必須 | ブラウザから Backend を直接叩かないため実質不要 |
| 認証 Cookie | クロスサイトとなり `SameSite=None; Secure` が必要。ブラウザのサードパーティ Cookie 規制の影響を受ける | Next.js と同一オリジンのため通常の `SameSite=Lax` で動作する |

**認証まわりでデプロイ時にハマる要因の大半を、設計段階で回避できています。** ただし保険として `ALLOWED_ORIGINS` には Vercel の本番ドメインを設定してください（§5）。

---

## 3. prod プロファイルの構成と事前確認

### 3-1. プロファイルごとの実装差し替え

`EmailSender` はプロファイルで実装が切り替わります。インターフェースに対して実装が 3 つあり、`AuthEmailService` はインターフェースだけを見ます。

| プロファイル | 実装 | 送信先 |
|---|---|---|
| `dev` | `SmtpEmailSender` | Mailpit（`localhost:8025` で目視確認） |
| `prod` | `ResendEmailSender` | Resend HTTP API（実送信） |
| その他（`test` 等） | `LogEmailSender`（`!dev & !prod`） | ログ出力のみ |

**ローカル開発では引き続き Mailpit を使います。** Resend の送信枠を消費せず、実在アドレスへ誤送信する事故もなく、オフラインでも動作するためです。

> `ResendEmailSender` はコンストラクタで `RESEND_API_KEY` の設定漏れを検査し、未設定なら起動を失敗させます。本番の設定漏れは「起動はするがメールだけ届かない」という発見の遅い障害になりやすいため、起動時に落とす方が安全です。

### 3-2. Redis の TLS

マネージド Redis（Upstash 等）は TLS 必須が一般的なため、環境変数で切り替えます。

```yaml
spring:
  data:
    redis:
      ssl:
        enabled: ${REDIS_SSL:false}   # ローカルの Docker Redis は平文のため既定 false
```

本番のみ `REDIS_SSL=true` を設定してください。

### 3-3. JVM のヒープ設定

`kivio-backend/Dockerfile` の `ENTRYPOINT` にはメモリオプションを指定していません。JVM の既定ではコンテナ割当メモリの 25% しかヒープに使わないため、小さいインスタンスでは窮屈になります。

Dockerfile は変更せず、環境変数 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` で調整します（§5 に記載済み）。

### 3-4. 事前チェック: ローカルで prod プロファイルを起動する

**クラウドへ上げる前に、必ず手元で `prod` プロファイルの起動を確認してください。** クラウド上の起動失敗はログ取得に時間がかかり、原因特定が遅くなります。手元で落ちるものはクラウドでも落ちます。

devcontainer の DB・Redis をそのまま使って確認できます。

```bash
cd kivio-backend
SPRING_PROFILES_ACTIVE=prod \
  DB_URL='jdbc:postgresql://db:5432/kivio' DB_USERNAME=kivio DB_PASSWORD=password \
  REDIS_HOST=redis REDIS_PORT=6379 REDIS_SSL=false \
  JWT_SECRET="$(openssl rand -base64 32)" \
  RESEND_API_KEY='re_dummy' \
  MAIL_FROM_ADDRESS='noreply@example.com' MAIL_FROM_NAME='Kivio' \
  ALLOWED_ORIGINS='https://example.com' PROBLEM_BASE_URL='https://example.com' \
  ./gradlew bootRun
```

`Started KivioBackendApplication` が出れば構成は正常です。

> **注意: devcontainer には `RESEND_API_KEY=re_` がプレースホルダーとして設定済みです。** そのため API キーの設定漏れをローカルで再現したい場合は、`RESEND_API_KEY=''` と明示的に空を渡す必要があります（環境変数を渡さないだけでは devcontainer の値が使われます）。

### 3-5. 事前チェックリスト

- [ ] `./gradlew build` が通る（テスト含む）
- [ ] ローカルで `SPRING_PROFILES_ACTIVE=prod` の起動を確認した
- [ ] `develop` → `main` の PR をマージした（＝リリース）
- [ ] `main` で CD が完走し、ghcr.io にイメージが push されている

---

## 4. 外部サービスのアカウント準備

| サービス | 用途 | 取得するもの |
|---|---|---|
| Neon | PostgreSQL | 接続文字列（host / db / user / password） |
| Upstash | Redis（OTP・登録セッション） | endpoint / port / password |
| Render | Backend ホスティング | — |
| Vercel | Frontend ホスティング | — |
| Resend | メール送信 | API キー、送信元ドメイン |
| Google Cloud Console | OAuth ログイン | Client ID / Client Secret |
| Cloudinary | 画像（Phase 2 後半） | Cloud name / API key / API secret |

> **料金プランの条件は各社とも変更が頻繁です。** 無料枠の有無・上限・スリープ条件は、必ず作業時点の公式ページで確認してください。本ドキュメントでは特定の数値を前提としません。

### Resend の注意点

独自ドメインを持っていない場合、Resend が提供する検証用ドメインからのみ送信できます。この場合、**送信先が自分のアドレスに限定される**などの制約が付くのが一般的です。第三者に登録フローを試してもらう予定があるなら、独自ドメインの取得と DNS 認証（SPF / DKIM）が必要になります。

`MAIL_FROM_ADDRESS` のデフォルト値は `noreply@kivio.example.com` というプレースホルダのため、**必ず実在する検証済みドメインのアドレスに上書きしてください。**

---

## 5. 環境変数一覧

### 5-1. Backend（Render）

| 変数名 | 値 | 備考 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | |
| `DB_URL` | `jdbc:postgresql://<host>/<db>?sslmode=require` | **JDBC 形式に変換が必要**（§6 STEP 1 参照） |
| `DB_USERNAME` | Neon のユーザー名 | |
| `DB_PASSWORD` | Neon のパスワード | |
| `DB_POOL_SIZE` | `5` | 無料枠 DB は同時接続数の上限が小さいため既定の 10 から絞る |
| `REDIS_HOST` | Upstash のエンドポイント | |
| `REDIS_PORT` | Upstash のポート | |
| `REDIS_PASSWORD` | Upstash のパスワード | |
| `REDIS_SSL` | `true` | マネージド Redis は TLS 必須が一般的（§3-2） |
| `JWT_SECRET` | `openssl rand -base64 32` の出力 | **ローカルの値を流用しない** |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID | |
| `GOOGLE_CLIENT_SECRET` | Google OAuth Client Secret | |
| `ALLOWED_ORIGINS` | `https://<vercel-domain>` | BFF 構成のため実質未使用だが保険として設定 |
| `PROBLEM_BASE_URL` | `https://<vercel-domain>` | ProblemDetail の `type` URI に使用 |
| `MAIL_FROM_ADDRESS` | 検証済みドメインの送信元アドレス | プレースホルダのままにしない |
| `MAIL_FROM_NAME` | `Kivio` | |
| `RESEND_API_KEY` | Resend の API キー | **未設定だと起動に失敗する**（設定漏れの早期検知のため） |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0` | §3-3 |

### 5-2. Frontend（Vercel）

| 変数名 | 値 | 備考 |
|---|---|---|
| `API_BASE_URL` | `https://<render-backend-domain>` | サーバー側 fetch / BFF が使用。**末尾スラッシュなし** |
| `NEXT_PUBLIC_API_BASE_URL` | `https://<render-backend-domain>` | 現状ほぼ未使用だが未設定だと localhost にフォールバックする |
| `AUTH_SECRET` | `openssl rand -base64 32` の出力 | NextAuth 用。`JWT_SECRET` とは別の値にする |
| `AUTH_GOOGLE_ID` | Google OAuth Client ID | Backend と同じ値 |
| `AUTH_GOOGLE_SECRET` | Google OAuth Client Secret | Backend と同じ値 |
| `AUTH_TRUST_HOST` | `true` | 任意。`src/auth.ts` で `trustHost: true` を指定済みのため未設定でも動作する |

> **秘密情報を Git にコミットしないこと。** `.env.local` は `.gitignore` 済みです。値は各プラットフォームの環境変数管理画面にのみ保存します。

---

## 6. デプロイ手順

**下から上へ（依存される側から）構築します。** DB → Redis → Backend → Frontend の順です。逆順にすると、起動しないサービスを前にして原因が特定できません。

### STEP 1: PostgreSQL（Neon）

1. Neon でプロジェクトを作成する

   > **リージョンは「ユーザーに近い場所」ではなく「Backend と同じ場所」を選びます。** DB / Redis はブラウザから直接呼ばれず、Backend からのみ・1 リクエストで複数回呼ばれるため、効くのは Backend との距離です。
   >
   > Render には Tokyo リージョンがないため（Oregon / Ohio / Frankfurt / Singapore）、アジア圏なら **Singapore で 3 つ（Render / Neon / Upstash）を揃える**ことになります。Vercel の Function Region も同じく `sin1` に合わせます（BFF → Backend はサーバー間通信のため）。
   >
   > 各社のリージョン提供状況は変わるため、作業時点で確認してください。
2. 接続文字列を取得する。Neon が提示するのは次の形式です。

   ```
   postgresql://<user>:<password>@<host>/<db>?sslmode=require
   ```

3. **これを Spring Boot 用の形式に変換します。** ここは間違えやすい箇所です。

   | 変数 | 値 |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<host>/<db>?sslmode=require` ← **ユーザー名・パスワードを含めない。先頭に `jdbc:` を付ける** |
   | `DB_USERNAME` | `<user>` |
   | `DB_PASSWORD` | `<password>` |

4. **手元から Flyway マイグレーションを実行し、スキーマを作成します。**

   このために `build.gradle.kts` に Flyway Gradle プラグインを適用しています（アプリ起動時の Flyway とは別に、CLI としてマイグレーションだけを実行するため）。

   ```bash
   cd kivio-backend
   DB_URL='jdbc:postgresql://<host>/<db>?sslmode=require' \
   DB_USERNAME='<user>' \
   DB_PASSWORD='<password>' \
   ./gradlew flywayMigrate
   ```

   > **⚠️ 環境変数は必ずコマンドの前に明示してください。** devcontainer は `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` を**ローカル DB 向けに設定済み**です。単に `./gradlew flywayMigrate` と打つと、本番 DB ではなくローカル DB に対して実行されます。

   > この段階で実行する理由: Backend の初回起動時にマイグレーションを走らせると、失敗時に「DB 接続の問題」なのか「マイグレーションの問題」なのかが切り分けられません。先に手元から通しておけば、Backend 起動時には検証済みのスキーマに対して `ddl-auto: validate` が走るだけになります。

   適用前に対象を確認したい場合は `flywayInfo` を使います（DB を変更しません）。

   ```bash
   DB_URL='...' DB_USERNAME='...' DB_PASSWORD='...' ./gradlew flywayInfo
   ```

5. **適用結果を確認します。** 本番スキーマに含まれるのは `V1`〜`V9` と `V11` の 10 件です。

   ```
   Schema version: 11
   ```

   > **開発用シードデータ（`V10` / `V13` / `V14`）が含まれないことが正しい状態です。** これらは `src/main/resources/db/seed/dev/` に置かれ、dev プロファイルの `spring.flyway.locations` からのみ読み込まれます。
   >
   > Flyway は locations を**再帰的に**走査するため、シードを `db/migration/dev/` に置くと prod でも適用されてしまいます（テストユーザー・ダミー申請が本番 DB に入る）。ディレクトリを `db/migration` の外に分離しているのはこのためです。`flywayInfo` の一覧に `seed development data` が現れた場合は設定が壊れているので、**マイグレーションを実行せず**に原因を調べてください。

6. Neon のコンソールでテーブルが作成されていることを確認する

### STEP 2: Redis（Upstash）

> **この STEP に「設定を書き込む場所」はありません。** STEP 1 の Flyway のような適用作業はなく、値を控えるだけです。控えた値は STEP 3 で Render の環境変数として入力します。

1. Upstash でデータベースを作成する（リージョンは **STEP 1 と同じ**＝ Backend と同じ場所）
2. **接続情報を 4 つの値に分解して控える**（下記）
3. 控えた値は STEP 3 まで使わない。**ローカルの `.env` には入れないこと**（ローカル開発が本番 DB / Redis を触ってしまう）

#### 接続文字列の分解

Upstash が提示するのは次の形式です。**そのまま `REDIS_HOST` に貼らないでください。**

```
rediss://default:AbCdEf123456@precious-mammal-12345.upstash.io:6379
         └ 無視 ┘└ password ┘ └────────── host ──────────────┘└port┘
```

| 変数 | 上の例での値 | 注意 |
|---|---|---|
| `REDIS_HOST` | `precious-mammal-12345.upstash.io` | `rediss://` もポートも含めない。ダッシュボードの「Endpoint」表示と同じ |
| `REDIS_PORT` | `6379` | |
| `REDIS_PASSWORD` | `AbCdEf123456` | `default:` はユーザー名なので含めない |
| `REDIS_SSL` | `true` | `rediss://` の 2 つ目の `s`（TLS）に対応する（§3-2） |

> STEP 1 の `DB_URL` と同じ種類の間違いです。**マネージドサービスが見せる接続文字列は、そのままでは Spring の設定値にならない**と考えてください。
>
> アプリケーションが読むのは `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL` の 4 つです。`REDIS_URL` という設定項目は存在しません。

> Redis は登録 OTP・登録セッション（`registrationToken`）の TTL ストレージです（ADR-006）。揮発データのみを保持するため、永続化やバックアップの設定は不要です。

### STEP 3: Backend（Render）

#### デプロイ方式の選択

| 方式 | 内容 | 評価 |
|---|---|---|
| **A. ghcr.io のイメージをデプロイ（推奨）** | CD が push 済みのイメージを指定する | ビルド済みのため高速。CI を通過したイメージがそのまま動く。CD 自動化への移行も容易 |
| B. リポジトリから Render にビルドさせる | Render 上で Dockerfile をビルド | 設定は簡単だが Gradle ビルドが毎回走り遅い。CI と別のビルドになるため「CI は通ったのに本番は違う」が起こりうる |

**方式 A を推奨します。**

#### 手順（方式 A）

1. `main` ブランチへマージ（＝リリース）し、CD の完走を確認する。イメージ名は次の形式です。

   ```
   ghcr.io/<owner>/<repo>/backend:sha-xxxxxxx
   ```

   > **`latest` タグではなく `sha-` タグを指定してください。** `latest` は可変タグで、次のリリースにより中身が変わります。何がデプロイされているか追跡できなくなり、ロールバック先も特定できません。

2. ghcr.io のイメージは既定で private です。以下のいずれかを選びます。
   - パッケージ設定で public に変更する（手軽。ソースは既に公開リポジトリなら実質的な情報差はない）
   - `read:packages` スコープの PAT を発行し、Render のレジストリ認証情報に登録する（private を維持したい場合）

3. Render で Web Service を作成し、上記イメージを指定する

4. 設定値:

   | 項目 | 値 |
   |---|---|
   | Health Check Path | `/api/v1/health` |
   | Port | `8080` |
   | 環境変数 | §5-1 の全項目 |

   > ヘルスチェックは `HealthController`（`/api/v1/health`、認証不要）を使います。Actuator（`/actuator/health`）も有効ですが、`SecurityConfig` の許可設定を確認する必要があるため、専用エンドポイントの方が確実です。

5. デプロイを実行し、ログで以下を確認する:
   - Flyway が「既に最新」と判断してスキップしている（STEP 1 で適用済みのため）

     ```
     Current version of schema "public": 11
     Schema "public" is up to date. No migration necessary.
     ```

   - Hibernate の `ddl-auto: validate` がエラーを出していない
   - Redis 接続エラーが出ていない
   - `Started KivioBackendApplication` が出力されている

6. 疎通確認:

   ```bash
   curl -i https://<render-backend-domain>/api/v1/health
   # → 200 {"status":"UP","timestamp":"..."}
   ```

   > **Backend のドメインをメモしておきます。** STEP 4 の `API_BASE_URL` に使用します。

### STEP 4: Frontend（Vercel）

1. Vercel でリポジトリをインポートする
2. Root Directory に `kivio-frontend` を指定する（モノレポ構成のため必須）
3. **Production Branch を `main` に設定する**（既定は `main` だが、リポジトリ設定によっては異なるため確認する）
4. 環境変数（§5-2）を設定する。`API_BASE_URL` には STEP 3 の Render ドメインを設定します
5. デプロイを実行する

> Vercel は Next.js をネイティブにビルドするため、`kivio-frontend/Dockerfile` は使用されません。Dockerfile はローカルの `docker compose` 用です。

> **Preview Deployment が staging の代わりになります**（§2-1）。`main` 以外のブランチや PR には自動で固有 URL が発行されるため、`develop` の状態をブラウザで確認したい場合はその URL を使ってください。ただし Preview 環境も `API_BASE_URL` は production の Backend を指す点に注意してください（Backend は 1 環境のみのため）。

### STEP 5: 相互参照する設定の更新

構築順の都合で、最後に「相手のドメインが確定してから」設定する項目があります。

1. **Render の環境変数を更新する**
   - `ALLOWED_ORIGINS` = `https://<vercel-domain>`
   - `PROBLEM_BASE_URL` = `https://<vercel-domain>`
   - 更新後、Backend の再デプロイが必要です

2. **Google Cloud Console の OAuth 設定を更新する**
   - 承認済みリダイレクト URI に `https://<vercel-domain>/api/auth/callback/google` を追加
   - 承認済み JavaScript 生成元に `https://<vercel-domain>` を追加

3. **Resend の送信元ドメインを確認する**（独自ドメイン利用時は DNS 認証の完了を待つ）

---

## 7. スモークテスト

デプロイ完了後、以下を**本番環境のブラウザで**実行します。ここまでの手順の妥当性は、このテストでのみ確認できます。

| # | 項目 | 期待結果 |
|---|---|---|
| 1 | `/` にアクセス | トップページが表示される |
| 2 | 新規登録（メールアドレス入力） | OTP メールが届く（**Resend / Redis の疎通確認**） |
| 3 | OTP 入力 → 登録完了 | ユーザーが作成される（**DB 書き込み確認**） |
| 4 | ログアウト → ログイン | 認証 Cookie が発行される（**JWT・Cookie 確認**） |
| 5 | Google ログイン | OAuth フローが完走する（**リダイレクト URI 設定確認**） |
| 6 | プロフィール編集 | 更新が永続化される |
| 7 | セラー申請の作成 | 申請が保存され、一覧に表示される |
| 8 | 管理者でセラー申請を承認 | ステータスが更新され、監査ログが記録される |
| 9 | ブラウザの DevTools → Network | `/api/v1/*` が Vercel ドメインに対して発行されている（BFF 経由であることの確認） |
| 10 | 存在しない ID へのアクセス | `application/problem+json` 形式のエラーが返る |

> 項目 2 が最も失敗しやすい箇所です（Resend の実装・ドメイン認証・Redis の TLS が同時に関わるため）。失敗した場合は §9 を参照してください。

**このスモークテストの結果を記録に残してください。** 次回以降の再デプロイ時の回帰確認チェックリストとして使えます。

---

## 8. 再デプロイ・ロールバック

### 再デプロイ（リリース）

1. `feature/*` → `develop` へマージ（結合。ここでは CD は動かない）
2. `develop` → `main` の PR を作成・マージ（＝リリース）
3. CI 成功 → CD が新しい `sha-xxxxxxx` タグのイメージを push
4. スキーマ変更（新しい Flyway マイグレーション）を含む場合、**Backend のデプロイ前に手元からマイグレーションを適用する**

   ```bash
   cd kivio-backend
   DB_URL=... DB_USERNAME=... DB_PASSWORD=... ./gradlew flywayMigrate
   ```

   > `ddl-auto: validate` のため、スキーマとエンティティが不一致だと Backend は起動に失敗します。マイグレーション先行が原則です。
   >
   > 環境変数の明示を忘れるとローカル DB に対して実行されます（§6 STEP 1）。

5. Render のイメージタグを新しい SHA に更新してデプロイ
6. Frontend は `main` への push で Vercel が自動デプロイされる

### ロールバック

| 対象 | 手順 |
|---|---|
| Backend | Render で 1 つ前の `sha-` タグのイメージを再デプロイ |
| Frontend | Vercel のダッシュボードから直前のデプロイを Promote |
| DB | **自動ロールバックは不可。** Flyway の破壊的変更（カラム削除等）は後方互換性を意識して設計する |

> DB のロールバックが困難であるため、**カラム削除は「新カラム追加 → 移行 → 旧カラム削除」の複数リリースに分割する**のが安全です。Phase 2 の段階では実データがないため厳密に運用する必要はありませんが、習慣として意識しておく価値があります。

---

## 9. トラブルシューティング

| 症状 | 想定原因 | 対処 |
|---|---|---|
| Backend が起動せず `UnsatisfiedDependencyException: EmailSender` | §3-1 未対応 | `ResendEmailSender`（`@Profile("prod")`）を実装する |
| Backend が起動せず Redis 接続タイムアウト | §3-2 未対応（TLS 不使用） | `REDIS_SSL=true` と設定項目を追加する |
| `Driver claims to not accept jdbcUrl` | `DB_URL` が `postgresql://` のまま | 先頭に `jdbc:` を付け、ユーザー名・パスワードを URL から除く（§6 STEP 1） |
| Hibernate の `SchemaManagementException` | マイグレーション未適用 | 手元から `flywayMigrate` を実行する |
| 本番 DB にテーブルができていない（ローカル DB が更新されている） | `flywayMigrate` 実行時に環境変数を明示せず、devcontainer 既定の `DB_URL` が使われた | コマンドの前に `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` を明示して再実行（§6 STEP 1） |
| 本番 DB に見覚えのないユーザー・申請データがある | 開発用シード（`V10` / `V13` / `V14`）が適用された | `flywayInfo` で確認。シードは `db/seed/dev/` にあり prod の locations 外であること（§6 STEP 1-5） |
| Frontend から API 呼び出しが失敗（500） | `API_BASE_URL` の誤り | 末尾スラッシュの有無・`https` を確認。Vercel のログでリクエスト先 URL を確認する |
| OTP メールが届かない | Resend のドメイン未認証 / 送信先制限 | Resend のダッシュボードで送信ログを確認。検証用ドメインの場合は送信先が制限される |
| Google ログインで `redirect_uri_mismatch` | リダイレクト URI 未登録 | Google Cloud Console に `https://<vercel-domain>/api/auth/callback/google` を追加（§6 STEP 5） |
| 初回アクセスが極端に遅い | インスタンスのスリープからの復帰 | §10 を参照 |
| メモリ不足でコンテナが再起動する | ヒープ設定 | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` を設定（§3-3） |

### ログの確認先

| 対象 | 確認場所 |
|---|---|
| Backend | Render の Logs タブ |
| Frontend（SSR / BFF） | Vercel の Runtime Logs |
| Frontend（ビルド） | Vercel の Build Logs |
| メール送信 | Resend のダッシュボード |

> ログには `correlationId` が出力されます（`CorrelationIdFilter`）。Frontend のリクエストと Backend のログを突き合わせる際に利用してください。

---

## 10. 既知の制約

### コールドスタート

無料枠のホスティングは、一定時間アクセスがないとインスタンスを停止するのが一般的です。復帰には数十秒かかる場合があります。Neon のような Serverless DB も同様に自動停止します。

**登録 OTP フローのように「ユーザーが待つ」画面ではこの遅延が体験を大きく損ないます。**

| 用途 | 判断 |
|---|---|
| 学習・動作確認のみ | 無料枠のままで問題ない |
| **職務経歴書に URL を記載する** | **有料プランを検討する価値が高い** |

採用担当者がリンクを開いて白い画面を長時間見ることは、アプリの品質評価に直結します。ポートフォリオとして提示する段階になったら、Backend と DB を常時稼働プランに変更することを推奨します。

### Cloudinary は未接続

Phase 2 の「4. ショップ・商品 CRUD」で追加します。`next.config.ts` の `images.remotePatterns` には `res.cloudinary.com` が設定済みのため、フロントエンド側の準備は完了しています。Backend 側で以下が必要になります。

- `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` の追加
- `io.kivio.infra` 配下への Cloudinary クライアント実装

### Swagger UI は無効

`application-prod.yaml` で API ドキュメントの公開を無効化しています（意図的な設定）。本番環境で API 仕様を確認したい場合は、ローカルの `dev` プロファイルで起動してください。

---

## 11. 今後のロードマップ

### ステップ 1: 手動デプロイの安定化（本ドキュメントの範囲）

手順を確立し、再デプロイを数回繰り返して手順の抜けを洗い出す。

### ステップ 2: CD の自動化

`cd.yml` の `deploy-backend` ジョブ（現在コメントアウト）を実装する。**本ドキュメント §6 の手順がそのまま仕様になります。**

- Render のデプロイフックを叩き、`imgURL` パラメーターで `sha-` タグのイメージを明示的に指定する
- Flyway マイグレーションを CD の中でどう扱うかの設計が必要（手動運用を維持するか、デプロイ前ステップとして自動化するか）
- GitHub Environment（`production`）に承認フローを設定すれば、マージから自動デプロイまでの間に人間の確認を挟める

### ステップ 3: AWS への移行（ポートフォリオ拡張）

PaaS で稼働実績を作った後の選択肢として検討する。

**移行それ自体よりも、「なぜ移行したか」を ADR として記録することに価値があります。** PaaS と IaaS の運用コスト・自由度・学習コストのトレードオフを、実際に両方を運用した経験に基づいて記述できる状態は、技術選定能力の証明になります。

想定構成:

| レイヤ | 移行先 |
|---|---|
| Backend | ECS Fargate（コンテナのまま移行しやすい）または EC2 |
| DB | RDS PostgreSQL |
| Redis | ElastiCache |
| 画像 | S3 + CloudFront（Cloudinary からの移行） |
| IaC | Terraform |

> **PaaS でのデプロイを飛ばして AWS から始めることは推奨しません。** ネットワーク・IAM・TLS 証明書・ロードバランサの設定を、アプリケーションの動作確認と同時に行うことになり、切り分けが困難になります。「動くアプリがある」状態を先に作ってから、インフラだけを差し替える方が確実です。
