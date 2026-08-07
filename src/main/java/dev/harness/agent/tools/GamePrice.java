package dev.harness.agent.tools;

import java.math.BigDecimal;

public record GamePrice(
        String title,
        BigDecimal price,
        String currency
) {
}
