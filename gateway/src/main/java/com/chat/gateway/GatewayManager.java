package com.chat.gateway;

import com.chat.common.ConfigLoader;
import com.chat.common.Frame;
import com.chat.network.Deduper;
import com.chat.network.NetworkManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GatewayManager {
    private final RoutingTable routingTable = new RoutingTable();
    private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(4);
    private final int port = ConfigLoader.getInt("gateway.port");
    private final Deduper deduper = new Deduper();
    private GatewayServer server;
    private NetworkManager netMgr;

    public void setNetworkManager(NetworkManager nm){ this.netMgr = nm; }

    public void start() throws IOException {
        // 1) inbound server
        server = new GatewayServer(port, this);
        exec.submit(server);

        // 2) load neighbor list from classpath resource
        String listRes = ConfigLoader.get("gateway.list"); // "gateways.txt"
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream(listRes)) {

            if (in == null) {
                throw new IOException("Could not find resource: " + listRes);
            }
            List<String> lines = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();

            for (String line : lines) {
                String[] parts = line.split(":");
                InetSocketAddress addr = new InetSocketAddress(
                    parts[0], Integer.parseInt(parts[1]));
                dial(addr);
            }
        }

        // 3) schedule deduper pruning
        exec.scheduleAtFixedRate(deduper::prune, 30, 30, TimeUnit.SECONDS);
    }

    
    public void stop() {
        // stop accepting new connections
        if (server != null) server.stop();
        // cancel scheduled tasks
        exec.shutdownNow();
        // close all existing links
        routingTable.all().forEach(GatewayConnection::close);
        routingTable.all().clear();
    }

  
    private void dial(InetSocketAddress addr) {
        exec.submit(() -> {
            try {
                Socket sock = new Socket(addr.getHostString(), addr.getPort());
                GatewayConnection conn = new GatewayConnection(sock, this);
                addConnection(addr, conn);
                new Thread(conn, "GW-OUT-" + addr).start();
            } catch (IOException ignored) {}
        });
    }

    public void addConnection(InetSocketAddress addr, GatewayConnection conn) {
        routingTable.add(addr, conn);
    }

    public void removeConnection(InetSocketAddress addr) {
        routingTable.remove(addr);
    }


    public void onGatewayFrame(byte[] raw, GatewayConnection src) {
        if (deduper.isNew(Frame.fromBytes(Arrays.copyOf(raw, Frame.HEADER_SIZE)))) {
            FrameForwarder.forward(raw, routingTable.all(), src);
            if (netMgr != null) netMgr.handleFromGateway(raw, raw.length);
        }
    }

    public void broadcastLocal(byte[] raw){
        if (deduper.isNew(Frame.fromBytes(Arrays.copyOf(raw, Frame.HEADER_SIZE))))
            FrameForwarder.forward(raw, routingTable.all(), null);
    }
}
