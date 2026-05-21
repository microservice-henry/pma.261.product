# Repositórios

## Minha entrega individual

| Componente | Repositório | Site |
|---|---|---|
| **Product Service (este)** | [microservice-henry/pma.261.product](https://github.com/microservice-henry/pma.261.product) | [microservice-henry.github.io/pma.261.product](https://microservice-henry.github.io/pma.261.product/) |

## Documentação central (umbrella)

Site agregador da plataforma com visão geral do projeto, arquitetura e links para todos os serviços:

- 🌐 [microservice-henry.github.io/pma.26.1.exchange](https://microservice-henry.github.io/pma.26.1.exchange/)

## Demais serviços do sistema (contexto)

Os serviços abaixo compõem o mesmo projeto distribuído. Não são minha contribuição individual — listados aqui como referência.

| Componente | Função |
|---|---|
| Account Service | CRUD de contas + hash SHA-256 de senhas |
| Auth Service | Geração e validação de JWT (HS256) |
| Gateway Service | API gateway com filtro de autorização |
| Order Service | Pedidos, consome account e product |
| Exchange Service | Cotações de moeda |
