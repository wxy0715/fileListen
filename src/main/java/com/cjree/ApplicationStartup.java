package com.cjree;

import com.cjree.core.common.config.SpringContainer;
import com.cjree.logListener.LoadLogDirectoryRunable;
import com.cjree.logListener.db.LogContentQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * 初始化事件类
 */
@Slf4j
public class ApplicationStartup implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        try {
            //启动日志目录监控
            new Thread(new LoadLogDirectoryRunable()).start();
            Thread.sleep(1000);
            SpringContainer.getBeanOfType(LogContentQueueService.class).start();
        } catch (InterruptedException e) {
        }
    }
}
