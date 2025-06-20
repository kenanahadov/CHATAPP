package com.chat.network;

import com.chat.common.ConfigLoader;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * Listens for incoming UDP packets and hands them to NetworkManager.
 * Uses SO_REUSEADDR so multiple instances can bind to the same port.
 */
class PacketListener implements Runnable {

    private final NetworkManager mgr;
    private volatile boolean     running = true;
    private DatagramSocket       sock;

    PacketListener(NetworkManager m) { this.mgr = m; }

    void stop() { running = false; if (sock != null) sock.close(); }

    @Override public void run() {
        int port = ConfigLoader.getInt("chat.port");
        byte[] buf = new byte[2048];

        try {
            /* ----- open socket with SO_REUSEADDR ----- */
            sock = new DatagramSocket(null);
            sock.setReuseAddress(true);
            sock.setBroadcast(true);
            sock.bind(new InetSocketAddress(port));

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                sock.receive(pkt);
                mgr.processIncoming(pkt.getData(), pkt.getLength());
            }

        } catch (SocketException se) {
            if (running) se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (sock != null) sock.close();
        }
    }
}
