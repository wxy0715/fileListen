package com.cjree.filelisten.service;

public interface FileMonitorService {
    /**
     * 启动文件监听服务
     */
    void startMonitoring();

    /**
     * 停止文件监听服务
     */
    void stopMonitoring();

    /**
     * 添加监听目录
     * @param directory 要监听的目录
     */
    void addMonitorDirectory(String directory);

    /**
     * 移除监听目录
     * @param directory 要移除的目录
     */
    void removeMonitorDirectory(String directory);

}
