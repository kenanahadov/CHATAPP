
package com.chat.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class ChatPanel extends JPanel {
    private final JTextPane pane = new JTextPane();
    private final StyledDocument doc = pane.getStyledDocument();

    public ChatPanel() {
        setLayout(new BorderLayout());
        pane.setEditable(false);
        add(new JScrollPane(pane), BorderLayout.CENTER);
    }

    public void append(String msg) {
        try { doc.insertString(doc.getLength(), msg + "\n", null); }
        catch(BadLocationException ignored){}
    }
}
