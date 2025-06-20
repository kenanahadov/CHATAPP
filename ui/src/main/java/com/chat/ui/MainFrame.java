package com.chat.ui;

import com.chat.common.ConfigLoader;
import com.chat.common.Frame;
import com.chat.common.FrameType;
import com.chat.common.UIEventBus;
import com.chat.gateway.GatewayManager;
import com.chat.network.NetworkManager;
import com.chat.security.KeyManager;
import com.chat.security.SecurityException;
import com.chat.security.CryptoUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Base64;
import java.security.PublicKey;


public class MainFrame extends JFrame {


    private final NetworkManager netMgr;
    private final GatewayManager gwMgr;
    private       String         nickname = "";

    //widgets

    private final ChatPanel     chatPane   = new ChatPanel();
    private final PeerListPanel peerList   = new PeerListPanel();
    private final StatusBar     statusBar  = new StatusBar();
    private final JTextField    inputField = new JTextField();
    private final JButton       sendButton = new JButton("Send");
    private final JComboBox<String> peerCombo = new JComboBox<>();

    private enum Mode { CLIENT, GATEWAY, BOTH }
    private Mode mode = Mode.BOTH;

    private final JMenuItem miGenKeys;
    private final JMenuItem miConnect;
    private final JMenuItem miDisconnect;
    private final JMenuItem miExit;
    private final JMenuItem miAbout;


    public MainFrame(NetworkManager netMgr, GatewayManager gwMgr) {
        super("Chat-System");
        this.netMgr = netMgr;
        this.gwMgr  = gwMgr;

        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenu help = new JMenu("Help");
        miGenKeys    = new JMenuItem("Generate Keys");
        miConnect    = new JMenuItem("Connect");
        miDisconnect = new JMenuItem("Disconnect");
        miExit       = new JMenuItem("Exit");
        miAbout      = new JMenuItem("About");


        file.add(miGenKeys);
        file.addSeparator();
        file.add(miConnect);
        file.add(miDisconnect);
        file.addSeparator();
        file.add(miExit);
        bar.add(file);
        help.add(miAbout);
        bar.add(help);
        setJMenuBar(bar);

        miDisconnect.setEnabled(false);
        miGenKeys   .addActionListener(e -> generateKeys());
        miConnect   .addActionListener(e -> doConnect());
        miDisconnect.addActionListener(e -> doDisconnect());
        miExit      .addActionListener(e -> System.exit(0));
        miAbout     .addActionListener(e -> showAbout());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        add(peerList, BorderLayout.WEST);
        add(chatPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusBar, BorderLayout.SOUTH);

        JPanel input = new JPanel(new BorderLayout());
        peerCombo.addItem("All");
        input.add(peerCombo, BorderLayout.WEST);
        input.add(inputField, BorderLayout.CENTER);
        input.add(sendButton,  BorderLayout.EAST);
        bottom.add(input, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);

        inputField.setEnabled(false);
        sendButton .setEnabled(false);
        peerCombo.setEnabled(false);

        ActionListener sendAct = e -> {
            String msgTxt = inputField.getText().trim();
            if (msgTxt.isEmpty()) return;

            String target = (String) peerCombo.getSelectedItem();
            if (target == null) target = "All";

            try {
                byte[] msgBytes = msgTxt.getBytes(StandardCharsets.UTF_8);
                byte[] sig = CryptoUtils.sign(msgBytes, KeyManager.loadPrivateKey());
                byte[] nickBytes = nickname.getBytes(StandardCharsets.UTF_8);

                if ("All".equals(target)) {
                    for (String peer : netMgr.getPeerNames()) {
                        if (peer.equals(nickname)) continue;
                        PublicKey pk = netMgr.getPeerKey(peer);
                        if (pk == null) continue;
                        byte[] enc = CryptoUtils.encrypt(msgBytes, pk);
                        byte[] payload = ByteBuffer.allocate(nickBytes.length + 1 + 2 + sig.length + enc.length)
                                .put(nickBytes).put((byte)0)
                                .putShort((short)sig.length).put(sig)
                                .put(enc).array();
                        Frame f = new Frame(FrameType.MESSAGE, ConfigLoader.getInt("chat.ttl"));
                        netMgr.sendFrame(f, payload);
                    }
                } else {
                    PublicKey pk = netMgr.getPeerKey(target);
                    if (pk == null) throw new Exception("Unknown peer public key");
                    byte[] enc = CryptoUtils.encrypt(msgBytes, pk);
                    byte[] payload = ByteBuffer.allocate(nickBytes.length + 1 + 2 + sig.length + enc.length)
                            .put(nickBytes).put((byte)0)
                            .putShort((short)sig.length).put(sig)
                            .put(enc).array();
                    Frame f = new Frame(FrameType.PRIVATE, ConfigLoader.getInt("chat.ttl"));
                    netMgr.sendFrame(f, payload);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Send failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            //local display
            String prefix = "All".equals(target) ? "" : " (to " + target + ")";
            chatPane.append(nickname + prefix + ": " + msgTxt);
            inputField.setText("");
        };
        sendButton.addActionListener(sendAct);
        inputField.addActionListener(sendAct);

        UIEventBus.subscribe(evt -> {
            if (evt instanceof Object[] arr && arr.length >= 2) {          // MESSAGE
                String nick = (String) arr[0];
                String txt  = (String) arr[1];
                boolean priv = arr.length == 3 && Boolean.TRUE.equals(arr[2]);
                String suffix = priv ? " (private)" : "";
                chatPane.append(nick + suffix + ": " + txt);
            } else if (evt instanceof Collection<?> coll) {                // PEER LIST
                List<String> names = new ArrayList<>();
                coll.forEach(o -> names.add(o.toString()));
                SwingUtilities.invokeLater(() -> {
                    peerList.setPeers(names);
                    statusBar.setPeerCount(names.size());
                    peerCombo.removeAllItems();
                    peerCombo.addItem("All");
                    for (String n : names) peerCombo.addItem(n);
                });
            }
        });
    }

    //helper functions 
    private void generateKeys() {
        try {
            KeyManager.generateKeyPair();
            JOptionPane.showMessageDialog(this,
                    "Keys generated in ~/.chatkeys/",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (SecurityException ex) {
            JOptionPane.showMessageDialog(this,
                    "Key generation failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doConnect() {
        nickname = JOptionPane.showInputDialog(this,
                                               "Enter your nickname:",
                                               "Connect",
                                               JOptionPane.PLAIN_MESSAGE);
        if (nickname == null || nickname.isBlank()) return;

        String[] options = {"Client+Gateway", "Client Only", "Gateway Only"};
        String sel = (String) JOptionPane.showInputDialog(this,
                "Select mode:", "Connect",
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (sel == null) return;
        mode = switch (sel) {
            case "Client Only"  -> Mode.CLIENT;
            case "Gateway Only" -> Mode.GATEWAY;
            default             -> Mode.BOTH;
        };

        // make sure keys exist
        try { KeyManager.loadPrivateKey(); KeyManager.loadPublicKey(); }
        catch (SecurityException ex) {
            JOptionPane.showMessageDialog(this,
                    "Generate keys first!",
                    "Missing Keys", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //start
        try {
            if (mode != Mode.GATEWAY) {
                netMgr.setNickname(nickname);
                netMgr.start();
            }
            if (mode != Mode.CLIENT) {
                gwMgr.start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Network start failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //HELLO
        if (mode != Mode.GATEWAY) {
            try {
                Frame hello = new Frame(FrameType.HELLO,
                                        ConfigLoader.getInt("chat.ttl"));
                byte[] nk = nickname.getBytes(StandardCharsets.UTF_8);
                byte[] pk = Base64.getEncoder()
                        .encode(KeyManager.loadPublicKey().getEncoded());
                byte[] payload = new byte[nk.length + 1 + pk.length];
                System.arraycopy(nk, 0, payload, 0, nk.length);
                payload[nk.length] = 0;
                System.arraycopy(pk, 0, payload, nk.length + 1, pk.length);
                netMgr.sendFrame(hello, payload);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "HELLO send failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        miConnect.setEnabled(false);
        miDisconnect.setEnabled(true);
        inputField.setEnabled(mode != Mode.GATEWAY);
        sendButton .setEnabled(mode != Mode.GATEWAY);
        peerCombo.setEnabled(mode != Mode.GATEWAY);
        statusBar.setMode(mode == Mode.BOTH ? "CLIENT+GW" :
                (mode == Mode.CLIENT ? "CLIENT" : "GATEWAY"));
    }

    private void doDisconnect() {
        if (mode != Mode.GATEWAY) {
            try {
                Frame bye = new Frame(FrameType.BYE,
                                      ConfigLoader.getInt("chat.ttl"));
                netMgr.sendFrame(bye,
                        nickname.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
            netMgr.stop();
        }
        if (mode != Mode.CLIENT) {
            gwMgr.stop();
        }

        miConnect.setEnabled(true);
        miDisconnect.setEnabled(false);
        inputField.setEnabled(false);
        sendButton .setEnabled(false);
        peerCombo.setEnabled(false);
        statusBar.setMode("DISCONNECTED");
        peerList.setPeers(List.of());
        statusBar.setPeerCount(0);
        mode = Mode.BOTH;
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "ChatApp\nDeveloped by XYZ University",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }
}
