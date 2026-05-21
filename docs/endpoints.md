# Endpoints

Todas as rotas autenticadas exigem o header `id-account` (injetado pelo gateway após validar JWT).

## `POST /products` — cria produto

**Headers:**
- `id-account: <uuid>` (obrigatório)
- `Content-Type: application/json`

**Body:**

```json
{
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

**Validação:**

| Campo | Regra |
|---|---|
| `name` | `@NotBlank` — não pode ser nulo ou só espaços |
| `price` | `@NotNull @Positive` — obrigatório, > 0 |
| `unit` | `@NotBlank` — não pode ser nulo ou só espaços |

Violação retorna `400 Bad Request` com mensagem de erro.

**Response (sucesso):**

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

**Cache:** `@CachePut(value="products", key="#result.id()")` — produto recém-criado já entra cacheado.

---

## `GET /products` — lista produtos

**Headers:**
- `id-account: <uuid>` (obrigatório)

**Query params:**
- `name` (opcional) — filtra produtos cujo nome **contém** a string (case-insensitive)

**Exemplo:**

```http
GET /products HTTP/1.1
id-account: 0195abfb-...

GET /products?name=tom HTTP/1.1
id-account: 0195abfb-...
```

**Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
    "name": "Tomato",
    "price": 10.12,
    "unit": "kg"
  },
  {
    "id": "0195abfe-e416-7052-be3b-27cdaf12a984",
    "name": "Cheese",
    "price": 0.62,
    "unit": "slice"
  }
]
```

**Cache:** `list` **não** é cacheado — listas mudam com frequência. Só `get(id)` se beneficia do cache.

---

## `GET /products/{id}` — busca por id

**Headers:**
- `id-account: <uuid>` (obrigatório)

**Response (encontrado):**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

**Response (não encontrado):**

```http
HTTP/1.1 404 Not Found
```

**Cache:** `@Cacheable(value="products", key="#id")`. 1ª chamada hita o Postgres; as seguintes vêm do Redis até o TTL de 60s expirar ou o produto ser modificado/deletado.

---

## `DELETE /products/{id}` — remove produto

**Headers:**
- `id-account: <uuid>` (obrigatório)

**Response (sucesso):**

```http
HTTP/1.1 204 No Content
```

**Response (não encontrado):**

```http
HTTP/1.1 404 Not Found
```

**Cache:** `@CacheEvict(value="products", key="#id")` — remove a entrada do cache imediatamente após o delete.

---

## Modelo (DTOs)

### `ProductRequest`

```java
public record ProductRequest(
    @NotBlank String name,
    @NotNull @Positive BigDecimal price,
    @NotBlank String unit
) {}
```

### `ProductResponse`

```java
public record ProductResponse(
    UUID id,
    String name,
    BigDecimal price,
    String unit
) {
    public static ProductResponse from(Product product) { ... }
}
```

### Entity `Product`

```java
@Entity @Table(name = "product")
public class Product {
    @Id @GeneratedValue
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    @Column(nullable = false, length = 50)
    private String unit;
}
```

## Schema (Flyway)

| Migration | Conteúdo |
|---|---|
| `V1__create_products_table.sql` | `CREATE TABLE products.product (id UUID PK, name, price NUMERIC(10,2), unit)` |

ID gerado pelo Postgres via `gen_random_uuid()` (default da coluna).

## Endpoints de gerenciamento (Actuator)

| Path | Conteúdo |
|---|---|
| `GET /actuator/health` | Liveness/readiness |
| `GET /actuator/prometheus` | Métricas no formato Prometheus |
| `GET /actuator/caches` | Lista de caches configurados |
| `GET /actuator/caches/products` | Estado live do cache `products` |
