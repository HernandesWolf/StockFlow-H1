package com.stockflow;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa a API HTTP de ponta a ponta: autenticação, permissões, validação e o fluxo de vendas. */
class SalesApiIT extends AbstractIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "admin12345"; // padrão do application.yml

    @Autowired
    private MockMvc mvc;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createAndGetId(String path, String token, String json) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createProduct(String token, int initialStock) throws Exception {
        String sku = "API-" + UUID.randomUUID().toString().substring(0, 8);
        return createAndGetId("/api/products", token, "{\"sku\":\"" + sku + "\",\"name\":\"Caderno\","
                + "\"costPrice\":2.50,\"salePrice\":5.00,\"minStock\":2,\"initialStock\":" + initialStock + "}");
    }

    private static String saleBody(long productId, int quantity) {
        return "{\"items\":[{\"productId\":" + productId + ",\"quantity\":" + quantity + "}]}";
    }

    // ------------------------------------------------------------------
    // Autenticação e permissões
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sem token, a API responde 401")
    void withoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token inválido responde 401")
    void invalidTokenReturns401() throws Exception {
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer isso.nao.e.um.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Senha errada no login responde 401")
    void wrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"senha-errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("O operador consulta e vende, mas não cadastra produtos, não cancela vendas e não vê relatórios")
    void operatorHasLimitedPermissions() throws Exception {
        String admin = login(ADMIN_USER, ADMIN_PASSWORD);
        String operatorName = "op-" + UUID.randomUUID().toString().substring(0, 8);
        createAndGetId("/api/users", admin,
                "{\"username\":\"" + operatorName + "\",\"password\":\"senha-segura-1\",\"role\":\"OPERATOR\"}");
        String operator = login(operatorName, "senha-segura-1");
        long productId = createProduct(admin, 10);

        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NOPE\",\"name\":\"X\",\"costPrice\":1,\"salePrice\":2}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/revenue").header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden());

        MvcResult sale = mvc.perform(post("/api/sales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(operatorName))
                .andReturn();
        Number saleId = JsonPath.read(sale.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/sales/" + saleId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Tentativa sem permissão\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dados inválidos respondem 400 com a lista de campos com problema")
    void invalidDataReturns400WithViolations() throws Exception {
        String admin = login(ADMIN_USER, ADMIN_PASSWORD);

        mvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"name\":\"Caderno\",\"costPrice\":-1,\"salePrice\":5.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    // ------------------------------------------------------------------
    // Fluxo completo de venda
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fluxo completo: entrada de estoque, venda idempotente, estoque insuficiente e cancelamento")
    void fullSalesFlow() throws Exception {
        String admin = login(ADMIN_USER, ADMIN_PASSWORD);
        long productId = createProduct(admin, 10);

        // Entrada de estoque: 10 + 5 = 15
        mvc.perform(post("/api/stock/entries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":5,\"reason\":\"Compra\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(15));

        // Venda de 3 unidades (3 x 5,00 = 15,00)
        String key = UUID.randomUUID().toString();
        MvcResult first = mvc.perform(post("/api/sales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.total").value(15.0))
                .andReturn();
        Number saleId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        // Repetição da MESMA requisição: 200 + Idempotent-Replay, mesma venda, estoque baixa uma vez só
        mvc.perform(post("/api/sales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 3)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(saleId.intValue()));

        mvc.perform(get("/api/products/" + productId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(12));

        // Mesma chave, conteúdo diferente: 422
        mvc.perform(post("/api/sales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 4)))
                .andExpect(status().isUnprocessableEntity());

        // Sem o header Idempotency-Key: 400
        mvc.perform(post("/api/sales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 1)))
                .andExpect(status().isBadRequest());

        // Estoque insuficiente: 409 com os detalhes
        mvc.perform(post("/api/sales")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(productId, 1000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.available").value(12))
                .andExpect(jsonPath("$.requested").value(1000));

        // Cancelamento (ADMIN): devolve o estoque
        mvc.perform(post("/api/sales/" + saleId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/products/" + productId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.stockQuantity").value(15));

        // Cancelar de novo: 409
        mvc.perform(post("/api/sales/" + saleId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Outra vez\"}"))
                .andExpect(status().isConflict());

        // Histórico de movimentações do produto: IN (inicial), IN (compra), SALE, SALE_CANCEL
        mvc.perform(get("/api/stock/movements?productId=" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @DisplayName("Histórico de preços registra a criação e cada alteração")
    void priceHistoryIsRecorded() throws Exception {
        String admin = login(ADMIN_USER, ADMIN_PASSWORD);
        long productId = createProduct(admin, 0);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Caderno\",\"costPrice\":2.50,\"salePrice\":6.00,\"minStock\":2,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salePrice").value(6.0));

        mvc.perform(get("/api/products/" + productId + "/price-history")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newSalePrice").value(6.0))
                .andExpect(jsonPath("$[0].oldSalePrice").value(5.0));
    }
}
