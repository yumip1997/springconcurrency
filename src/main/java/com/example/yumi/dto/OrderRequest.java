package com.example.yumi.dto;

import com.example.yumi.entity.Order;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotNull(message = "회원번호는 필수입니다")
    private Long memberNo;

    @NotNull(message = "상품번호는 필수입니다")
    private Long productNo;

    @NotNull(message = "주문수량은 필수입니다")
    @Min(value = 1, message = "주문수량은 1 이상이어야 합니다")
    private Integer orderQuantity;

    public Order toOrder() {
        return new Order(memberNo, productNo, orderQuantity);
    }

    public InventoryReduceRequest toInventoryReduceRequest() {
        return new InventoryReduceRequest(productNo, orderQuantity);
    }
}

