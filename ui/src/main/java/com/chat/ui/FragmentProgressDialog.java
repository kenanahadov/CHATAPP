
package com.chat.ui;

import javax.swing.*;

public class FragmentProgressDialog extends JDialog {
    private final JProgressBar bar = new JProgressBar();

    public FragmentProgressDialog(JFrame parent, int total) {
        super(parent, "Sending...", false);
        setSize(300, 80);
        bar.setMaximum(total);
        add(bar);
    }

    public void updateProgress(int sent) { bar.setValue(sent); }
}
