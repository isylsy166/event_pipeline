package model;

import java.time.OffsetDateTime;

public class PageViewEvent extends Event {

    private String pageUrl;
    private String referrer;

    public PageViewEvent(String userId, String sessionId, OffsetDateTime timestamp,
                         String ipAddress, String userAgent, String pageUrl, String referrer) {
        super(EventType.PAGE_VIEW, userId, sessionId, timestamp, ipAddress, userAgent);
        this.pageUrl = pageUrl;
        this.referrer = referrer;
    }

    public String getPageUrl() { return pageUrl; }
    public String getReferrer() { return referrer; }
}