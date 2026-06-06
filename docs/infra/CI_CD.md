# CI/CD ガイド

> 関連ファイル: `.github/workflows/ci.yml`、`.github/workflows/cd-dev.yml`  
> 関連ドキュメント: [DOCKER_GUIDE.md](./DOCKER_GUIDE.md)、[BACKEND_CODING_STANDARDS.md](../development/BACKEND_CODING_STANDARDS.md)

---

## 目次

1. [CI/CD とは](#1-cicd-とは)
2. [パイプライン全体像](#2-パイプライン全体像)
3. [CI ワークフロー（ci.yml）](#3-ci-ワークフローciyml)
4. [CD ワークフロー（cd-dev.yml）](#4-cd-ワークフローcd-devyml)
5. [GitHub の初回セットアップ](#5-github-の初回セットアップ)
6. [日々の使い方](#6-日々の使い方)
7. [トラブルシューティング](#7-トラブルシューティング)

---

## 1. CI/CD とは

### CI（Continuous Integration）とは

コードを Push するたびに、自動でテスト・静的解析を実行する仕組みです。

**自動化しない場合に起きること:**
- 「自分のマシンでは動いた」がマージ後に壊れる
- テストを手動で実行し忘れる
- コーディング規約違反がコードベースに蓄積する

**CI を導入すると:**
- Push のたびに必ず検証が走るため、壊れたコードが紛れ込みにくくなる
- コードレビュー前に機械的なチェックが完了している

### CD（Continuous Delivery）とは

CI が成功したコードを、自動で Docker イメージにパッケージ化してコンテナレジストリに登録する仕組みです。

**本プロジェクトの CD の役割:**
- `develop` ブランチへのマージが完了したら、デプロイ可能なイメージを自動生成する
- イメージに `sha-xxxxxxx` タグを付けることで「どのコミットのビルドか」を追跡可能にする
- Trivy でイメージ内の脆弱性を自動スキャンして Security タブに記録する

---

## 2. パイプライン全体像

```
┌─────────────────────────────────────────────────────────┐
│  開発者の操作                                             │
│  PR(pull request) 作成 or develop/main への push         │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│  CI ワークフロー (.github/workflows/ci.yml)               │
│                                                         │
│  ┌──────────────────────┐  ┌──────────────────────────┐ │
│  │  Backend job         │  │  Frontend job            │ │
│  │  ① Checkstyle        │  │  ① ESLint               │ │
│  │  ② Test + JaCoCo     │  │  ② TypeCheck            │ │
│  │  ③ bootJar ビルド    │  │  ③ Next.js ビルド         │ │
│  └──────────────────────┘  └──────────────────────────┘ │
│  （両 job が成功した場合のみ CI 成功）                       │ 
└─────────────────────────┬───────────────────────────────┘
                          │ CI が develop branch で成功
                          │ （PR の CI 成功では CD は動かない）
                          ▼
┌─────────────────────────────────────────────────────────┐
│  CD ワークフロー (.github/workflows/cd-dev.yml)           │
│                                                         │
│  ┌──────────────────────┐  ┌──────────────────────────┐ │
│  │  backend イメージ      │  │  frontend イメージ        │ │
│  │  ① Docker build      │  │  ① Docker build         │ │
│  │  ② ghcr.io push      │  │  ② ghcr.io push         │ │
│  │  ③ Trivy スキャン     │  │  ③ Trivy スキャン         │ │
│  └──────────────────────┘  └──────────────────────────┘ │
│  （2 サービスは並列実行）                                   │
└─────────────────────────────────────────────────────────┘
```

### どの操作で何が動くか

| 操作 | CI | CD |
|---|---|---|
| PR を作成・更新（develop/main 向け） | 動く | 動かない |
| develop に push（PR マージ含む） | 動く | CI 成功後に動く |
| main に push | 動く | 動かない（main 用 CD は未実装）|

---

## 3. CI ワークフロー（ci.yml）

### トリガー条件

```yaml
on:
  pull_request:
    branches: [develop, main]   # これらへの PR を出したとき
  push:
    branches: [develop, main]   # これらに直接 push したとき
```

### Concurrency（同時実行の制御）

PR に新しいコミットを積んだとき、古い CI ランを自動でキャンセルします。最新のコミットの結果だけを残すことで、不要なリソース消費を防ぎます。

`develop`/`main` への push では、各コミットの結果を全て記録するためキャンセルしません。

### Backend job

| ステップ | 内容 |
|---|---|
| Set up JDK 25 | Eclipse Temurin 25 をインストール。`build.gradle.kts` のハッシュでキャッシュキーを生成し、毎回の依存関係ダウンロードを省略する |
| Checkstyle | `checkstyleMain` + `checkstyleTest` を実行。違反があれば HTML レポートをアーティファクトに保存して失敗する |
| Test | `./gradlew test jacocoTestCoverageVerification` を実行。Testcontainers が PostgreSQL コンテナを自動起動するため `services:` の設定は不要。カバレッジが 80% 未満だと失敗する |
| Upload artifacts | テスト結果（XML）・カバレッジレポート（HTML）を 14 日間保持（失敗時でも保存） |
| bootJar ビルド | テストをスキップして実行可能 JAR を生成し、Dockerfile でのビルドが通ることを確認する |

### Frontend job

| ステップ | 内容 |
|---|---|
| Set up pnpm | pnpm 9 をインストール |
| Set up Node.js | Node.js 24 をインストール。`pnpm-lock.yaml` のハッシュでキャッシュキーを生成 |
| Install dependencies | `pnpm install --frozen-lockfile` でロックファイルと一致するバージョンのみインストール |
| Lint | `pnpm lint` で ESLint を実行 |
| Type check | `pnpm typecheck` で `tsc --noEmit` を実行 |
| Build | `pnpm build` で Next.js の本番ビルドを実行 |

`HUSKY: "0"` を環境変数に設定し、CI 環境で Husky（git フック）が起動しないようにしています。

---

## 4. CD ワークフロー（cd-dev.yml）

### トリガー条件

```yaml
on:
  workflow_run:
    workflows: [CI]     # "CI" という名前のワークフローが
    types: [completed]  # 完了したとき
    branches: [develop] # develop ブランチで動いた場合のみ
```

`workflow_run` を使うことで「CI が成功したコミットのみ」をビルド対象にできます。PR の CI が通っても CD は動きません。`develop` への PR マージ後に CI が通ったときだけ動きます。

### イメージのタグ戦略

1 回のビルドで 2 種類のタグを付けます。

| タグ | 例 | 目的 |
|---|---|---|
| `sha-xxxxxxx` | `sha-a1b2c3d` | **不変タグ**。どのコミットのビルドかを一意に識別する。デプロイ時はこのタグを使う |
| `develop` | `develop` | **可変タグ**。develop ブランチの最新ビルドを指す。「最新を試したい」ときに使う |

### コンテナレジストリ（ghcr.io）

GitHub Container Registry（`ghcr.io`）にイメージを保存します。

- `ghcr.io/<owner>/<repo>/backend:sha-xxxxxxx`
- `ghcr.io/<owner>/<repo>/frontend:sha-xxxxxxx`

`GITHUB_TOKEN` が自動付与されるため、外部サービスの認証情報（Secrets）を別途設定する必要はありません。

### Trivy セキュリティスキャン

プッシュしたイメージ内の OS パッケージ・依存ライブラリの既知脆弱性を Trivy でスキャンします。

- 検出対象: `HIGH` / `CRITICAL` の脆弱性
- 修正されていない脆弱性（`ignore-unfixed: true`）は除外
- 現時点では **ジョブを失敗させず可視化のみ** (`exit-code: "0"`)
- 結果は GitHub の **Security タブ → Code scanning** に SARIF 形式でアップロードされる

> 運用が安定したら `exit-code: "1"` に変更し、脆弱性発見時にビルドをブロックする運用に移行できます。

### ビルドキャッシュ

`cache-from / cache-to: type=gha` で GitHub Actions のキャッシュを利用します。Docker の各レイヤーをキャッシュするため、ソースコードを変更しても依存関係のダウンロードをスキップできます。backend と frontend でキャッシュスコープを分離しているため、一方の変更が他方のキャッシュを汚染しません。

### デプロイ

`cd-dev.yml` の末尾にある `deploy` ジョブはコメントアウトされています。インフラ環境が決まり次第、以下のいずれかを使って実装します。

- **パターン A**: SSH + Docker Compose（VPS 等）
- **パターン B**: Kubernetes（`kubectl set image`）

---

## 5. GitHub の初回セットアップ

### Branch Ruleset の設定

マージ前に CI 通過を必須にするため、`develop` と `main` に Branch Ruleset を設定します。

> **前提: CI を最低1回実行しておく**  
> 「Add checks」の検索欄にチェック名が表示されるのは、**そのリポジトリで CI ワークフローが少なくとも1回完了した後**です。  
> まだ CI を実行していない場合は、`develop` ブランチに空コミットを push して CI を通してから Ruleset を設定してください。
> ```bash
> git commit --allow-empty -m "chore: trigger CI for initial run"
> git push origin develop
> ```
> GitHub Actions タブで `CI / Backend` と `CI / Frontend` が緑になったことを確認してから次の手順へ進みます。

`develop` と `main` それぞれに対して以下の手順を実施します（設定値は同じ）。

1. GitHub リポジトリの **Settings → Branches** を開く
2. **Branch protection rules** セクションの **「Add branch ruleset」** をクリック
3. 各フィールドを設定する

| フィールド | 値 |
|---|---|
| Ruleset Name | `protect-develop`（または `protect-main`） |
| Enforcement status | **Active** |

4. **Target branches** → **「Add target」** → **「Include by pattern」** をクリックし、`develop`（または `main`）を入力して **「Add Inclusion pattern」**

5. **Rules** セクションで以下を有効にする

| ルール | 設定 |
|---|---|
| Restrict deletions | ✅ |
| Block force pushes | ✅ |
| Require a pull request before merging | ✅ |
| Require status checks to pass | ✅ → **「Add checks」** の入力欄に `CI / Backend` と入力して Enter、次に `CI / Frontend` も同様に追加する |

> **「Add checks」でチェック名が候補に出ない場合**  
> ドロップダウンに候補が表示されなくても、**名前を手入力して Enter を押せば登録できます**。  
> それでも追加できない場合は、`develop` への直接 push ではなく **PR 経由の CI ラン**が必要です。  
> テスト PR（空コミット）を `develop` に向けて作成し、`CI / Backend` と `CI / Frontend` が緑になったあとに再試行してください。
> ```bash
> git checkout -b chore/test-ci-checks
> git commit --allow-empty -m "chore: test PR for CI check registration"
> git push origin chore/test-ci-checks
> # GitHub 上で develop ← chore/test-ci-checks の PR を作成 → CI 完了後に Add checks を再試行
> ```

6. **Bypass list** は空のまま（バイパスを許可しない）にして **「Create」** をクリック

> **Bypass list について**  
> Bypass list に Collaborator やロールを追加すると、そのユーザーはルールを迂回してマージできます。  
> 厳密に保護したい場合は空のままにしてください。

### ghcr.io イメージの可視性設定

初回 CD 実行後、生成されたイメージのデフォルト可視性は **Private** です。必要に応じて変更します。

1. GitHub の **Packages** ページを開く（プロフィール → Packages）
2. 対象のイメージ（`backend` / `frontend`）を選択
3. **Package settings → Danger Zone → Change visibility** で変更

---

## 6. 日々の使い方

### PR を出した後の確認手順

1. PR ページの **Checks タブ** を開く
2. `CI / Backend` と `CI / Frontend` の結果を確認する
3. 失敗している場合は該当 job をクリックして詳細を確認する

### CI が失敗したときのデバッグ

**Checkstyle 違反の場合:**
```bash
# kivio-backend/ ディレクトリで実行する
cd kivio-backend
./gradlew checkstyleMain checkstyleTest
# build/reports/checkstyle/main.html をブラウザで開くと詳細が確認できる
```

**テスト失敗の場合:**

Actions の **Artifacts** セクションから `backend-test-results` をダウンロードすると、
JUnit の XML レポートと HTML レポートが入っています。

```bash
# kivio-backend/ ディレクトリで実行する（Docker が起動していること）
cd kivio-backend
./gradlew test
```

**カバレッジ不足の場合:**

`backend-coverage-report` アーティファクトをダウンロードすると JaCoCo のカバレッジレポート（HTML）が確認できます。カバレッジが低いクラスにテストを追加してください。

**フロントエンドの型エラーの場合:**
```bash
cd kivio-frontend
pnpm typecheck   # tsc --noEmit
pnpm lint
```

### ghcr.io のイメージを確認する

develop へのマージ後、以下で確認できます。

イメージが **Private**（デフォルト）の場合は先にログインが必要です。

```bash
# GitHub の Personal Access Token（read:packages スコープ）でログイン
docker login ghcr.io -u <GitHubユーザー名> -p <PAT>
```

```bash
# 最新 develop イメージを pull して起動確認
docker pull ghcr.io/<owner>/<repo>/backend:develop
docker pull ghcr.io/<owner>/<repo>/frontend:develop
```

### Trivy の脆弱性結果を確認する

1. GitHub リポジトリの **Security タブ** を開く
2. **Code scanning** を選択する
3. `trivy-backend` / `trivy-frontend` カテゴリのアラートを確認する

---

## 7. トラブルシューティング

| 症状 | 原因 | 対処 |
|---|---|---|
| Checkstyle FAILED | コーディング規約違反 | `kivio-backend/` で `./gradlew checkstyleMain checkstyleTest` を実行し、HTML レポートで違反箇所を確認する |
| Test FAILED — Cannot connect to Docker | Testcontainers が Docker を見つけられない | ローカルなら Docker Desktop を起動する。CI では通常発生しない |
| jacocoTestCoverageVerification FAILED | テストカバレッジ < 80% | `kivio-backend/` で `./gradlew test` 後にカバレッジレポートを確認し、テストを追加する |
| CD が動かない | CI が develop branch で成功していない | Actions タブで CI のログを確認する |
| ghcr.io push 失敗（403） | リポジトリの Packages 権限が不足 | Settings → Actions → General → Workflow permissions を **Read and write** に変更する |
| `pnpm install` FAILED — lockfile mismatch | `pnpm-lock.yaml` が `package.json` と不一致 | ローカルで `pnpm install` を実行して `pnpm-lock.yaml` を更新し、commit する |
| Next.js build FAILED | 型エラー・import エラー | ローカルで `pnpm typecheck` と `pnpm build` を実行して確認する |
| Trivy アラートが増えた | 使用イメージ・ライブラリの脆弱性が新たに公開された | Security タブでアラートを確認し、影響範囲を評価する。修正バージョンがあれば依存関係を更新する |
