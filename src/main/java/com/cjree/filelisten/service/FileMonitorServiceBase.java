package com.cjree.filelisten.service;

import com.cjree.filelisten.entity.FileMonitorConfigPo;
import com.cjree.filelisten.entity.FileOperationLogPo;
import com.cjree.filelisten.enums.PathTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
public abstract class FileMonitorServiceBase {
    @Resource
    protected FileMonitorConfigService fileMonitorConfigService;
    @Resource
    protected FileOperationLogService fileOperationLogService;

    // 监听服务
    protected WatchService watchService;

    // 存储路径与WatchKey的映射
    protected final Map<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();

    // 文件最后修改时间缓存（用于去重）
    protected final Map<Path, Long> fileLastModified = new ConcurrentHashMap<>();

    // 文件内容读取位置缓存
    protected final Map<Path, Long> filePositions = new ConcurrentHashMap<>();


    /**
     * 从数据库加载并启动所有监听配置
     */
    protected void loadAndStartMonitors() {
        List<FileMonitorConfigPo> enabledConfigs = fileMonitorConfigService.lambdaQuery().eq(FileMonitorConfigPo::getEnabled, Boolean.TRUE).list();
        log.info("加载到 {} 个启用的监听配置", enabledConfigs.size());
        // 启动每个配置的监听
        for (FileMonitorConfigPo fileMonitorConfigPo : enabledConfigs) {
            Path path = Paths.get(fileMonitorConfigPo.getMonitorPath());
            // 新增目录监听
            if (Objects.equals(fileMonitorConfigPo.getPathType(), PathTypeEnum.DIRECTORY.getCode())) {
                addMonitorDirectory(path, fileMonitorConfigPo.getRecursive());
                continue;
            }
            // 新增文件监听
            if (Objects.equals(fileMonitorConfigPo.getPathType(), PathTypeEnum.FILE.getCode())) {
                addMonitorFile(fileMonitorConfigPo.getMonitorPath(), fileMonitorConfigPo.getIncludePatterns(), fileMonitorConfigPo.getExcludePatterns());
                continue;
            }
            log.error("文件路径类型配置错误 : {} ", fileMonitorConfigPo.getMonitorPath());
        }
    }


    /**
     * 添加目录监听
     */
    protected void addMonitorDirectory(Path directory, boolean recursive) {
        try {
            if (!Files.exists(directory)) {
                log.error("目录不存在: {}", directory);
                return;
            }
            registerDirectory(directory, recursive);

            log.info("增加监听目录: {} (是否递归子目录: {})", directory, recursive);
        } catch (Exception e) {
            log.error("失败的添加目录监听: {}", directory, e);
        }
    }

    /**
     * 注册目录到WatchService
     */
    protected void registerDirectory(Path dir, boolean recursive) throws IOException {
        if (!Files.isDirectory(dir)) {
            log.error("传参路径必须为目录: {}", dir);
            return;
        }

        // 原子操作：如果dir未在map中，则注册并缓存；否则直接返回已存在的key
        WatchKey existingKey = watchKeys.putIfAbsent(dir, new DummyWatchKey()); // 临时占位
        if (existingKey != null) {
            // 已注册，直接返回
            log.warn("目录已经注册: {}", dir);
            return;
        }

        try {
            // 执行实际注册（此时已通过putIfAbsent确保唯一）
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            // 替换临时值为真实WatchKey
            watchKeys.replace(dir, key);
            log.info("注册目录: {}", dir);
            // 保存配置到数据库（如果不存在）
            saveOrUpdateConfig(dir.toString(),
                    PathTypeEnum.DIRECTORY.getCode(),
                    recursive, Boolean.TRUE,null,null);
        } catch (Exception e) {
            // 注册失败，移除临时占位
            watchKeys.remove(dir);
            throw e;
        }

        // 如果需要递归，注册所有子目录
        if (recursive) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path subDir : stream) {
                    if (Files.isDirectory(subDir)) {
                        registerDirectory(subDir, true);
                    }
                }
            }
        }
    }


    /**
     * 添加文件监听
     * @param path 文件路径
     * @param includePatterns 包含的文件模式
     * @param excludePatterns 排除的文件模式
     */
    public void addMonitorFile(String path, String includePatterns, String excludePatterns) {
        try {
            Path filePath = Paths.get(path);
            // 验证文件是否存在且是一个文件
            if (!Files.exists(filePath)) {
                log.warn("文件不存在: {}", path);
                return;
            }
            if (!Files.isRegularFile(filePath)) {
                log.warn("文件不符合规则: {}", path);
                return;
            }
            // 检查文件是否符合包含/排除模式
            if (!isFileIncluded(filePath)) {
                log.warn("文件不符合模式: {}", path);
                return;
            }
            // 注册文件监听
            registerFileWatch(filePath);

            // 保存配置到数据库
            saveOrUpdateConfig(path, PathTypeEnum.FILE.getCode(),
                    false, true, includePatterns, excludePatterns);

            log.info("增加文件监听: {}", path);
        } catch (Exception e) {
            log.error("Failed to add file monitor: {}", path, e);
        }
    }

    /**
     * 线程安全地注册文件监听
     */
    private void registerFileWatch(Path filePath) throws IOException {
        // 获取文件所在目录
        Path parentDir = filePath.getParent();
        if (parentDir == null) {
            log.error("不能获取文件的父级目录: {}", filePath);
            return;
        }

        // 确保文件所在目录已被监听
        if (!watchKeys.containsKey(parentDir)) {
            // 注册父目录，监听文件变化事件
            WatchKey key = parentDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(parentDir, key);
            log.info("注册父目录去监听文件变化: {}", parentDir);
        }

        // 初始化文件位置信息（从文件末尾开始监听新内容）
        long fileSize = Files.size(filePath);
        filePositions.put(filePath, fileSize);
        fileLastModified.put(filePath, getLastModifiedTime(filePath));
    }

    /**
     * 保存或更新监听配置
     */
    private void saveOrUpdateConfig(String path, String pathType,
                                    boolean recursive, boolean enabled,String includePatterns, String excludePatterns) {
        // 根据path查询查询数据库id
        FileMonitorConfigPo fileMonitorConfigPo = fileMonitorConfigService.lambdaQuery()
                .eq(FileMonitorConfigPo::getMonitorPath, path)
                .eq(FileMonitorConfigPo::getEnabled, Boolean.TRUE)
                .select(FileMonitorConfigPo::getId)
                .one();
        // 创建或更新数据库配置
        FileMonitorConfigPo config = new FileMonitorConfigPo();
        config.setId(fileMonitorConfigPo != null ? fileMonitorConfigPo.getId() : null);
        config.setMonitorPath(path);
        config.setPathType(pathType);
        config.setRecursive(recursive);
        config.setEnabled(enabled);
        config.setIncludePatterns(includePatterns);
        config.setExcludePatterns(excludePatterns);
        fileMonitorConfigService.insertOrUpdate(config);
    }

    /**
     * 更新配置状态（启用/禁用）
     */
    protected void updateConfigStatus(String path, boolean enabled) {
        fileMonitorConfigService.lambdaUpdate()
                .eq(FileMonitorConfigPo::getMonitorPath, path)
                .set(FileMonitorConfigPo::getEnabled, enabled)
                .update();
    }


    /**
     * 异步保存操作日志到数据库
     */
    @Async("dbWriterExecutor")
    protected void saveOperationLog(String filePath, String operationType, String content, String operator) {
        try {
            FileOperationLogPo log = new FileOperationLogPo();
            log.setFilePath(filePath);
            log.setOperationType(operationType);
            log.setContent(content);
            log.setOperator(operator);
            log.setOperationTime(new Date());
            fileOperationLogService.insert(log);
        } catch (Exception e) {
            log.error("Failed to save operation log for {}:{}", filePath, operationType, e);
        }
    }

    /**
     * 获取文件操作人
     */
    protected String getFileOperator() {
        // 实现获取文件操作人的逻辑
        return System.getProperty("user.name");
    }

    /**
     * 检查文件是否符合包含/排除模式
     */
    protected boolean isFileIncluded(Path path) {
        Path filePath = path.toAbsolutePath();
        FileMonitorConfigPo fileMonitorConfigPo = fileMonitorConfigService.lambdaQuery()
                .eq(FileMonitorConfigPo::getMonitorPath, filePath.toString())
                .eq(FileMonitorConfigPo::getEnabled, Boolean.TRUE)
                .select(FileMonitorConfigPo::getIncludePatterns, FileMonitorConfigPo::getExcludePatterns)
                .one();
        if (fileMonitorConfigPo == null) return true;
        String includePatterns = fileMonitorConfigPo.getIncludePatterns();
        String excludePatterns = fileMonitorConfigPo.getExcludePatterns();
        String fileName = filePath.getFileName().toString();

        // 处理排除模式
        if (excludePatterns != null && !excludePatterns.isEmpty()) {
            String[] excludePatternsArray = excludePatterns.split(",");
            for (String pattern : excludePatternsArray) {
                if (matchesPattern(fileName, pattern.trim())) {
                    return false;
                }
            }
        }

        // 处理包含模式（默认全部包含）
        if (includePatterns == null || includePatterns.isEmpty()) {
            return true;
        }

        String[] includePatternsArray = includePatterns.split(",");
        for (String pattern : includePatternsArray) {
            if (matchesPattern(fileName, pattern.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查文件名是否匹配模式（简单的通配符匹配）
     */
    private boolean matchesPattern(String fileName, String pattern) {
        // 转换为正则表达式
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return Pattern.matches(regex, fileName);
    }


    /**
     * 检查是否是子目录
     */
    protected boolean isSubdirectory(Path root, Path subDir) {
        return subDir.startsWith(root);
    }

    /**
     * 获取上次修改时间
     */
    protected long getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }


    /**
     * 处理目录创建事件
     * @param directory 被创建的目录
     */
    abstract void handleDirectoryCreate(File directory);

    /**
     * 处理目录删除事件
     * @param directory 被删除的目录
     */
    abstract void handleDirectoryDelete(File directory);

    /**
     * 处理目录修改事件
     * @param directory 被修改的目录
     */
    abstract void handleDirectoryModify(File directory);

    /**
     * 处理文件创建事件
     * @param file 被创建的文件
     */
    abstract void handleFileCreate(File file);

    /**
     * 处理文件删除事件
     * @param file 被删除的文件
     */
    abstract void handleFileDelete(File file);

    /**
     * 处理文件修改事件
     * @param file 被修改的文件
     */
    abstract void handleFileChange(File file);




    // 临时占位用的空实现（避免null值）
    private static class DummyWatchKey implements WatchKey {
        @Override public boolean isValid() { return false; }
        @Override public List<WatchEvent<?>> pollEvents() { return List.of(); }
        @Override public boolean reset() { return false; }
        @Override public void cancel() {}
        @Override public Watchable watchable() { return null; }
    }
}
