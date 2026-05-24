# ADR-003: エンティティ別ソフト削除戦略

**ステータス:** 採用済み  
**作成日:** 2026-05-24  
**作成者:** Dao Nguyen

---

## コンテキスト

マーケットプレイスのデータには、削除後もビジネス・法的理由で保持が必要なものがある:

- **注文・決済**: 法人税法・電子帳簿保存法により7年間の保持が必要
- **ユーザー**: 退会後も注文履歴との参照整合性が必要。個人情報保護法により不要になった PII は削除義務あり
- **商品**: 過去の注文明細はスナップショット保存のため、商品削除後も履歴は残す
- **ショップ**: ユーザー退会時に連動して削除

エンティティごとに性質が異なるため、一律のソフト削除戦略は適切でない。

---

## 決定事項

**エンティティの性質に応じて3つの削除方式を使い分ける:**

| 削除方式 | 対象エンティティ | 実装 |
|---|---|---|
| `deleted_at` タイムスタンプ | `users`, `shops`, `categories` | `@SQLRestriction("deleted_at IS NULL")` |
| `status = 'DELETED'` | `products` | 既存ステータス管理を流用 |
| 削除操作を提供しない | `orders`, `order_items`, `payments` | API に DELETE エンドポイントを用意しない |

---

## 理由

### `deleted_at` タイムスタンプを使うエンティティ

**users / shops / categories**

- ユーザーが「退会申請」した際に論理削除が必要（USER-03）
- Soft delete 後も一定期間は匿名化バッチが走るまで ID を保持し、外部キー参照の整合性を維持する
- `@MappedSuperclass` の `SoftDeletableEntity` として共通化し、`@SQLRestriction` で通常クエリから自動除外

```java
// 共通基底クラス
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {
    @Column(name = "deleted_at")
    protected LocalDateTime deletedAt;

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}

// @SQLRestriction で通常クエリに自動フィルタリング
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User extends SoftDeletableEntity { ... }
```

### `status = 'DELETED'` を使うエンティティ

**products**

- 商品は既に `DRAFT / ACTIVE / INACTIVE` のステータス管理があり、`DELETED` を追加するのが自然
- `deleted_at` カラムは追加しない（ステータスで代替）
- 商品物理削除バッチ実行前は ID が保持されるため、`cart_items` との参照整合性が維持される
- `@SQLRestriction` ではなくアプリ層でステータスフィルタリングを行う

```java
// products の削除は status 変更で対応
public void delete() {
    if (this.status == ProductStatus.DELETED) {
        throw new IllegalStateException("既に削除済みです");
    }
    this.status = ProductStatus.DELETED;
}
```

### 削除操作を提供しないエンティティ

**orders / order_items / payments**

- 会計記録として法的に保持義務がある（7年間）
- API に DELETE エンドポイントを用意しないことでアプリ層から削除不可能にする
- 将来の誤実装を防ぐため、Repository に `delete*()` メソッドを expose しない

---

## 結果

### Soft delete の実装統一

`SoftDeletableEntity` 基底クラスと `@SQLRestriction` の組み合わせにより、`deleted_at` ベースのエンティティはすべての JPA クエリから自動的に除外される。

```sql
-- @SQLRestriction により自動で付与される WHERE 条件
SELECT * FROM users WHERE deleted_at IS NULL AND ...
```

**注意**: Soft delete されたレコードを意図的に参照したい場合（管理者の匿名化バッチ等）は、ネイティブクエリ（`@Query(nativeQuery = true)`）を使用する。

### データ保持ポリシーとの連携

Soft delete 後のデータはバッチジョブで段階的に処理する:

| エンティティ | Soft delete | 90日後 | 180日後 |
|---|---|---|---|
| `users` | `deleted_at` 設定 | 匿名化バッチ | - |
| `shops` | `deleted_at` 設定（user に連動） | 匿名化バッチ | - |
| `categories` | `deleted_at` 設定 | - | 物理削除バッチ |
| `products` | `status = 'DELETED'` | - | 物理削除バッチ |

詳細は `REQUIREMENTS.md § 15` を参照。

---

## 参照

- `REQUIREMENTS.md § 4.6`（データ整合性・削除方針）
- `REQUIREMENTS.md § 15`（データ保持ポリシー）
- [docs/design/DB_DESIGN.md § 1.5](../docs/design/DB_DESIGN.md)
