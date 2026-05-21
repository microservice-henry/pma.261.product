# API Reference

API REST do Product Service. Todas as rotas autenticadas exigem o header `id-account` (injetado pelo Gateway após validar o JWT).

**Base URL local:** `http://localhost:8080`
**Base URL via Gateway:** `https://<gateway-host>/products/...`

---

## Endpoints

| Método | Path | Descrição |
|---|---|---|
| `POST` | `/products` | Cria um novo produto |
| `GET` | `/products` | Lista produtos (filtro opcional `?name=`) |
| `GET` | `/products/{id}` | Busca produto por id (servido por cache) |
| `DELETE` | `/products/{id}` | Remove produto (e invalida cache) |

---

## `POST /products`

Cria um novo produto. O campo `id` é gerado pelo banco via `gen_random_uuid()`. O `@CachePut` popula o Redis imediatamente, então a próxima leitura por `id` já é cache hit.

**Headers:**

- `id-account: <uuid>` — obrigatório
- `Content-Type: application/json`

**Body (request):**

```json
{
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

**Validação:**

| Campo | Regra | Erro |
|---|---|---|
| `name` | `@NotBlank` — não vazio | 400 `name must not be blank` |
| `price` | `@NotNull @Positive` | 400 `price must be positive` |
| `unit` | `@NotBlank` | 400 `unit must not be blank` |

=== "cURL"

    ```bash
    curl -i -X POST http://localhost:8080/products \
      -H 'id-account: 00000000-0000-0000-0000-000000000001' \
      -H 'Content-Type: application/json' \
      -d '{"name":"Tomato","price":10.12,"unit":"kg"}'
    ```

=== "Python"

    ```python
    import requests

    response = requests.post(
        "http://localhost:8080/products",
        headers={
            "id-account": "00000000-0000-0000-0000-000000000001",
            "Content-Type": "application/json",
        },
        json={"name": "Tomato", "price": 10.12, "unit": "kg"},
    )

    print(response.status_code)         # 201
    print(response.json())              # {'id': '...', 'name': 'Tomato', ...}
    ```

**Response 201 Created:**

```json
{
  "id": "ac340642-57f9-472d-8ae0-df613a176ee9",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

---

## `GET /products`

Lista todos os produtos. Suporta filtro **opcional** por nome (case-insensitive, busca por substring usando `findByNameContainingIgnoreCase`).

**Headers:**

- `id-account: <uuid>` — obrigatório

**Query parameters:**

| Param | Tipo | Descrição |
|---|---|---|
| `name` | string (opcional) | Filtra produtos cujo nome contém a substring (case-insensitive) |

!!! note "Sem cache"

    O endpoint `list` **não** é cacheado. Listas mudam com frequência e o ganho seria marginal — apenas `get(id)` aproveita o cache.

=== "cURL"

    ```bash
    # Lista completa
    curl -s http://localhost:8080/products \
      -H 'id-account: 00000000-0000-0000-0000-000000000001' | jq .

    # Filtrando por nome
    curl -s "http://localhost:8080/products?name=tom" \
      -H 'id-account: 00000000-0000-0000-0000-000000000001' | jq .
    ```

=== "Python"

    ```python
    import requests

    headers = {"id-account": "00000000-0000-0000-0000-000000000001"}

    # Lista completa
    all_products = requests.get(
        "http://localhost:8080/products", headers=headers
    ).json()

    # Com filtro por nome
    matches = requests.get(
        "http://localhost:8080/products",
        headers=headers,
        params={"name": "tom"},
    ).json()
    ```

**Response 200 OK:**

```json
[
  {
    "id": "ac340642-57f9-472d-8ae0-df613a176ee9",
    "name": "Tomato",
    "price": 10.12,
    "unit": "kg"
  },
  {
    "id": "b21cc304-1a4e-4f8b-b22d-9c4f8a7a6e0e",
    "name": "Cheese",
    "price": 0.62,
    "unit": "slice"
  }
]
```

---

## `GET /products/{id}`

Recupera um produto por `id`. **Servido por cache** com TTL de 60s. O método é anotado com `@Cacheable(value="products", key="#id")`.

**Headers:**

- `id-account: <uuid>` — obrigatório

**Path params:**

| Param | Tipo | Descrição |
|---|---|---|
| `id` | UUID | Id do produto |

=== "cURL"

    ```bash
    curl -s http://localhost:8080/products/ac340642-57f9-472d-8ae0-df613a176ee9 \
      -H 'id-account: 00000000-0000-0000-0000-000000000001' | jq .
    ```

=== "Python"

    ```python
    import requests

    product_id = "ac340642-57f9-472d-8ae0-df613a176ee9"
    response = requests.get(
        f"http://localhost:8080/products/{product_id}",
        headers={"id-account": "00000000-0000-0000-0000-000000000001"},
    )

    if response.status_code == 200:
        product = response.json()
    elif response.status_code == 404:
        print("Produto não encontrado")
    ```

**Response 200 OK (cache hit ou miss):**

```json
{
  "id": "ac340642-57f9-472d-8ae0-df613a176ee9",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

**Response 404 Not Found:**

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Product not found",
  "instance": "/products/ac340642-57f9-472d-8ae0-df613a176ee9"
}
```

---

## `DELETE /products/{id}`

Remove o produto e **invalida o cache** (`@CacheEvict(value="products", key="#id")`).

**Headers:**

- `id-account: <uuid>` — obrigatório

=== "cURL"

    ```bash
    curl -i -X DELETE http://localhost:8080/products/ac340642-57f9-472d-8ae0-df613a176ee9 \
      -H 'id-account: 00000000-0000-0000-0000-000000000001'
    ```

=== "Python"

    ```python
    import requests

    product_id = "ac340642-57f9-472d-8ae0-df613a176ee9"
    response = requests.delete(
        f"http://localhost:8080/products/{product_id}",
        headers={"id-account": "00000000-0000-0000-0000-000000000001"},
    )
    assert response.status_code == 204
    ```

**Response 204 No Content** (sucesso, sem corpo).

**Response 404 Not Found** (produto inexistente) — mesmo formato do `GET`.

---

## Modelo de erro (RFC 7807 — ProblemDetails)

Habilitado por `spring.mvc.problemdetails.enabled: true`. Todos os erros seguem o mesmo formato canônico:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/products"
}
```

### Códigos de status

| Status | Quando |
|---|---|
| `200 OK` | Sucesso em `GET` |
| `201 Created` | Sucesso em `POST` |
| `204 No Content` | Sucesso em `DELETE` |
| `400 Bad Request` | Body inválido (`@NotBlank`, `@Positive`), header `id-account` faltando, JSON malformado |
| `404 Not Found` | `GET`/`DELETE` em `id` inexistente |
| `500 Internal Server Error` | Falha no banco ou no Redis |

### Exemplo — body inválido

```bash
curl -i -X POST http://localhost:8080/products \
  -H 'id-account: 00000000-0000-0000-0000-000000000001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"","price":-1,"unit":""}'
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/products"
}
```

---

## Endpoints de gerenciamento (Spring Actuator)

Habilitados em `application.yaml` via `management.endpoints.web.exposure.include: health,info,prometheus,caches`. Não exigem `id-account` (são internos pro operador / Prometheus).

| Path | Descrição |
|---|---|
| `GET /actuator/health` | Liveness/readiness (status do Postgres e do Redis) |
| `GET /actuator/info` | Build info |
| `GET /actuator/prometheus` | Métricas em formato Prometheus (HTTP requests, JVM, cache, repository) |
| `GET /actuator/caches` | Lista de caches configurados (`["products"]`) |
| `GET /actuator/caches/products` | Estado live do cache `products` |

```bash
# Verifica saúde
curl -s http://localhost:8080/actuator/health | jq .

# Métricas Prometheus (formato texto)
curl -s http://localhost:8080/actuator/prometheus | head -20

# Estado live do cache
curl -s http://localhost:8080/actuator/caches/products | jq .
```

Detalhes do uso dessas métricas para validar o cache estão em [Bottlenecks](bottlenecks.md).
