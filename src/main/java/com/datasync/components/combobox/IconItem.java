package com.datasync.components.combobox;

import java.util.Objects;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-07-02
 **/
public class IconItem {
    
    private final Icon icon;
    
    private final String text;
    
    private final Object userData;   // 可选：携带业务数据
    
    public IconItem(Icon icon, String text, Object userData) {
        this.icon = icon;
        this.text = text;
        this.userData = userData;
    }
    
    public IconItem(Icon icon, String text) {
        this(icon, text, null);
    }
    
    public Icon getIcon() {
        return icon;
    }
    
    public String getText() {
        return text;
    }
    
    public Object getUserData() {
        return userData;
    }
    
    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IconItem other = (IconItem) obj;
        return Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
