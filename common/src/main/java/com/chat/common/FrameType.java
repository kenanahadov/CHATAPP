
package com.chat.common;

public enum FrameType {
    HELLO((byte)1),
    MESSAGE((byte)2),
    BYE((byte)3),
    PART((byte)4);

    private final byte code;
    FrameType(byte code) { this.code = code; }
    public byte getCode() { return code; }

    public static FrameType fromCode(byte code) {
        for (FrameType ft : values()) {
            if (ft.code == code) return ft;
        }
        throw new IllegalArgumentException("Unknown frame type: " + code);
    }
}
