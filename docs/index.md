# Product Service

API REST para gerenciar o catálogo de produtos da loja. Microsserviço Spring Boot 4 com persistência PostgreSQL, cache Redis e métricas Prometheus.

## Stack

- **Java 25** + **Spring Boot 4.0.3**
- **PostgreSQL** (schema `products`) — produção via AWS RDS
- **Flyway** para migrations
- **Redis** via `spring-boot-starter-cache` + `spring-boot-starter-data-redis`
- **Prometheus** + Micrometer via Spring Boot Actuator
- **Kubernetes** (EKS) com HPA (1-5 réplicas, target 50% CPU)
- **Docker** (`eclipse-temurin:25`)
- **Jenkins** — Build → Push → (Deploy opcional)

## Endpoints (resumo)

| Método | Path | Descrição |
|---|---|---|
| `POST` | `/products` | Cria produto |
| `GET` | `/products` | Lista todos (filtro opcional `?name=...`) |
| `GET` | `/products/{id}` | Busca por id |
| `DELETE` | `/products/{id}` | Remove |

Detalhes em [Endpoints](endpoints.md).

## Estrutura do código

```
src/main/java/store/product/
├── ProductApplication.java   # @SpringBootApplication
├── ProductController.java    # @RestController @RequestMapping("/products")
├── ProductService.java       # @Cacheable/@CachePut/@CacheEvict
├── ProductRepository.java    # extends JpaRepository<Product, UUID>
├── Product.java              # @Entity com Lombok @Getter/@Setter
├── ProductRequest.java       # record + @NotBlank/@NotNull/@Positive
├── ProductResponse.java      # record + static from(Product)
└── CacheConfig.java          # RedisCacheManager (TTL 60s, prefix products::)
```

## Destaques implementados

- ✅ CRUD com cache Redis (3× speedup — ver [Bottlenecks](bottlenecks.md))
- ✅ Métricas nativas de cache via Spring + Micrometer + Prometheus
- ✅ Validação via Jakarta Validation (`@NotBlank`, `@Positive`)
- ✅ Schema isolado `products` no Postgres
- ✅ Kubernetes manifests com HPA (CPU 50%, 1-5 réplicas)
- ✅ Pipeline Jenkins (build + push para Docker Hub)
- ✅ Confiança no perímetro do gateway (`id-account` injetado via header)
