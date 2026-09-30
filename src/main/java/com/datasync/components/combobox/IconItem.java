package com.datasync.components.combobox;

import java.util.Objects;
import javax.swing.*;

/**
 * @param userData 可选：携带业务数据
 * @author liuweiping
 * @date 2026-07-02
 */
public record IconItem(Icon icon, String text, Object userData) {
    
    public IconItem(Icon icon, String text) {
        this(icon, text, null);
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
