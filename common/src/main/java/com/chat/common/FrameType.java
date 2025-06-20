
package com.chat.common;

public enum FrameType {
    HELLO((byte)1),
    MESSAGE((byte)2),
    PRIVATE((byte)3),
    BYE((byte)4),
    PART((byte)5);

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
