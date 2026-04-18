-- 1. 이벤트 타입별 발생 횟수
SELECT event_type,
       COUNT(*) AS event_count
FROM events
GROUP BY event_type
ORDER BY event_count DESC;


-- 2. 유저별 총 이벤트 수
SELECT user_id,
       COUNT(*) AS event_count
FROM events
GROUP BY user_id
ORDER BY event_count DESC;


-- 3. 시간대별 이벤트 추이
SELECT DATE_TRUNC('hour', timestamp) AS hour,
       COUNT(*) AS event_count
FROM events
GROUP BY hour
ORDER BY hour;


-- 4. 에러 이벤트 비율
SELECT ROUND(
    COUNT(*) FILTER (WHERE event_type = 'ERROR') * 100.0 / COUNT(*), 2
) AS error_rate_pct
FROM events;


-- 5. 상품별 총 매출
SELECT p.product_id,
       COUNT(*)            AS purchase_count,
       SUM(p.amount)       AS total_revenue,
       AVG(p.amount)       AS avg_amount
FROM purchase_events p
GROUP BY p.product_id
ORDER BY total_revenue DESC;