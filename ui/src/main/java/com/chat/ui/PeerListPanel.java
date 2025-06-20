
package com.chat.ui;

import javax.swing.*;
import java.awt.*;

public class PeerListPanel extends JPanel {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    public PeerListPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(list), BorderLayout.CENTER);
        setPreferredSize(new Dimension(150, 400));
    }

    public void setPeers(java.util.List<String> peers) {
        model.clear();
        peers.forEach(model::addElement);
    }
}
