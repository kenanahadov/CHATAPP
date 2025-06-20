package com.chat.app;

import com.chat.network.NetworkManager;
import com.chat.gateway.GatewayManager;
import com.chat.ui.MainFrame;

import javax.swing.SwingUtilities;
    //this fucn is used launch the MainFrame(Java Swing)
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            NetworkManager netMgr = new NetworkManager();
            GatewayManager  gwMgr  = new GatewayManager();
            MainFrame       ui     = new MainFrame(netMgr, gwMgr);
            ui.setVisible(true);
        });
    }
}
