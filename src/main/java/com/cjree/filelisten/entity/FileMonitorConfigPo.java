package com.cjree.filelisten.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cjree.core.basic.base.AbstractCacheableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "FileOperationLog", description = "文件监听配置表")
@TableName(value = "file_operation_log", autoResultMap = true)
public class FileMonitorConfigPo extends AbstractCacheableModel {

    @Schema(description = "监听的文件/目录绝对路径")
    @TableField("monitor_path")
    private String monitorPath;

    @Schema(description = "路径类型：DIRECTORY(目录)、FILE(文件)")
    @TableField("path_type")
    private String pathType;

    @Schema(description = "是否递归监听：1-是，0-否（仅对目录有效）")
    @TableField("recursive")
    private Boolean recursive;

    @Schema(description = "是否启用：1-启用，0-禁用")
    @TableField("enabled")
    private Boolean enabled;

    @Schema(description = "包含的文件模式（多个用逗号分隔）")
    @TableField("include_patterns")
    private String includePatterns;

    @Schema(description = "排除的文件模式（多个用逗号分隔）")
    @TableField("exclude_patterns")
    private String excludePatterns;
}
