package com.cjree.filelisten.service;

import com.cjree.core.basic.base.AbstractService;
import com.cjree.filelisten.entity.FileOperationLogPo;
import com.cjree.filelisten.mapper.FileOperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FileOperationLogServiceImpl extends AbstractService<FileOperationLogPo, FileOperationLogMapper> implements FileOperationLogService {

}
