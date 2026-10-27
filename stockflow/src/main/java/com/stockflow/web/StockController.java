package com.stockflow.web;

import com.stockflow.domain.MovementType;
import com.stockflow.service.StockService;
import com.stockflow.web.dto.PageResponse;
import com.stockflow.web.dto.StockAdjustmentRequest;
import com.stockflow.web.dto.StockEntryRequest;
import com.stockflow.web.dto.StockExitRequest;
import com.stockflow.web.dto.StockMovementResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse entry(@Valid @RequestBody StockEntryRequest request, Authentication authentication) {
        return stockService.registerEntry(request, authentication.getName());
    }

    @PostMapping("/exits")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse exit(@Valid @RequestBody StockExitRequest request, Authentication authentication) {
        return stockService.registerExit(request, authentication.getName());
    }

    /** Somente ADMIN (regra no SecurityConfig). */
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse adjust(@Valid @RequestBody StockAdjustmentRequest request,
                                        Authentication authentication) {
        return stockService.adjust(request, authentication.getName());
    }

    /** Histórico de movimentações. Exemplo: GET /api/stock/movements?productId=1&type=SALE */
    @GetMapping("/movements")
    public PageResponse<StockMovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return stockService.list(productId, type, pageable);
    }
}
