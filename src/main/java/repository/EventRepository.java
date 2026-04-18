package repository;

import config.DatabaseConfig;
import model.ErrorEvent;
import model.Event;
import model.PageViewEvent;
import model.PurchaseEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EventRepository {

    private static final String INSERT_EVENT = """
            INSERT INTO events (event_type, user_id, session_id, timestamp, ip_address, user_agent)
            VALUES (?::varchar, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_PAGE_VIEW = """
            INSERT INTO page_view_events (event_id, page_url, referrer)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_PURCHASE = """
            INSERT INTO purchase_events (event_id, product_id, amount)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_ERROR = """
            INSERT INTO error_events (event_id, error_code, error_message)
            VALUES (?, ?, ?)
            """;

    public void save(Event event) {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            try {
                long eventId = insertBaseEvent(conn, event);
                event.setId(eventId);
                insertSubEvent(conn, event);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save event: " + event.getEventType(), e);
        }
    }

    private long insertBaseEvent(Connection conn, Event event) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_EVENT)) {
            ps.setString(1, event.getEventType().name());
            ps.setString(2, event.getUserId());
            ps.setString(3, event.getSessionId());
            ps.setObject(4, event.getTimestamp());
            ps.setString(5, event.getIpAddress());
            ps.setString(6, event.getUserAgent());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    private void insertSubEvent(Connection conn, Event event) throws SQLException {
        switch (event.getEventType()) {
            case PAGE_VIEW -> insertPageView(conn, (PageViewEvent) event);
            case PURCHASE  -> insertPurchase(conn, (PurchaseEvent) event);
            case ERROR     -> insertError(conn, (ErrorEvent) event);
        }
    }

    private void insertPageView(Connection conn, PageViewEvent event) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_PAGE_VIEW)) {
            ps.setLong(1, event.getId());
            ps.setString(2, event.getPageUrl());
            ps.setString(3, event.getReferrer());
            ps.executeUpdate();
        }
    }

    private void insertPurchase(Connection conn, PurchaseEvent event) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_PURCHASE)) {
            ps.setLong(1, event.getId());
            ps.setString(2, event.getProductId());
            ps.setBigDecimal(3, event.getAmount());
            ps.executeUpdate();
        }
    }

    private void insertError(Connection conn, ErrorEvent event) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_ERROR)) {
            ps.setLong(1, event.getId());
            ps.setString(2, event.getErrorCode());
            ps.setString(3, event.getErrorMessage());
            ps.executeUpdate();
        }
    }
}

