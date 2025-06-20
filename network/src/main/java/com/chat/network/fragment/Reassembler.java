
package com.chat.network.fragment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.common.ConfigLoader;

public class Reassembler {
    private final Map<UUID, ReassemblyBuffer> map = new ConcurrentHashMap<>();
    private final int timeout = ConfigLoader.getInt("fragment.timeout_ms");

    public byte[] process(byte[] raw) {
        if (raw.length < FragmentHeader.HEADER_SIZE) return null;
        byte[] hdrBytes = Arrays.copyOf(raw, FragmentHeader.HEADER_SIZE);
        FragmentHeader fh = FragmentHeader.fromBytes(hdrBytes);
        byte[] slice = Arrays.copyOfRange(raw, FragmentHeader.HEADER_SIZE, raw.length);
        UUID id = fh.getFrameId();
        map.computeIfAbsent(id, k -> new ReassemblyBuffer(fh.getTotal(), timeout))
           .add(fh.getIndex(), slice);

        ReassemblyBuffer buf = map.get(id);
        if (buf.isComplete()) {
            map.remove(id);
            return buf.assemble();
        }
        return null;
    }

    public void prune() {
        map.entrySet().removeIf(e -> e.getValue().expired());
    }
}
