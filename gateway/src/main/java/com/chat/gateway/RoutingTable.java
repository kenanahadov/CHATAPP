
package com.chat.gateway;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    private final ConcurrentHashMap<InetSocketAddress, GatewayConnection> map = new ConcurrentHashMap<>();
    public void add(InetSocketAddress addr, GatewayConnection c){ map.put(addr,c);}
    public void remove(InetSocketAddress addr){ map.remove(addr);}
    public Collection<GatewayConnection> all(){ return map.values();}
}
