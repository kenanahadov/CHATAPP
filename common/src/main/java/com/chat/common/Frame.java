
package com.chat.common;

import java.nio.ByteBuffer;
import java.util.UUID;

public class Frame {
    public static final int HEADER_SIZE = 16 + 1 + 4; // UUID + type + ttl
    private final UUID id;
    private final FrameType type;
    private int ttl;

    public Frame(FrameType type, int ttl) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.ttl = ttl;
    }

    public Frame(UUID id, FrameType type, int ttl) {
        this.id = id;
        this.type = type;
        this.ttl = ttl;
    }

    public UUID getId() { return id; }
    public FrameType getType() { return type; }
    public int getTtl() { return ttl; }

    public boolean decrementTtl() {
        if (ttl > 0) ttl--;
        return ttl > 0;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE);
        buf.putLong(id.getMostSignificantBits());
        buf.putLong(id.getLeastSignificantBits());
        buf.put(type.getCode());
        buf.putInt(ttl);
        return buf.array();
    }

    public static Frame fromBytes(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Not enough data for header");
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        long msb = buf.getLong();
        long lsb = buf.getLong();
        byte t = buf.get();
        int ttl = buf.getInt();
        return new Frame(new UUID(msb, lsb), FrameType.fromCode(t), ttl);
    }
}
