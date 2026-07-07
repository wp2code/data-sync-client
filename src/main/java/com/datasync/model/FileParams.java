package com.datasync.model;

import lombok.Data;

/**
 * @author liuweiping
 * @date 2026-07-07
 **/
@Data
public class FileParams {
    
    /**
     * 项目ID或路径，必填
     */
    private String projectOrId;
    
    /**
     * 要提交到的分支名称，必填
     */
    private String branch;
    
    /**
     * 提交信息，必填
     */
    private String commitMessage;
    
    /**
     * 文件路径，必填
     */
    private String filePath;
    
    /**
     * 文件内容，必填
     */
    private String content;
    
    /**
     * 编码格式 base64或text
     */
    private String encoding = "base64";
    /**
     * 文件名称
     */
    private String fileName;
    
    /**
     * 提交作者姓名
     */
    private String authorName;
    
    /**
     * 要从中创建分支的基准分支名称。
     */
    private String startBranch;
}
