package com.chat.gateway;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class GatewayConnection implements Runnable {
    private static final Logger log = Logger.getLogger(GatewayConnection.class.getName());

    private final Socket sock;
    private final GatewayManager mgr;
    private final DataInputStream in;
    private final DataOutputStream out;

    public GatewayConnection(Socket sock, GatewayManager mgr) throws IOException {
        this.sock = sock;
        this.mgr  = mgr;
        this.in   = new DataInputStream(sock.getInputStream());
        this.out  = new DataOutputStream(sock.getOutputStream());
    }

    // Returns the remote address:port for this socket
    public InetSocketAddress addr() {
        return new InetSocketAddress(sock.getInetAddress(), sock.getPort());
    }

    // Send a full raw frame over this TCP link and conenct us
    public synchronized void send(byte[] data) {
        try {
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to send to " + addr() + ": " + e.getMessage());
            mgr.removeConnection(addr());
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                int len = in.readInt();
                byte[] buf = new byte[len];
                in.readFully(buf);
                mgr.onGatewayFrame(buf, this);
            }
        } catch (IOException e) {
            log.log(Level.INFO, "Connection closed: " + addr());
        } finally {
            mgr.removeConnection(addr());
        }
    }

    // Close the socket to end the loop here
    public void close() {
        try {
            sock.close();
        } catch (IOException ignored) {}
    }
}
