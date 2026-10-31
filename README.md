# StockFlow

**API REST para gestão de vendas e estoque**, desenvolvida com Java 21 e Spring Boot. O projeto prioriza integridade transacional: impedir estoque negativo, evitar vendas duplicadas e manter histórico auditável das operações.

## Tecnologias

- Java 21 e Spring Boot
- Spring Web, Spring Data JPA e Spring Security
- PostgreSQL e Flyway
- JWT para autenticação e autorização por perfil
- Docker e Docker Compose
- JUnit 5, Mockito e Testcontainers
- Springdoc OpenAPI (Swagger UI)

## Funcionalidades

- Cadastro e consulta de produtos, categorias e fornecedores
- Controle de entradas, saídas e ajustes de estoque
- Registro, consulta e cancelamento de vendas
- Histórico de movimentações e de preços
- Relatórios de faturamento, valorização de estoque, margem e reposição
- Autorização por perfis ADMIN e OPERATOR

## Decisões técnicas em destaque

### Concorrência no estoque

As operações de venda bloqueiam os registros de produto com `SELECT ... FOR UPDATE`, em ordem de identificador. Isso serializa alterações concorrentes sobre os mesmos itens. Uma restrição `CHECK` no banco também impede saldo negativo.

### Idempotência de vendas

A criação de venda exige o cabeçalho `Idempotency-Key`. A chave possui restrição de unicidade para impedir que uma repetição da mesma solicitação gere uma segunda venda.

### Cancelamento e auditoria

O cancelamento reverte o estoque e registra as movimentações na mesma transação. O histórico operacional é preservado; vendas são canceladas, não apagadas.

### Integridade dos valores

Valores monetários usam `BigDecimal` no Java e `NUMERIC(12,2)` no banco. Preço e custo são registrados nos itens da venda para preservar o valor histórico da operação.

## Como executar

Pré-requisitos: Java 21, Maven e Docker com Docker Compose.

### Aplicação e banco em containers

```bash
docker compose up --build
```

### Banco em container e aplicação local

```bash
docker compose up -d db
mvn spring-boot:run
```

A documentação interativa fica disponível em:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

Na primeira execução, o projeto cria um usuário administrativo de demonstração. Consulte `.env.example` e a configuração da aplicação para conhecer e substituir as credenciais locais. **Não reutilize credenciais de demonstração em ambientes expostos.**

## Testes

```bash
mvn test
```

Executa os testes unitários. Para incluir os testes de integração com Testcontainers, com Docker disponível:

```bash
mvn verify
```

Os testes de integração documentados incluem cenários de concorrência, idempotência, cancelamento e restrições do banco.

## Estrutura do projeto

```text
src/main/java/com/stockflow/
├── config/       configuração, JWT e OpenAPI
├── domain/       entidades e regras de domínio
├── repository/   persistência e consultas
├── service/      casos de uso e transações
├── security/     autenticação e autorização
└── web/          controllers, DTOs e tratamento de erros

src/main/resources/db/migration/  migrações Flyway
src/test/java/                    testes unitários e de integração
```

## Próximos passos

- Interface web com React e TypeScript
- Evolução da experiência de operação e dos relatórios

---

Projeto de portfólio desenvolvido para demonstrar decisões de backend relacionadas a consistência de dados, concorrência, segurança e testes.
