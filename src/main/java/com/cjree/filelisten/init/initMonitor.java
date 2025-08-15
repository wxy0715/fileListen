package com.cjree.filelisten.init;

import com.cjree.filelisten.service.FileMonitorService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class initMonitor implements ApplicationRunner {
    @Resource
    private FileMonitorService fileMonitorService;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        fileMonitorService.startMonitoring();
    }
}
