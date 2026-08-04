# セラー申請画面レイアウト設計仕様

# セラー申請フォーム / 審査状況表示

**対象画面:** `/seller/applications/new`（Phase 2）
**対象コンポーネント:** `(authenticated)/seller/applications/new/page.tsx`, `SellerApplicationView`, `SellerApplicationIntro`, `SellerApplicationForm`, `SellerApplicationStatusCard`, `SellerApplicationSkeleton`
**スコープ:** `/seller/applications/new` 単一ページ（グローバル chrome は**使う**。`/profile/*` のようなセグメント chrome は**持たない**）
**MASTER.md との関係:** このファイルのルールが MASTER.md を上書きする（§16 準拠）
**layout.md との関係:** `GlobalHeader` / `GlobalFooter` / `MobileBottomNav` をそのまま使用する。本ファイルはその内側の 1 ページのみを定義する。
**user-profile.md との関係:** フォーム部品（`FieldError` / `FormAlert` / `SaveStatus`）とタッチターゲット規約（`h-11`）を**そのまま継承**する。異なるのは保存モデル（§5.3）とセクション構造（本画面は 1 カラム・`SettingsSection` を使わない）。
**実装タスク:** `docs/implementation-plans/seller-application.md` SA-15〜SA-19（SA-00〜SA-13b はバックエンド・ドキュメント完了済み）
**作成日:** 2026-08-02

---

## 目次

1. [設計原則](#1-設計原則--この画面は台帳でもフォームでもなく窓口である)
2. [ダークモード方針](#2-ダークモード方針)
3. [全体レイアウト構造](#3-全体レイアウト構造)
4. [状態モデル](#4-状態モデル--1-url4-状態--3-つの前段)
5. [画面パーツ仕様](#5-画面パーツ仕様)
6. [文言（マイクロコピー）](#6-文言マイクロコピー)
7. [エラー UI 対応表](#7-エラー-ui-対応表)
8. [ローディング・スケルトン](#8-ローディングスケルトン)
9. [モーション方針](#9-モーション方針)
10. [レスポンシブまとめ](#10-レスポンシブまとめ)
11. [アクセシビリティ要件](#11-アクセシビリティ要件)
12. [コンポーネント構成とファイルマッピング](#12-コンポーネント構成とファイルマッピング)
13. [デザイントークン参照](#13-デザイントークン参照master-md-2)
14. [実装優先度](#14-実装優先度seller-applicationmd-タスクとの対応)
15. [Open Questions](#15-open-questions)
16. [frontend-design レビュー反映ログ](#16-frontend-design-レビュー反映ログ2026-08-02)
17. [実装反映ログ](#17-実装反映ログ2026-08-02sa-15sa-19)

---

## 1. 設計原則 — この画面は「台帳」でも「フォーム」でもなく「窓口」である

`/auth/*` は**初対面のユーザーに 1 つのタスクを完遂させる**画面、`/profile/*` は**既にある自分のデータを点検し部分的に書き換える**画面だった。`/seller/applications/new` はそのどちらでもない。

この画面は **① まだ決めていない人に説明し、② 一度だけ送信させ、③ その後は結果を待つ窓口**である。同じ URL が、申請前は「案内板 ＋ 申請書」、申請後は「受付票」に変わる。

| 原則 | `/auth/*` | `/profile/*` | `/seller/applications/new`（本仕様） |
|---|---|---|---|
| **訪問回数** | 1〜2 回 | 何度も再訪する | **申請前に数回・申請後は「確認だけ」に数回** |
| **主タスク** | 完遂（登録・ログイン） | 点検と部分修正 | **決断 → 送信 → 待機** |
| **入力量** | 短いフィールド複数 | 既存値の書き換え | **自由記述 1 つ（最大 1000 文字）だけ** |
| **画面の主役** | フォーム | 区画（罫線） | **現在の状態**。フォームはその状態で取れる 1 つの行動にすぎない |
| **失敗のコスト** | 入り直せばよい | 一部不可逆（退会） | **送信は取り消せない**（取り下げ API が無い）。ただし却下されれば再申請できる |
| **説得の必要** | 不要（意思は既にある） | 不要 | **必要**。「出品者になると何ができるか」を提示しないと、自由記述の申請理由を書く動機が生まれない |

**結論:** レイアウトは **1 カラムの縦一本**にする。`/profile/*` の 2 カラム罫線構成（`SettingsSection`）は**持ち込まない** — 並列する独立タスクが存在せず、見出し列を作っても左側が空くだけだからである。代わりに、**上から「今どの状態か」→「なぜ／何を書くか」→「行動」**の順で 1 本に積む。この順序は 4 状態すべてで変わらない（§4）。

**塗り面の規律:** `user-profile.md §1` の「塗り面はアイデンティティヘッダー 1 箇所だけ」に相当する制約として、本画面では **同時に画面へ出る塗り面は 1 つまで**とする。

| 状態 | 唯一の塗り面 |
|---|---|
| 未申請 | `SellerApplicationIntro`（`bg-secondary/50`） |
| `PENDING` | ステータスカード内の「申請理由」引用ブロック（`bg-muted/60`） |
| `REJECTED` | 却下理由ブロック（`bg-destructive/5`）。**このときイントロは出さない**（説得は済んでおり、読むべきは却下理由である）。上に並ぶ申請理由の引用は塗りを外し `border` だけにする — 却下理由が省略された却下では引用が唯一の塗り面になるので `bg-muted/60` のままでよい |

---

## 2. ダークモード方針

`layout.md §1` に準拠し**ライトモード固定**。`/seller/*` は Phase 3+ でセグメントスコープのダーク対応を検討する対象だが、**本画面は例外的に「セラーになる前のバイヤーが見る画面」**であり、実質バイヤー面である。将来 `/seller/dashboard` 以下をダーク化する場合も、**本画面はライトのまま残す**（申請前後で配色が反転すると、同じ人が同じ URL を再訪したときに別サービスに見える）。

---

## 3. 全体レイアウト構造

### 3.1 レイヤー構造

```
GlobalHeader（layout.md §3・スティッキー z-20）
─────────────────────────────────────────────
main#main-content
  └─ ページヘッダー（h1「セラー申請」＋ リード文）   ← 状態非依存・SC
  └─ 通知帯（SaveStatus / FormAlert）              ← 状態遷移のフィードバック・CC
  └─ 状態別コンテンツ（4 状態のいずれか 1 つ）        ← CC
─────────────────────────────────────────────
GlobalFooter（layout.md §9）
MobileBottomNav（< md・layout.md §8）
```

`/profile/*` と違い **アイデンティティヘッダーもサイドナビも置かない**。申請は「アカウント設定の一項目」ではなく単発の手続きであり、周辺に並ぶ兄弟ページが存在しない（`/seller/*` の他のページは SELLER 専用で、この画面の利用者からは到達できない）。

### 3.2 デスクトップ / ノート（lg 〜）— 未申請

```
┌──────────────────────────────────────────────────────────────┐
│  GlobalHeader                                                 │
├──────────────────────────────────────────────────────────────┤
│                                                                │
│            セラー申請                          ← h1 font-serif │
│            Kivio への出店申請と、審査状況の確認ができます。      │
│                                                                │
│            ┌ SellerApplicationIntro ──────────────────────┐   │
│            │ 出品者になるとできること      bg-secondary/50 │   │
│            │  ▫ ショップを開設できます                     │   │
│            │  ▫ 商品を登録して販売できます                  │   │
│            │  ▫ 注文と売上を管理できます                    │   │
│            │  審査結果はこのページで確認できます             │   │
│            └───────────────────────────────────────────────┘   │
│                                                                │
│            申請理由                              ← h2         │
│            ────────────────────────────────────────────       │
│            どのような商品を販売したいか、これまでの            │
│            経験などをご記入ください。                          │
│            ┌──────────────────────────────────────────┐       │
│            │                                          │       │
│            │                          min-h-40        │       │
│            └──────────────────────────────────────────┘       │
│            1000文字以内                        0 / 1000       │
│                                                                │
│                                       [   申請する   ]         │
│                                                                │
├──────────────────────────────────────────────────────────────┤
│  GlobalFooter                                                 │
└──────────────────────────────────────────────────────────────┘
                     ← max-w-2xl（672px）→
```

| 要素 | 指定 |
|---|---|
| 外側コンテナ | `mx-auto max-w-2xl px-6 py-10 lg:py-14` |
| 本文幅 | `max-w-2xl`（672px）。`/profile/*` の `max-w-3xl` より**狭くする** — 本画面は読ませる散文（制度説明・却下理由）が主で、和文 1 行 40〜45 字に収めた方が読みやすい |
| 縦リズム | ブロック間 `space-y-8`（32px）。`MASTER.md §4` のセクション間 |
| 中央寄せ | コンテナのみ中央。**テキストは左揃え**（`text-center` は使わない。日本語の長文で中央揃えは可読性を落とす） |

### 3.3 デスクトップ — `PENDING` / `REJECTED`

```
            セラー申請
            Kivio への出店申請と、審査状況の確認ができます。  ← 状態で差し替えない（§5.1）

            ┌ SellerApplicationStatusCard ────────────────┐
            │▎ [🕐 審査中]              2026年8月2日 申請  │  ▎= border-l-2 border-l-warning
            │                                              │
            │  申請理由                                     │
            │  ┌────────────────────────────────────────┐  │
            │  │ ハンドメイドのアクセサリーを…           │  │  bg-muted/60
            │  └────────────────────────────────────────┘  │
            │                                              │
            │  審査には数日かかる場合があります。            │
            │  結果が出るとこのページの表示が変わります。     │
            └──────────────────────────────────────────────┘

            ── REJECTED では、同じカードの中が次のようになる ──

            ┌ SellerApplicationStatusCard ────────────────┐
            │▎ [✕ 却下]                 2026年8月2日 申請  │  ▎= border-l-2 border-l-destructive
            │                                              │
            │  申請理由                                     │
            │  ┌────────────────────────────────────────┐  │
            │  │ ハンドメイドのアクセサリーを…           │  │  塗りを外して border のみ
            │  └────────────────────────────────────────┘  │  （塗り面は却下理由に譲る・§1）
            │                                              │
            │  ┌ 却下理由 ──────────────────────────────┐  │
            │  │ 申請内容から取り扱い商品を…             │  │  bg-destructive/5
            │  └────────────────────────────────────────┘  │
            └──────────────────────────────────────────────┘

            再申請                                    ← h2
            ────────────────────────────────────────────
            内容を見直して、あらためて申請できます。
            ┌──────────────────────────────────────────┐
            │                                          │
            └──────────────────────────────────────────┘
                                       [  再申請する  ]
```

### 3.4 モバイル（< md）

```
┌──────────────────────────┐
│  GlobalHeader（簡易）      │
├──────────────────────────┤
│  セラー申請                │  ← px-6 py-10
│  Kivio への出店申請と…     │
│                          │
│ ┌──────────────────────┐ │
│ │ 出品者になるとできること│ │  ← イントロは同じ・padding のみ縮小
│ └──────────────────────┘ │
│                          │
│  申請理由                 │
│  ──────────────────────  │
│  ┌──────────────────────┐│
│  │                      ││  ← min-h-40 は維持（縮めない）
│  └──────────────────────┘│
│  1000文字以内     0 / 1000│
│                          │
│  [      申請する       ] │  ← w-full
├──────────────────────────┤
│  GlobalFooter             │
│  MobileBottomNav（固定）   │  ← グローバルレイアウトの pb-16 で回避済み
└──────────────────────────┘
```

- テキストエリアの高さは**モバイルでも縮めない**。1000 文字の自由記述で入力域が 3 行しかないと、書いた内容を読み返せず推敲できない。
- `< sm` のパディングは `px-5`（グローバル chrome の `px-6` より 4px 狭い）ではなく **`px-6` に揃える**。ヘッダー・フッターと左端が揃わないと 1 枚の紙に見えない。

### 3.5 ページ骨格

```tsx
// src/app/(authenticated)/seller/applications/new/page.tsx — SC
import type { Metadata } from 'next'
import { SellerApplicationView } from '@/components/seller/SellerApplicationView'

export const metadata: Metadata = {
  title: 'セラー申請 | Kivio',
}

export default function SellerApplicationNewPage() {
  return (
    <div className="mx-auto max-w-2xl px-6 py-10 lg:py-14">
      <header className="space-y-2">
        <h1 className="text-foreground font-serif text-3xl font-bold">セラー申請</h1>
        <p className="text-muted-foreground text-base leading-relaxed">
          Kivio への出店申請と、審査状況の確認ができます。
        </p>
      </header>

      <SellerApplicationView />
    </div>
  )
}
```

- **`<h1>` とリード文は Server Component 側に置き、状態で変えない。** 状態を知っているのは `SellerApplicationView`（CC）だけであり、見出しを状態依存にすると SC/CC 境界を跨ぐか、ページ全体を CC にする必要が出る。加えて、同じ URL で `<h1>` が入れ替わるとスクリーンリーダー利用者がページの同一性を見失う。
- ルートグループは `(authenticated)`（`FRONTEND_IA.md §3` のディレクトリ設計どおり）。未認証のガードは `src/proxy.ts` の `PROTECTED_PATHS`（`/seller` 登録済み）が担う。
- `SellerApplicationView` は上マージンを自分で持つ（`mt-8`）。ページ側で `space-y-*` を使わないのは、View がリダイレクト中に何も描画しない場合に余白だけが残るのを避けるため。

---

## 4. 状態モデル — 1 URL・4 状態 ＋ 3 つの前段

`SellerApplicationView` が持つ分岐は **7 段**。順序に意味があり、**入れ替えると誤リダイレクトや一瞬のフォーム表示（フラッシュ）が発生する**。

| # | 条件 | 描画 | 順序の理由 |
|---|---|---|---|
| 1 | `!useAuthHydrated()` | `SellerApplicationSkeleton` | **ストア復元前にロールを判定してはならない。** 復元前の `user` は `null` で、そのまま次段へ進むと BUYER 以外と誤判定してリダイレクトが走る |
| 2 | `user.role !== 'ROLE_BUYER'` | リダイレクト通知（§5.5）＋ `router.replace(ROUTES.seller.applicationRedirect)` | クエリを撃つ前に弾く。SELLER/ADMIN に `GET /seller-applications/me` を投げても意味が無い（200 か 404 かに関わらず画面を見せない） |
| 3 | `query.isPending` | `SellerApplicationSkeleton` | — |
| 4 | `query.isError` | エラーブロック ＋ 再読み込み（§5.6） | 404 は API クライアントで `null` 化済み（`R-3`）。ここに来るのは 5xx・通信断のみ |
| 5 | `data === null` | `SellerApplicationIntro` ＋ `SellerApplicationForm`（`mode="create"`） | **未申請は正常状態**。エラー表示にしない |
| 6 | `data.status === 'PENDING'` | `SellerApplicationStatusCard`（フォーム無し） | 再申請ボタンを**出さない**。押せないボタンを見せるより、存在しない方が明快 |
| 7 | `data.status === 'REJECTED'` | `SellerApplicationStatusCard` ＋ `SellerApplicationForm`（`mode="reapply"`） | 却下理由を**読んでから**書けるよう、必ずカードが上・フォームが下 |
| 8 | `data.status === 'APPROVED'` | リダイレクト通知 ＋ `router.replace(...)` | 段 2 と同じ通知コンポーネントを使う（到達経路が違うだけで、利用者にとっては同じ「もう申請は不要」という結論） |

**この画面へ到達しうる 3 つの経路**（いずれも設計に含める）:

| 経路 | 到達しうるロール | 扱い |
|---|---|---|
| `UserMenu` の「セラー申請」 | `ROLE_BUYER` のみ（`isBuyer` 条件付き・`UserMenu.tsx:93`） | 4 状態すべて |
| `MobileMenuSheet` の「セラー申請」 | 同上 | 同上 |
| `GlobalFooter` の「セラー登録」 | **ロール非依存・未認証も含む**（`GlobalFooter/index.tsx:5`） | 未認証 → `proxy.ts` が `/auth/login?from=...` へ。SELLER/ADMIN → 段 2 のリダイレクト |

> **ヘッダーリンクの表示条件（`seller-application.md` OQ-7 の決定）:** `FRONTEND_IA.md §2` は「申請未済かつ PENDING 申請なしの場合のみ表示」と規定しているが、**現行の `isBuyer` のみを維持する**。条件を厳密化するとグローバルヘッダーが全ページで `GET /seller-applications/me` を叩くことになり、コストが便益に見合わない。**PENDING 中にリンクを踏んだら審査中 UI が出る**挙動を正とする — したがって本画面は「未申請者のためのフォーム」ではなく、**申請済み者にとっても正当な到達先**として設計する（§4 段 6・7）。

**リダイレクトの実装上の注意:**

- `router.replace` は `useEffect` 内で呼ぶ。レンダー中に呼ぶと React が警告を出し、Strict Mode で二重実行される。
- `replace` であって `push` ではない。`push` にすると戻るボタンでこの画面へ戻り、またリダイレクトされる無限ループになる。
- リダイレクト先は **`ROUTES.seller.applicationRedirect` の 1 定数**（暫定値 `'/'`）。段 2 と段 8 で別々の文字列リテラルを書かない（`feature/seller-dashboard` #11 完了時に 1 箇所差し替えるため。OQ-4）。

---

## 5. 画面パーツ仕様

### 5.1 リード文を状態で差し替えない

`<h1>` 直下のリード文は SC にあり、**PENDING でも REJECTED でも同じ文が出る**。

- リード文を状態依存にするには page.tsx を CC 化するか、View がページ見出しごと描画する必要がある。**メタデータと見出しを SC に置く**という `FRONTEND_CODING_STANDARDS` の原則を崩さない。
- 差し替えない代わりに、**リード文は勧誘ではなく「このページで何ができるか」に徹する** — 採用文言「Kivio への出店申請と、審査状況の確認ができます。」。4 状態のうち 2 つ（`PENDING` / `REJECTED`）は**すでに申請した人**が見る画面であり、そこに「出店しませんか」と書くと、状況を見に来た人に決断を促す文が出続けることになる。
- **説得はイントロが担う**（§5.2）。イントロは未申請のときだけ出るので、勧誘の言葉は出る状態が正しく限定される。リード文にまで持たせる必要が無い。
- 状態別の文言はすべて**ステータスカードの中**にある（§6）。

### 5.2 `SellerApplicationIntro` — 制度説明（未申請時のみ）

`MASTER.md §13`「安心ポイント: アイコン + 短文（3ポイント）」のパターンを流用する。**新しいレイアウト語彙を発明しない。**

```tsx
// src/components/seller/SellerApplicationIntro.tsx — SC（状態を持たない）
import { Store, PackagePlus, TrendingUp } from 'lucide-react'

const BENEFITS = [
  { icon: Store, title: 'ショップを開設できます', body: '審査に通ると、あなた専用のショップページが作成されます。' },
  { icon: PackagePlus, title: '商品を登録して販売できます', body: '写真・価格・在庫を登録して、すぐに販売を開始できます。' },
  { icon: TrendingUp, title: '注文と売上を管理できます', body: '注文状況や売上をダッシュボードで確認できます。' },
] as const

export function SellerApplicationIntro() {
  return (
    <section aria-labelledby="intro-heading" className="bg-secondary/50 rounded-xl p-5 sm:p-6">
      <h2 id="intro-heading" className="text-foreground font-serif text-lg font-bold">
        出品者になるとできること
      </h2>
      <ul className="mt-4 space-y-4">
        {BENEFITS.map(({ icon: Icon, title, body }) => (
          <li key={title} className="flex gap-3">
            <Icon className="text-accent mt-0.5 h-5 w-5 shrink-0" aria-hidden />
            <div className="space-y-0.5">
              <p className="text-foreground text-base font-medium">{title}</p>
              <p className="text-muted-foreground text-sm leading-relaxed">{body}</p>
            </div>
          </li>
        ))}
      </ul>
      <div className="text-muted-foreground border-border mt-5 space-y-1 border-t pt-4 text-sm leading-relaxed">
        <p>申請内容を確認のうえ、結果をお知らせします。審査状況はこのページで確認できます。</p>
        <p>送信後の取り消しはできません。内容をご確認のうえ送信してください。</p>
      </div>
    </section>
  )
}
```

**仕様と禁止事項:**

| 項目 | 指定 |
|---|---|
| 面 | `bg-secondary/50 rounded-xl p-6`（未申請時における**唯一の塗り面**・§1）。影は使わない |
| アイコン | Lucide `w-5 h-5`・`text-accent`（ティール）。**絵文字禁止**（`MASTER.md §11`） |
| 3 点の内容 | Phase 2 で**実際に到達できる機能だけ**を書く。売上手数料・振込サイクル・分析機能など未実装の話は書かない |
| **審査期間を数字で書かない** | 「3営業日以内」等の SLA は**書かない**。審査は `feature/admin`（#12）が未実装で、運用の実績値も無い。守れない約束を UI に置かない（`user-profile.md §8.1` の「アップロードできるように見える UI にしない」と同じ判断） |
| **通知手段を約束しない** | 「メールでお知らせします」と書かない。メール通知は `feature/mail-notification`（#14）、アプリ内通知は `feature/notification`（#10）で**いずれも未実装**。現時点で確実なのは「このページで確認できる」ことだけなので、そう書く |
| 表示条件 | `data === null`（未申請）のときのみ。**REJECTED では出さない**（§1 の塗り面規律・説得は既に済んでいる） |

### 5.3 `SellerApplicationForm` — 申請理由の入力

```
申請理由                                          ← h2 font-serif text-lg
────────────────────────────────────────────────
どのような商品を販売したいか、これまでの経験などをご記入ください。

┌────────────────────────────────────────────────┐
│                                                │
│                                    min-h-40    │
│                                                │
└────────────────────────────────────────────────┘
1000文字以内                                0 / 1000

                                    [   申請する   ]
```

```tsx
// src/components/seller/SellerApplicationForm.tsx — CC
'use client'
const MAX_REASON_LENGTH = 1000

interface SellerApplicationFormProps {
  /** 'create' = 未申請からの新規申請 / 'reapply' = 却下後の再申請。文言のみが変わる */
  mode: 'create' | 'reapply'
  onSubmitted: () => void
  onConflict: (message: string) => void
}
```

| 項目 | 指定 |
|---|---|
| コンポーネント | shadcn `Textarea`（`@/components/ui/textarea`）。既定 `min-h-16` を **`min-h-40`（160px）に上書き**する |
| ラベル | **セクション見出し `<h2>` はラベルを兼ねない。** `<Label htmlFor="reason">申請理由</Label>` を別途置き、文字列が重複するため **`sr-only`** にする（視覚的な役割は見出しが果たし、支援技術には `<Label>` が入力名を伝える）。§11 参照 |
| 文字数カウンター | 入力欄の**右下**に `text-xs tabular-nums`。`0 / 1000` 形式。`> 1000` で `text-destructive` |
| **`maxLength` を付けない** | ブラウザの `maxLength` は超過分を**無言で切り捨てる**。長文を貼り付けたときに末尾が消えたことに気づけず、IME 変換中の切り詰め事故も起こる。超過は入力させたうえで、カウンターの色とエラーメッセージで気づかせる |
| カウンターの数え方 | `value.length`（**生の長さ**）。zod は `.trim()` 後に評価するため末尾空白分ずれるが、カウンターは**先に赤くなる側**にずれるので安全側 |
| 送信ボタン | `bg-accent text-accent-foreground h-11 w-full sm:w-auto sm:min-w-40`。ラベルは `mode` で「申請する」/「再申請する」 |
| ボタン位置 | `flex justify-end`（`sm` 以上）／`w-full`（`< sm`）。`user-profile.md §7.5` と同じ |
| 送信中 | `Loader2` スピナー ＋ `disabled`（二重送信防止）。ラベルは変えない |
| `autoComplete` | `off`（申請理由に補完候補は無い） |

> **保存モデルの意図的な差分（`user-profile.md §7.5` からの逸脱）**
>
> プロフィール設定の保存ボタンは `isDirty` でないと押せない。**本画面の送信ボタンは `isPending` のとき以外つねに活性にする。**
>
> | 理由 | 説明 |
> |---|---|
> | フィールドが 1 つしかない | 「なぜ押せないのか」を示す手掛かりが画面上に存在しない。複数フィールドなら「どこかを変えれば押せる」と推測できるが、単一フィールドでは行き止まりに見える |
> | 非活性はスクリーンリーダーに何も伝えない | 空のまま送信 → `role="alert"` のフィールドエラーが読み上げられる方が、`disabled` のまま沈黙するより情報量が多い |
> | 部分更新ではない | `PATCH`（差分送信）ではなく `POST`（新規作成）であり、「変更があるか」という概念自体が無い |
>
> つまり `isDirty` ゲートは**部分更新フォーム固有の規約**であって、フォーム全般の規約ではない。両者を区別して継承する。

**成功時の副作用（順序が重要）:**

1. `queryClient.invalidateQueries({ queryKey: queryKeys.sellerApplication.me })`
2. 親（`SellerApplicationView`）の `savedAt` シグナルを更新 → 通知帯に「申請を送信しました」
3. 再取得の結果 `status = PENDING` になり、**フォームがアンマウントされてステータスカードに置き換わる**

> **⚠️ 送信成功のフィードバックは親が持つ。** `SaveStatus` をフォーム内に置くと、上記 3 でフォームごとアンマウントされ、**成功メッセージが一瞬も表示されない**。`savedAt` state と `<SaveStatus>` は必ず `SellerApplicationView` 側に置く。409 の `FormAlert` も同じ理由で親に置く（§7）。

### 5.4 `SellerApplicationStatusCard` — 審査状況

```tsx
// src/components/seller/SellerApplicationStatusCard.tsx — 表示のみ（'use client' を書かない）
interface SellerApplicationStatusCardProps {
  application: SellerApplication
  /** 送信成功でフォームが消えたあと、フォーカスをここへ移すために親が渡す（§11） */
  ref?: Ref<HTMLElement>
}
```

```
┌─────────────────────────────────────────────────┐
│▎ [🕐 審査中]                    2026年8月2日 申請 │
│                                                 │
│  申請理由                                        │
│  ┌───────────────────────────────────────────┐  │
│  │ ハンドメイドのアクセサリーを販売したいです… │  │
│  └───────────────────────────────────────────┘  │
│                                                 │
│  審査には数日かかる場合があります。               │
│  結果が出るとこのページの表示が変わります。        │
└─────────────────────────────────────────────────┘
 ▎= border-l-2（ステータス色）
```

| 要素 | 指定 |
|---|---|
| カード | `rounded-xl border border-border border-l-2 bg-background p-5 sm:p-6`。**影なし**（`shadow-card` は商品カード＝クリック可能要素の合図。`user-profile.md §9.1` の住所カードと同じ判断） |
| 左レール | `border-l-warning`（PENDING）／`border-l-destructive`（REJECTED）／`border-l-success`（APPROVED）。`AccountNav` のアクティブレール・デフォルト住所カードと**同じ語彙**（2px の左罫線＝状態の標識） |
| ヘッダー行 | `flex flex-wrap items-center justify-between gap-2`。左にステータスバッジ、右に申請日 `text-sm text-muted-foreground tabular-nums` |
| 見出し | カード内の小見出し（「申請理由」「却下理由」）は `<h3>` `text-sm font-medium text-muted-foreground`。カード自体の `<h2>` は視覚的に不要なので `sr-only` で「申請状況」を置く（§11） |
| 申請理由の引用 | `bg-muted/60 rounded-lg p-4 text-sm leading-relaxed whitespace-pre-wrap`。**`line-clamp` を使わない**（日本語の切り詰めは事故のもと・`MASTER.md §14`。自分が書いた文章を全文読み返せることに価値がある） |
| 却下理由 | `rounded-lg border border-destructive/20 bg-destructive/5 p-4 text-sm leading-relaxed whitespace-pre-wrap`。`<h3>` は `text-destructive` |
| 日付書式 | `2026年8月2日`。`lib/format.ts` の `formatDate` を再利用（新設しない） |

**ステータスバッジ:**

| status | 表示 | クラス | アイコン |
|---|---|---|---|
| `PENDING` | 審査中 | `bg-warning/10 text-warning border-warning/20 border` | `Clock` |
| `REJECTED` | 却下 | `bg-destructive/10 text-destructive`（`Badge variant="destructive"` の既定と一致） | `XCircle` |
| `APPROVED` | 承認済み | `bg-success/10 text-success border-success/20 border` | `CheckCircle2` |

- **色 ＋ アイコン ＋ 文言の 3 重表現**（色単独禁止・`MASTER.md §14`）。
- `components/ui/badge.tsx` に `warning` / `success` バリアントは**存在しない**。`className` で上書きし、**バリアントを新設しない**（本画面のためだけに共通コンポーネントの API を広げない）。
- `APPROVED` はリダイレクトされるため通常は表示されないが、リダイレクト完了までの数フレームで描画されうるため定義しておく。

**`reviewComment` が無い却下の扱い（重要）:**

`spring.jackson.default-property-inclusion: non_null` により、**審査コメント未記入の却下では `reviewComment` がレスポンスから丸ごと省略される**（`R-8`）。この場合:

- 却下理由ブロックを**出さない**。「理由: なし」「（理由の記載はありません）」等の**否定文を置かない**（`user-profile.md §8.2` の「できないことをわざわざ言わない」と同じ）。
- ステータスバッジ「却下」と再申請フォームは通常どおり出す。**理由が無くても再申請の導線は必ず残す**（SELLER-05）。
- したがって型は `reviewComment?: string | null` で受け、`if (application.reviewComment)` の真値判定で出し分ける（`=== null` 比較にすると `undefined` を取りこぼす）。

### 5.5 リダイレクト通知（`SellerApplicationView` 内のローカル関数）

段 2（SELLER/ADMIN）・段 8（APPROVED）で、`router.replace` の完了までに描画する。**空白や骨組みだけを出さない** — 一瞬とはいえ「何も無いページ」に見え、遷移が事故なのか意図なのか判別できない。

```tsx
function RedirectNotice({ message }: { message: string }) {
  return (
    <p role="status" className="text-muted-foreground flex items-center gap-2 py-10 text-sm">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {message}
    </p>
  )
}
```

| 経路 | 文言 |
|---|---|
| `ROLE_SELLER` | すでに出品者として登録されています。トップページへ移動します。 |
| `ROLE_ADMIN` | 管理者アカウントではセラー申請を行えません。トップページへ移動します。 |
| `APPROVED`（ロールは BUYER のまま＝開発中の不整合状態・`R-6`） | 申請は承認済みです。トップページへ移動します。 |

> `ProfileSettingsForm` がスケルトンを同一ファイル内の非エクスポート関数として持つのと同じ流儀で、`RedirectNotice` も `SellerApplicationView.tsx` 内に閉じる。**3 行の表示のためにファイルを増やさない。**

### 5.6 取得失敗（段 4）

```tsx
<div className="mt-8 space-y-4">
  <FormAlert>申請状況を取得できませんでした。時間をおいて再度お試しください。</FormAlert>
  <Button variant="outline" onClick={() => query.refetch()} className="h-11">
    <RotateCcw className="h-4 w-4" aria-hidden />
    再読み込み
  </Button>
</div>
```

- **再試行はページリロードではなく `refetch()`。** `location.reload()` は認証ストアの再ハイドレートまで巻き戻す。
- `FormAlert` は既存コンポーネントをそのまま使う（`role="alert"` / `aria-live="polite"` を内蔵済み）。

---

## 6. 文言（マイクロコピー）

`VALIDATION_RULES.md §6` とバックエンドの例外メッセージが**正**。ここで新しい言い回しを作らない。

| 位置 | 文言 | 根拠 |
|---|---|---|
| `<h1>` | セラー申請 | `FRONTEND_IA.md §1.3` の画面名 |
| リード文 | Kivio への出店申請と、審査状況の確認ができます。 | 状態非依存（§5.1）。勧誘はイントロに置く |
| イントロ見出し | 出品者になるとできること | 本仕様 |
| イントロ末尾 | 申請内容を確認のうえ、結果をお知らせします。審査状況はこのページで確認できます。 | 未実装機能を約束しない（§5.2） |
| フォーム見出し | 申請理由 / 再申請 | 本仕様 |
| フォーム説明 | どのような商品を販売したいか、これまでの経験などをご記入ください。 | 本仕様 |
| ヘルプ | 1000文字以内 | `VALIDATION_RULES.md §6` |
| 未入力エラー | 申請理由を入力してください | **`VALIDATION_RULES.md §6` と一字一句一致**（zod / Bean Validation 共通） |
| 超過エラー | 申請理由は1000文字以内で入力してください | 同上 |
| 送信ボタン | 申請する / 再申請する | 本仕様 |
| 送信成功 | 申請を送信しました | `SaveStatus` の `message` prop |
| 審査中の説明 | 審査には数日かかる場合があります。結果が出るとこのページの表示が変わります。 | 期間を**断定しない**（§5.2） |
| 却下後の説明 | 内容を見直して、あらためて申請できます。 | SELLER-05 |
| 409（審査中） | 審査中の申請があります。結果が出るまでお待ちください。 | `SellerApplicationPendingException` の文言に準拠 |
| 409（承認済み） | すでに出品者として承認されています。 | `SellerApplicationAlreadyApprovedException` の文言に準拠 |

**書かないもの:**

- 「審査には3営業日かかります」等の具体的な所要日数（運用実績も審査機能も無い）
- 「結果はメールでお知らせします」（メール通知未実装）
- 「申請を取り消すことはできません」→ **書く**。取り下げ API が無いのは事実で、送信前に伝える価値がある。ただしイントロではなく**送信ボタンの直上に小さく**置くと直前の脅しになるため、**イントロ末尾に置く**。
  - 採用文言: 「送信後の取り消しはできません。内容をご確認のうえ送信してください。」

---

## 7. エラー UI 対応表

| API | エラーコード | 表示位置 | 文言・挙動 |
|---|---|---|---|
| `GET /seller-applications/me` | **404** | — | **エラーではない。** API クライアントで `null` に変換し「未申請」として扱う（`R-3`・§4 段 5） |
| `GET /seller-applications/me` | 5xx / 通信断 | 本文領域 | `FormAlert` ＋「再読み込み」ボタン（§5.6） |
| `POST /seller-applications` | `VALIDATION_FAILED`(422) | フィールド直下 `FieldError` | `VALIDATION_RULES.md §6` の文言。**通常は zod が先に弾く**ためサーバー由来の 422 は保険 |
| `POST /seller-applications` | `SELLER_APPLICATION_PENDING`(409) | **通知帯（親）** `FormAlert` | 「審査中の申請があります。結果が出るまでお待ちください。」＋ **`me` を invalidate** → 画面が審査中表示へ切り替わる |
| `POST /seller-applications` | `SELLER_APPLICATION_ALREADY_APPROVED`(409) | 同上 | 「すでに出品者として承認されています。」＋ invalidate → リダイレクトへ流れる |
| `POST /seller-applications` | その他 4xx/5xx・通信断 | フォーム内 `FormAlert` | `resolveApiError` の既定文言（`DEFAULT_API_ERROR` / `NETWORK_ERROR`） |
| 全 API | 401（トークン失効） | — | BFF の single-flight refresh に委ねる。復帰不能なら `proxy.ts` が `/auth/login?from=/seller/applications/new` へ |

> **409 の文言を「親の通知帯」に置く理由:** 409 を受けたら必ず `invalidateQueries` する（他タブ・別デバイスで状態が進んだサインだから）。すると再取得結果に応じて**フォームがアンマウントされる**ため、フォーム内の `FormAlert` は表示された瞬間に消える。文言と invalidate を両立させるには、フォームより長生きする親に文言を持たせるしかない。
>
> 一方、`VALIDATION_FAILED` や通信断は**画面構造が変わらない**ため、フォーム内に出して問題ない。「画面が切り替わるエラーは親・切り替わらないエラーは子」が本画面の切り分け規則。

**エラーコードと文言の対応は `resolveApiError` の `overrides` で渡す**（`lib/apiErrors.ts`・`overrides` に無いコードは `DEFAULT_API_ERROR` に落ちる）。コンポーネント内に `switch` を書かない。

---

## 8. ローディング・スケルトン

`MASTER.md §10`（300ms 以上の非同期にはスケルトン・実物と同寸で CLS 防止）に従う。

```tsx
// src/components/seller/SellerApplicationSkeleton.tsx — SC
export function SellerApplicationSkeleton() {
  return (
    <div className="mt-8 space-y-8">
      {/* イントロ。塗り（bg-secondary/50）は置かない — 淡すぎて bg-muted のバーが沈むため。
          一枚板ではなく実物と同じ行構成で組む（高さが本文の折り返しで変わるので固定高にできない）*/}
      <div className="p-5 sm:p-6">
        <Skeleton className="h-7 w-48" />                   {/* 見出し */}
        <div className="mt-4 space-y-4">
          {[0, 1, 2].map((index) => (                       {/* 3 つの利点 */}
            <div key={index} className="flex gap-3">
              <Skeleton className="size-5 shrink-0" />
              <div className="w-full space-y-1.5">
                <Skeleton className="h-5 w-52" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-2/3 sm:hidden" />{/* 説明文はモバイルのみ 2 行 */}
              </div>
            </div>
          ))}
        </div>
        <div className="border-border mt-5 space-y-2 border-t pt-4">   {/* 末尾の注意書き */}
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      </div>

      <div className="space-y-4">
        <div className="space-y-2">
          <Skeleton className="h-7 w-24" />                 {/* 見出し ＋ 罫線 */}
          <Skeleton className="h-4 w-3/4" />                {/* 説明 */}
        </div>
        <Skeleton className="h-40 w-full rounded-lg" />     {/* テキストエリア */}
        <div className="flex justify-between">              {/* ヘルプ / 文字数カウンター */}
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-16" />
        </div>
        <Skeleton className="h-11 w-full sm:ml-auto sm:w-40" />        {/* 送信ボタン */}
      </div>
    </div>
  )
}
```

| 対象 | 扱い |
|---|---|
| ハイドレート前（段 1）・クエリ取得中（段 3） | 上記スケルトン。**どちらの段でも同じものを出す**（利用者から見れば区別できない待ち時間で、形を変える意味が無い） |
| スケルトンの形 | **未申請（イントロ＋フォーム）に寄せる**。4 状態のうち初回訪問で最も出現頻度が高いのが未申請であり、確率の高い形に寄せた方が確定時のガタつきが小さい |
| 高さの合わせ方 | **一枚板の固定高にしない。** イントロは説明文の折り返しで高さが変わり（デスクトップ約 360px・モバイル約 465px）、`h-44` のような固定高では確定時に 200px 超の押し下げが出る。実物と同じ行構成・同じパディングで組み、折り返しの差は `sm:hidden` の追加行で吸収する |
| リダイレクト中（段 2・8） | スケルトンではなく `RedirectNotice`（§5.5）。骨組みを見せてから消すと「読み込めたのに消えた」ように見える |
| 送信中 | スケルトンに差し替え**ない**。フォームは表示したままボタンをスピナー＋非活性にする（入力内容が消えたように見えるのを防ぐ） |

`Suspense` 境界は置かない。データ取得は `SellerApplicationView`（CC）が TanStack Query で行い、ページは SC のまま見出しだけを返す。

---

## 9. モーション方針

`user-profile.md §11` と同じく、**初回スタガー登場は採用しない**。加えて本画面では**状態が切り替わる瞬間のトランジションも付けない**。

| 対象 | 指定 |
|---|---|
| フォーム → ステータスカードの入れ替え | **アニメーションなし**。切り替えは「送信が受理された」という重い事実であり、演出でなめらかにすると"何かが起きた"感が薄れる。代わりに通知帯（`SaveStatus`）が事実を言葉で伝える |
| 通知帯（`SaveStatus` / `FormAlert`）の出現 | `motion-safe:animate-[auth-fade_150ms_ease-out]`（`globals.css` の既存 keyframes を再利用。**新規 keyframes を足さない**） |
| ボタンのホバー / 活性・非活性 | `transition-colors duration-150`。0ms 切替は禁止（`MASTER.md §14`） |
| 送信中スピナー | `Loader2` ＋ `animate-spin`（`user-profile.md` の各フォームと同一） |
| リダイレクト通知のスピナー | 同上 |
| 文字数カウンターの色変化 | `transition-colors duration-150` |
| テキストエリアの高さ変化 | `field-sizing-content`（shadcn 既定）による自然な伸長。トランジションは付けない（入力中に高さが遅れて伸びると文字位置が揺れる） |

`prefers-reduced-motion: reduce` では `motion-reduce:animate-none` / `motion-reduce:transition-none`。

---

## 10. レスポンシブまとめ

| 要素 | `< sm`（モバイル） | `sm 〜 lg` | `lg 〜` |
|---|---|---|---|
| コンテナ | `max-w-2xl px-6 py-10` | 同左 | `max-w-2xl px-6 py-14` |
| `<h1>` | `text-2xl` | `text-3xl` | `text-3xl` |
| イントロ | `p-5`・アイコン列は縦積みしない（`flex gap-3` のまま） | `p-6` | `p-6` |
| テキストエリア | `min-h-40`（縮めない） | `min-h-40` | `min-h-40` |
| 送信ボタン | `w-full` | `w-auto` 右寄せ `min-w-40` | 同左 |
| ステータスカード | `p-5`・ヘッダー行は `flex-wrap` で 2 行に折り返す | `p-6`・1 行 | `p-6`・1 行 |
| 通知帯 | 本文と同幅 | 同左 | 同左 |

**水平スクロールを出さない:** 長い URL や改行の無い長文が `reason` に入りうる。引用ブロックは `whitespace-pre-wrap` ＋ **`break-words`** を併用する（`break-all` にすると日本語が不自然な位置で折れる）。

---

## 11. アクセシビリティ要件

- **見出し階層:** `<h1>`「セラー申請」はページに 1 つ（SC 側）。イントロ・フォーム・再申請の見出しは `<h2>`。ステータスカード内の「申請理由」「却下理由」は `<h3>`。状態が切り替わっても `<h1>` は変わらない（§5.1）。
- **ステータスカードの見出し:** カード自体には視覚的な `<h2>` を置かないが、`<h2 className="sr-only">申請状況</h2>` を持たせ、見出しジャンプで飛べるようにする。`aria-labelledby` でカードの `<section>` に紐付ける。
- **ラベル:** テキストエリアには `<Label htmlFor="reason">` を必ず置く。セクション見出し `<h2>申請理由</h2>` はラベルの代わりにならない（`MASTER.md §15`「placeholder / 見出しでラベルを代替しない」）。見出しとラベルの文字列が重複するため、**ラベルは `sr-only`** とし、視覚的には見出しが役割を果たす。
- **エラー:** `aria-invalid` ＋ `aria-describedby="reason-error"` で `FieldError` に紐付ける。`FieldError` は `id` を受け取る既存実装をそのまま使う。
- **文字数カウンター:** `aria-hidden="true"`。**ライブリージョンにしない** — 1 文字入力ごとに「1 / 1000」「2 / 1000」と読み上げられると入力が成立しない。上限超過の通知は送信時のエラーメッセージ（`role="alert"` 経由）が担う。
- **通知帯:** 成功は `SaveStatus`（`aria-live="polite"`）、409 は `FormAlert`（`role="alert"`）。既存実装が両方持っているため追加対応は不要。
- **フォーカス管理:** 送信成功でフォームがアンマウントされると、フォーカスが `<body>` に落ちてスクリーンリーダー利用者が現在位置を失う。**成功後はステータスカードの `<section>`（`tabIndex={-1}`）へフォーカスを移す。** リダイレクト時（段 2・8）は移動先ページに委ねるため何もしない。
- **リダイレクト通知:** `role="status"`。無言でページが変わらないようにする。
- **タッチターゲット:** 送信ボタン `h-11`、再読み込みボタン `h-11`。テキストエリアは面積が十分。
- **コントラスト:** `bg-secondary/50` の上の `text-muted-foreground`(#64748B) は 4.5:1 を満たす（`user-profile.md §13` で `bg-secondary/60` について確認済み。より薄い面なので条件は緩い）。`text-warning`(#D97706) を `bg-warning/10` の上に置くバッジは**文字サイズ 12px** のため、`border-warning/20` の枠を併用して形状でも判別できるようにする。
- **色単独禁止:** ステータスは バッジ文言 ＋ アイコン ＋ 色、却下理由ブロックは 見出し「却下理由」＋ 枠 ＋ 色。左レールの色は**装飾**であり、意味はすべてテキストが担う。
- **`prefers-reduced-motion`:** §9。

---

## 12. コンポーネント構成とファイルマッピング

```
src/
├── app/
│   └── (authenticated)/
│       └── seller/
│           └── applications/
│               └── new/
│                   └── page.tsx                  # ★ SC: metadata + h1 + リード文 + <SellerApplicationView />
│
├── components/
│   └── seller/                                   # 既存（.gitkeep のみ）→ 本スライスで中身を作る
│       ├── SellerApplicationView.tsx             # ★ CC: 7 段の分岐 + 通知帯 + RedirectNotice（非公開）
│       ├── SellerApplicationIntro.tsx            # ★ SC: 制度説明（§5.2）
│       ├── SellerApplicationForm.tsx             # ★ CC: reason 入力 + 送信（create / reapply 兼用・§5.3）
│       ├── SellerApplicationStatusCard.tsx       # ★ 表示のみ: PENDING / REJECTED（§5.4）
│       └── SellerApplicationSkeleton.tsx         # ★ SC: ローディング（§8）
│
├── hooks/
│   ├── queries/
│   │   └── useSellerApplicationQuery.ts          # ★ queryKeys.sellerApplication.me
│   └── mutations/
│       └── useCreateSellerApplicationMutation.ts # ★ 成功で me を invalidate
│
├── lib/
│   ├── api/client/
│   │   └── sellerApplications.ts                 # ★ getMySellerApplication（404→null）/ createSellerApplication
│   ├── validations/
│   │   └── sellerApplication.ts                  # ★ sellerApplicationSchema
│   └── constants/
│       └── index.ts                              # 🔄 ROUTES.seller に applicationNew / applicationRedirect を追加
│
└── types/
    ├── enums.ts                                  # 🔄 SellerApplicationStatus を追加（UserRole と同居）
    └── api/
        ├── seller-application.ts                 # ★ SellerApplication 型
        └── index.ts                              # 🔄 re-export
```

**新規の共通コンポーネントは作らない。** `FieldError` / `FormAlert` / `SaveStatus` / `Textarea` / `Badge` / `Button` / `Skeleton` はすべて実装済みのものを再利用する。`badge.tsx` に `warning` バリアントを足すこともしない（§5.4）。

**Client / Server Component 境界**（`FRONTEND_CODING_STANDARDS` の「`'use client'` は葉に限定」に準拠）:

| コンポーネント | SC / CC | 理由 |
|---|---|---|
| `new/page.tsx` | SC | metadata と見出しのみ。状態は子が持つ |
| `SellerApplicationIntro` / `SellerApplicationSkeleton` | SC | 純粋な表示。props も取らない |
| `SellerApplicationStatusCard` | 表示のみ | props を受けるだけ。`'use client'` は書かない（CC からインポートされるためクライアントバンドルには入るが、境界宣言を増やさない） |
| `SellerApplicationView` | CC | `useAuthHydrated` / `useRouter` / TanStack Query / 通知帯の state |
| `SellerApplicationForm` | CC | RHF ＋ mutation |

**クエリキー / mutation 後の副作用:**

| 用途 | キー・副作用 |
|---|---|
| 申請状況 | `queryKeys.sellerApplication.me` → `['seller-application','me']`（`src/lib/queryKeys.ts:58` に**定義済み**） |
| `POST /seller-applications` 成功 | `invalidate(queryKeys.sellerApplication.me)` ＋ 親の `savedAt` シグナル更新 |
| `POST` が 409 | `invalidate(queryKeys.sellerApplication.me)` ＋ 親の通知帯に文言（§7） |

- `staleTime` を**伸ばさない**（既定 0）。管理者が別途審査を進めるため、マウントのたびに最新を取りに行く価値がある。
- `retry` は既定のまま。404 は API クライアントで `null` 化済みなので、リトライ対象になるのは 5xx のみ。
- `useSellerApplicationQuery(enabled)` は **`enabled` を 1 つ受け取る**。§4 段 2 の「クエリを撃つ前に弾く」を実現するには、View が `hydrated && role === ROLE_BUYER` を渡して取得自体を止める必要があるため（`useQuery` は早期 return より前に呼ばれる）。
- **5xx・通信断では invalidate しない。** 失敗した再取得の結果で画面がエラー表示に落ちると、入力中のフォームごと失われる。再取得するのは「状態が実際に進んだ」成功時と 409 のときだけ（§7）。

---

## 13. デザイントークン参照（MASTER.md §2）

| 用途 | トークン | 値 |
|---|---|---|
| イントロ面（未申請時の唯一の塗り面） | `bg-secondary/50` | #EEF3F9 50% |
| イントロのアイコン | `text-accent` | #1A9E87 |
| ページ見出し | `font-serif text-2xl sm:text-3xl font-bold` | — |
| セクション見出し | `font-serif text-lg font-bold` | — |
| 説明文 | `text-sm text-muted-foreground leading-relaxed` | #64748B |
| ステータスカード枠 | `border border-border rounded-xl` | #E2E8F0 / 16px |
| レール（審査中） | `border-l-2 border-l-warning` | #D97706 |
| レール（却下） | `border-l-2 border-l-destructive` | #DC2626 |
| レール（承認済み） | `border-l-2 border-l-success` | #1A9E87 |
| バッジ（審査中） | `bg-warning/10 text-warning border-warning/20` | — |
| バッジ（却下） | `bg-destructive/10 text-destructive` | — |
| 申請理由の引用 | `bg-muted/60 rounded-lg` | #F1F5F9 60% / 12px |
| 却下理由ブロック | `border-destructive/20 bg-destructive/5 rounded-lg` | #DC2626 系 |
| 主 CTA（申請する） | `bg-accent` / `text-accent-foreground` | #1A9E87 / #FFF |
| 送信成功 | `text-success` ＋ `Check`（`SaveStatus` 既定） | #1A9E87 |
| 文字数カウンター（通常 / 超過） | `text-muted-foreground` / `text-destructive` | #64748B / #DC2626 |
| 入力・ボタン高さ | `h-11` / テキストエリア `min-h-40` | 44px / 160px |
| カード角丸 | `rounded-xl`（カード）/ `rounded-lg`（内側ブロック） | 16px / 12px |

**新規 HEX の持ち込みは無し**（`MASTER.md §14`）。不透明度サフィックス（`/60` `/50` `/20` `/10` `/5`）のみで濃淡を作る。

> **未解決（本画面では直さない）— ステータスバッジのコントラスト比**
>
> `MASTER.md §「バッジ / タグ」` が定める `bg-{tone}/10 text-{tone}` の組み合わせは、白地の上で WCAG AA（通常文字 4.5:1）に届かない。バッジ本文は `text-xs`（12px）なので大文字扱いの緩和も効かない。
>
> | バッジ | 前景 / 背景（実効値） | コントラスト比 | AA |
> |---|---|---|---|
> | 審査中 | `#D97706` / `bg-warning/10` ≒ `#FBF1E6` | **2.84:1** | ✗ |
> | 却下 | `#DC2828` / `bg-destructive/10` ≒ `#FBE9E9` | **4.12:1** | ✗ |
> | 承認済み | `#1C9C8B` / `bg-success/10` ≒ `#E8F5F3` | **3.05:1** | ✗ |
>
> 本画面固有の問題ではなく `MASTER.md` 由来で、`badge.tsx` の `destructive` バリアント・トップページの騰落表示など**プロダクト全体で同じ組み合わせが使われている**。本画面だけ配色を変えると、同じ意味の記号が画面ごとに違って見える。したがって**修正は MASTER.md 側（トークンかバッジ規約）で行う**。候補は 2 つ:
>
> 1. 文字だけ `text-foreground` にし、色は塗り・枠線・アイコン・レールで持たせる（トークン追加なし）
> 2. 淡色面の上に載せる用の濃い前景トークン（`--warning-strong` 等）を足す（`MASTER.md §2` の改訂が必要）
>
> なお本画面は**色単独で状態を表していない**（バッジの文言・アイコン・左レール・カード内の説明文が同じ情報を持つ）ため、色が読み取れないことによる情報の欠落は起きない。欠けているのは文字の可読性のみである。

---

## 14. 実装優先度（`seller-application.md` タスクとの対応）

| 優先度 | 項目 | 対応タスク | 状態 |
|---|---|---|---|
| P0 | `types/api/seller-application.ts` ＋ `enums.ts` ＋ `ROUTES.seller.applicationNew` / `applicationRedirect` | SA-15 | ✅ |
| P0 | zod スキーマ（`validations/sellerApplication.ts`・§6 の文言と一致させる） | SA-16 | ✅ |
| P0 | API クライアント（**404→null**）＋ query / mutation フック | SA-17 | ✅ |
| P0 | `page.tsx` ＋ `SellerApplicationView`（7 段の分岐・§4） | SA-18 | ✅ |
| P0 | `SellerApplicationIntro` / `SellerApplicationForm` / `SellerApplicationStatusCard` / `SellerApplicationSkeleton` | SA-18 | ✅ |
| P1 | コンポーネントテスト（RTL + MSW・4 状態 + ロール分岐 + 409） | SA-19 | ✅ |
| P2 | `ROUTES.seller.applicationRedirect` を `/seller/dashboard` へ差し替え | `feature/seller-dashboard`（#11） | ⬜ |
| P2 | 審査結果の通知導線（通知タイプ `SELLER_APPLICATION` → 本画面） | `feature/notification`（#10） | ⬜ |

---

## 15. Open Questions

本設計書の作成にあたり検討した論点。**すべて 2026-08-02 時点でクローズ済み**（`OQ-S3` / `OQ-S4` は実装計画書側の OQ-4 / OQ-7 の決定をそのまま引く）。

| # | 論点 | 決定 | 反映先 |
|---|---|---|---|
| **OQ-S1** | **審査の所要期間を書くか** | **書かない。** 審査機能（`feature/admin` #12）が未実装で運用実績も無い。「数日かかる場合があります」という幅のある表現に留め、日数を断定しない | §5.2 / §6 |
| **OQ-S2** | **通知手段を書くか**（「メールでお知らせします」） | **書かない。** メール（#14）・アプリ内通知（#10）とも未実装。確実なのは「このページで確認できる」ことだけなので、そう書く | §5.2 / §6 |
| **OQ-S3** | **APPROVED / SELLER / ADMIN のリダイレクト先** | 実装計画 OQ-4 の決定に従い `ROUTES.seller.applicationRedirect`（暫定 `'/'`）の 1 定数を 3 経路すべてが参照する | §4 / §12 |
| **OQ-S4** | **ヘッダーの「セラー申請」リンクの表示条件** | 実装計画 OQ-7 の決定に従い現行の `isBuyer` のみを維持。結果として**本画面は申請済み者の到達先でもある**ため、PENDING / REJECTED を「正当な表示」として設計する | §4 |
| **OQ-S5** | **送信ボタンを `isDirty` で非活性にするか**（`user-profile.md §7.5` との整合） | **しない。** 単一フィールドでは非活性の理由を示す手掛かりが無く、スクリーンリーダーにも何も伝わらない。`isDirty` ゲートは部分更新フォーム固有の規約と位置づける | §5.3 |
| **OQ-S6** | **テキストエリアに `maxLength` を付けるか** | **付けない。** 貼り付け時の無言切り捨てと IME 変換中の切り詰めを避け、超過はカウンターの色とエラーメッセージで伝える | §5.3 |
| **OQ-S7** | **却下理由が空（`reviewComment` 省略）のときの表示** | **理由ブロックごと出さない。** 「理由なし」等の否定文を置かない。バッジと再申請フォームは通常どおり出す | §5.4 |
| **OQ-S8** | **409 の文言をどこに出すか** | **親（`SellerApplicationView`）の通知帯。** 409 では必ず invalidate するためフォームがアンマウントされ、フォーム内に出すと文言が一瞬で消える。「画面が切り替わるエラーは親・切り替わらないエラーは子」を切り分け規則とする | §5.3 / §7 |
| **OQ-S9** | **申請履歴（過去の却下）を一覧表示するか** | **しない。** 一覧 API が仕様に存在せず（`GET /me` は最新 1 件のみ）、`API_DESIGN.md §4` にも定義が無い。必要になれば API 追加とセットで別スライスに起票する | §4 |
| **OQ-S10** | **`/seller/*` のダークモード拡張時に本画面を含めるか** | **含めない。** 本画面は実質バイヤー面であり、申請前後で配色が反転すると同一 URL が別サービスに見える | §2 |

---

## 16. frontend-design レビュー反映ログ（2026-08-02）

`auth.md §17` / `user-profile.md §18` と同じ基準でレビューした。**確立済みアイデンティティ（Navy × Teal / Noto Serif・Sans JP / ライト固定 / HEX 直書き禁止 / 商品を主役）を侵さない範囲**でのみ、スキルの指針を採用している。

### 採用した点

| frontend-design の指針 | 本仕様への反映 | 合致理由 |
|---|---|---|
| **状態を「画面の主役」に据える** | 4 状態すべてで「状態 → 理由 → 行動」の縦一本に統一し、状態ごとにレイアウトを作り分けなかった（§1・§3.3） | 同一 URL が別ページに見えるのを防ぐ。利用者は「申請したページに戻ってきた」と認識できる |
| **既存語彙の再利用（新しい記号を作らない）** | 状態の標識に **2px の左レール**を使った。`AccountNav` のアクティブ表現・デフォルト住所カードと同じ記号で、`/profile/*` を使ったことのある人には既知の語彙になる（§5.4） | プロダクト全体で記号の意味が揺れない。新しいコンポーネントもバリアントも増えない |
| **Typography — 見出しに性格を持たせる** | `<h1>` と各セクション見出しを `font-serif` で通した（`user-profile.md` と同じ運用） | 罫線と余白しかない画面に唯一の"声"を与える。フォントは既定のまま使い分けだけで対応 |
| **Motion — 高インパクトな瞬間に集約** | 状態の入れ替えに**アニメーションを付けない**判断（§9）。「送信が受理された」瞬間はモーションではなく**言葉**（通知帯）で伝える | なめらかな遷移は"何かが起きた"感を薄める。取り消せない操作の完了は、はっきり見せる方が誠実 |
| **意図的なコントラスト** | 塗り面を状態ごとに 1 つだけに制限した（§1）。未申請＝イントロ、却下＝却下理由。**その状態で最も読むべきものだけが色を持つ** | 面の数ではなく面の位置で優先順位を伝える。`user-profile.md` の「塗り面 1 箇所」規律の応用 |

### あえて見送った点

| 指針 | 不採用の理由 |
|---|---|
| ヒーロー的な勧誘ビジュアル（イラスト・グラデーション・大型バナー） | 「5分でお店が開ける」型の LP は `MASTER.md §13` のトップページ用パターン。ここは**すでに申請しようとしている人**が来る画面で、説得はイントロ 3 点で足りる。画像アセットも存在しない |
| ステップインジケーター（申請 → 審査 → 承認 の 3 ステップ表示） | ステップが進むのは**管理者の操作**であり、利用者が能動的に進められない。進捗バーは「自分で次に進める」ことを示唆する記号なので誤解を生む。ステータスバッジ 1 つで十分 |
| フォームのマルチステップ化 | 入力フィールドは `reason` の 1 つだけ。分割する対象が無い |
| 却下理由を折りたたみ（Accordion）にする | 再申請の材料そのもの。畳んで隠すのは目的に反する |
| 申請内容のプレビュー / 確認画面 | フィールドが 1 つで、送信後もステータスカードで全文を読み返せる。確認ステップは摩擦のみが増える |
| トースト（Sonner）での成功通知 | `user-profile.md §7.6` と同じ判断。`sonner` は未導入で、`SaveStatus` のインライン表示で足りる。新規依存を足さない |

**結論:** `user-profile.md` が確立した語彙（Serif 見出し・ティールの CTA・2px の左レール・44px タッチターゲット・塗り面 1 箇所）をそのまま継承し、構造だけを「並列する区画（台帳）」から**「1 本の縦の流れ（窓口）」**に置き換えた。共有部品は 1 つも増えず、増えたのは `components/seller/` の 5 ファイルだけである。

---

## 17. 実装反映ログ（2026-08-02・SA-15〜SA-19）

実装（`seller-application.md` SA-15〜SA-19）で確定した、本仕様の記述だけでは決まらなかった点。**実装だけを変えず、設計書側にも反映済み。**

| # | 論点 | 実装での決定 | 反映先 |
|---|---|---|---|
| 1 | **却下理由ブロックの置き場所** — §3.3 の図ではカードの外（兄弟）に見えるが、§5.4 の要素表と §11 のフォーカス設計はカードの一部として書かれている | **カード内**（申請理由の引用の下）に置いた。カードは「1 つの状態」を表す単位であり、フォーカスの受け皿（`<section tabIndex={-1}>`）も 1 つで済む | §3.3 の図をカード内に描き直した |
| 2 | **`<h1>` のサイズ** — §3.5 のコード例は `text-3xl` 固定だが、§10 の表は `< sm` で `text-2xl` | **§10 を採った**（`text-2xl sm:text-3xl`）。レスポンシブ表のほうが具体的で、モバイルで 30px の見出しは行が折れる | §3.5 のコード例は据え置き（§10 が正） |
| 3 | **取り消し不可の告知** — §6 で「イントロ末尾に置く」と決めたが §5.2 のコード例に入っていなかった | イントロ末尾の罫線下に 2 文並べた。コード例も更新 | §5.2 |
| 4 | **クエリを撃たずに弾く方法** | `useSellerApplicationQuery(enabled)` に `enabled` を 1 つ渡す形にした。`useQuery` は早期 return より前に呼ばれるため、これが無いと SELLER / ADMIN でも `GET /me` が飛ぶ | §12 |
| 5 | **5xx で invalidate するか** | **しない。** 409 と成功時のみ。失敗した再取得で画面がエラー表示に落ちると、入力中のフォームごと失われる | §12 |
| 6 | **ステータスカードへのフォーカス移動の実装** | React 19 の ref-as-prop で `SellerApplicationStatusCard` が `ref` を受け取り、View 側は「送信成功フラグ ＋ カードが現れたら 1 度だけ focus」で拾う。カードは再取得の完了後に初めて現れるため、`savedAt` の変化だけを見ると空振りする | §5.4 |

### 追記（同日・frontend-design レビュー後）

実装後にデザインレビューを通し、**設計書側の指定そのものに問題があった 4 点**を設計書ごと直した。

| # | 指摘 | 直した内容 | 反映先 |
|---|---|---|---|
| 7 | **スケルトンが実物より 200px 以上短い** — §8 のコード例（`h-44` の一枚板）はイントロの実寸（デスクトップ約 360px・モバイル約 465px）と合わず、確定時に本文が大きく押し下げられていた。`MASTER.md §10`「実物と同寸で CLS 防止」に反する | 一枚板をやめ、実物と同じ行構成・パディングで組み直した。文字数カウンター行も追加。ズレはデスクトップ約 43px・モバイル約 80px まで縮小 | §8 |
| 8 | **`FormAlert` の出現にモーションが無い** — §9 は「通知帯（`SaveStatus` / `FormAlert`）の出現に `auth-fade` 150ms」と定めていたが、`SaveStatus` が内蔵しているのに対し `FormAlert` は共通コンポーネント側に持っていないため、指定が実装に落ちていなかった | 本画面の 3 箇所（409 の通知帯・取得エラー・送信エラー）に `motion-safe:animate-[auth-fade_150ms_ease-out]` を付けた。`FormAlert` 自体は変更しない（他画面の出方を変えないため） | §9 |
| 9 | **`REJECTED` で塗り面が 2 つ出ていた** — §1 は「同時に出る塗り面は 1 つまで・`REJECTED` の唯一の塗り面は却下理由ブロック」と定めているのに、申請理由の引用（`bg-muted/60`）と却下理由（`bg-destructive/5`）が並んでいた。しかも `bg-muted/60` の方が濃く、**読ませたい却下理由より自分の申請文が目立つ**逆転が起きていた | 却下理由が出るときだけ、申請理由の引用を塗りから枠線（`border border-border`）に落とした。却下理由が省略された却下では引用が唯一の塗り面になるため塗りのまま | §1 / §5.4 |
| 10 | **リード文が申請済みの人に勧誘を出し続ける** — 旧文言「Kivio に出店して、あなたの商品を販売しませんか。」。§5.1 はこれを「一見不自然だが許容する」としていたが、許容の理由は SC/CC 境界の話であって**文言そのものを勧誘にしておく理由にはなっていない**。可視 3 状態のうち 2 つ（審査中・却下）は申請済みの人が見る | リード文を状態非依存のまま「Kivio への出店申請と、審査状況の確認ができます。」に変更。説得はイントロ（未申請時のみ表示）が担うため、勧誘の言葉が出る状態が正しく限定される。SC/CC 境界は元のまま | §5.1 / §6 |

**保留:** ステータスバッジのコントラスト比（AA 未達）。原因が `MASTER.md` のバッジ規約でプロダクト全体に及ぶため、本画面だけの修正は行っていない。詳細と修正候補は §13 の注記を参照。

**乖離なし:** 7 段の分岐と順序（§4）、塗り面の規律（§1・上記 #9 で実装を合わせた）、`isDirty` ゲート不採用（OQ-S5）、`maxLength` 不使用（OQ-S6）、`reviewComment` 省略時の扱い（OQ-S7）、409 の文言を親に置く（OQ-S8）、新規の共通コンポーネント・`badge` バリアントを増やさない（§12）は仕様どおり実装され、いずれも回帰テストが付いている（実装計画 §9.2）。
