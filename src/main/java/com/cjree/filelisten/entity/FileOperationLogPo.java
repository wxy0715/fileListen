package com.cjree.filelisten.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cjree.core.basic.base.AbstractCacheableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "FileOperationLog", description = "文件操作日志表")
@TableName(value = "file_operation_log", autoResultMap = true)
public class FileOperationLogPo extends AbstractCacheableModel {

    @Schema(description = "文件/目录绝对路径")
    @TableField("file_path")
    private String filePath;

    @Schema(description = "操作类型：CREATE(文件创建)、DELETE(文件删除)、MODIFY(文件修改)、DIRECTORY_CREATE(目录创建)、DIRECTORY_DELETE(目录删除)")
    @TableField("operation_type")
    private String operationType;

    @Schema(description = "操作内容（文件新增的行或修改内容）")
    @TableField("content")
    private String content;

    @Schema(description = "操作人（文件所属用户或系统用户）")
    @TableField("operator")
    private String operator;

    @Schema(description = "操作发生时间")
    @TableField("operation_time")
    private Date operationTime;
}
