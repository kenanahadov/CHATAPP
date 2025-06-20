
package com.chat.network.fragment;

import java.nio.ByteBuffer;
import java.time.Instant;

public class ReassemblyBuffer {
    private final byte[][] slices;
    private final Instant start;
    private final long timeoutMs;

    public ReassemblyBuffer(int total, long timeoutMs) {
        this.slices = new byte[total][];
        this.start = Instant.now();
        this.timeoutMs = timeoutMs;
    }

    public synchronized void add(int idx, byte[] payload) {
        slices[idx] = payload;
    }

    public synchronized boolean isComplete() {
        for (byte[] b : slices) if (b == null) return false;
        return true;
    }

    public synchronized byte[] assemble() {
        int len=0;
        for(byte[] b:slices) len+=b.length;
        ByteBuffer buf=ByteBuffer.allocate(len);
        for(byte[] b:slices) buf.put(b);
        return buf.array();
    }

    public boolean expired() {
        return Instant.now().toEpochMilli() - start.toEpochMilli() > timeoutMs;
    }
}
