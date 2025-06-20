
package com.chat.ui;

import javax.swing.*;

public class ErrorNotifier {
    public static void showError(String title, String msg) {
        JOptionPane.showMessageDialog(null, msg, title, JOptionPane.ERROR_MESSAGE);
    }
}
