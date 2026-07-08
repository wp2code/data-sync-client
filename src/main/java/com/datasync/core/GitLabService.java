package com.datasync.core;

import com.datasync.model.CommitParams;
import com.datasync.model.FileParams;
import com.datasync.model.GitLabAuthConfig;
import com.datasync.util.GlobalUtil;
import com.datasync.util.SQLiteConfigUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mysql.cj.util.StringUtils;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitAction;
import org.gitlab4j.api.models.CommitPayload;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.RepositoryFileResponse;
import org.gitlab4j.models.Constants;

/**
 * GitLab 接口服务
 *
 * @author liuweiping
 * @date 2026-07-07
 **/
@Slf4j
public class GitLabService {
    
    private GitLabApi gitLabClient;
    
    @Getter
    private GitLabAuthConfig gitLabAuthConfig;
    
    private final Cache<Long, List<Project>> PROJECT_CACHE = CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(2))
            .build();
    
    private final Cache<String, List<Branch>> BRANCH_CACHE = CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(2))
            .build();
    
    // ────────── 单例 ──────────
    private static final GitLabService INSTANCE = new GitLabService();
    
    public static GitLabService getInstance() {
        return INSTANCE;
    }
    
    private GitLabService() {
    }
    
    /**
     * gitlab 登录
     *
     * @param login       gitlab 登录信息
     * @param isAutoLogin 是否自动登录
     * @return token
     */
    public GitLabApi login(GitLabAuthConfig login, boolean isAutoLogin) {
        try {
            final GitLabApi gitLabApi = GitLabApi.oauth2Login(login.getUrl(), login.getUsername(), login.getPassword());
            this.gitLabClient = gitLabApi;
            log.info("[GitLab] Login success, url: {}, revision: {}", login.getUrl(), gitLabApi.getVersion().toString());
            return gitLabApi;
        } catch (Exception ex) {
            if (isAutoLogin) {
                log.warn("[GitLab] Login failed, try to auto login");
                return null;
            }
            log.error("[GitLab] Login failed", ex);
            throw new RuntimeException("[GitLab] Login failed", ex);
        }
    }
    
    public void autoLogin() {
        gitLabClient(true);
    }
    
    public void logout() {
        PROJECT_CACHE.cleanUp();
        BRANCH_CACHE.cleanUp();
        gitLabClient = null;
    }
    
    /**
     * 获取项目列表
     *
     * @return 项目列表
     */
    public List<Project> getProjectList() {
        try {
            if (gitLabAuthConfig != null) {
                final List<Project> projects = PROJECT_CACHE.getIfPresent(gitLabAuthConfig.getId());
                if (projects != null && !projects.isEmpty()) {
                    return projects;
                }
            }
            final List<Project> projects = gitLabClient(false).getProjectApi().getProjects();
            if (projects != null && !projects.isEmpty()) {
                if (gitLabAuthConfig != null) {
                    PROJECT_CACHE.put(gitLabAuthConfig.getId(), projects);
                }
            }
            return projects;
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Get Project Failed", ex);
        }
    }
    
    /**
     * 获取项目分支列表
     *
     * @param projectOrId 项目id
     * @return 分支列表
     */
    public List<Branch> getBranchList(String projectOrId) {
        try {
            List<Branch> branches = BRANCH_CACHE.getIfPresent(projectOrId);
            if (branches != null && !branches.isEmpty()) {
                return branches;
            }
            branches = gitLabClient(false).getRepositoryApi().getBranches(projectOrId);
            if (branches != null && !branches.isEmpty()) {
                BRANCH_CACHE.put(projectOrId, branches);
            }
            return gitLabClient(false).getRepositoryApi().getBranches(projectOrId);
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Get Project Branch Failed", ex);
        }
    }
    
    /**
     * 创建文件
     *
     * @param projectOrId   项目ID或路径
     * @param file          文件信息
     * @param branch        分支
     * @param commitMessage 内容
     * @return 结果
     */
    public RepositoryFileResponse createFile(String projectOrId, RepositoryFile file, String branch, String commitMessage) {
        try {
            return gitLabClient(false).getRepositoryFileApi().createFile(projectOrId, file, branch, commitMessage);
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Create File Failed", ex);
        }
    }
    
    /**
     * 更新文件
     *
     * @param projectOrId   项目ID或路径
     * @param file          文件信息
     * @param branch        分支
     * @param commitMessage 内容
     * @return 结果
     */
    public RepositoryFileResponse updateFile(String projectOrId, RepositoryFile file, String branch, String commitMessage) {
        try {
            return gitLabClient(false).getRepositoryFileApi().updateFile(projectOrId, file, branch, commitMessage);
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Update File Failed", ex);
        }
    }
    
    /**
     * 新增或跟新文件
     *
     * @param fileParams 文件参数
     * @return 响应参数
     */
    public RepositoryFileResponse createOrUpdateFile(FileParams fileParams) {
        fileParams.setFilePath(GlobalUtil.getFullFilePath(fileParams.getFilePath(), fileParams.getFileName()));
        //判断文件是否存在
        final RepositoryFile fileRes = existsGetFile(fileParams.getProjectOrId(), fileParams.getFilePath(), fileParams.getBranch());
        final RepositoryFile file = new RepositoryFile();
        file.setEncoding(Constants.Encoding.forValue(fileParams.getEncoding()));
        file.setContent(fileParams.getContent());
        if (fileRes == null || StringUtils.isNullOrEmpty(fileRes.getFilePath())) {
            file.setFileName(fileParams.getFileName());
            file.setFilePath(fileParams.getFilePath());
            fileParams.setCommitMessage("feat: 新增文件" + (fileParams.getCommitMessage() != null ? fileParams.getCommitMessage() : ""));
            return createFile(fileParams.getProjectOrId(), file, fileParams.getBranch(), fileParams.getCommitMessage());
        }
        file.setFileName(fileRes.getFileName());
        file.setFilePath(fileRes.getFilePath());
        file.setLastCommitId(fileRes.getLastCommitId());
        fileParams.setCommitMessage("feat: 更新文件" + (fileParams.getCommitMessage() != null ? fileParams.getCommitMessage() : ""));
        return updateFile(fileParams.getProjectOrId(), file, fileParams.getBranch(), fileParams.getCommitMessage());
    }
    
    /**
     * 获取文件内容
     *
     * @param projectOrId 项目ID或路径
     * @param filePath    文件路径
     * @param branch      The name of branch, tag or commit
     * @return 文件内容
     */
    public RepositoryFile getFile(String projectOrId, String filePath, String branch) {
        try {
            return gitLabClient(false).getRepositoryFileApi().getFile(projectOrId, filePath, branch);
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Get File Failed", ex);
        }
    }
    
    public RepositoryFile existsGetFile(String projectOrId, String filePath, String branch) {
        try {
            return gitLabClient(false).getRepositoryFileApi().getFile(projectOrId, filePath, branch);
        } catch (Exception ex) {
            if (ex instanceof GitLabApiException gitLabApiException) {
                if (gitLabApiException.getHttpStatus() == 404) {
                    return null;
                }
            }
            throw new RuntimeException("[GitLab] Get File Failed", ex);
        }
    }
    
    /**
     * 获取文件流
     *
     * @param projectOrId 项目ID或路径
     * @param filePath    文件路径
     * @param branch      The name of branch, tag or commit
     * @return 文件流
     */
    public InputStream getRawFile(String projectOrId, String filePath, String branch) {
        try {
            return gitLabClient(false).getRepositoryFileApi().getRawFile(projectOrId, filePath, branch);
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Get File Stream Failed", ex);
        }
    }
    
    public boolean isLogin() {
        return gitLabClient != null;
    }
    
    /**
     * 提交代码
     *
     * @param commitParams 提交参数
     * @return 提交结果
     */
    public Commit commit(CommitParams commitParams) {
        try {
            CommitPayload commitPayload = new CommitPayload();
            commitPayload.setBranch(commitParams.getBranch());
            commitPayload.setCommitMessage(commitParams.getCommitMessage());
            final CommitAction commitAction = new CommitAction();
            commitAction.setAction(CommitAction.Action.forValue(commitParams.getAction()));
            commitAction.setContent(commitParams.getContent());
            commitAction.setFilePath(commitParams.getFilePath());
            commitAction.setEncoding(Constants.Encoding.forValue(commitParams.getEncoding()));
            commitPayload.setActions(List.of(commitAction));
            return gitLabClient(false).getCommitsApi().createCommit(commitParams.getProjectOrId(), commitPayload);
        } catch (Exception ex) {
            throw new RuntimeException("[GitLab] Commit Failed", ex);
        }
    }
    
    
    private GitLabApi gitLabClient(boolean isAutoLogin) {
        if (this.gitLabClient == null) {
            final GitLabAuthConfig gitLabAuthConfig = SQLiteConfigUtil.getInstance().loadGitLabAuthConfig();
            this.gitLabAuthConfig = gitLabAuthConfig;
            if (gitLabAuthConfig == null) {
                throw new RuntimeException("[GitLab] 未配置 GitLab");
            }
            this.login(gitLabAuthConfig, isAutoLogin);
        }
        return this.gitLabClient;
    }
    
    
}
