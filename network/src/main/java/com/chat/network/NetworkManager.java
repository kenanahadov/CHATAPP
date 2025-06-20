package com.chat.network;

import com.chat.common.*;
import com.chat.network.fragment.Fragmenter;
import com.chat.network.fragment.Reassembler;
import com.chat.gateway.GatewayManager;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import com.chat.security.CryptoUtils;
import com.chat.security.KeyManager;
import com.chat.security.SecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.*;


public class NetworkManager {

    private final Deduper        deduper     = new Deduper();
    private final PacketListener listener    = new PacketListener(this);
    private final Reassembler    reassembler = new Reassembler();
    private final Fragmenter     fragmenter  = new Fragmenter();
    private       Thread         listenerThr;
    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor();

    private final ConcurrentMap<String, PublicKey> peers = new ConcurrentHashMap<>();
    private volatile GatewayManager gwMgr = null;
    private volatile String myNick = "";


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
    public void setGatewayManager(GatewayManager gw){ this.gwMgr = gw; }
    public ConcurrentMap<String, PublicKey> getPeers(){ return peers; }
    public void addLocalPeer(String nick, PublicKey key){
        peers.put(nick, key);
        UIEventBus.publish(peers.keySet());
    }


    public void sendFrame(Frame f, byte[] body) throws Exception {
        deduper.isNew(f); // record
        byte[] assembled = ByteBuffer.allocate(Frame.HEADER_SIZE + body.length)
                .put(f.toBytes()).put(body).array();
        for (byte[] p : fragmenter.fragment(f, body)) sendRawBytes(p);
        if (gwMgr != null) gwMgr.broadcastLocal(assembled);
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

        if ("true".equalsIgnoreCase(ConfigLoader.get("spoof.enabled"))) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "python3", "spoofer/scripts/spoofer.py",
                        "-i", ConfigLoader.get("spoof.iface"),
                        "-d", ConfigLoader.get("chat.subnet"),
                        "-s", ConfigLoader.get("spoof.src_ip"),
                        "-c", ConfigLoader.get("spoof.src_mac"),
                        "-p", Base64.getEncoder().encodeToString(data));
                pb.inheritIO();
                pb.start();
            } catch (Exception ignored) {}
        }
    }


    public void handleIncoming(byte[] data, int len) {
        processIncoming(data, len, false);
    }

    public void handleFromGateway(byte[] data, int len) {
        processIncoming(data, len, true);
    }

    private void processIncoming(byte[] data, int len, boolean fromGw) {
        byte[] assembled = reassembler.process(Arrays.copyOf(data, len));
        if (assembled == null) return;

        Frame frame = Frame.fromBytes(Arrays.copyOf(assembled, Frame.HEADER_SIZE));
        if (!deduper.isNew(frame) || !frame.decrementTtl()) return;

        if (gwMgr != null) {
            if (fromGw) {
                try { sendRawBytes(assembled); } catch (Exception ignored) {}
            } else {
                gwMgr.broadcastLocal(assembled);
            }
        }

        byte[] body = Arrays.copyOfRange(assembled, Frame.HEADER_SIZE, assembled.length);

        switch (frame.getType()) {
            case HELLO -> {
                int sep = -1;
                for (int i = 0; i < body.length; i++)
                    if (body[i] == 0) { sep = i; break; }
                if (sep < 1) return;

                String nick = new String(body, 0, sep, StandardCharsets.UTF_8);
                byte[] pkBytes = Arrays.copyOfRange(body, sep + 1, body.length);
                PublicKey pub;
                try {
                    pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pkBytes));
                } catch (Exception e) { return; }

                boolean isNew = peers.putIfAbsent(nick, pub) == null;
                UIEventBus.publish(peers.keySet());
                if (isNew && !myNick.equals(nick) && !myNick.isBlank()) {
                    try {
                        byte[] myPub = KeyManager.loadPublicKey().getEncoded();
                        byte[] respBody = ByteBuffer.allocate(myNick.getBytes().length + 1 + myPub.length)
                                .put(myNick.getBytes(StandardCharsets.UTF_8)).put((byte)0).put(myPub).array();
                        Frame resp = new Frame(FrameType.HELLO,
                                               ConfigLoader.getInt("chat.ttl"));
                        sendFrame(resp, respBody);
                    } catch (Exception ignored) {}
                }
            }

            case BYE -> {
                String nick = new String(body, StandardCharsets.UTF_8);
                peers.remove(nick);
                UIEventBus.publish(peers.keySet());
            }

            case MESSAGE -> {
                int sep = -1;
                for (int i = 0; i < body.length; i++)
                    if (body[i] == 0) { sep = i; break; }
                if (sep < 1) return;

                String dest = new String(body, 0, sep, StandardCharsets.UTF_8);
                if (!dest.equals(myNick)) return;

                byte[] enc = Arrays.copyOfRange(body, sep + 1, body.length);
                try {
                    byte[] dec = CryptoUtils.decrypt(enc, KeyManager.loadPrivateKey());
                    int sidx = -1;
                    for (int i = 0; i < dec.length; i++)
                        if (dec[i] == 0) { sidx = i; break; }
                    if (sidx < 1) return;
                    String from = new String(dec, 0, sidx, StandardCharsets.UTF_8);
                    String msg  = new String(dec, sidx + 1, dec.length - sidx - 1, StandardCharsets.UTF_8);
                    if (from.equals(myNick)) return;
                    UIEventBus.publish(new Object[]{from, msg});
                } catch (SecurityException ignored) {}
            }

            default -> { /* ignore */ }
        }
    }
}
