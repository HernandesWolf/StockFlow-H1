StockFlow
API REST para gestão de vendas e estoque de pequenos negócios, feita com Java 21 + Spring Boot. O foco não é só cadastrar produtos: é garantir a integridade das operações (estoque nunca negativo, nenhuma venda duplicada, histórico auditável).

Como rodar
Pré-requisitos: Java 21, Maven e Docker.

# Opção 1 - tudo em containers (banco + API)
docker compose up --build

# Opção 2 - só o banco no Docker, API pela linha de comando/IDE
docker compose up -d db
mvn spring-boot:run
Swagger UI (documentação interativa): http://localhost:8080/swagger-ui.html
Health check: http://localhost:8080/actuator/health
Usuário admin criado na 1ª execução: admin / admin12345 (troque via .env, veja .env.example)
Testes
mvn test      # unitários (*Test) - rápidos, sem Docker
mvn verify    # unitários + integração (*IT) - usam Testcontainers (precisa de Docker)
Fluxo rápido (exemplo)
# 1) login -> devolve o token JWT
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}'

# 2) registrar uma venda (o header Idempotency-Key é obrigatório)
curl -s -X POST localhost:8080/api/sales \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: venda-0001' \
  -d '{"items":[{"productId":1,"quantity":2}]}'
Endpoints
Área	Endpoints	Quem acessa
Auth	POST /api/auth/login	público
Usuários	POST/GET /api/users	ADMIN
Categorias, fornecedores, produtos	GET / POST / PUT / DELETE em /api/categories, /api/suppliers, /api/products	leitura: logado; escrita: ADMIN
Histórico de preços	GET /api/products/{id}/price-history	logado
Vendas	POST /api/sales, GET /api/sales, GET /api/sales/{id}	logado
Cancelar venda	POST /api/sales/{id}/cancel	ADMIN
Estoque	POST /api/stock/entries, /exits, GET /api/stock/movements	logado
Inventário (ajuste)	POST /api/stock/adjustments	ADMIN
Relatórios	/api/reports/top-products, /revenue, /stock-valuation, /top-margin, /reorder-alerts	ADMIN
Decisões de projeto (o que explicar numa entrevista)
1. Última unidade vendida por duas pessoas ao mesmo tempo
Cada venda roda em uma transação que trava as linhas dos produtos com SELECT ... FOR UPDATE (sempre em ordem de id, para evitar deadlock). A segunda venda espera a primeira terminar, enxerga o estoque já atualizado e recebe "estoque insuficiente". Como rede de segurança, o banco tem CHECK (stock_quantity >= 0). Teste: SaleConcurrencyIT.

2. Requisição repetida não duplica a venda (idempotência)
O cliente envia Idempotency-Key. A chave tem UNIQUE no banco e é gravada como primeira operação da transação. Repetir a requisição devolve a venda original; reutilizar a mesma chave com conteúdo diferente retorna erro. Duas requisições simultâneas com a mesma chave: a segunda esbarra no UNIQUE e devolve a venda da primeira. Teste: SaleIdempotencyIT.

3. Cancelamento com reversão controlada
Cancelar trava a venda e os produtos, devolve as unidades ao estoque e grava os lançamentos de reversão na mesma transação. Dois cancelamentos simultâneos não revertem o estoque duas vezes. Teste: SaleCancellationIT.

4. Auditoria no próprio banco
stock_movement, price_history e sale_item são append-only (triggers recusam UPDATE/DELETE); vendas nunca são apagadas, só canceladas. Cada movimentação guarda quem fez e o saldo após a operação. Teste: DatabaseConstraintsIT.

5. Outros pontos
Valores monetários em NUMERIC(12,2) / BigDecimal (nunca double).
Preço e custo são "fotografados" no item da venda: mudar o produto depois não altera vendas antigas.
Esquema versionado com Flyway; Hibernate só valida (ddl-auto: validate).
Segurança: Spring Security + JWT, perfis ADMIN e OPERATOR.
CI: GitHub Actions roda mvn verify e confere se a imagem Docker compila.
Estrutura
src/main/java/com/stockflow/
  config/      segurança, JWT, OpenAPI, usuário admin inicial
  domain/      entidades (Product, Sale, StockMovement...) com as regras de negócio
  repository/  Spring Data JPA (inclui consultas com lock FOR UPDATE)
  service/     casos de uso (SaleService, StockService, ReportService...)
  security/    filtro e serviço de JWT
  web/         controllers REST, DTOs e tratamento de erros
src/main/resources/db/migration/   migrações Flyway
src/test/java/                     testes unitários e de integração
Próximos passos
Interface em React + TypeScript (segunda etapa).



# StockFlow API 🚀

> A robust, enterprise-grade RESTful API for inventory management, stock movement tracking, and sales control, engineered with modern Java and Spring Boot best practices.

---

## 📌 About the Project

**StockFlow** is designed to solve critical challenges in commercial management: preventing stock discrepancies, handling high-concurrency sales safely, ensuring idempotency, and providing precise real-time revenue reporting. 

Built with production-readiness in mind, this project demonstrates solid backend fundamentals, secure authentication pipelines, robust relational database modeling, and automated testing strategies.

---

## 🛠️ Tech Stack & Architecture

* **Core:** Java 17+, Spring Boot
* **Security:** Spring Security, JWT (JSON Web Token) Stateless Authentication & Role-Based Access Control (RBAC)
* **Persistence & Migrations:** Spring Data JPA, PostgreSQL, Flyway Database Migrations
* **Resilience & Business Logic:** Concurrency control, Idempotency keys, custom business exceptions
* **Containerization & Tooling:** Docker, Docker Compose, Maven
* **Testing:** JUnit 5, Mockito, Spring Boot Test (Integration Tests, Concurrency & Idempotency ITs)
* **Documentation:** Springdoc OpenAPI (Swagger UI)

---

## ✨ Key Features & Engineering Highlights

1. **Secure Authentication & Authorization:** Stateless JWT filters with role-based routing (`ADMIN`, `USER`) and secure password hashing.
2. **Concurrency & Idempotency Safeguards:** Implemented integration tests (`SaleConcurrencyIT`, `SaleIdempotencyIT`) to prevent race conditions during heavy inventory checkout and avoid duplicate transaction processing.
3. **Automated Database Versioning:** Schema lifecycle managed cleanly via Flyway migrations.
4. **Comprehensive Reporting:** Endpoints for stock valuation, top-selling products, and daily revenue metrics.
5. **Robust Error Handling:** Global exception handling returning standardized, descriptive error responses for business rule violations and insufficient stock scenarios.

---

## 🚀 Getting Started (Quickstart with Docker)

### Prerequisites
* [Docker](https://www.docker.com/) & Docker Compose
* Java 17+ (if running locally without containers)
* Maven

### Running the Application

1. Clone the repository:
   ```bash
   git clone [https://github.com/HernandesWolf/StockFlow.git](https://github.com/HernandesWolf/StockFlow.git)
   cd StockFlow
