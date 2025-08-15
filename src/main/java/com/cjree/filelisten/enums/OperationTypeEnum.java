package com.cjree.filelisten.enums;

/**
 * 操作类型枚举
 */
public enum OperationTypeEnum {
    CREATE("CREATE", "文件创建"),
    MODIFY("MODIFY", "文件修改"),
    DELETE("DELETE", "文件删除"),
    DIRECTORY_CREATE("DIRECTORY_CREATE", "目录创建"),
    DIRECTORY_DELETE("DIRECTORY_DELETE", "目录删除"),
    DIRECTORY_MODIFY("DIRECTORY_MODIFY", "目录修改");
    private final String code;
    private final String description;

    OperationTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return code;
    }
}