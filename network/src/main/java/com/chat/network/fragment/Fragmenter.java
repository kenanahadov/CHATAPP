
package com.chat.network.fragment;

import com.chat.common.*;
import com.chat.network.NetworkManager;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

public class Fragmenter {
    private final int maxPayload = ConfigLoader.getInt("fragment.max_payload");

    public List<byte[]> fragment(Frame frame, byte[] body) {
        byte[] header = frame.toBytes();
        byte[] full = ByteBuffer.allocate(header.length + body.length)
                                .put(header).put(body).array();
        int totalLen = full.length;
        int sliceCount = (int)Math.ceil((double)totalLen / maxPayload);
        List<byte[]> out = new ArrayList<>(sliceCount);
        UUID fid = frame.getId();
        short total = (short)sliceCount;
        int offset=0;
        for (int i=0;i<sliceCount;i++) {
            int len = Math.min(maxPayload, totalLen-offset);
            byte[] slice = Arrays.copyOfRange(full, offset, offset+len);
            FragmentHeader fh = new FragmentHeader(fid,total,(short)i);
            byte[] pkt = ByteBuffer.allocate(FragmentHeader.HEADER_SIZE + slice.length)
                                   .put(fh.toBytes()).put(slice).array();
            out.add(pkt);
            offset += len;
        }
        return out;
    }

    public void sendAll(Frame frame, byte[] body, NetworkManager nm) throws Exception {
        for (byte[] p : fragment(frame, body)) nm.sendRawBytes(p);
    }
}
