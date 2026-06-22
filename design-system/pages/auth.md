# 認証画面レイアウト設計仕様

# ログイン / 会員登録（OTP 3 ステップ）

**対象画面:** `/auth/login`, `/auth/register`
**対象コンポーネント:** `(auth)/layout.tsx`, `LoginForm`, `RegisterFlow`（`RegisterEmailStep` / `RegisterOtpStep` / `RegisterPasswordStep`）, `GoogleSignInButton`, `AuthBrandPanel`, `AuthMinimalFooter`
**スコープ:** `(auth)` ルートグループ専用レイアウト（グローバルヘッダー・グローバルフッターを**使わない**）
**MASTER.md との関係:** このファイルのルールが MASTER.md を上書きする（§16 準拠）
**layout.md との関係:** `(auth)/*` は `layout.md` の `GlobalHeader` / `GlobalFooter` / `MobileBottomNav` を**いずれも使用しない**。本ファイルが認証系の chrome を独立定義する。
**実装タスク:** `docs/implementation-plans/auth.md` T-16（コンポーネント）/ T-17（ページ）
**作成日:** 2026-06-13

---

## 1. 設計原則 — なぜ認証画面は独自 chrome を持つのか

認証画面（ログイン・登録）は回遊用ページではなく**フォーム完遂を唯一の目的とするページ**である。したがってトップページ等の共通 chrome（`GlobalHeader` / `GlobalFooter`）をそのまま使わず、専用の最小 chrome を持つ。判断の根拠:

| 原則 | 適用 |
|---|---|
| **出口（escape hatch）を減らす** | グローバルヘッダーの検索・カテゴリ・カート、フッターの12本のマーケ導線はすべて「フォーム離脱経路」。認証フロー中断＝離脱（特にチェックアウト前ログインはカゴ落ちに直結）するため、ヘッダーは全省略・フッターは最小化する。 |
| **信頼と法令の最小要件は残す** | ログインは個人情報・認証情報を入力する最も警戒される場面。法的必須（プライバシー・利用規約・特商法・著作権）と「ログインできない時の駆け込み先」（ヘルプ）だけは残す。マーケ導線（セラー登録・採用・キャンペーン）は出さない。 |
| **別レイアウトグループで分離** | `(auth)` ルートグループ専用 `layout.tsx` に隔離し、共通レイアウトのキャンペーンバナーや A/B テスト差し込みが認証画面に波及しないようにする。 |
| **視覚的重心をフォームへ** | `bg-primary` の重い4カラムフッターは視覚重心を下に引っ張りフォーム集中を阻害する。1行の軽量フッターにする。 |
| **モバイルの縦スクロール最小化** | ソフトキーボード出現時に「送信ボタンが遠い」UX を避けるため、フォーム下に長大フッターを置かない。 |

**結論:** 認証画面の chrome は「通常 chrome の削除」ではなく、**通常フッターから "出口リンク" を抜き "信頼リンク" だけ残した最小バリアント**＋ヘッダー全省略、と定義する。

---

## 2. ダークモード方針

`layout.md §1` に準拠し**ライトモード固定**。`(auth)/layout.tsx` の祖先に `dark` クラスを付けない。Phase 2 はダークモード無効。

---

## 3. 全体レイアウト構造（Split 2 カラム）

### 3.1 デスクトップ / タブレット横（md 〜）

左に**ブランドパネル（イラスト）**、右に**フォームカラム**を置く左右 2 分割。グローバルヘッダーは置かない（ロゴは左パネル内）。

フッターは右カラム内ではなく**画面全幅**（左パネル＋右フォームの下を横断）に置く。全体は「上=Split 行（`flex-1`）／下=全幅フッター」の縦 2 段構成。

```
┌───────────────────────────┬───────────────────────────────┐
│  AuthBrandPanel (md〜表示) │  Form Column                  │
│  bg-primary               │  bg-background                │
│  ─────────────────────    │  ───────────────────────────  │
│  [Kivio ロゴ（白）]        │                               │
│                           │     ┌───────────────────┐     │
│  日本中の個人から          │     │ Kivio へようこそ    │     │
│  最高の商品を。            │     │ メールアドレス      │     │
│                           │     │ パスワード          │     │
│  ■ login-illustration ■   │     │   パスワードを忘れた方│     │
│  ■                    ■   │     │ [ ログイン ]        │     │
│  ■                    ■   │     │ ─── または ───     │     │
│                           │     │ [G] Google で続行   │     │
│                           │     │ アカウント新規登録   │     │
│                           │     └───────────────────┘     │
│                           │  （カード枠なし・背景直置き）   │
├───────────────────────────┴───────────────────────────────┤
│  AuthMinimalFooter — 画面全幅                              │
│  © 2026 Kivio · プライバシー · 利用規約 · 特商法 · ヘルプ   │
└────────────────────────────────────────────────────────────┘
       lg: 5/12 幅              lg: 7/12 幅
       md: 1/2 幅               md: 1/2 幅
```

フッターを全幅にしたことで、左ブランドパネルの下端は画面最下部ではなく**フッター上端**に揃う（Split 行の高さ = `100dvh − フッター高`）。

**カラム比率:**

| ブレークポイント | 左（ブランド） | 右（フォーム） |
|---|---|---|
| `md`（768〜1024） | `1/2` | `1/2` |
| `lg`（1024〜） | `5/12`（≈42%） | `7/12`（≈58%） |
| `< md` | **非表示** | 全幅（§4 モバイル参照） |

**高さ:** 両カラムとも `min-h-dvh`（`dvh` でモバイルのアドレスバー伸縮に追従）。フォームカラムは内部で `flex flex-col` とし、フォーム本体を**垂直中央**・フッターを**最下部**に固定する。

### 3.2 `(auth)/layout.tsx` 骨格

```tsx
// src/app/(auth)/layout.tsx — Server Component（"use client" 不要）
import { AuthBrandPanel } from "@/components/auth/AuthBrandPanel";
import { AuthMinimalFooter } from "@/components/auth/AuthMinimalFooter";
import { AuthMobileLogo } from "@/components/auth/AuthMobileLogo";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    // 縦 2 段: 上 = Split 行（flex-1）／下 = 全幅フッター
    <div className="min-h-dvh flex flex-col bg-background">
      {/* Split 行: 画面の上部いっぱいを占める */}
      <div className="flex-1 grid md:grid-cols-2 lg:grid-cols-12">
        {/* 左: ブランドパネル（md 未満は非表示） */}
        <AuthBrandPanel className="hidden md:flex lg:col-span-5" />

        {/* 右: フォームカラム */}
        <div className="flex flex-col lg:col-span-7">
          {/* モバイル専用: 上部中央ロゴ（md 以上は左パネルにロゴがあるので非表示） */}
          <AuthMobileLogo className="md:hidden" />

          {/* フォーム本体: 垂直中央 */}
          <main id="main-content" className="flex-1 flex items-center justify-center px-6 py-10">
            <div className="w-full max-w-100">{children}</div>   {/* max-w-100 = 400px */}
          </main>
        </div>
      </div>

      {/* 最小フッター: 画面全幅（Split 行の外・最下段） */}
      <AuthMinimalFooter />
    </div>
  );
}
```

**スキップリンク:** `(auth)/layout.tsx` 先頭にも `layout.md §12` と同形のスキップリンクを置き、`#main-content` に対応させる。

---

## 4. モバイル（< md）レイアウト

ブランドパネルは**完全非表示**。イラストは縦長構図のためモバイル上部バナーへのトリミングが難しく、キーボード出現時の縦スクロールも増えるため出さない。代わりに**上部中央に Kivio ロゴ**を置き、フォームを中央に据える。背景はフォームカラムと同じ `bg-background`（白地）。

```
┌──────────────────────────┐
│         [Kivio]          │  ← AuthMobileLogo（py-8, 中央, → /）
│                          │
│   Kivio へようこそ        │
│   メールアドレス          │
│   パスワード              │
│        パスワードを忘れた方 │
│   [ ログイン ]            │
│   ─── または ───          │
│   [G] Google で続行       │
│   アカウント新規登録       │
│                          │
│  （余白で中央寄せ）        │
│  ─────────────────────   │
│  © 2026 Kivio · 規約…    │  ← AuthMinimalFooter（全幅）
└──────────────────────────┘
```

```tsx
// src/components/auth/AuthMobileLogo.tsx — SC
export function AuthMobileLogo({ className }: { className?: string }) {
  return (
    <div className={cn("flex justify-center pt-8 pb-2", className)}>
      <Link href="/" className="flex items-center gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-md">
        <Image src="/images/kivio-logo.svg" alt="" width={32} height={32} priority />
        <span className="font-serif text-xl font-bold text-primary leading-none">Kivio</span>
      </Link>
    </div>
  );
}
```

ロゴは `/`（トップ）へのリンクとする（認証を中断してトップへ戻る正規の出口を 1 つだけ残す）。

---

## 5. ブランドパネル（左・md 以上）

### 5.1 アセット

| 用途 | ファイル | 形式・備考 |
|---|---|---|
| ブランドパネル主役イラスト | `public/images/login-illustration.png` | 縦長（≈2:3）。畳の部屋でラップトップを操作する女性＋浮遊する商品（バッグ・アクセサリー・花瓶・キャンドル・トップス）。ティールのアクセント光。**現在は `docs/design/frontend/img/login-illustration.png` にあるため、実装時に `public/images/` へコピーすること。** |
| ロゴ（白反転） | `public/images/kivio-logo.svg` | `brightness-0 invert` で白化（`layout.md §9.2` と同手法） |
| グレインテクスチャ | `public/images/noise.svg` | ブランドパネルの質感付与用（§5.2）。フラクタルノイズの軽量 SVG（数 KB）。`aria-hidden` 装飾。未用意時は当レイヤーを省略してもデザイン要件は満たす（任意強化）。 |

### 5.2 構成

`bg-primary`（#1E3A5F）の濃紺地をベースに、**質感と奥行きを与える装飾レイヤー**（上下シェーディング・accent グロー 2 層・微細グレイン）を重ね、その上にロゴ＋セリフ見出しのタグライン、下半分にイラストを配置する。単色ベタ塗りではなく低彩度の奥行きを持たせることで、フォーム側の白面との対比をブランド表現に変える（frontend-design「atmosphere & depth over flat solid」を**ブランドパネルに限定**して採用。フォーム側＝商品/入力面には持ち込まない）。

> **トークン安全:** 奥行きは `bg-primary` ＋ `white/5` `black/25` の不透明度オーバーレイと `bg-accent/10〜15` のグローのみで表現し、**新規 HEX を持ち込まない**（`MASTER.md §14` 禁止パターン）。グレインは装飾用 SVG（`public/images/noise.svg`、`aria-hidden`）。

```tsx
// src/components/auth/AuthBrandPanel.tsx — SC
export function AuthBrandPanel({ className }: { className?: string }) {
  return (
    <aside
      className={cn(
        "relative flex-col justify-between overflow-hidden bg-primary text-primary-foreground p-10",
        className
      )}
    >
      {/* 奥行きレイヤー（すべて装飾・aria-hidden） */}
      {/* 1. 上→下のシェーディング: 濃紺の単調さ・グラデーションバンディングを回避 */}
      <div aria-hidden className="absolute inset-0 bg-gradient-to-b from-white/5 via-transparent to-black/25" />
      {/* 2. accent グロー 2 層: 光源の奥行き（ロゴ矢印ティールの残光） */}
      <div aria-hidden className="absolute -top-20 -right-24 w-80 h-80 rounded-full bg-accent/15 blur-3xl" />
      <div aria-hidden className="absolute -bottom-28 -left-16 w-96 h-96 rounded-full bg-accent/10 blur-3xl" />
      {/* 3. 微細グレイン: 平面の質感付与（mix-blend-soft-light で控えめに） */}
      <div
        aria-hidden
        className="absolute inset-0 opacity-[0.12] mix-blend-soft-light [background-image:url('/images/noise.svg')] [background-size:180px]"
      />

      {/* 上部: ロゴ + タグライン */}
      <div className="relative z-10 space-y-6">
        <Link href="/" className="flex items-center gap-2 w-fit focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-foreground/60 rounded-md">
          <Image src="/images/kivio-logo.svg" alt="" width={36} height={36} className="brightness-0 invert" priority />
          <span className="font-serif text-2xl font-bold leading-none">Kivio</span>
        </Link>
        <div className="space-y-4">
          <p className="font-serif text-2xl lg:text-3xl font-bold leading-snug tracking-tight text-balance">
            日本中の個人から、<br />最高の商品を。
          </p>
          {/* 装飾: accent の細い下線（エディトリアルなリズム） */}
          <span aria-hidden className="block w-12 h-0.5 rounded-full bg-accent" />
        </div>
      </div>

      {/* 下部: イラスト */}
      <div className="relative z-10 mt-8 flex justify-center">
        <Image
          src="/images/login-illustration.png"
          alt=""                          {/* 装飾画像 → alt="" */}
          width={520}
          height={780}
          className="w-full max-w-90 h-auto rounded-2xl"
          priority
        />
      </div>
    </aside>
  );
}
```

**注意:**
- イラスト・グロー・シェーディング・グレインはすべて**装飾**（`alt=""` / `aria-hidden`）。意味はタグラインのテキストが担う。
- `priority` 付与（左パネルの LCP 要素）。グレイン SVG は数 KB の軽量アセットに留め、LCP を阻害しない。
- タグラインは `layout.md §9` フッターのコピー「日本中の個人から最高の商品を。」とブランド統一。`tracking-tight text-balance` で 2 行の折り返しを整える。
- 奥行きレイヤーはあくまで**低彩度・低コントラスト**。白ロゴ／タグラインのコントラスト比 4.5:1 を侵さない範囲（グローは `blur-3xl` で拡散、グレインは `opacity-0.12`）に抑える。

---

## 6. フォームカラム（右）

### 6.1 背景・コンテナ

- 背景は `bg-background`（白）。**カード枠・影は使わない**（フォーム直置き）。左の濃紺パネルとの色面コントラストが境界を担うため、カードは不要。
- フォーム最大幅 `max-w-100`（400px）。`px-6 py-10` で上下左右に余白。
- 垂直中央寄せ（`§3.2` の `flex-1 flex items-center`）。

### 6.2 フォーム内の見出し

各画面の最上部にセリフ見出しを置く。**ログイン⇄会員登録の相互リンクは見出し下ではなくフォーム末尾**に配置する（参考画像 `image.png` 準拠。§7.1 / §8）。

ログイン画面の見出し（補助文なし。ウェルカム訴求）:

```tsx
<header className="mb-8">
  <h1 className="font-serif text-2xl font-bold text-foreground">Kivio へようこそ</h1>
  {/* 代替案: 「おかえりなさい」。リターンユーザー前提なら温かみのある文言を選ぶ */}
</header>
```

登録画面の見出しは各ステップ見出し（§8.1）を `<h1>` とする。

### 6.3 入力フィールド — フローティングラベル

認証系の**すべてのテキスト入力**（ログイン: email / password、登録: email / 表示名 / password / passwordConfirm）は**フローティングラベル**方式に統一する。未入力時はラベルがフィールド内中央に「プレースホルダー風」に表示され、フォーカスまたは入力開始でラベルが縮小して上部へ浮上し、ラベルとして残る。

> **例外:** Step2 の OTP（6 桁分割入力 `InputOTP`）はフローティングラベルを使わない（分割セルにラベルを内包できないため）。OTP はステップ見出し（§8.3）＋各セルに `aria-label` で代替する。

#### 6.3.1 共通コンポーネント `FloatingLabelInput`

CSS の `:placeholder-shown` / `:focus` の peer 連動で実現し、JS の状態管理は不要。`forwardRef` で react-hook-form の `register` に対応する。

```tsx
// src/components/ui/FloatingLabelInput.tsx — CC
import { forwardRef, useId } from "react";
import { cn } from "@/lib/utils";

interface FloatingLabelInputProps extends React.ComponentProps<"input"> {
  label: string;
  error?: boolean;
}

export const FloatingLabelInput = forwardRef<HTMLInputElement, FloatingLabelInputProps>(
  function FloatingLabelInput({ label, error, className, id, ...props }, ref) {
    const autoId = useId();
    const inputId = id ?? autoId;
    return (
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          placeholder=" "                          // 必須: 空白プレースホルダーで :placeholder-shown を有効化
          aria-invalid={error || undefined}
          className={cn(
            "peer w-full h-14 rounded-lg border bg-background px-3 pt-5 pb-1 text-base text-foreground",
            "outline-none transition-[color,border-color,box-shadow] duration-150",
            "border-input focus:border-accent focus:ring-2 focus:ring-ring",
            "aria-[invalid=true]:border-destructive aria-[invalid=true]:focus:ring-destructive/40",
            "motion-reduce:transition-none",
            className
          )}
          {...props}
        />
        <label
          htmlFor={inputId}
          className={cn(
            "pointer-events-none absolute left-3 top-2 text-xs text-muted-foreground transition-all duration-150 motion-reduce:transition-none",
            // 未入力かつ未フォーカス → 中央・大（プレースホルダー位置）
            "peer-placeholder-shown:top-1/2 peer-placeholder-shown:-translate-y-1/2 peer-placeholder-shown:text-base",
            // フォーカス時 → 上部へ浮上・小・accent（入力の有無に関わらず）
            "peer-focus:top-2 peer-focus:translate-y-0 peer-focus:text-xs peer-focus:text-accent",
            // エラー時はラベルも destructive
            "peer-aria-[invalid=true]:text-destructive"
          )}
        >
          {label}
        </label>
      </div>
    );
  }
);
```

#### 6.3.2 状態と挙動

| 状態 | ラベル位置 | ラベルサイズ・色 | ボーダー |
|---|---|---|---|
| 未入力・未フォーカス | フィールド中央（プレースホルダー風） | `text-base` / `text-muted-foreground` | `border-input` |
| フォーカス中（空でも） | 上部へ浮上 | `text-xs` / `text-accent` | `border-accent` + `ring-ring` |
| 入力あり・未フォーカス | 上部に固定 | `text-xs` / `text-muted-foreground` | `border-input` |
| エラー（`aria-invalid`） | 上部 | `text-xs` / `text-destructive` | `border-destructive` |

**実装上の要点:**
- `placeholder=" "`（半角スペース）が**必須**。空文字だと `:placeholder-shown` が効かずラベルが浮上したままになる。
- 入力には浮上ラベル分の上余白を確保（`h-14` + `pt-5 pb-1`）。`h-14`（56px）はタッチターゲット 44px を満たす（`MASTER.md §15`）。
- **オートフィル:** ブラウザ自動入力でも値が入るため `:placeholder-shown` が外れラベルは浮上位置を保つ。Chrome の `-webkit-autofill` 黄色背景は `globals.css` で `box-shadow: inset 0 0 0 1000px var(--background)` 等で抑制する（任意）。
- `prefers-reduced-motion` 時は `motion-reduce:transition-none` で浮上アニメを無効化（位置は CSS 状態のため即時切替で機能は維持）。

#### 6.3.3 パスワード欄（表示切替トグル付き）

パスワードはフローティングラベル＋右端に表示/非表示トグル（目アイコン）。入力の右パディングを広げてアイコンと重ならないようにする。

```tsx
// 抜粋: FloatingLabelInput をラップし、右端にトグルを重ねる
<div className="relative">
  <FloatingLabelInput
    type={visible ? "text" : "password"}
    label="パスワード"
    autoComplete="current-password"   // 登録 Step3 は "new-password"
    className="pr-11"                  // トグル分の余白
    {...register("password")}
  />
  <button
    type="button"
    onClick={() => setVisible((v) => !v)}
    aria-label={visible ? "パスワードを非表示" : "パスワードを表示"}
    aria-pressed={visible}
    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
  >
    {visible ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
  </button>
</div>
```

#### 6.3.4 エラーメッセージ・補助テキスト

zod バリデーションメッセージはフィールド**直下**に表示（`text-sm text-destructive`、`role` は RHF が `aria-describedby` で紐付け）。パスワードの「8文字以上」等の常時ヘルプも同位置に `text-muted-foreground` で出す。フローティングラベルは**ラベルの代替であってエラー表示ではない**点に注意（プレースホルダーをラベル代わりにしない＝§13 のアクセシビリティ要件を満たす）。

### 6.4 初回ロードの演出（控えめなスタガー登場）

frontend-design の「**1 回の、よく振り付けられたページロード**（staggered reveal）は散発的なマイクロインタラクションより記憶に残る」という指針を、**過剰演出せず**に採用する。フォーム本体（ブランドパネルは静止）の要素を上から順に短いディレイで立ち上げ、「画面が整っていく」感触だけを与える。

- 対象: フォームカラム内の **見出し → 各入力 → CTA → 区切り → ソーシャル → 末尾リンク** を `60〜80ms` ずつずらして `opacity 0→1` ＋ `translate-y-2→0`。
- 時間: 各要素 `duration-200` / `ease-out`（`MASTER.md §7`）。総演出は **300ms 以内**に収め、入力開始を妨げない。
- **ブランドパネル・フッターはアニメートしない**（フレーム全体がふわつくのを避け、フォームへ視線を集約）。
- 実装は CSS のみ（`animation-delay` を段階付与する `[&>*:nth-child(n)]` か、各要素に `style={{ animationDelay }}`）で可。Motion ライブラリ導入は任意で、入れる場合も `staggerChildren` の軽量利用に留める。
- **`prefers-reduced-motion: reduce` では全要素を即時表示**（`motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:translate-y-0`）。`MASTER.md §14`「状態変化を 0ms で行わない」はモーション許容時の話であり、reduced-motion 指定時はアクセシビリティ優先で即時表示してよい。
- **RegisterFlow の画面切替**（§8.5）は別物：そちらは「次へ進んだ」フィードバック用の `duration-150` トランジション。初回ロードのスタガーは**最初のマウント時のみ**で、ステップ切替ごとには再生しない（毎回再生すると操作の度に待たされ煩雑）。

```tsx
// 例: フォームコンテナ側でスタガーを付与（CSS のみ・reduced-motion 安全）
// globals.css に @keyframes auth-rise { from { opacity:0; transform: translateY(8px) } to { opacity:1; transform:none } }
<div className="[&>*]:motion-safe:animate-[auth-rise_200ms_ease-out_both]
                [&>*:nth-child(2)]:[animation-delay:60ms]
                [&>*:nth-child(3)]:[animation-delay:120ms]
                [&>*:nth-child(4)]:[animation-delay:180ms]
                [&>*:nth-child(5)]:[animation-delay:240ms]">
  {/* h1 / fields / CTA / divider / social / link ... */}
</div>
```

---

## 7. ログインフォーム（`LoginForm`）

`react-hook-form` + `loginSchema`（`src/lib/validations/auth.ts`）+ `useMutation`。バリデーション文言の正は `docs/design/VALIDATION_RULES.md`。

### 7.1 レイアウト

参考画像 `image.png` 右パネルの並びに準拠（上から: ウェルカム見出し → email → password → パスワード忘れ → ログイン → または → Google → 新規登録リンクは**末尾**）。

```
┌─────────────────────────────┐
│ Kivio へようこそ             │  font-serif text-2xl（補助文なし）
├─────────────────────────────┤
│ メールアドレス               │  Label
│ [______________________]    │  Input（type=email, autoComplete=email）
│                             │
│ パスワード                   │  Label
│ [______________________]    │  Input（type=password, autoComplete=current-password）
│            パスワードを忘れた方 │  ← 入力欄"直下"に右寄せ（Phase 2 は "#"）
│                             │
│ [        ログイン        ]   │  Button（accent・full width・h-11）
│                             │
│ ───────── または ─────────   │  Divider（§9）
│                             │
│ [G]  Google で続行          │  GoogleSignInButton（§10）
│                             │
│ アカウントをお持ちでない方は 会員登録 │  ← フォーム末尾・中央寄せ（→ /auth/register）
└─────────────────────────────┘
```

> 上図の「メールアドレス」「パスワード」は**フィールド内のフローティングラベル**（§6.3）。未入力時はフィールド中央に表示され、フォーカス/入力で上部へ浮上する。独立した上付き `<Label>` は置かない。

### 7.2 主要仕様

- email / password は `FloatingLabelInput`（§6.3）を使用。password は表示切替トグル付き（§6.3.3）。
- 送信ボタンは `bg-accent text-accent-foreground`（ティール CTA）、`w-full h-11`、`isPending` 中は spinner ＋ disabled。
- 「パスワードを忘れた方」リンクは**パスワード入力欄の直下に右寄せ**（`flex justify-end` / `text-sm text-accent`）。参考画像準拠。Phase 2 は `href="#"`（リセット機能は別フェーズ）。
- 「会員登録」への導線は**フォーム末尾**（Google ボタンの下）に中央寄せで配置（`text-sm text-muted-foreground`、`会員登録` を `text-accent`）。`→ /auth/register`。
- **セキュリティ:** ログイン失敗は `INVALID_CREDENTIALS` のみを `aria-live="polite"` の領域に表示し、「メール／パスワードのどちらが誤りか」を示さない（`auth.md` の方針・列挙攻撃対策）。
- `USER_DEACTIVATED` は専用文言（「このアカウントは利用停止中です。サポートへお問い合わせください。」）。

### 7.3 エラー UI 対応表

| エラーコード | 表示 | 場所 |
|---|---|---|
| `INVALID_CREDENTIALS` | 「メールアドレスまたはパスワードが正しくありません」 | フォーム上部 alert（`aria-live`） |
| `USER_DEACTIVATED` | 「このアカウントは利用停止中です」 | フォーム上部 alert |
| `RATE_LIMIT_EXCEEDED` | 「試行回数が上限に達しました。しばらくしてからお試しください」 | フォーム上部 alert |
| zod バリデーション | フィールド直下に各メッセージ | 各 Input 下 |

---

## 8. 会員登録フロー（`RegisterFlow` — 3 画面の順送り）

`RegisterFlow`（CC）が現在の画面（`email` / `otp` / `password`）と `email`・`registrationToken` を内部状態で保持し、子画面を切り替える。**可視のステッパー／進捗表示は持たない**（§8.1）が、内部的には順序のある状態機械であり、子コンポーネント名の `*Step` は「フロー内の順序画面」を指す（可視ステッパーとは無関係）。コンポーネント名は `Wizard` がステッパー付き UI を連想させるため `RegisterFlow` とする。詳細フローは `auth.md §6.3 ステップ5`。

> **入力フィールド:** Step1 のメール、Step3 の表示名・パスワード・確認用パスワードは `FloatingLabelInput`（§6.3）を使用する。**Step2 の OTP のみフローティングラベル非対応**（6 桁分割入力 `InputOTP`。§8.3 / §6.3 例外）。

### 8.1 進捗インジケーターを置かない

ステッパーバー・ドット・「ステップ n / 3」テキストの**いずれも表示しない**。手順は 3 画面のみで各画面が自己説明的（メール → 届いたコード → パスワード）であり、行政手続きのような長大フローではないため、進捗 chrome はノイズになる（Mercari・Amazon の新規登録も同様にステッパー非表示）。

進捗 chrome を持たない代わりに、**各画面の見出し・補助文が文脈（今どこか・もうすぐ終わるか）を担う**。下表の補助文、特に Step3「あと少しで完了です」と Step2 の宛先エコーは**ステッパー削除後の唯一のオリエンテーション手段**なので省略しない（§8.2〜8.4）。

各画面の見出し（`font-serif text-2xl`・`<h1>`）と補助文:

| 画面 | 見出し | 補助文 |
|---|---|---|
| 1 email | メールアドレスを入力 | 「認証コードをお送りします」 |
| 2 otp | 認証コードを入力 | 「{email} に送信した6桁のコードを入力してください」 |

### 8.2 Step1 — `RegisterEmailStep`（メール → request-otp）

```
メールアドレスを入力
認証コードをお送りします

メールアドレス
[______________________]      （type=email, autoComplete=email）

[      認証コードを送信      ]  （accent・full・h-11）

─────── または ───────
[G] Google で登録

既にアカウントをお持ちですか？ ログイン   ← 末尾リンク
```

- `requestOtpSchema` でバリデーション → `requestOtp(email)`。
- 成功（202）で `email` を保持し Step2 へ。
- `EMAIL_ALREADY_REGISTERED`: 「このメールアドレスは登録済みです」＋**ログインへ誘導するリンク**（`/auth/login?email=...` 等）を alert 内に出す。
- `RATE_LIMIT_EXCEEDED`: 「送信回数が上限に達しました。しばらくしてからお試しください」。

### 8.3 Step2 — `RegisterOtpStep`（OTP → verify-otp）

```
認証コードを入力
xxx@example.com に送信した6桁のコードを入力してください

[ _ _ _ _ _ _ ]              （6桁・大きめ中央寄せ・inputMode=numeric・autoComplete=one-time-code）

[        確認          ]      （accent・full・h-11）

コードが届きませんか？ 再送信（00:42）  ← 再送信ボタン（クールダウン表示）
← メールアドレスを変更                ← Step1 へ戻る
```

- `verifyOtpSchema`（`/^\d{6}$/`）→ `verifyOtp(email, otp)`。
- 入力 UI は OTP 専用（6 桁・`inputMode="numeric"`・`autoComplete="one-time-code"`・ペースト対応）。shadcn の `InputOTP` 系を想定。
- 成功（200）で `registrationToken` を保持し Step3 へ。
- `OTP_INVALID`: **残り試行回数を表示**して再入力（「コードが正しくありません（残り n 回）」）。
- `OTP_EXPIRED` / `OTP_MAX_ATTEMPTS_EXCEEDED`: 入力を無効化し「コードを再送信」を促す（Step1 の `requestOtp` を再実行 → 新しい OTP）。
- **再送信ボタンはクールダウン**（例: 60 秒カウントダウン中は disabled）。`expiresInSeconds` を補助表示に使ってもよい。
- 「メールアドレスを変更」で Step1 へ戻れる（戻ると OTP 状態は破棄）。

### 8.4 Step3 — `RegisterPasswordStep`（パスワード → complete）

```
パスワードを設定
あと少しで完了です

表示名
[______________________]      （autoComplete=nickname, 100字以内）

パスワード
[__________________] 👁        （type=password, autoComplete=new-password, 表示切替）

パスワード（確認）
[__________________]          （autoComplete=new-password）

[      登録して始める      ]   （accent・full・h-11）

登録すると 利用規約 と プライバシーポリシー に同意したものとみなされます。  ← 同意文（text-xs muted）
```

- `completeRegistrationSchema`（password 8 文字以上・passwordConfirm 一致・displayName 必須 1〜100 字）→ `completeRegistration(registrationToken, ...)`。
- 成功（201）で `AuthTokens` を Zustand（`useAuthStore`）に保存 → `router.replace('/')`（自動ログイン）。
- `REGISTRATION_SESSION_INVALID`（セッション 30 分 TTL 切れ等）: alert で通知し **Step1 へ戻す**（「セッションの有効期限が切れました。最初からやり直してください」）。
- `EMAIL_ALREADY_REGISTERED`（complete 時の競合）: 同上、ログイン誘導。
- パスワードは表示/非表示トグル（目アイコン・`aria-label` 必須）。
- **法的同意文**は送信ボタン直下に常時表示（クリックではなく「登録＝同意」型）。利用規約・プライバシーポリシーへのリンクを含む。

### 8.5 画面切替トランジション

画面切替時は左パネルは固定したまま右フォームのみ差し替わる。フォームコンテナに軽いフェード/スライド（`MASTER.md §7` のマイクロインタラクション規則・`duration-150`）を付与してよい。`prefers-reduced-motion` 尊重。進捗 chrome が無い分、切替自体が「次へ進んだ」フィードバックを担うため、遷移は無音すぎないよう軽いモーションを推奨（ただし `prefers-reduced-motion` では無効化）。

---

## 9. ディバイダー（「または」）

ソーシャルログインと email フォームの区切り。

```tsx
<div className="relative my-6">
  <div className="absolute inset-0 flex items-center">
    <span className="w-full border-t border-border" />
  </div>
  <div className="relative flex justify-center">
    <span className="bg-background px-3 text-xs text-muted-foreground">または</span>
  </div>
</div>
```

`bg-background` でラインを切り抜く（フォーム側がカードなし＝背景色そのものなので `bg-background` で一致させる）。

---

## 10. Google サインインボタン（`GoogleSignInButton`）

```tsx
// src/components/auth/GoogleSignInButton.tsx — CC
<Button
  type="button"
  variant="outline"
  className="w-full h-11 gap-2 font-medium border-border"
  onClick={() => signIn("google")}     // NextAuth
>
  <GoogleIcon className="w-4 h-4" />     {/* 公式 G マーク SVG（多色）。lucide ではなく専用アセット */}
  Google で{mode === "login" ? "続行" : "登録"}
</Button>
```

- variant は `outline`（白地・枠線）。ソーシャルボタンは accent CTA と差別化し、メール導線を主・ソーシャルを副に見せる。
- アイコンは **Google 公式の多色 G マーク**（ブランドガイドライン準拠）。lucide の単色アイコンは使わない。
- ラベルはログイン「Google で続行」／登録「Google で登録」。

---

## 11. 最小フッター（`AuthMinimalFooter`）

`layout.md §9` の重い4カラムフッターは使わず、**1 行の軽量フッター**。`bg-primary` ではなく `bg-background`（低彩度）。`(auth)/layout.tsx` の最下段に置かれ**画面全幅**を占める（左パネル＋右フォームの下を横断。§3.2）。内側コンテンツは `max-w-7xl` で中央寄せ。

```tsx
// src/components/auth/AuthMinimalFooter.tsx — SC
export function AuthMinimalFooter() {
  return (
    <footer className="border-t border-border">
      <nav
        aria-label="フッターナビゲーション"
        className="max-w-7xl mx-auto px-6 py-4 flex flex-col items-center gap-2 sm:flex-row sm:justify-center sm:gap-4"
      >
        <p className="text-xs text-muted-foreground">© 2026 Kivio, Inc.</p>
        <div className="flex items-center gap-x-4 gap-y-1 flex-wrap justify-center">
          {[
            { label: "プライバシーポリシー", href: "#" },
            { label: "利用規約", href: "#" },
            { label: "特定商取引法に基づく表記", href: "#" },
            { label: "ヘルプ", href: "#" },
          ].map(({ label, href }) => (
            <Link
              key={label}
              href={href}
              className="text-xs text-muted-foreground hover:text-foreground transition-colors duration-150"
            >
              {label}
            </Link>
          ))}
        </div>
      </nav>
    </footer>
  );
}
```

- 残すリンクは**法的必須**（プライバシー・利用規約・特商法）＋**サポート**（ヘルプ）の 4 本のみ。
- マーケ導線（セラー登録・採用・FAQ 階層・会社情報）は出さない（§1 原則）。
- Phase 2 は `href="#"`。実 URL 確定後に差し替え。

---

## 12. レスポンシブまとめ

| 要素 | `< md`（モバイル） | `md`（タブレット横） | `lg`（デスクトップ） |
|---|---|---|---|
| ブランドパネル | 非表示 | 表示（1/2 幅） | 表示（5/12 幅） |
| 上部ロゴ | `AuthMobileLogo`（中央） | なし（左パネル内ロゴ） | なし |
| フォーム幅 | `max-w-100` 全幅余白 | `max-w-100` 中央 | `max-w-100` 中央 |
| フォーム背景 | `bg-background`（白） | `bg-background` | `bg-background` |
| フッター | **全幅**・縦積み 1〜2 行 | **全幅**・1 行中央 | **全幅**・1 行中央（左パネル下も横断） |

---

## 13. アクセシビリティ要件

- **ランドマーク:** `<main id="main-content">`（§3.2）、フッターは `<footer>` + `<nav aria-label="フッターナビゲーション">`、左パネルは `<aside>`。
- **スキップリンク:** `(auth)/layout.tsx` 先頭（`layout.md §12` と同形）。
- **見出し階層:** 各画面 `<h1>`（ログイン／各登録画面の見出し）は 1 ページ 1 つ。`RegisterFlow` は画面切替時に新しい見出し（`<h1>`）へフォーカス移動するか `aria-live` で読み上げ、ステッパーが無くても支援技術利用者が「画面が変わった」ことを認識できるようにする。
- **フォーム:** すべての Input に `<Label htmlFor>`（フローティングラベルも**実体は `<label htmlFor>`** であり、プレースホルダーをラベル代わりにしない＝§6.3）。エラーは `aria-invalid` ＋ `aria-describedby` でメッセージに紐付け。サーバーエラー alert は `role="alert"` / `aria-live="polite"`。
- **フローティングラベル:** ラベルは常に DOM 上に存在し読み上げ可能。`placeholder=" "` は視覚的トリガーのみで支援技術には無意味（読み上げ対象にしない）。コントラストは浮上時 `text-xs` でも 4.5:1 を満たす配色（muted-foreground #64748B / accent #1A9E87）。
- **OTP:** `inputMode="numeric"`・`autoComplete="one-time-code"`（iOS/Android の SMS/メール自動入力対応）。
- **パスワード表示トグル:** ボタンに `aria-label`（「パスワードを表示」/「パスワードを非表示」）、`aria-pressed`。
- **オートコンプリート:** login=`current-password` / register=`new-password` / email=`email` / displayName=`nickname`。
- **タッチターゲット:** 送信・ソーシャル・再送信ボタンは `h-11`（44px）以上（`MASTER.md §15`）。
- **装飾画像:** イラスト・グロー・ロゴアイコンは `alt=""` / `aria-hidden`。
- **モーション:** ステップ遷移アニメは `prefers-reduced-motion` で無効化。
- **フォーカス:** ロゴ・リンク・ボタンに `focus-visible:ring-2 focus-visible:ring-ring`。左パネル（濃紺）上のロゴは `ring-primary-foreground/60`。

---

## 14. コンポーネント構成とファイルマッピング

```
src/
├── app/
│   └── (auth)/
│       ├── layout.tsx                  # AuthLayout（SC・Split 2カラム + スキップリンク）
│       ├── login/
│       │   └── page.tsx                # SC: <LoginForm /> を配置
│       └── register/
│           └── page.tsx                # SC: <RegisterFlow /> を配置
│       # ❌ verify-email/page.tsx は不要（OTP 方式で廃止）
│
└── components/
    └── auth/
        ├── AuthBrandPanel.tsx          # SC: 左ブランドパネル（ロゴ+タグライン+イラスト）
        ├── AuthMobileLogo.tsx          # SC: モバイル上部中央ロゴ
        ├── AuthMinimalFooter.tsx       # SC: 最小1行フッター
        ├── AuthDivider.tsx             # SC: 「または」ディバイダー（任意・共通化する場合）
        ├── LoginForm.tsx               # CC: react-hook-form + loginSchema + useMutation
        ├── RegisterFlow.tsx            # CC: 現在画面/email/registrationToken 状態管理 + 画面切替（可視ステッパーなし）
        ├── RegisterEmailStep.tsx       # CC: Step1（request-otp）
        ├── RegisterOtpStep.tsx         # CC: Step2（verify-otp・再送信クールダウン）
        ├── RegisterPasswordStep.tsx    # CC: Step3（complete・自動ログイン）
        └── GoogleSignInButton.tsx      # CC: signIn("google")

components/ui/
        └── FloatingLabelInput.tsx      # CC: フローティングラベル付き input（forwardRef・§6.3）
```

**Client / Server Component 境界:**

| コンポーネント | SC / CC | 理由 |
|---|---|---|
| `(auth)/layout.tsx` | SC | 静的レイアウト |
| `login/page.tsx` / `register/page.tsx` | SC | フォーム本体は子の CC に委譲 |
| `AuthBrandPanel` / `AuthMobileLogo` / `AuthMinimalFooter` | SC | 静的 |
| `LoginForm` | CC | フォーム状態・mutation |
| `RegisterFlow` ＋ 各 Step | CC | 画面状態・mutation |
| `GoogleSignInButton` | CC | onClick（`signIn`） |
| `FloatingLabelInput` | CC | `forwardRef` で RHF `register` に渡す（フォーム内で使用） |

**API クライアント / ストア / 型 / バリデーション**（実装済み・T-12〜15）:

| 役割 | パス |
|---|---|
| API クライアント | `src/lib/api/client/auth.ts` |
| Zod スキーマ | `src/lib/validations/auth.ts` |
| Zustand ストア | `src/stores/useAuthStore.ts` |
| 型定義 | `src/types/api/auth.ts` / `src/types/enums.ts` |

> ※ `auth.md §6.2` は旧パス（`lib/api/auth.ts` 等）を記載しているが、実装は上記 `client/` 配下の構成（T-14/15 で確定済み）に従う。

---

## 15. デザイントークン参照（MASTER.md §2）

| 用途 | トークン | 値 |
|---|---|---|
| 左パネル地 | `bg-primary` | #1E3A5F |
| 左パネル文字 | `text-primary-foreground` | #FFFFFF |
| CTA（送信ボタン） | `bg-accent` / `text-accent-foreground` | #1A9E87 / #FFFFFF |
| リンク（強調） | `text-accent` | #1A9E87 |
| フォーム地 | `bg-background` | 白 |
| 補助文・フッター | `text-muted-foreground` | #64748B |
| ディバイダー・枠線 | `border-border` | — |
| 見出しフォント | `font-serif`（Noto Serif JP Bold） | — |
| 本文フォント | Noto Sans JP | — |

---

## 16. Phase 2 実装優先度

| 優先度 | コンポーネント | 状態 |
|---|---|---|
| P0 | `FloatingLabelInput`（§6.3・全フォーム共通） | T-16 |
| P0 | `(auth)/layout.tsx`（Split・最小フッター・モバイルロゴ） | T-17 |
| P0 | `AuthBrandPanel`（イラスト配置・要 `public/images/` へコピー） | T-16 |
| P0 | `LoginForm`（email/password・エラー UI） | T-16 |
| P0 | `RegisterFlow` ＋ 3 画面（ステッパーなし） | T-16 |
| P1 | `GoogleSignInButton` | T-16 |
| P1 | `AuthMinimalFooter` | T-16 |
| P1 | フォーム初回スタガー演出（§6.4・CSS のみ） | T-16 |
| P2 | ブランドパネル奥行きレイヤー（グロー・シェーディング・グレイン §5.2） | T-16（グレイン SVG 用意は任意） |
| P2 | パスワード忘れリンク先・フッター実 URL | Phase 3+ |

---

## 17. frontend-design レビュー反映ログ（2026-06-13）

`/frontend-design` スキルの観点で本仕様をレビューし、**本プロジェクトの確立済みアイデンティティ（`MASTER.md §1`「Simple & Modern」／商品を主役／Noto Serif・Sans JP 固定／ライト固定／HEX 直書き禁止）と認証画面の目的（フォーム完遂・離脱防止 §1）に合致するものだけ**を採り入れた。スキルが推す「大胆・唯一無二」志向のうち、認証フォームの文脈に合わないものは明示的に見送った。

### 採用した点（プロジェクトに合致）

| frontend-design の指針 | 本仕様への反映 | 合致理由 |
|---|---|---|
| **Atmosphere & depth over flat solid** | ブランドパネルに上下シェーディング＋accent グロー 2 層＋微細グレインを追加（§5.2） | 装飾は**左パネル限定**で商品/入力面に及ばない。トークン（`white/x` `black/x` `accent/x`）のみで HEX 直書きを回避。ブランド表現の格上げ。 |
| **1 回のよく振り付けられたページロード（staggered reveal）** | フォーム要素の初回スタガー登場（§6.4・300ms 以内・reduced-motion で即時） | 高インパクト・低リスク。`MASTER.md §7` のモーショントークン内に収め、入力を妨げない。 |
| **Typographic refinement** | タグラインに `tracking-tight text-balance`＋accent 下線のエディトリアルなリズム（§5.2） | フォントは固定のまま、組版の精度だけを上げる範囲。 |

### あえて見送った点（プロジェクトに不適）

| frontend-design の指針 | 不採用の理由 |
|---|---|
| 個性的・意外性のあるディスプレイ／ボディフォント（Inter 等の回避、独自フォント） | `MASTER.md §1` で Noto Serif JP＋Noto Sans JP が**確定**（日本語対応必須・ブランド統一）。差し替え不可。 |
| マキシマリズム／グリッド破壊・大胆なオーバーラップ・斜め構成 | 認証はフォーム完遂が唯一の目的（§1）。視覚的ノイズは離脱・入力ミスを招く。Split 2 カラムの整然さを維持。 |
| accent カラーの大面積支配・ドラマティックな配色 | 商品画像を主役にするため公開面は白基調（`MASTER.md §1`）。accent は CTA・フォーカス・細部アクセントに限定。 |
| カスタムカーソル／重い装飾ボーダー／グレイン全面適用 | フォーム面の可読性・タッチ精度・パフォーマンス（LCP）を優先。装飾はブランドパネルに隔離。 |
| 散発的なマイクロインタラクションの多用 | 「1 回のロード演出に集約」というスキル自身の指針に従い、入力フィールド単位の過剰演出はしない。 |

**結論:** frontend-design の本質（**意図の明確さ＝refined minimalism も bold maximalism も "intentionality" が肝**）に従い、本プロジェクトは前者を選択。採り入れたのは「奥行き・演出の振り付け・組版精度」という*洗練を深める*要素に限り、アイデンティティを破る*奇抜さ*は持ち込まない。
```
