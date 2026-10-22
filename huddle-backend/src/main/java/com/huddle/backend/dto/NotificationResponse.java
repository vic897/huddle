package com.huddle.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private String id;
    private String type;
    private String title;
    private String message;

    @JsonProperty("isRead")
    private boolean isRead;

    private String targetUrl;
    private LocalDateTime createdAt;

    @JsonProperty("read")
    public boolean getRead() {
        return isRead;
    }

    @JsonProperty("read")
    public void setRead(boolean read) {
        this.isRead = read;
    }
}