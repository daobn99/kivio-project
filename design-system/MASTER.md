# Kivio — デザインシステム マスターファイル

> **参照ルール:** 特定ページを実装する場合、先に `design-system/pages/[ページ名].md` を確認する。
> そのファイルが存在する場合、そのルールが本ファイルを**上書き**する。
> 存在しない場合は、本ファイルのルールのみに従う。

---

**プロジェクト:** Kivio — マルチベンダーマーケットプレイス
**スタック:** Next.js 15 (App Router) + TypeScript + shadcn/ui + Tailwind CSS 4.x
**作成日:** 2026-05-31
**カテゴリ:** マーケットプレイス (P2P) / EC

---

## 1. 設計根拠

| 決定事項 | 選択 | 理由 |
|---|---|---|
| **メインカラー** | バイオレット `#7C3AED` | 信頼感 + 創造性を表現。赤・オレンジ系が多い EC サイトとの差別化。 |
| **アクセント/CTA** | グリーン `#16A34A` | 「購入・確定」の普遍的な色覚シグナル。バイオレット背景に対して高コントラスト。 |
| **フォント** | Noto Serif JP（見出し）+ Noto Sans JP（本文） | 日本語対応必須。Serif は高級感を演出し、Sans はスキャン読みに最適。可変フォントで読み込み最小化。 |
| **スタイル** | Vibrant & Block-based | ポートフォリオとして視覚的インパクトを重視。若年層向けマーケット感を表現。 |
| **ダークモード** | 完全対応 | shadcn/ui のデフォルト機能。採用審査でのアクセシビリティ評価を意識。 |

---

## 2. カラーシステム

### 2.1 セマンティックトークン（CSS 変数）

shadcn/ui + Tailwind CSS 4.x では、CSS変数に `hsl()` 等の**完全な色値**を格納し、`@theme inline` で Tailwind ユーティリティにマッピングする。bare HSL tuple（`270 100% 98%` のみ）は Tailwind v3 の書き方であり使用しない。

#### ライトモード

```css
:root {
  /* ベース */
  --background:        hsl(270, 100%, 98%);  /* #FAF5FF */
  --foreground:        hsl(263,  67%, 35%);  /* #4C1D95 */

  /* カード / ポップオーバー */
  --card:              hsl(0, 0%, 100%);
  --card-foreground:   hsl(263, 67%, 35%);   /* #4C1D95 */
  --popover:           hsl(0, 0%, 100%);
  --popover-foreground: hsl(263, 67%, 35%);  /* #4C1D95 */

  /* ブランド */
  --primary:           hsl(262, 83%, 58%);   /* #7C3AED */
  --primary-foreground: hsl(0, 0%, 100%);
  --secondary:         hsl(258, 100%, 76%);  /* #A78BFA */
  --secondary-foreground: hsl(222, 47%, 11%); /* #0F172A — secondary ボタン上の文字色 */

  /* アクセント（CTA / 成功） */
  --accent:            hsl(142, 72%, 36%);   /* #16A34A */
  --accent-foreground: hsl(0, 0%, 100%);

  /* ニュートラル */
  --muted:             hsl(267, 71%, 94%);   /* #ECEEF9 */
  --muted-foreground:  hsl(258, 30%, 55%);
  --border:            hsl(259, 100%, 87%);  /* #DDD6FE */
  --input:             hsl(259, 100%, 90%);
  --ring:              hsl(262, 83%, 58%);   /* #7C3AED */

  /* フィードバック */
  --destructive:       hsl(0, 72%, 51%);     /* #DC2626 */
  --destructive-foreground: hsl(0, 0%, 100%);
  --warning:           hsl(38, 92%, 50%);    /* #F59E0B */
  --warning-foreground: hsl(0, 0%, 100%);
  --success:           hsl(142, 72%, 36%);   /* #16A34A */
  --success-foreground: hsl(0, 0%, 100%);

  /* チャート */
  --chart-1: hsl(262, 83%, 58%);
  --chart-2: hsl(142, 72%, 36%);
  --chart-3: hsl(199, 89%, 48%);
  --chart-4: hsl(38,  92%, 50%);
  --chart-5: hsl(355, 78%, 60%);

  /* 角丸 */
  --radius: 0.5rem;
}
```

#### ダークモード

```css
.dark {
  --background:        hsl(263, 80%,  6%);   /* #0C0520 */
  --foreground:        hsl(258, 100%, 93%);  /* #EDE9FE */

  --card:              hsl(263, 60%, 10%);
  --card-foreground:   hsl(258, 100%, 93%);
  --popover:           hsl(263, 60%, 10%);
  --popover-foreground: hsl(258, 100%, 93%);

  --primary:           hsl(258, 100%, 76%);  /* #A78BFA — ダーク背景で視認性を確保 */
  --primary-foreground: hsl(263, 100%, 10%);
  --secondary:         hsl(262, 50%,  30%);
  --secondary-foreground: hsl(258, 100%, 93%);

  --accent:            hsl(142, 60%, 45%);   /* #22C55E — ダーク背景で少し明るく */
  --accent-foreground: hsl(0, 0%, 100%);

  --muted:             hsl(263, 50%, 16%);
  --muted-foreground:  hsl(258, 30%, 65%);
  --border:            hsl(263, 40%, 20%);
  --input:             hsl(263, 40%, 20%);
  --ring:              hsl(258, 100%, 76%);

  --destructive:       hsl(0, 62%, 50%);
  --destructive-foreground: hsl(0, 0%, 100%);
  --warning:           hsl(38, 80%, 55%);
  --warning-foreground: hsl(0, 0%, 100%);
  --success:           hsl(142, 60%, 45%);
  --success-foreground: hsl(0, 0%, 100%);
}
```

### 2.2 Tailwind CSS 4.x `@theme inline` マッピング

Tailwind CSS v4 では `@theme inline` を使い、CSS変数をそのまま `var(--)` で参照する。
`hsl(var(--...))` は **v3 の書き方**であり v4 では不要。

```css
/* app/globals.css */
@import "tailwindcss";

@custom-variant dark (&:is(.dark *));

@theme inline {
  /* カラー */
  --color-background:           var(--background);
  --color-foreground:           var(--foreground);
  --color-card:                 var(--card);
  --color-card-foreground:      var(--card-foreground);
  --color-popover:              var(--popover);
  --color-popover-foreground:   var(--popover-foreground);
  --color-primary:              var(--primary);
  --color-primary-foreground:   var(--primary-foreground);
  --color-secondary:            var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-accent:               var(--accent);
  --color-accent-foreground:    var(--accent-foreground);
  --color-muted:                var(--muted);
  --color-muted-foreground:     var(--muted-foreground);
  --color-border:               var(--border);
  --color-input:                var(--input);
  --color-ring:                 var(--ring);
  --color-destructive:          var(--destructive);
  --color-destructive-foreground: var(--destructive-foreground);
  --color-warning:              var(--warning);
  --color-warning-foreground:   var(--warning-foreground);
  --color-success:              var(--success);
  --color-success-foreground:   var(--success-foreground);

  /* 角丸 */
  --radius-sm:   calc(var(--radius) - 4px);  /* 4px */
  --radius-md:   var(--radius);              /* 8px */
  --radius-lg:   calc(var(--radius) + 4px);  /* 12px */
  --radius-xl:   calc(var(--radius) + 8px);  /* 16px */
  --radius-full: 9999px;
}
```

### 2.3 カラー早見表

| 役割 | ライト | ダーク | 用途 |
|---|---|---|---|
| プライマリ | `#7C3AED` | `#A78BFA` | ブランドカラー、フォーカスリング |
| アクセント/CTA | `#16A34A` | `#22C55E` | 購入ボタン、成功ステート |
| 背景 | `#FAF5FF` | `#0C0520` | ページ背景 |
| 前景 | `#4C1D95` | `#EDE9FE` | 本文テキスト |
| ミュート | `#ECEEF9` | — | バッジ、タグ、サブ背景 |
| ボーダー | `#DDD6FE` | — | 区切り線、入力枠 |
| 破壊的操作 | `#DC2626` | `#EF4444` | 削除・エラー |
| 警告 | `#F59E0B` | `#FBBF24` | 在庫少・注意 |

---

## 3. タイポグラフィ

### 3.1 フォントスタック

```ts
// app/layout.tsx
import { Noto_Serif_JP, Noto_Sans_JP } from "next/font/google";

const notoSerif = Noto_Serif_JP({
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
  variable: "--font-heading",
  display: "swap",
  preload: false, // JP サブセットは容量大のため遅延読み込み
});

const notoSans = Noto_Sans_JP({
  weight: ["300", "400", "500", "700"],
  subsets: ["latin"],
  variable: "--font-body",
  display: "swap",
  preload: false, // JP フォントはファイルサイズが大きいため preload 禁止
});
```

### 3.2 タイプスケール

| トークン | サイズ | 行間 | ウェイト | 用途 |
|---|---|---|---|---|
| `text-xs` | 12px | 1.5 | 400 | メタ情報、ラベル |
| `text-sm` | 14px | 1.5 | 400 | サブテキスト、キャプション |
| `text-base` | 16px | 1.75 | 400 | 本文（最小サイズ） |
| `text-lg` | 18px | 1.75 | 500 | リード文、カード本文 |
| `text-xl` | 20px | 1.4 | 600 | セクション小見出し |
| `text-2xl` | 24px | 1.3 | 600 | カード見出し、ページ内 H3 |
| `text-3xl` | 30px | 1.25 | 700 | ページ H2 |
| `text-4xl` | 36px | 1.2 | 700 | ページ H1 |
| `text-5xl` | 48px | 1.1 | 700 | ヒーロー見出し |
| `text-6xl` | 60px | 1.0 | 700 | LP 大見出し（デスクトップのみ） |

**ルール:** 本文は最小 16px（iOS 自動ズーム防止）。日本語テキストに `text-sm` 以下は使用しない。

### 3.3 フォント使い分け

```
Noto Serif JP → h1, h2, ヒーロータイトル, ショップ名
Noto Sans JP  → h3–h6, 本文, ラベル, ボタン, 入力フィールド, ナビゲーション
```

---

## 4. スペーシングシステム

8pt グリッドベース。Tailwind デフォルトスケールを使用する。

| トークン | px | rem | 用途 |
|---|---|---|---|
| `space-1` | 4px | 0.25rem | アイコン隣接ギャップ |
| `space-2` | 8px | 0.5rem | インライン要素間 |
| `space-3` | 12px | 0.75rem | リスト間、バッジ内 |
| `space-4` | 16px | 1rem | 標準パディング |
| `space-6` | 24px | 1.5rem | カード内パディング |
| `space-8` | 32px | 2rem | セクション間 |
| `space-12` | 48px | 3rem | セクション大ギャップ |
| `space-16` | 64px | 4rem | ヒーローパディング |
| `space-24` | 96px | 6rem | ページ上下余白 |

---

## 5. シャドウシステム

```css
--shadow-sm:   0 1px 2px rgba(0,0,0,0.05);
--shadow-md:   0 4px 6px rgba(0,0,0,0.07), 0 2px 4px rgba(0,0,0,0.04);
--shadow-lg:   0 10px 15px rgba(0,0,0,0.08), 0 4px 6px rgba(0,0,0,0.04);
--shadow-xl:   0 20px 25px rgba(0,0,0,0.10), 0 10px 10px rgba(0,0,0,0.04);
--shadow-card: 0 2px 8px rgba(124,58,237,0.08); /* プライマリカラー薄め */
```

---

## 6. 角丸スケール

| トークン | 値 | 用途 |
|---|---|---|
| `rounded-sm` | 4px | タグ、バッジ |
| `rounded-md` | 8px | 入力フィールド、ボタン |
| `rounded-lg` | 12px | カード |
| `rounded-xl` | 16px | モーダル、ドロップダウン |
| `rounded-2xl` | 20px | ヒーローカード、画像コンテナ |
| `rounded-full` | 9999px | アバター、ピル型タグ |

---

## 7. アニメーショントークン

```css
/* 時間 */
--duration-instant:  100ms;   /* ホバー色変化 */
--duration-fast:     150ms;   /* ボタン hover */
--duration-normal:   200ms;   /* カード hover、モーダル開閉 */
--duration-slow:     300ms;   /* ページ遷移、ドロワー */
--duration-xslow:    400ms;   /* 複雑なトランジション（最大値） */

/* イージング */
--ease-in:     cubic-bezier(0.4, 0, 1, 1);
--ease-out:    cubic-bezier(0, 0, 0.2, 1);    /* 登場アニメーションに使用 */
--ease-inout:  cubic-bezier(0.4, 0, 0.2, 1);  /* 移動アニメーションに使用 */
--ease-spring: cubic-bezier(0.175, 0.885, 0.32, 1.275); /* バウンス */
```

**ルール:**
- マイクロインタラクション: 150–200ms `ease-out`
- モーダル開閉: 200ms `ease-out`（スケール + フェード）
- ページ遷移: 300ms `ease-inout`
- `prefers-reduced-motion: reduce` 時 → duration を 1ms に短縮

---

## 8. Z-Index スケール

| トークン | 値 | 用途 |
|---|---|---|
| `z-0` | 0 | 通常要素 |
| `z-10` | 10 | ホバーエフェクト、`card:hover` |
| `z-20` | 20 | スティッキーヘッダー |
| `z-30` | 30 | ドロップダウン、ポップオーバー |
| `z-40` | 40 | ツールチップ |
| `z-50` | 50 | モーダルオーバーレイ |
| `z-100` | 100 | トースト / 通知 |
| `z-1000` | 1000 | 開発ツール、最前面 |

---

## 9. ブレークポイント

Tailwind CSS v4 ではブレークポイントを TypeScript config ではなく `@theme` CSS変数で定義する。

```css
/* app/globals.css 内の @theme inline ブロックに追記 */
@theme inline {
  /* カスタムブレークポイント（xs は Tailwind v4 デフォルト外） */
  --breakpoint-xs:  375px;   /* 小型スマートフォン（iPhone SE） */
  /* sm 640px, md 768px, lg 1024px, xl 1280px は Tailwind v4 デフォルト値を踏襲 */
  --breakpoint-2xl: 1440px;  /* v4 デフォルト 1536px をワイドデスクトップ向けに上書き */
}
```

| ブレークポイント | px | 対象 |
|---|---|---|
| `xs` | 375px | 小型スマートフォン（iPhone SE） |
| `sm` | 640px | 大型スマートフォン（横向き） |
| `md` | 768px | タブレット |
| `lg` | 1024px | 小型ノートPC |
| `xl` | 1280px | デスクトップ |
| `2xl` | 1440px | ワイドデスクトップ |

**レイアウト切替ルール:**
- `md 未満`: 1カラム、ボトムナビゲーション
- `md〜lg`: 2カラム、サイドバー折りたたみ
- `lg 以上`: サイドバー常時表示、商品3〜4カラムグリッド

---

## 10. コンポーネント仕様（shadcn/ui）

### ボタン

```tsx
// shadcn Button バリアント対応表
// primary     → bg-primary text-primary-foreground
// secondary   → bg-secondary text-secondary-foreground
// outline     → border-primary text-primary
// destructive → bg-destructive text-destructive-foreground
// ghost       → hover:bg-muted

// CTA（購入・確定）はアクセントカラーを使用
<Button className="bg-accent hover:bg-accent/90 text-accent-foreground">
  購入する
</Button>
```

### 商品カード

```tsx
// 推奨幅: 200–280px（3〜4列グリッド）
<Card className="rounded-lg shadow-card hover:shadow-lg transition-shadow duration-200 cursor-pointer">
  {/* 画像: aspect-[4/3] object-cover */}
  {/* 商品名: text-sm font-medium line-clamp-2 */}
  {/* 価格: text-lg font-bold tabular-nums */}
  {/* ショップ名: text-xs text-muted-foreground */}
</Card>
```

### フォーム

```tsx
// shadcn の Input / Label / FormMessage を使用
// エラー: border-destructive + FormMessage（text-sm text-destructive）
// フォーカス: ring-2 ring-ring ring-offset-2
// ラベルは常に表示。placeholder は補助のみで代替不可
```

### ナビゲーション

```tsx
// デスクトップ: 固定トップナビ（z-20）+ パンくずリスト（lg 以上）
// モバイル: ボトムナビ（最大5項目、アイコン + ラベル、z-20）
// サイドバー: Sheet コンポーネント（md 以下でドロワー、lg 以上でインライン表示）
```

### バッジ / タグ

```tsx
// カテゴリタグ: bg-primary/10 text-primary rounded-full text-xs px-2 py-0.5
// ステータスバッジ: bg-success/10 text-success / bg-warning/10 text-warning
// "新着" / "セール": bg-accent text-accent-foreground rounded-sm text-xs font-bold
```

### トースト

```tsx
// shadcn Sonner（toaster）を使用
// 自動消去: 4000ms
// 表示位置: 右下（デスクトップ）/ 上中央（モバイル）
// 成功: グリーンの左ボーダー（accent）
// エラー: レッドの左ボーダー（destructive）
```

### ローディング（Skeleton / Suspense）

```tsx
// 300ms 以上かかる非同期処理にはスケルトンを使用
// Suspense + Skeleton でブロッキングレンダリングを回避

// app/products/page.tsx
import { Suspense } from "react";
import { ProductGridSkeleton } from "@/components/skeletons";

export default function ProductsPage() {
  return (
    <Suspense fallback={<ProductGridSkeleton />}>
      <ProductGrid />
    </Suspense>
  );
}

// スケルトンは実際のコンポーネントと同じサイズ・レイアウトで定義（CLS 防止）
// shadcn Skeleton: <Skeleton className="h-50 w-full rounded-lg" />
```

### エンプティステート

```tsx
// 空リスト・検索結果ゼロ・未ウィッシュリスト等に必ず表示
// メッセージ + 行動促進アクションをセットで提供

// 例: カートが空の場合
<div className="flex flex-col items-center gap-4 py-16 text-center">
  <ShoppingCart className="w-12 h-12 text-muted-foreground" />
  <p className="text-muted-foreground">カートに商品がありません</p>
  <Button asChild variant="outline">
    <Link href="/">商品を探す</Link>
  </Button>
</div>
```

---

## 11. アイコン

**ライブラリ:** [Lucide React](https://lucide.dev/) — shadcn/ui デフォルト

```tsx
import { ShoppingCart, Heart, Search, Store, User } from "lucide-react";

// サイズ統一
// sm: 16px (w-4 h-4) → インライン、バッジ内
// md: 20px (w-5 h-5) → ボタン内
// lg: 24px (w-6 h-6) → ナビゲーション
// xl: 32px (w-8 h-8) → カード内アイコン
```

**ルール:**
- 絵文字をアイコン代わりに使用しない
- ストローク幅: `1.5`（Lucide デフォルト）に統一
- インタラクティブなアイコンには `aria-label` 必須

---

## 12. 画像ガイドライン

- **フォーマット:** WebP / AVIF（Cloudinary 自動変換）
- **アスペクト比:** `aspect-[4/3]`（商品）、`aspect-square`（アバター）、`aspect-[16/9]`（バナー）
- **プレースホルダー:** blur プレースホルダー（`next/image` の blurDataURL）
- **1商品あたり上限:** 5枚（UI 上はカルーセル + ドットインジケーター）
- **アバターフォールバック:** イニシャル表示（Radix Avatar コンポーネント使用）
- **CLS 防止:** `width` / `height` を必ず指定、または `fill` + `relative` コンテナ

---

## 13. ページ構成パターン（マーケットプレイス）

| セクション | 目的 | 主要要素 |
|---|---|---|
| **ヒーロー** | 検索を CTA にする | 大見出し + 検索バー + 人気タグ |
| **カテゴリ** | ビジュアルで探す | 6〜8カテゴリ、グリッドまたは横スクロール |
| **注目商品** | 信頼感・商品数のアピール | 商品カード 4〜8件 |
| **安心ポイント** | 安心感の醸成 | アイコン + 短文（3ポイント） |
| **セラー向け CTA** | セラー獲得 | "5分でお店が開ける" バナー |

---

## 14. 禁止パターン

| パターン | 理由 |
|---|---|
| 絵文字をアイコン代わりに使用 | プラットフォーム依存、テーマ非対応 |
| ボタンに `cursor: default` | クリック可能と認識されない |
| ステータス表示をカラーのみで表現 | WCAG アクセシビリティ違反 |
| 非活性要素に `opacity: 0.3` | コントラスト不足。`0.38–0.5` + `pointer-events-none` を使用 |
| モバイルで `100vh` | iOS Safari のアドレスバー問題。`min-h-dvh` を使用 |
| コンポーネント内にハードコードした HEX 値 | テーマ変更不可。必ず CSS 変数 / Tailwind トークンを使用 |
| 状態変化を 0ms で行う | UX が不自然。最低 150ms のトランジションを設定 |
| 更新操作に `PUT` を使用 | CLAUDE.md の規約により常に `PATCH` を使用 |
| 日本語テキストを積極的に切り詰める | 日本語は単語境界がないため `line-clamp` は慎重に使用 |
| Z-index に任意の値を使用 | §8 のスケール定義を参照する |

---

## 15. アクセシビリティ チェックリスト（納品前確認）

- [ ] 本文テキストのコントラスト比 4.5:1 以上（ライト・ダーク両方）
- [ ] フォーカスリング: `ring-2 ring-ring ring-offset-2`（全インタラクティブ要素）
- [ ] アイコンのみのボタンに `aria-label` を設定
- [ ] 画像に `alt` テキストを設定（装飾画像は `alt=""`）
- [ ] フォームラベルに `<label>` または `aria-label` を設定（placeholder で代替しない）
- [ ] エラーメッセージをフィールド直下に表示（`role="alert"` または `aria-live="polite"`）
- [ ] Tab 順序が視覚的な順序と一致
- [ ] `prefers-reduced-motion` 対応（アニメーション duration → 1ms）
- [ ] タッチターゲット最小 44×44px
- [ ] モバイルで水平スクロールが発生しない
- [ ] 固定ナビバー後ろにコンテンツが隠れない（`pt-16` または `scroll-mt` で対処）
- [ ] スキップリンク（`<a href="#main">メインコンテンツへ</a>`）をナビゲーション前に配置
- [ ] ステータス表示はカラー単独でなくアイコン/テキストも併用
- [ ] エンプティステートに説明文 + 行動促進アクションを設定

---

## 16. ページ別オーバーライドの参照方法

特定ページを実装する場合の手順:

```
1. design-system/pages/[ページ名].md を確認する
2. ファイルが存在する → そのルールが本ファイルを上書きする
3. ファイルが存在しない → 本ファイルのルールのみを使用する

コンテキストプロンプト例:
"[ページ名] ページを実装します。
 design-system/MASTER.md を読んでください。
 design-system/pages/[ページ名].md も確認し、
 存在する場合はそのルールを優先してください。"
```

**既存のページ別オーバーライド:** _(未作成 — ページ実装時に順次追加)_
