package com.omnikit.components;

import com.formdev.flatlaf.FlatLaf;
import com.omnikit.ui.UiConstants;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.IOException;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * 列表分组拖拽支持
 * <p>
 * 为「分组标题 + 组内列表项」结构提供拖放能力：列表项可拖到分组标题（或组内任意位置）加入该分组， 拖到未分组区域（含列表空白处）移出分组。仅在同一个 JVM 内传递「移动到分组」回调，不读写系统剪贴板。
 *
 * @author liuweiping
 * @date 2026-11-10
 **/
public final class GroupDragSupport {
    
    /**
     * 高亮态标记：记录在目标组件上，用于判定是否处于高亮中并避免重复备份原样式
     */
    private static final String HIGHLIGHT_ACTIVE_KEY = "groupDragHighlight";
    
    private static final String HIGHLIGHT_BACKGROUND_KEY = "groupDragBackground";
    
    private static final String HIGHLIGHT_OPAQUE_KEY = "groupDragOpaque";
    
    private static final String HIGHLIGHT_FOREGROUND_KEY = "groupDragForeground";
    
    /**
     * 自动滚动的触发边缘（像素）与每次滚动的步长（像素）
     */
    private static final int SCROLL_EDGE = 24;
    
    private static final int SCROLL_STEP = 24;
    
    /**
     * 拖拽状态监听：dragging 为拖拽开始/结束，grouped 为被拖动项当前是否已有分组
     */
    @FunctionalInterface
    public interface DragStateListener {
        
        void dragStateChanged(boolean dragging, boolean grouped);
    }
    
    /**
     * 拖拽载荷的数据格式：跨组件传递「移动到分组」回调（参数为目标分组名，空串表示移出分组）
     */
    private static final DataFlavor MOVE_FLAVOR = createMoveFlavor();
    
    private GroupDragSupport() {
    }
    
    /**
     * 让列表项可作为拖拽源：按住并拖动超过系统阈值时启动拖拽。 子组件不会把鼠标事件冒泡到父组件，故需为整棵子树逐一注册
     */
    public static void enableDragSource(JComponent component, Consumer<String> moveToGroup) {
        enableDragSource(component, moveToGroup, false, null);
    }
    
    /**
     * 让列表项可作为拖拽源，并在拖拽开始/结束时回调拖拽状态（用于显隐「移出分组」投放提示等外部提示）； grouped 为该项当前是否已有分组，未分组项拖动时无需提示移出
     */
    public static void enableDragSource(JComponent component, Consumer<String> moveToGroup, boolean grouped, DragStateListener dragStateListener) {
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(component, DnDConstants.ACTION_MOVE, e -> {
            e.startDrag(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), new MoveTransferable(moveToGroup), new DragSourceAdapter() {
                
                @Override
                public void dragDropEnd(DragSourceDropEvent dsde) {
                    notifyDragState(dragStateListener, false, grouped);
                }
            });
            notifyDragState(dragStateListener, true, grouped);
        });
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent childComponent) {
                enableDragSource(childComponent, moveToGroup, grouped, dragStateListener);
            }
        }
    }
    
    private static void notifyDragState(DragStateListener dragStateListener, boolean dragging, boolean grouped) {
        if (dragStateListener != null) {
            dragStateListener.dragStateChanged(dragging, grouped);
        }
    }
    
    /**
     * 让组件接收拖拽并移动到指定分组：groupName 为空串表示移出分组； highlight 为拖拽停留时高亮的组件（通常为分组标题或分组内容区），可为 null
     */
    public static void enableGroupTarget(JComponent target, String groupName, JComponent highlight) {
        enableGroupTarget(target, groupName, highlight, null);
    }
    
    /**
     * 让组件接收拖拽并移动到指定分组；autoScrollPane 不为 null 时，拖拽停留在其上下边缘会自动滚动列表
     */
    public static void enableGroupTarget(JComponent target, String groupName, JComponent highlight, JScrollPane autoScrollPane) {
        String name = groupName == null ? "" : groupName;
        new DropTarget(target, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
            
            @Override
            public void dragEnter(DropTargetDragEvent e) {
                updateDragState(e);
            }
            
            @Override
            public void dragOver(DropTargetDragEvent e) {
                updateDragState(e);
            }
            
            @Override
            public void dragExit(DropTargetEvent e) {
                highlight(highlight, false);
            }
            
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent e) {
                highlight(highlight, false);
                if (!e.isDataFlavorSupported(MOVE_FLAVOR)) {
                    e.rejectDrop();
                    return;
                }
                try {
                    e.acceptDrop(DnDConstants.ACTION_MOVE);
                    Consumer<String> moveToGroup = (Consumer<String>) e.getTransferable().getTransferData(MOVE_FLAVOR);
                    moveToGroup.accept(name);
                    e.dropComplete(true);
                } catch (UnsupportedFlavorException | IOException ex) {
                    e.dropComplete(false);
                }
            }
            
            private void updateDragState(DropTargetDragEvent e) {
                if (e.isDataFlavorSupported(MOVE_FLAVOR)) {
                    e.acceptDrag(DnDConstants.ACTION_MOVE);
                    highlight(highlight, true);
                    autoScroll(target, e, autoScrollPane);
                } else {
                    e.rejectDrag();
                    highlight(highlight, false);
                }
            }
        }, true);
    }
    
    /**
     * 拖拽到滚动区域上下边缘时自动滚动：列表较长时可把项拖到当前视口以外的分组
     */
    private static void autoScroll(JComponent target, DropTargetDragEvent e, JScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        JViewport viewport = scrollPane.getViewport();
        int y = SwingUtilities.convertPoint(target, e.getLocation(), viewport).y;
        int delta = 0;
        if (y < SCROLL_EDGE) {
            delta = -SCROLL_STEP;
        } else if (y > viewport.getHeight() - SCROLL_EDGE) {
            delta = SCROLL_STEP;
        }
        if (delta == 0) {
            return;
        }
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        bar.setValue(Math.max(bar.getMinimum(), Math.min(bar.getMaximum(), bar.getValue() + delta)));
    }
    
    /**
     * 切换高亮态：首次高亮时记录原有背景与透明状态，取消高亮时原样恢复； 同时把子树内所有文字标签的前景色切为高亮文字色，并保留各自原前景色
     */
    private static void highlight(JComponent component, boolean active) {
        if (component == null) {
            return;
        }
        if (active) {
            if (component.getClientProperty(HIGHLIGHT_ACTIVE_KEY) == null) {
                component.putClientProperty(HIGHLIGHT_ACTIVE_KEY, Boolean.TRUE);
                component.putClientProperty(HIGHLIGHT_BACKGROUND_KEY, component.getBackground());
                component.putClientProperty(HIGHLIGHT_OPAQUE_KEY, component.isOpaque());
            }
            component.setOpaque(true);
            component.setBackground(highlightBackground());
        } else if (component.getClientProperty(HIGHLIGHT_ACTIVE_KEY) != null) {
            if (component.getClientProperty(HIGHLIGHT_BACKGROUND_KEY) instanceof Color background) {
                component.setBackground(background);
            }
            component.setOpaque(Boolean.TRUE.equals(component.getClientProperty(HIGHLIGHT_OPAQUE_KEY)));
            component.putClientProperty(HIGHLIGHT_ACTIVE_KEY, null);
            component.putClientProperty(HIGHLIGHT_BACKGROUND_KEY, null);
            component.putClientProperty(HIGHLIGHT_OPAQUE_KEY, null);
        }
        highlightText(component, active);
        component.repaint();
    }
    
    /**
     * 文字高亮：仅改变标签前景色（背景高亮只作用于区块本身，避免子标签被逐个铺色）
     */
    private static void highlightText(Component component, boolean active) {
        if (component instanceof JLabel label) {
            if (active) {
                if (label.getClientProperty(HIGHLIGHT_FOREGROUND_KEY) == null) {
                    label.putClientProperty(HIGHLIGHT_FOREGROUND_KEY, label.getForeground());
                }
                label.setForeground(highlightForeground());
            } else if (label.getClientProperty(HIGHLIGHT_FOREGROUND_KEY) instanceof Color foreground) {
                label.setForeground(foreground);
                label.putClientProperty(HIGHLIGHT_FOREGROUND_KEY, null);
            }
            label.repaint();
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                highlightText(child, active);
            }
        }
    }
    
    /**
     * 高亮背景色：暗色主题用深靛蓝底，浅色主题用淡靛蓝底，均避开接近白色的底色，保证在列表背景上能明显区分
     */
    private static Color highlightBackground() {
        return FlatLaf.isLafDark() ? new Color(0x2C5463) : new Color(0xCCE1EC);
    }
    
    /**
     * 高亮文字色：与背景成对取值，暗色主题用主色浅色版，浅色主题用主色
     */
    private static Color highlightForeground() {
        return FlatLaf.isLafDark() ? UiConstants.COLOR_PRIMARY_LIGHT : UiConstants.COLOR_PRIMARY;
    }
    
    private static DataFlavor createMoveFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.util.function.Consumer");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("无法创建拖拽数据格式", e);
        }
    }
    
    /**
     * 拖拽载荷：携带「移动到分组」回调，目标分组由投放端决定
     */
    private static final class MoveTransferable implements Transferable {
        
        private final Consumer<String> moveToGroup;
        
        MoveTransferable(Consumer<String> moveToGroup) {
            this.moveToGroup = moveToGroup;
        }
        
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {MOVE_FLAVOR};
        }
        
        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return MOVE_FLAVOR.equals(flavor);
        }
        
        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!MOVE_FLAVOR.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return moveToGroup;
        }
    }
}
