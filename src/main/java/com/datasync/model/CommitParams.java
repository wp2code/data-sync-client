package com.datasync.model;

import lombok.Data;

/**
 * @author liuweiping
 * @date 2026-07-07
 **/
@Data
public class CommitParams {
    
    /**
     * 项目ID或路径
     */
    private String projectOrId;
    
    /**
     * 要提交到的分支名称
     */
    private String branch;
    
    /**
     * 用作新提交父分支的分支名称
     */
    private String startBranch;
    
    /**
     * 提交信息
     */
    private String commitMessage;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件内容
     */
    private String content;
    
    /**
     * text 或 base64。默认为 text。
     */
    private String encoding = "text";
    
    /**
     * 要执行的操作：create、delete、move、update 或 chmod
     */
    private String action;
}
