package com.chat.gateway;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GatewayServer implements Runnable {
    private static final Logger log = Logger.getLogger(GatewayServer.class.getName());

    private final int port;
    private final GatewayManager mgr;
    private volatile boolean running = true;

    public GatewayServer(int port, GatewayManager mgr) {
        this.port = port;
        this.mgr  = mgr;
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            log.info("Gateway server listening on port " + port);
            while (running) {
                Socket sock = server.accept();
                GatewayConnection conn = new GatewayConnection(sock, mgr);
                mgr.addConnection(conn.addr(), conn);
                new Thread(conn, "GW-IN-" + conn.addr()).start();
            }
        } catch (IOException e) {
            if (running)
                log.log(Level.SEVERE, "GatewayServer error: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        // connect to ourselves to unblock accept()
        try (Socket s = new Socket("localhost", port)) {
        } catch (IOException ignored) {}
    }
}
