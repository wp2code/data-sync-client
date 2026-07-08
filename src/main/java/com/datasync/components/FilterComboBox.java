package com.datasync.components;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * 支持文本筛选的下拉框组件。
 * <p>
 * 打开下拉时会显示全部选项；在编辑框输入关键字时实时过滤列表；选中项后编辑框显示选中内容。
 *
 * @param <T> 下拉项类型
 */
public class FilterComboBox<T> extends JComboBox<T> {
    
    private final List<T> allItems = new ArrayList<>();
    
    public FilterComboBox() {
        setEditable(true);
        initListeners();
    }
    
    private void initListeners() {
    }
    
    
    /**
     * 设置全部候选项，并清空当前过滤条件显示完整列表。
     */
    public void setAllItems(List<T> items) {
        removeAllItems();
        allItems.clear();
        if (items != null) {
            allItems.addAll(items);
            for (final T item : items) {
                addItem(item);
            }
        }
    }
    
    /**
     * 返回全部候选项（不受过滤影响）。
     */
    public List<T> getAllItems() {
        return new ArrayList<>(allItems);
    }
    
    /**
     * 清空全部候选项。
     */
    public void clearAllItems() {
        allItems.clear();
    }
    
    @Override
    public void addItem(T item) {
        if (item == null) {
            return;
        }
        super.addItem(item);
        allItems.add(item);
        
    }
    
    @Override
    public void removeItem(Object anObject) {
        super.removeItem(anObject);
        allItems.remove(anObject);
    }
    
}
