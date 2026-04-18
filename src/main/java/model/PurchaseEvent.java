package model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class PurchaseEvent extends Event {

    private String productId;
    private BigDecimal amount;

    public PurchaseEvent(String userId, String sessionId, OffsetDateTime timestamp,
                         String ipAddress, String userAgent, String productId, BigDecimal amount) {
        super(EventType.PURCHASE, userId, sessionId, timestamp, ipAddress, userAgent);
        this.productId = productId;
        this.amount = amount;
    }

    public String getProductId() { return productId; }
    public BigDecimal getAmount() { return amount; }
}