package model;

import java.time.OffsetDateTime;

public class ErrorEvent extends Event {

    private String errorCode;
    private String errorMessage;

    public ErrorEvent(String userId, String sessionId, OffsetDateTime timestamp,
                      String ipAddress, String userAgent, String errorCode, String errorMessage) {
        super(EventType.ERROR, userId, sessionId, timestamp, ipAddress, userAgent);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
