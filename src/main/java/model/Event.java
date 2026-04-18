package model;

import java.time.OffsetDateTime;

public abstract class Event {

    private Long id;
    private EventType eventType;
    private String userId;
    private String sessionId;
    private OffsetDateTime timestamp;
    private String ipAddress;
    private String userAgent;

    public Event(EventType eventType, String userId, String sessionId,
                 OffsetDateTime timestamp, String ipAddress, String userAgent) {
        this.eventType = eventType;
        this.userId = userId;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public EventType getEventType() { return eventType; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
}

