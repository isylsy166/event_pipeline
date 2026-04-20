package generator;

import model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EventGenerator {

    private static final Random RANDOM = new Random();
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(9);
    private static final int LOOKBACK_DAYS = 7;

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
        OffsetDateTime timestamp = randomTimestamp(type);
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

    private static OffsetDateTime randomTimestamp(EventType type) {
        OffsetDateTime now = OffsetDateTime.now(ZONE_OFFSET);
        int daysAgo = weightedDaysAgo();
        OffsetDateTime baseDate = now.minusDays(daysAgo);

        int hour = weightedHour(type, baseDate.getDayOfWeek());
        int minute = RANDOM.nextInt(60);
        int second = RANDOM.nextInt(60);

        OffsetDateTime candidate = baseDate
                .withHour(hour)
                .withMinute(minute)
                .withSecond(second)
                .withNano(0);

        if (candidate.isAfter(now)) {
            return now.minusMinutes(RANDOM.nextInt(30)).withNano(0);
        }
        return candidate;
    }

    private static int weightedDaysAgo() {
        int roll = RANDOM.nextInt(100);
        if (roll < 35) {
            return 0;
        }
        if (roll < 60) {
            return 1;
        }
        if (roll < 75) {
            return 2;
        }
        if (roll < 86) {
            return 3;
        }
        if (roll < 93) {
            return 4;
        }
        if (roll < 97) {
            return 5;
        }
        return RANDOM.nextInt(LOOKBACK_DAYS - 5) + 5;
    }

    private static int weightedHour(EventType type, DayOfWeek dayOfWeek) {
        int roll = RANDOM.nextInt(100);
        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

        return switch (type) {
            case PAGE_VIEW -> {
                if (weekend) {
                    yield pickHour(roll, 10, 13, 15, 18, 20, 23);
                }
                yield pickHour(roll, 8, 11, 12, 17, 19, 22);
            }
            case PURCHASE -> {
                if (weekend) {
                    yield pickHour(roll, 11, 14, 15, 18, 19, 22);
                }
                yield pickHour(roll, 10, 12, 13, 18, 19, 22);
            }
            case ERROR -> {
                if (weekend) {
                    yield pickHour(roll, 9, 12, 13, 17, 18, 23);
                }
                yield pickHour(roll, 8, 10, 11, 17, 18, 23);
            }
        };
    }

    private static int pickHour(int roll, int low1, int high1, int low2, int high2, int low3, int high3) {
        if (roll < 15) {
            return randomBetween(0, 6);
        }
        if (roll < 45) {
            return randomBetween(low1, high1);
        }
        if (roll < 80) {
            return randomBetween(low2, high2);
        }
        return randomBetween(low3, high3);
    }

    private static int randomBetween(int startInclusive, int endInclusive) {
        return startInclusive + RANDOM.nextInt(endInclusive - startInclusive + 1);
    }
}
