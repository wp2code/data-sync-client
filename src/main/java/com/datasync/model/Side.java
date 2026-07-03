package com.datasync.model;

/**
 * 标识数据库侧（源库/目标库），替代布尔 isSource 参数。
 */
public enum Side {
    SOURCE, TARGET;

    /** 中文标签 */
    public String label() {
        return this == SOURCE ? "源数据库" : "目标数据库";
    }

    /** 简短标签 */
    public String shortLabel() {
        return this == SOURCE ? "源库" : "目标库";
    }
}
