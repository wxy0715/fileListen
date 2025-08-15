package com.cjree.filelisten.service;

import com.cjree.core.basic.base.AbstractService;
import com.cjree.filelisten.entity.FileMonitorConfigPo;
import com.cjree.filelisten.mapper.FileMonitorConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FileMonitorConfigServiceImpl extends AbstractService<FileMonitorConfigPo, FileMonitorConfigMapper> implements FileMonitorConfigService {

}
