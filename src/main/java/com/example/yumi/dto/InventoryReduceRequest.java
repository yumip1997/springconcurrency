package com.example.yumi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReduceRequest {

    @NotNull(message = "상품번호는 필수입니다")
    private Long productNo;

    @NotNull(message = "차감수량은 필수입니다")
    @Min(value = 1, message = "차감수량은 1 이상이어야 합니다")
    private Integer quantity;
}

