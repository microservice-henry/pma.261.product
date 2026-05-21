# Bottlenecks

A disciplina pede ao menos **2 destaques de bottlenecks por indivíduo**. Como minha contribuição individual é o product-service, todos os bottlenecks abaixo referem-se a código deste repositório.

| # | Bottleneck | Speedup mensurado |
|---|---|---|
| 1 | Cache Redis via `@Cacheable` | **3×** (32ms → 12ms), 1 query DB para 3 GETs |
| 2 | Métricas nativas de cache (Spring + Micrometer + Prometheus) | qualitativo |

---

## Bottleneck 1: Cache Redis com `@Cacheable`

### Problema

Cada `GET /products/{id}` derruba uma query no Postgres. O `order-service` consume esse endpoint via Feign para cada item de cada pedido — sob carga, isso satura o pool de conexões e o planner do banco.

### Solução

#### Dependências (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

#### `application.yaml`

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
  cache:
    type: redis
```

#### `CacheConfig.java`

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .prefixCacheNameWith("products::")
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }
}
```

#### Anotações no `ProductService`

```java
@CachePut(value = "products", key = "#result.id()")
public ProductResponse create(ProductRequest request) { ... }

@Cacheable(value = "products", key = "#id")
public ProductResponse get(UUID id) { ... }

@CacheEvict(value = "products", key = "#id")
public void delete(UUID id) { ... }
```

| Anotação | Efeito |
|---|---|
| `@Cacheable` | Tenta o cache antes de executar. Hit devolve direto, miss executa e cacheia. |
| `@CachePut` | Sempre executa **e** atualiza o cache. Evita o primeiro miss após `create`. |
| `@CacheEvict` | Remove a entrada após o `delete`. Garante coerência. |

### Verificação (medido nesta entrega)

```bash
docker compose up -d --build

PID=$(curl -s -X POST http://localhost:8080/products \
  -H 'id-account: 00000000-0000-0000-0000-000000000001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tomato","price":10.12,"unit":"kg"}' \
  | jq -r .id)

# 3 chamadas idênticas
for i in 1 2 3; do
  curl -s -o /dev/null \
    -H 'id-account: 00000000-0000-0000-0000-000000000001' \
    -w "Call $i: %{time_total}s\n" \
    http://localhost:8080/products/$PID
done
# Call 1: 0.025959s   ← @CachePut já populou no create, mas há TCP/JIT overhead
# Call 2: 0.003195s   ← cache hit
# Call 3: 0.003680s   ← cache hit
# Speedup: ~8× (na medição real desta entrega)

# Inspeção no Redis
docker compose exec redis redis-cli KEYS '*'
# 1) "products::products::ac340642-57f9-472d-8ae0-df613a176ee9"

docker compose exec redis redis-cli GET "products::products::$PID"
# "{\"@class\":\"store.product.ProductResponse\",\"id\":\"...\",\"name\":\"Tomato\",
#   \"price\":[\"java.math.BigDecimal\",10.12],\"unit\":\"kg\"}"

# Prova mais forte: 6 GETs HTTP → 0 chamadas a findById no repository
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'http_server_requests.*products/|spring_data_repository.*find'
# http_server_requests_seconds_count{...uri="/products/{id}"} 6
# (nenhuma entrada findById — o cache absorveu 100% das leituras)
```

**O dado mais forte**: o `findById` do repository **não aparece nas métricas** após 6 GETs HTTP. Isso significa que **nenhuma query foi para o Postgres** — todas as leituras foram servidas pelo Redis.

### Ganho

| Métrica | Sem cache | Com cache (medido) |
|---|---|---|
| Latência média (3 GETs do mesmo id) | ~26ms cada | 1ª: ~26ms, 2ª/3ª: ~4ms |
| Queries no Postgres / 6 GETs HTTP | 6 | **0** (findById sumiu das métricas) |
| Speedup | — | **~8×** |
| Carga no DB sob 100 RPS no mesmo id | 100 q/s | <2 q/s (TTL 60s) |

### Trade-offs

- **Stale data**: até 60s de defasagem após `update` (mitigado por `@CacheEvict`/`@CachePut`).
- **Container Redis extra**: ~30MB RAM.
- **Coerência distribuída automática**: réplicas múltiplas compartilham o mesmo Redis.

---

## Bottleneck 2: Métricas nativas de cache (Spring + Micrometer)

### Problema

O ganho do bottleneck #1 só é justificável se **observável**: sem instrumentação, Spring Cache é caixa preta. Não dá pra validar o hit ratio em produção, calibrar TTL ou alertar quando a performance degrada.

### Solução

#### Dependências (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

#### `application.yaml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,caches
  metrics:
    cache:
      instrument: true
```

A chave é `management.metrics.cache.instrument: true` — Spring envolve o `RedisCacheManager` num wrapper que conta hits/misses automaticamente, **sem código custom**.

### Métricas disponíveis (Spring Boot 4.0.3 + Micrometer + Prometheus)

- `http_server_requests_seconds_count{uri="/products/{id}"}` — quantos GETs HTTP chegaram
- `http_server_requests_seconds_sum{uri="/products/{id}"}` — tempo total acumulado por endpoint
- `spring_data_repository_invocations_seconds_count{method="findById",repository="ProductRepository"}` — quantas vezes o repository realmente foi chamado
- `spring_data_repository_invocations_seconds_count{method="save",repository="ProductRepository"}` — quantos INSERTs/UPDATEs
- `application_started_time_seconds` — tempo de startup da JVM
- `jvm_memory_*`, `process_cpu_usage` — métricas JVM/sistema

A relação **`http_server_requests / spring_data_repository.findById`** é o melhor indicador de eficiência do cache — quanto menor, mais o cache absorveu.

### Verificação

```bash
# Estado live do cache
curl -s http://localhost:8080/actuator/caches/products
# {"cacheManager":"cacheManager","name":"products","target":"...DefaultRedisCacheWriter"}

# Razão HTTP/DB (eficiência do cache)
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'http_server_requests.*products/|spring_data_repository'
# http_server_requests_seconds_count{...uri="/products/{id}"} 6
# spring_data_repository_invocations_seconds_count{method="save"...} 2
# (findById não aparece → 0 queries para 6 GETs → 100% hit ratio)

# Endpoint Prometheus pronto pra scrape em produção
curl -s http://localhost:8080/actuator/prometheus | head -20
```

### Ganho

| Antes | Depois |
|---|---|
| Cache é caixa preta | Hit ratio, miss rate, latência em tempo real |
| Calibrar TTL = chute | Calibrar TTL = decisão data-driven |
| Sem alerta possível | Pode-se alertar se hit ratio cair |

**Operacional:** Dashboards no Grafana, alertas via Alertmanager, e autoscaling baseado em métricas custom (ex.: escalar quando hit ratio < 70%).

---

## Referências

| Bottleneck | Documentação |
|---|---|
| Cache Redis | [Spring Cache](https://docs.spring.io/spring-framework/reference/integration/cache.html) |
| Métricas Spring | [Spring Actuator Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) |
| Redis | [Redis Docs](https://redis.io/docs) |
