# Vídeo de apresentação

Demonstração ao vivo do `product-service`: arquitetura, CRUD funcionando, validação RFC 7807 e os dois bottlenecks (cache Redis com speedup mensurável + métricas Prometheus).

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

## Roteiro

| Tempo | Bloco | Conteúdo |
|---|---|---|
| **0:00–0:15** | Abertura | Identificação e contexto |
| **0:15–0:45** | Arquitetura | Visão da plataforma + papel do product-service no Trusted Layer |
| **0:45–1:30** | Demo CRUD | `POST`, `GET` com filtro `?name=`, validação RFC 7807 |
| **1:30–2:05** | Bottleneck #1 | Cache Redis ao vivo — speedup de ~7× (22ms → 3ms) |
| **2:05–2:30** | Bottleneck #2 | Métricas Prometheus + prova de 0 queries no Postgres para 3 GETs |
| **2:30** | Encerramento | Links para repo, imagem Docker e site |

---

## Comandos executados na demo

Reproduza localmente seguindo o guia de [Development](development.md).

### Criação e validação

```bash
PID=$(curl -s -X POST http://localhost:8080/products \
  -H 'id-account: 00000000-0000-0000-0000-000000000001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tomato","price":10.12,"unit":"kg"}' | jq -r .id)
```

### Demonstração do cache (3 GETs no mesmo `id`)

```bash
for i in 1 2 3; do
  curl -s -o /dev/null \
    -H 'id-account: 00000000-0000-0000-0000-000000000001' \
    -w "Call $i: %{time_total}s\n" \
    http://localhost:8080/products/$PID
done
```

### Inspeção do Redis e das métricas

```bash
docker compose exec redis redis-cli KEYS '*'

curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'http_server_requests.*products/|spring_data_repository'
```
