# product-service

API REST para gerenciar o catálogo de produtos da loja. Entrega individual do Nathan no projeto da disciplina **PMA 26.1** (Insper).

## Stack

- Java 25 / Spring Boot 4.0.3
- PostgreSQL (schema `products`)
- Flyway migrations
- Redis (cache `@Cacheable` com TTL 60s)
- Prometheus metrics em `/actuator/prometheus`

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/products` | Cria produto |
| `GET` | `/products` | Lista produtos (filtro opcional `?name=`) |
| `GET` | `/products/{id}` | Busca por id |
| `DELETE` | `/products/{id}` | Remove produto |

Todas as rotas autenticadas exigem o header `id-account` (injetado pelo gateway).

## Rodando localmente

```bash
cp .env.example .env
docker compose up -d --build
```

Sobe Postgres + Redis + product na mesma rede Docker. Flyway aplica as migrations no startup.

## Docker

```bash
mvn -B -DskipTests clean package
docker build -t microservice-henry/product .
docker run -p 8080:8080 --env-file .env microservice-henry/product
```

## Kubernetes

Manifests em `k8s/` (configmap, secrets, deployment, service, hpa). Edite `secrets.yaml` antes de aplicar:

```bash
echo -n "sua-senha" | base64
```

## Documentação completa

- 🌐 Site deste repo: [microservice-henry.github.io/pma.261.product](https://microservice-henry.github.io/pma.261.product/)
- 🌐 Site umbrella (plataforma): [microservice-henry.github.io/pma.26.1.exchange](https://microservice-henry.github.io/pma.26.1.exchange/)

Para rodar este site MkDocs localmente:

```bash
pip install -r docs-requirements.txt
mkdocs serve
# → http://localhost:8000
```
