CREATE TABLE `file_operation_log` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `file_path` varchar(255) NOT NULL COMMENT '文件/目录绝对路径',
    `operation_type` varchar(20) NOT NULL COMMENT '操作类型：CREATE(文件创建)、DELETE(文件删除)、MODIFY(文件修改)、DIRECTORY_CREATE(目录创建)、DIRECTORY_DELETE(目录删除)',
    `content` text COMMENT '操作内容（文件新增的行或修改内容）',
    `operator` varchar(50) DEFAULT NULL COMMENT '操作人（文件所属用户或系统用户）',
    `operation_time` datetime NOT NULL COMMENT '操作发生时间',
    `creator` bigint DEFAULT NULL COMMENT '创建者',
    `updator` bigint DEFAULT NULL COMMENT '更新者',
    `remark` varchar(128) DEFAULT NULL COMMENT '备注',
    `available` varchar(3) default 'YES' NOT NULL COMMENT '是否可用',
    `create_date` datetime default current_timestamp COMMENT '创建时间',
    `update_date` datetime default current_timestamp on update current_timestamp COMMENT '更新时间',
    `version_date` datetime default current_timestamp on update current_timestamp COMMENT '版本时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_file_path` (`file_path`) COMMENT '按文件路径查询索引',
    KEY `idx_operation_time` (`operation_time`) COMMENT '按操作时间查询索引',
    KEY `idx_operation_type` (`operation_type`) COMMENT '按操作类型查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件操作日志表，记录文件/目录的创建、删除、修改等操作';

CREATE TABLE `file_monitor_config` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `monitor_path` varchar(255) NOT NULL COMMENT '监听的文件/目录绝对路径',
    `path_type` varchar(20) NOT NULL COMMENT '路径类型：DIRECTORY(目录)、FILE(文件)',
    `recursive` tinyint NOT NULL DEFAULT '1' COMMENT '是否递归监听：1-是，0-否（仅对目录有效）',
    `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1-启用，0-禁用',
    `include_patterns` varchar(500) DEFAULT NULL COMMENT '包含的文件模式（多个用逗号分隔）',
    `exclude_patterns` varchar(500) DEFAULT NULL COMMENT '排除的文件模式（多个用逗号分隔）',
    `creator` bigint DEFAULT NULL COMMENT '创建者',
    `updator` bigint DEFAULT NULL COMMENT '更新者',
    `remark` varchar(128) DEFAULT NULL COMMENT '备注',
    `available` varchar(3) default 'YES' NOT NULL COMMENT '是否可用',
    `create_date` datetime default current_timestamp COMMENT '创建时间',
    `update_date` datetime default current_timestamp on update current_timestamp COMMENT '更新时间',
    `version_date` datetime default current_timestamp on update current_timestamp COMMENT '版本时间戳',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_monitor_path` (`monitor_path`) COMMENT '监听路径唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件监听配置表';
