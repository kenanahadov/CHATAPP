package com.chat.network;

import com.chat.common.*;
import com.chat.network.fragment.Fragmenter;
import com.chat.network.fragment.Reassembler;
import com.chat.gateway.GatewayManager;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Arrays;
import java.util.concurrent.*;
import com.chat.security.KeyManager;
import com.chat.security.CryptoUtils;
import java.nio.ByteBuffer;


public class NetworkManager {

    private final Deduper        deduper     = new Deduper();
    private final PacketListener listener    = new PacketListener(this);
    private final Reassembler    reassembler = new Reassembler();
    private final Fragmenter     fragmenter  = new Fragmenter();
    private       Thread         listenerThr;
    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor();

    private GatewayManager gwMgr;

    private final ConcurrentMap<String, PublicKey> peers = new ConcurrentHashMap<>();
    private volatile String myNick = "";

    public void setGatewayManager(GatewayManager mgr) { this.gwMgr = mgr; }


    public void start() {
        listenerThr = new Thread(listener, "PacketListener");
        listenerThr.start();
        exec.scheduleAtFixedRate(deduper::prune,      30, 30, TimeUnit.SECONDS);
        exec.scheduleAtFixedRate(reassembler::prune,   5,  5, TimeUnit.SECONDS);
    }

    public void stop() {
        listener.stop();
        if (listenerThr != null) listenerThr.interrupt();
        exec.shutdownNow();
        peers.clear();
    }

    public void setNickname(String nick) { myNick = nick == null ? "" : nick; }

    public PublicKey getPeerKey(String nick) {
        return peers.get(nick);
    }

    public java.util.Set<String> getPeerNames() {
        return java.util.Set.copyOf(peers.keySet());
    }


    public void sendFrame(Frame f, byte[] body) throws Exception {
        fragmenter.sendAll(f, body, this);
    }

    public void sendRawBytes(byte[] data) throws Exception {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            DatagramPacket pkt = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName(ConfigLoader.get("chat.subnet")),
                    ConfigLoader.getInt("chat.port"));
            sock.send(pkt);
        }
    }


    public void processIncoming(byte[] data, int len, boolean fromGateway) {
        byte[] assembled = reassembler.process(Arrays.copyOf(data, len));
        if (assembled == null) return;

        Frame frame = Frame.fromBytes(Arrays.copyOf(assembled, Frame.HEADER_SIZE));
        if (!deduper.isNew(frame) || !frame.decrementTtl()) return;

        byte[] hdr = frame.toBytes();
        System.arraycopy(hdr, 0, assembled, 0, Frame.HEADER_SIZE);

        try {
            if (fromGateway) {
                sendRawBytes(assembled);
            } else if (gwMgr != null) {
                gwMgr.broadcastLocal(assembled);
            }
        } catch (Exception ignored) { }

        byte[] body = Arrays.copyOfRange(assembled, Frame.HEADER_SIZE, assembled.length);
        handleFrame(frame, body);
    }

    public void processIncoming(byte[] data, int len) { processIncoming(data, len, false); }

    private void handleFrame(Frame frame, byte[] body) {
        switch (frame.getType()) {
            case HELLO -> {
                int sep = -1;
                for (int i = 0; i < body.length; i++)
                    if (body[i] == 0) { sep = i; break; }

                String nick;
                PublicKey key = null;
                if (sep < 0) {
                    nick = new String(body, StandardCharsets.UTF_8);
                } else {
                    nick = new String(body, 0, sep, StandardCharsets.UTF_8);
                    try {
                        String b64 = new String(body, sep + 1,
                                body.length - sep - 1,
                                StandardCharsets.UTF_8);
                        byte[] kb = Base64.getDecoder().decode(b64);
                        key = KeyFactory.getInstance("RSA")
                                .generatePublic(new X509EncodedKeySpec(kb));
                    } catch (Exception ignored) {}
                }

                boolean isNew = peers.putIfAbsent(nick, key) == null;
                UIEventBus.publish(peers.keySet());
                if (isNew && !myNick.equals(nick) && !myNick.isBlank()) {
                    try {
                        Frame resp = new Frame(FrameType.HELLO,
                                ConfigLoader.getInt("chat.ttl"));
                        PublicKey myKey = KeyManager.loadPublicKey();
                        byte[] nk = myNick.getBytes(StandardCharsets.UTF_8);
                        byte[] pk = Base64.getEncoder().encode(myKey.getEncoded());
                        byte[] payload = new byte[nk.length + 1 + pk.length];
                        System.arraycopy(nk, 0, payload, 0, nk.length);
                        payload[nk.length] = 0;
                        System.arraycopy(pk, 0, payload, nk.length + 1, pk.length);
                        sendFrame(resp, payload);
                    } catch (Exception ignored) {}
                }
            }

            case BYE -> {
                String nick = new String(body, StandardCharsets.UTF_8);
                peers.remove(nick);
                UIEventBus.publish(peers.keySet());
            }

            case MESSAGE, PRIVATE -> {
                int sep = -1;
                for (int i = 0; i < body.length; i++)
                    if (body[i] == 0) { sep = i; break; }
                if (sep < 1) return;

                String nick = new String(body, 0, sep, StandardCharsets.UTF_8);
                PublicKey pk = peers.get(nick);

                ByteBuffer buf = ByteBuffer.wrap(body, sep + 1,
                        body.length - sep - 1);
                if (buf.remaining() < 2) return;
                int slen = buf.getShort() & 0xffff;
                if (buf.remaining() < slen) return;
                byte[] sig = new byte[slen];
                buf.get(sig);

                byte[] encBytes = new byte[buf.remaining()];
                buf.get(encBytes);

                byte[] msgBytes;
                try {
                    msgBytes = CryptoUtils.decrypt(encBytes, KeyManager.loadPrivateKey());
                } catch (Exception ignored) { return; }

                if (pk != null) {
                    try {
                        if (!CryptoUtils.verify(msgBytes, sig, pk)) return;
                    } catch (Exception ignored) { return; }
                }

                String msg = new String(msgBytes, StandardCharsets.UTF_8);
                if (nick.equals(myNick)) return;

                if (frame.getType() == FrameType.PRIVATE)
                    UIEventBus.publish(new Object[] { nick, msg, Boolean.TRUE });
                else
                    UIEventBus.publish(new Object[] { nick, msg });
            }

            default -> { /* ignore */ }
        }
    }
}
