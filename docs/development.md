# Development

Guia para rodar, testar e contribuir com o Product Service localmente.

---

## Pré-requisitos

| Ferramenta | Versão | Por quê |
|---|---|---|
| **JDK 25** (Eclipse Temurin) | ≥ 25 | Spring Boot 4.0.3 exige Java 25 |
| **Maven** | ≥ 3.9 | Build do jar e gerenciamento de deps |
| **Docker** + **Docker Compose** | recente | Subir Postgres + Redis + product em uma rede |
| **`jq`** (opcional) | qualquer | Formatar saídas JSON nos smoke tests |
| **Python 3.12** + `pip` (opcional) | — | Rodar o site MkDocs localmente |

```bash
# macOS — via Homebrew
brew install openjdk@25 maven docker jq

# Verifica
java --version       # openjdk 25 ...
mvn --version        # Apache Maven 3.9.x
docker --version
```

---

## Setup local — passo a passo

### 1. Clonar o repositório

```bash
git clone https://github.com/microservice-henry/pma.261.product.git
cd pma.261.product
```

### 2. Copiar `.env.example` para `.env`

```bash
cp .env.example .env
```

O arquivo `.env` contém valores default que funcionam com o `compose.yaml` (Postgres + Redis locais).

### 3. Subir tudo com Docker Compose

```bash
docker compose up -d --build
```

O compose orquestra três containers:

- `postgres` (Postgres 16) — schema `products` criado pelo Flyway no startup.
- `redis` (Redis 7-alpine) — cache.
- `product` — Spring Boot 4, depende dos dois acima com healthchecks.

Verificar que tudo subiu:

```bash
docker compose ps
docker compose logs -f product | grep "Started ProductApplication"
```

### 4. Smoke test

```bash
# Criar
PID=$(curl -s -X POST http://localhost:8080/products \
  -H 'id-account: 00000000-0000-0000-0000-000000000001' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Tomato","price":10.12,"unit":"kg"}' | jq -r .id)

echo "Created: $PID"

# Demonstrar o cache (3 GETs no mesmo id)
for i in 1 2 3; do
  curl -s -o /dev/null \
    -H 'id-account: 00000000-0000-0000-0000-000000000001' \
    -w "Call $i: %{time_total}s\n" \
    http://localhost:8080/products/$PID
done

# Inspecionar o Redis
docker compose exec redis redis-cli KEYS '*'
```

Esperado: 1ª chamada ~26ms, 2ª/3ª ~4ms — o cache está absorvendo as leituras. Ver [Bottlenecks](bottlenecks.md) para o experimento completo.

### 5. Derrubar a stack

```bash
docker compose down          # mantém volumes (dados persistem)
docker compose down -v       # também remove volumes (banco zerado)
```

---

## Variáveis de ambiente

| Variável | Default (compose) | Default (k8s) | Descrição |
|---|---|---|---|
| `DATABASE_HOST` | `postgres` | `postgres` (ConfigMap) | Host do Postgres |
| `DATABASE_PORT` | `5432` | `5432` (ConfigMap) | Porta do Postgres |
| `DATABASE_DB` | `storedb` | `storedb` (ConfigMap) | Nome do banco |
| `DATABASE_USERNAME` | `postgres` | Secret | Usuário |
| `DATABASE_PASSWORD` | `postgres` | Secret | Senha |
| `REDIS_HOST` | `redis` | `redis` (ConfigMap) | Host do Redis |
| `REDIS_PORT` | `6379` | `6379` (ConfigMap) | Porta do Redis |

No Kubernetes, `DATABASE_USERNAME` e `DATABASE_PASSWORD` vêm de `k8s/secrets.yaml` (base64). O resto vem do `k8s/configmap.yaml`.

---

## Build standalone (sem Docker)

Se você já tem Postgres e Redis rodando localmente:

```bash
# Compila o jar
mvn clean package

# Roda passando env vars
DATABASE_HOST=localhost DATABASE_PORT=5432 \
DATABASE_DB=storedb DATABASE_USERNAME=postgres DATABASE_PASSWORD=postgres \
REDIS_HOST=localhost REDIS_PORT=6379 \
  java -jar target/product-service-1.0.0.jar
```

O serviço sobe em `http://localhost:8080`.

---

## Build da imagem Docker

```bash
mvn -B -DskipTests clean package
docker build -t microservice-henry/product .
docker run -p 8080:8080 --env-file .env microservice-henry/product
```

A imagem usa `eclipse-temurin:25` como base e copia o jar gerado pelo Maven.

---

## Testes

```bash
mvn test
```

Stack de testes:

- **JUnit 5** — framework
- **Spring Boot Test** — context de teste
- **AssertJ** — asserções fluentes (`assertThat(...)`)
- **Mockito** — mocks

Tudo já vem via `spring-boot-starter-test` no `pom.xml`.

---

## Pipeline CI/CD (Jenkins)

O `Jenkinsfile` na raiz define duas stages:

1. **Build** — `mvn -B -DskipTests clean package`
2. **Build & Push Image** — `docker buildx` constrói imagem multi-arquitetura (linux/arm64 + linux/amd64) e dá push pro Docker Hub em `microservice-henry/product:latest` e `:${BUILD_ID}`.

```groovy
pipeline {
    agent any
    environment {
        SERVICE = 'product'
        NAME    = "microservice-henry/${env.SERVICE}"
    }
    stages {
        stage('Build') { steps { sh 'mvn -B -DskipTests clean package' } }
        stage('Build & Push Image') { /* docker buildx multi-arch + push */ }
    }
}
```

Para rodar o pipeline, o Jenkins precisa de uma credential `dockerhub-credential` (username + token).

---

## Deploy em Kubernetes

Manifests em `k8s/` prontos para aplicação manual:

```bash
# Trocar credenciais antes (Settings → secrets.yaml)
echo -n "minha-senha" | base64

# Apply em ordem
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

# Verificar rollout
kubectl rollout status deployment/product --timeout=120s
kubectl get pods -l app=product
kubectl get hpa product
```

O **HPA** mantém 1 réplica em idle e escala até 5 quando o CPU médio passa de 50%.

---

## Documentação MkDocs

### Rodar local

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r docs-requirements.txt
mkdocs serve
# → http://localhost:8000
```

Mudanças em `docs/**` ou `mkdocs.yml` são live-reloaded.

### Build estático

```bash
mkdocs build --strict
# → ./site/ contém HTML + assets
```

O `--strict` falha em qualquer warning (links quebrados, anchors inexistentes) — usar como gate antes de comitar.

### Deploy automático (GitHub Pages)

O workflow `.github/workflows/docs.yml` dispara em qualquer push em `main` que toque em `docs/`, `mkdocs.yml` ou `docs-requirements.txt`. Ele:

1. Faz checkout
2. Instala Python 3.12 + deps
3. Executa `mkdocs gh-deploy --force` → publica HTML na branch `gh-pages`

O GitHub Pages serve essa branch em `https://microservice-henry.github.io/pma.261.product/`.

---

## Convenções de código

- **Java 25** — usar records onde fizer sentido, prefer `var` para tipos locais óbvios.
- **Lombok** — `@RequiredArgsConstructor`, `@Getter`/`@Setter`, `@NoArgsConstructor`/`@AllArgsConstructor` para entities.
- **Naming**:
    - `*Controller` — REST endpoint (`@RestController`)
    - `*Service` — regras de negócio (`@Service`)
    - `*Repository` — JPA (`extends JpaRepository`)
    - `*Request` / `*Response` — DTOs como `record`
- **Cache annotations** — sempre no service layer, nunca no controller ou repository.
- **Validação** — Jakarta Validation (`@NotBlank`, `@Positive`, etc) nos records de request, `@Valid` no controller.
