package com.cjree;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StopWatch;

/**
 * @author wxy
 */
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@EnableScheduling
@EnableAsync
@Slf4j
public class Application {
	public static void main(String[] args) {
		StopWatch stopWatch = new StopWatch("file");
		stopWatch.start();
		SpringApplication.run(Application.class, args);
		stopWatch.stop();
		log.info("-----------------流程服务启动完成,耗时：{}秒---------------------", stopWatch.getTotalTimeSeconds());
	}
}
