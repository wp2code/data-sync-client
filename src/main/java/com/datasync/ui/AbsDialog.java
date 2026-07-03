package com.datasync.ui;

import java.awt.*;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-07-03
 **/
public abstract class AbsDialog extends JDialog {
    
    public AbsDialog(Frame owner, String title, boolean modal, int width, int height) {
        super(owner, title, modal);
        setSize(width, height);
        setLocationRelativeTo(owner);
  
    }
}
