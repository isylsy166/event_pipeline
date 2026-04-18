package generator;

import model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EventGenerator {

    private static final Random RANDOM = new Random();

    private static final List<String> USER_IDS = List.of(
            "user_001", "user_002", "user_003", "user_004", "user_005",
            "user_006", "user_007", "user_008", "user_009", "user_010"
    );

    private static final List<String> PAGE_URLS = List.of(
            "/home", "/products", "/products/123", "/cart", "/checkout",
            "/mypage", "/search", "/about", "/contact", "/blog"
    );

    private static final List<String> REFERRERS = Arrays.asList(
            "https://google.com", "https://naver.com", "https://instagram.com",
            "https://facebook.com", null, null, null
    );

    private static final List<String> PRODUCT_IDS = List.of(
            "prod_A1", "prod_B2", "prod_C3", "prod_D4", "prod_E5"
    );

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
    );

    private static final List<String> ERROR_CODES = List.of(
            "ERR_500", "ERR_404", "ERR_403", "ERR_TIMEOUT", "ERR_DB"
    );

    private static final List<String> ERROR_MESSAGES = List.of(
            "Internal server error",
            "Page not found",
            "Access denied",
            "Request timeout",
            "Database connection failed"
    );

    // PAGE_VIEW 70%, PURCHASE 20%, ERROR 10%
    private static final List<EventType> EVENT_TYPE_POOL = List.of(
            EventType.PAGE_VIEW, EventType.PAGE_VIEW, EventType.PAGE_VIEW,
            EventType.PAGE_VIEW, EventType.PAGE_VIEW, EventType.PAGE_VIEW,
            EventType.PAGE_VIEW,
            EventType.PURCHASE, EventType.PURCHASE,
            EventType.ERROR
    );

    public Event generate() {
        EventType type = pick(EVENT_TYPE_POOL);
        String userId = pick(USER_IDS);
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        OffsetDateTime timestamp = OffsetDateTime.now();
        String ipAddress = randomIp();
        String userAgent = pick(USER_AGENTS);

        return switch (type) {
            case PAGE_VIEW -> new PageViewEvent(
                    userId, sessionId, timestamp, ipAddress, userAgent,
                    pick(PAGE_URLS), pick(REFERRERS)
            );
            case PURCHASE -> new PurchaseEvent(
                    userId, sessionId, timestamp, ipAddress, userAgent,
                    pick(PRODUCT_IDS), randomAmount()
            );
            case ERROR -> new ErrorEvent(
                    userId, sessionId, timestamp, ipAddress, userAgent,
                    pick(ERROR_CODES), pick(ERROR_MESSAGES)
            );
        };
    }

    private static <T> T pick(List<T> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }

    private static String randomIp() {
        return RANDOM.nextInt(256) + "." + RANDOM.nextInt(256) + "."
                + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256);
    }

    private static BigDecimal randomAmount() {
        double amount = 1000 + RANDOM.nextDouble() * 99000;
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
}

