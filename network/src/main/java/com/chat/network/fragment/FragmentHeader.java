
package com.chat.network.fragment;

import java.nio.ByteBuffer;
import java.util.UUID;

public class FragmentHeader {
    public static final int HEADER_SIZE = 16 + 2 + 2;
    private final UUID frameId;
    private final short total;
    private final short index;

    public FragmentHeader(UUID id, short total, short index) {
        this.frameId = id;
        this.total = total;
        this.index = index;
    }

    public UUID getFrameId() { return frameId; }
    public short getTotal() { return total; }
    public short getIndex() { return index; }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE);
        buf.putLong(frameId.getMostSignificantBits());
        buf.putLong(frameId.getLeastSignificantBits());
        buf.putShort(total);
        buf.putShort(index);
        return buf.array();
    }

    public static FragmentHeader fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        long msb=buf.getLong(), lsb=buf.getLong();
        short tot=buf.getShort(), idx=buf.getShort();
        return new FragmentHeader(new UUID(msb,lsb), tot, idx);
    }
}
