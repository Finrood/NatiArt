package com.portcelana.natiart.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "shipping_rate_limit_window")
public class RateLimitWindow {
    @Id
    @Column(length = 128)
    private String clientKey;

    @Column(nullable = false)
    private long windowStart;

    @Column(nullable = false)
    private int requestCount;

    @Version
    private long version;

    protected RateLimitWindow() {}

    public RateLimitWindow(String clientKey, long windowStart, int requestCount) {
        this.clientKey = clientKey;
        this.windowStart = windowStart;
        this.requestCount = requestCount;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public RateLimitWindow reset(long newWindowStart) {
        this.windowStart = newWindowStart;
        this.requestCount = 1;
        return this;
    }

    public RateLimitWindow increment() {
        this.requestCount++;
        return this;
    }
}
