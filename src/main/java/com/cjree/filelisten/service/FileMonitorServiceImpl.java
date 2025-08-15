package com.cjree.filelisten.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.SimpleQuery;
import com.cjree.core.common.utils.CoreObjectUtil;
import com.cjree.filelisten.entity.FileMonitorConfigPo;
import com.cjree.filelisten.enums.OperationTypeEnum;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileMonitorServiceImpl extends FileMonitorServiceBase implements FileMonitorService {

    // 事件处理线程池
    private final ExecutorService eventExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2);

    // 数据库写入线程池
    private final ExecutorService dbWriterExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void startMonitoring() {
        try {
            // 初始化WatchService
            watchService = FileSystems.getDefault().newWatchService();
            // 从数据库加载启用的监听配置
            loadAndStartMonitors();
            // 启动监听线程
            startWatchThread();
            log.info("文件监听启动成功");
        } catch (IOException e) {
            log.error("启动监听服务失败", e);
            throw new RuntimeException("启动监听服务失败", e);
        }
    }

    /**
     * 启动WatchService监听线程
     */
    private void startWatchThread() {
        Thread watchThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // 获取事件（阻塞操作）,尝试获取一个表示文件系统变化的WatchKey,watchService内部维护了LinkedBlockingDeque
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key == null) {
                        continue;
                    }

                    // 获取关联的路径
                    Path dir = (Path) key.watchable();
                    if (dir == null) {
                        continue;
                    }

                    // 处理所有事件
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        // 忽略溢出事件
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        // 获取事件关联的文件/目录
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path fileName = pathEvent.context();
                        Path fullPath = dir.resolve(fileName);

                        // 提交事件到线程池处理
                        eventExecutor.submit(() -> handleEvent(dir, fullPath, kind));
                    }

                    // 重置key，以便继续接收事件
                    boolean valid = key.reset();
                    if (!valid) {
                        // key无效，说明目录已被删除，移除监听
                        watchKeys.remove(dir);
                        log.info("目录已被已删除, 移除监听: {}", dir);
                    }
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    log.error("Error in watch thread", e);
                }
            }
        }, "FileWatchThread");
        // 设置为守护线程，主程序退出时自动终止
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * 处理监听到的事件
     */
    private void handleEvent(Path dir, Path fullPath, WatchEvent.Kind<?> kind) {
        try {
            // 目录创建事件：如果需要递归监听，注册新目录
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                if (Files.isDirectory(fullPath)) {
                    registerDirectory(fullPath, true);
                    handleDirectoryCreate(fullPath.toFile());
                } else {
                    // 文件创建事件
                    if (isFileIncluded(fullPath)) {
                        handleFileCreate(fullPath.toFile());
                    }
                }
            }
            // 文件/目录删除事件
            else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                if (Files.isDirectory(fullPath)) {
                    handleDirectoryDelete(fullPath.toFile());
                    // 从监听中移除目录
                    WatchKey key = watchKeys.remove(fullPath);
                    if (key != null) {
                        key.cancel();
                    }
                } else {
                    if (isFileIncluded(fullPath)) {
                        handleFileDelete(fullPath.toFile());
                        // 清理缓存
                        fileLastModified.remove(fullPath);
                        filePositions.remove(fullPath);
                    }
                }
            }
            // 文件修改事件
            else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                if (Files.isDirectory(fullPath)) {
                    // 移除旧目录的监听
                    removeMonitorDirectory(dir.toString());
                    // 新增新目录监听
                    registerDirectory(fullPath, true);
                    handleDirectoryModify(fullPath.toFile());
                } else {
                    // 文件创建事件
                    if (isFileIncluded(fullPath)) {
                        handleFileChange(fullPath.toFile());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling event for path: {}", fullPath, e);
        }
    }

    @PreDestroy
    @Override
    public void stopMonitoring() {
        try {
            // 关闭线程池
            eventExecutor.shutdown();
            dbWriterExecutor.shutdown();
            eventExecutor.awaitTermination(5, TimeUnit.SECONDS);
            dbWriterExecutor.awaitTermination(5, TimeUnit.SECONDS);

            // 关闭WatchService
            if (watchService != null) {
                watchService.close();
            }

            // 清理资源
            watchKeys.clear();
            fileLastModified.clear();
            filePositions.clear();

            log.info("NIO file monitor service stopped successfully");
        } catch (Exception e) {
            log.error("Failed to stop NIO file monitor service", e);
        }
    }

    @Override
    public void addMonitorDirectory(String directory) {
        addMonitorDirectory(Paths.get(directory), true);
    }

    @Override
    public void removeMonitorDirectory(String directory) {
        Path dir = Paths.get(directory);
        // 取消所有子目录的监听
        Set<Path> toRemove = watchKeys.keySet().stream()
                .filter(path -> isSubdirectory(dir, path))
                .collect(Collectors.toSet());

        toRemove.forEach(path -> {
            WatchKey key = watchKeys.remove(path);
            if (key != null) {
                key.cancel();
            }
        });
        // 更新数据库配置
        List<Long> ids = SimpleQuery.list(
                Wrappers.lambdaQuery(FileMonitorConfigPo.class)
                        .likeRight(FileMonitorConfigPo::getMonitorPath, directory),
                FileMonitorConfigPo::getId
        );
        fileMonitorConfigService.logicDeleteBatch(ids);
        log.info("移除监听目录及子目录: {}", directory);
    }

    public void handleDirectoryCreate(File directory) {
        log.info("目录创建: {}", directory.getAbsolutePath());
        saveOperationLog(directory.getAbsolutePath(), OperationTypeEnum.DIRECTORY_CREATE.getCode(), null, getFileOperator());
    }

    public void handleDirectoryDelete(File directory) {
        log.info("目录删除: {}", directory.getAbsolutePath());
        saveOperationLog(directory.getAbsolutePath(),  OperationTypeEnum.DIRECTORY_DELETE.getCode(), null, getFileOperator());
    }

    public void handleDirectoryModify(File directory) {
        log.info("目录修改: {}", directory.getAbsolutePath());
        saveOperationLog(directory.getAbsolutePath(),  OperationTypeEnum.DIRECTORY_MODIFY.getCode(), null, getFileOperator());
    }

    public void handleFileCreate(File file) {
        log.info("文件创建: {}", file.getAbsolutePath());
        Path path = file.toPath();
        filePositions.put(path, 0L);
        fileLastModified.put(path, getLastModifiedTime(path));
        // 异步记录到数据库
        saveOperationLog(file.getAbsolutePath(), "CREATE", null, getFileOperator());
    }

    public void handleFileDelete(File file) {
        log.info("删除文件: {}", file.getAbsolutePath());
        saveOperationLog(file.getAbsolutePath(), "DELETE", null, getFileOperator());
    }

    public void handleFileChange(File file) {
        Path path = file.toPath();
        long lastModified = getLastModifiedTime(path);

        // 去重：检查是否真的修改
        if (fileLastModified.containsKey(path) &&
                fileLastModified.get(path).equals(lastModified)) {
            return;
        }

        fileLastModified.put(path, lastModified);

        try {
            long fileLength = Files.size(path);
            long position = filePositions.getOrDefault(path, 0L);

            // 文件被截断，从头开始读
            if (position > fileLength) {
                position = 0L;
            }

            // 读取新增内容
            if (position < fileLength) {
                String newContent = readFileFromPosition(path, position, fileLength);
                if (CoreObjectUtil.isNotEmpty(newContent)) {
                    saveOperationLog(file.getAbsolutePath(), "MODIFY", newContent, getFileOperator());
                }
                filePositions.put(path, fileLength);
            }
        } catch (Exception e) {
            log.error("Error handling file change: {}", file.getAbsolutePath(), e);
        }
    }


    /**
     * 从指定位置读取文件内容（使用NIO提升性能）
     */
    private String readFileFromPosition(Path path, long start, long end) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            // 使用内存映射文件提升大文件读取性能
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, start, end - start);
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

}
