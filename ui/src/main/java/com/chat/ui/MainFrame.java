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

import java.security.PublicKey;
import java.nio.ByteBuffer;
import java.util.Base64;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


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

    private final JMenuItem       miGenKeys;
    private final JMenuItem       miConnect;
    private final JMenuItem       miDisconnect;
    private final JMenuItem       miExit;
    private final JCheckBoxMenuItem miGateway;
    private final JMenuItem       miAbout;


    public MainFrame(NetworkManager netMgr, GatewayManager gwMgr) {
        super("Chat-System");
        this.netMgr = netMgr;
        this.gwMgr  = gwMgr;

        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        miGenKeys    = new JMenuItem("Generate Keys");
        miConnect    = new JMenuItem("Connect");
        miDisconnect = new JMenuItem("Disconnect");
        miExit       = new JMenuItem("Exit");
        miGateway    = new JCheckBoxMenuItem("Gateway Mode");

        JMenu help = new JMenu("Help");
        miAbout     = new JMenuItem("About");


        file.add(miGenKeys);
        file.addSeparator();
        file.add(miConnect);
        file.add(miDisconnect);
        file.add(miGateway);
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
        miAbout     .addActionListener(e ->
                JOptionPane.showMessageDialog(this,
                        "Anonymous Chat Application\nDeveloper: " +
                        ConfigLoader.get("app.developer"),
                        "About", JOptionPane.INFORMATION_MESSAGE));

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());

        add(peerList, BorderLayout.WEST);
        add(chatPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusBar, BorderLayout.SOUTH);

        JPanel input = new JPanel(new BorderLayout());
        input.add(inputField, BorderLayout.CENTER);
        input.add(sendButton,  BorderLayout.EAST);
        bottom.add(input, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);

        inputField.setEnabled(false);
        sendButton .setEnabled(false);

        ActionListener sendAct = e -> {
            String msgTxt = inputField.getText().trim();
            if (msgTxt.isEmpty()) return;

            netMgr.getPeers().forEach((nick, pub) -> {
                if (nick.equals(nickname)) return;
                try {
                    byte[] enc = CryptoUtils.encrypt(
                            (nickname + '\0' + msgTxt)
                                    .getBytes(StandardCharsets.UTF_8), pub);
                    byte[] body = ByteBuffer.allocate(nick.getBytes().length + 1 + enc.length)
                            .put(nick.getBytes(StandardCharsets.UTF_8))
                            .put((byte)0)
                            .put(enc).array();
                    Frame f = new Frame(FrameType.MESSAGE,
                                        ConfigLoader.getInt("chat.ttl"));
                    netMgr.sendFrame(f, body);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Send failed:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            chatPane.append(nickname + ": " + msgTxt);
            inputField.setText("");
        };
        sendButton.addActionListener(sendAct);
        inputField.addActionListener(sendAct);

        UIEventBus.subscribe(evt -> {
            if (evt instanceof Object[] arr && arr.length == 2) {          // MESSAGE
                String nick = (String) arr[0];
                String txt  = (String) arr[1];
                chatPane.append(nick + ": " + txt);
            } else if (evt instanceof Collection<?> coll) {                // PEER LIST
                List<String> names = new ArrayList<>();
                coll.forEach(o -> names.add(o.toString()));
                SwingUtilities.invokeLater(() -> {
                    peerList.setPeers(names);
                    statusBar.setPeerCount(names.size());
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
            netMgr.setNickname(nickname);
            netMgr.start();
            if (miGateway.isSelected()) {
                gwMgr.setNetworkManager(netMgr);
                netMgr.setGatewayManager(gwMgr);
                gwMgr.start();
                statusBar.setMode("GATEWAY");
            } else {
                statusBar.setMode("CLIENT");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Network start failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //HELLO
        try {
            byte[] pub = KeyManager.loadPublicKey().getEncoded();
            byte[] body = ByteBuffer.allocate(nickname.getBytes().length + 1 + pub.length)
                    .put(nickname.getBytes(StandardCharsets.UTF_8)).put((byte)0).put(pub).array();
            netMgr.addLocalPeer(nickname, KeyManager.loadPublicKey());
            Frame hello = new Frame(FrameType.HELLO,
                                    ConfigLoader.getInt("chat.ttl"));
            netMgr.sendFrame(hello, body);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "HELLO send failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        miConnect.setEnabled(false);
        miDisconnect.setEnabled(true);
        inputField.setEnabled(true);
        sendButton .setEnabled(true);
    }

    private void doDisconnect() {
        try {
            Frame bye = new Frame(FrameType.BYE,
                                  ConfigLoader.getInt("chat.ttl"));
            netMgr.sendFrame(bye,
                    nickname.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }

        netMgr.stop();
        if (miGateway.isSelected()) {
            gwMgr.stop();
            netMgr.setGatewayManager(null);
        }

        miConnect.setEnabled(true);
        miDisconnect.setEnabled(false);
        inputField.setEnabled(false);
        sendButton .setEnabled(false);
        statusBar.setMode("DISCONNECTED");
        peerList.setPeers(List.of());
        statusBar.setPeerCount(0);
    }
}
