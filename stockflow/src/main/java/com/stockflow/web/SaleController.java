package com.stockflow.web;

import com.stockflow.domain.SaleStatus;
import com.stockflow.service.SaleResult;
import com.stockflow.service.SaleService;
import com.stockflow.web.dto.CancelSaleRequest;
import com.stockflow.web.dto.CreateSaleRequest;
import com.stockflow.web.dto.PageResponse;
import com.stockflow.web.dto.SaleResponse;
import com.stockflow.web.dto.SaleSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REPLAY_HEADER = "Idempotent-Replay";

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    /**
     * Registra uma venda. O header Idempotency-Key é OBRIGATÓRIO (use um UUID novo por venda).
     *  - 201 Created: venda criada agora.
     *  - 200 OK + header "Idempotent-Replay: true": a chave já tinha sido processada; devolvemos a venda original.
     *  - 409: estoque insuficiente.  422: a chave foi reutilizada com conteúdo diferente.
     */
    @PostMapping
    public ResponseEntity<SaleResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreateSaleRequest request,
            Authentication authentication) {
        SaleResult result = saleService.create(idempotencyKey, request, authentication.getName());
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header(REPLAY_HEADER, "true")
                    .body(result.sale());
        }
        return ResponseEntity
                .created(URI.create("/api/sales/" + result.sale().id()))
                .body(result.sale());
    }

    @GetMapping
    public PageResponse<SaleSummaryResponse> list(
            @RequestParam(required = false) SaleStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return saleService.list(status, pageable);
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return saleService.get(id);
    }

    /** Somente ADMIN (regra no SecurityConfig). Reverte o estoque de forma controlada e auditável. */
    @PostMapping("/{id}/cancel")
    public SaleResponse cancel(@PathVariable Long id,
                               @Valid @RequestBody CancelSaleRequest request,
                               Authentication authentication) {
        return saleService.cancel(id, request.reason(), authentication.getName());
    }
}
