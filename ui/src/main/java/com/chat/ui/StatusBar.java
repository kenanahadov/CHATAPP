
package com.chat.ui;

import javax.swing.*;
import java.awt.*;

public class StatusBar extends JPanel {
    private final JLabel mode    = new JLabel("Mode: CLIENT");
    private final JLabel peers   = new JLabel("Peers: 0");
    private final JLabel gateway = new JLabel("Gateway: OFF");

    public StatusBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        add(mode); add(peers); add(gateway);
    }

    public void setMode(String m){ mode.setText("Mode: "+m); }
    public void setPeerCount(int n){ peers.setText("Peers: "+n); }
    public void setGateway(boolean on){ gateway.setText("Gateway: "+(on?"ON":"OFF")); }
}
