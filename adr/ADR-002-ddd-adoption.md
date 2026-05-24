# ADR-002: DDD（ドメイン駆動設計）の採用

**ステータス:** 採用済み  
**作成日:** 2026-05-24  
**作成者:** Dao Nguyen

---

## コンテキスト

モジュラーモノリスを採用した（ADR-001 参照）後、内部の設計パターンを決める必要があった。選択肢として以下があった:

1. **トランザクションスクリプト** — Service にビジネスロジックを記述するシンプルな手続き型アプローチ
2. **ドメイン駆動設計（DDD）** — ビジネスドメインをモデル化し、集約・値オブジェクト・ドメインイベントを活用するアプローチ

---

## 決定事項

**Tactical DDD + Strategic DDD** を採用する。ただし、ポートフォリオプロジェクトであることを考慮し、**コードの意図明確化と保守性**を目的として現実的な範囲で適用する。

### 採用するパターン

| パターン | 採用 | 用途 |
|---|---|---|
| Bounded Context | ✓ | パッケージ境界 = 将来のマイクロサービス境界 |
| 集約（Aggregate） | ✓ | 一貫性の境界。集約ルート経由でのみ変更を受け付ける |
| 集約ルート（Aggregate Root） | ✓ | Repository の単位として機能 |
| 値オブジェクト（Value Object） | ✓ | `Money`, `Email`, `OrderStatus` 等を `record` で実装 |
| ドメインイベント（Domain Event） | ✓ | Spring Application Events でドメイン間通信 |
| Repository（集約単位） | ✓ | 1集約に1 Repository Interface |
| Application Service | ✓ | ユースケースの調整・トランザクション管理 |
| Domain Service | △ | 単一集約に収まらないロジックにのみ |
| CQRS | ✗ | オーバーエンジニアリング（MVPスコープ外） |
| Event Sourcing | ✗ | オーバーエンジニアリング |

---

## 理由

### 採用根拠

1. **既存パッケージ構成が Bounded Context に自然対応**  
   `identity / catalog / order / messaging / notification / review / platform / audit` というパッケージ分割は、DDD の Bounded Context の考え方に自然に対応している。設計を後から変える必要がない。

2. **集約によるビジネスルールのカプセル化**  
   例: `Order` が `OrderItem` を含む集約を定義することで、「注文明細は注文を経由してのみ追加・削除できる」という不変条件をコードで表現できる。

3. **値オブジェクトによる型安全性**  
   `int` で価格を扱う代わりに `Money` 型を導入することで、価格と在庫数の取り違えをコンパイル時に防ぐ。

4. **ドメインイベントによる疎結合**  
   Spring Events を使用することで、`identity` ドメインのセラー申請承認が `catalog` ドメインのショップ作成に直接依存しなくなる。

5. **ポートフォリオとしての学習価値**  
   DDD の実践的な実装はバックエンドエンジニアとして重要なスキルであり、ポートフォリオとしての価値が高い。

### 採用しないパターンの理由

- **CQRS**: 読み取りと書き込みを分離するには独立したデータストアや複雑なインフラが必要であり、MVPには過剰
- **Event Sourcing**: 状態管理が複雑になり、ポートフォリオの説明コストが高い

---

## 結果

### 得られるもの

- ビジネスルールがドメインオブジェクト内にカプセル化され、Service が薄くなる
- ドメイン間の依存が明示的になり、将来の分割が容易
- ポートフォリオとして DDD の実践を示せる

### 受け入れるトレードオフ

- 単純なCRUD操作でも集約・値オブジェクトを経由するため、コード量が増える
- DDD に不慣れなメンバーにとって学習コストがある（ソロ開発のため影響は限定的）
- 集約の設計ミスが後のリファクタリングコストになる

### Bounded Context の境界ルール

各 Bounded Context は以下のルールを守る:

```
do:
  ・同一コンテキスト内のエンティティを直接 import する
  ・Application Service または Spring Events 経由でクロスドメイン通信する

don't:
  ・他コンテキストのエンティティを直接 import する
  ・他コンテキストの Repository を DI して直接使用する
```

---

## 参照

- [DOMAIN_MODEL.md](../docs/architecture/DOMAIN_MODEL.md)
- [ADR-001-modular-monolith.md](./ADR-001-modular-monolith.md)
- Evans, Eric. "Domain-Driven Design: Tackling Complexity in the Heart of Software."
