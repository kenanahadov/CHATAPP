
package com.chat.gateway;

import java.util.Collection;

public class FrameForwarder {
    public static void forward(byte[] raw, Collection<GatewayConnection> conns, GatewayConnection except){
        for(GatewayConnection c:conns) if(c!=except) c.send(raw);
    }
}
