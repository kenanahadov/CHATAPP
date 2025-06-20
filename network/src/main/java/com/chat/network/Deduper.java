
package com.chat.network;

import com.chat.common.Frame;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Deduper {
    private final ConcurrentMap<UUID, Instant> seen = new ConcurrentHashMap<>();
    private final Duration expiry = Duration.ofSeconds(60);

    public boolean isNew(Frame f) {
        Instant now = Instant.now();
        Instant prev = seen.putIfAbsent(f.getId(), now);
        return prev == null;
    }

    public void prune() {
        Instant cutoff = Instant.now().minus(expiry);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
