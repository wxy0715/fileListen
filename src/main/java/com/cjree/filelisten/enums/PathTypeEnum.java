package com.cjree.filelisten.enums;

/**
 * 路径类型枚举
 */
public enum PathTypeEnum {
    FILE("FILE", "文件"),
    
    DIRECTORY("DIRECTORY", "目录");

    private final String code;
    private final String description;

    PathTypeEnum(String code, String description) {
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