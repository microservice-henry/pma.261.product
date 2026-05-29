# Vídeo de apresentação

<div style="position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden; max-width: 100%; margin: 2rem 0;">
  <iframe
    style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; border-radius: 8px;"
    src="https://www.youtube.com/embed/IS5kZkxHp-A"
    title="Product API — Nathan | PMA 26.1"
    frameborder="0"
    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
    referrerpolicy="strict-origin-when-cross-origin"
    allowfullscreen>
  </iframe>
</div>

🔗 Link direto: [youtu.be/IS5kZkxHp-A](https://youtu.be/IS5kZkxHp-A){:target="_blank"}

---

## O que foi apresentado

### 1. Product API — Endpoints principais

Demonstração ao vivo do `POST /products`, `GET /products?name=...` e `GET /products/{id}` passando pelo Gateway com autenticação JWT (header `id-account` injetado). Validação de entrada via Jakarta Validation com resposta no padrão `ProblemDetails` (RFC 7807) quando o body é inválido.

### 2. Bottleneck — Cache Redis (`@Cacheable`)

O `ProductService` é cacheado em Redis com TTL de 60 segundos. As três anotações cobrem o ciclo de vida da entrada: `@CachePut` na criação popula o cache imediatamente, `@Cacheable` no `get` serve de Redis quando o `id` já está cacheado, e `@CacheEvict` no `delete` remove a entrada. Demonstrado ao vivo com três `GET /products/{id}` consecutivos.

```java
@CachePut(value = "products", key = "#result.id()")
public ProductResponse create(ProductRequest request) { ... }

@Cacheable(value = "products", key = "#id")
public ProductResponse get(UUID id) { ... }

@CacheEvict(value = "products", key = "#id")
public void delete(UUID id) { ... }
```

Resultado mensurado no vídeo: **1ª chamada ~22ms** (miss + Postgres), **2ª/3ª chamadas ~3ms** (hit no Redis) — **~7× de speedup**.

### 3. Bottleneck — Observabilidade

Spring Boot Actuator + Micrometer expondo métricas no formato Prometheus em `/actuator/prometheus`. Sem código custom — apenas configuração. Demonstrado ao vivo:

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'http_server_requests.*products/|spring_data_repository'
```

Os contadores provam que **3 GETs HTTP** chegaram em `/products/{id}` mas **`findById` não aparece nas métricas do repository** — o cache absorveu 100% das leituras, e o Postgres recebeu zero queries.
