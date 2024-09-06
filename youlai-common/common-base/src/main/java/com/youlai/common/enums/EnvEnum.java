package com.youlai.common.enums;

import com.youlai.common.base.IBaseEnum;
import lombok.Getter;

/**
 * 环境枚举
 *
 * @author Ray
 * @since 4.0.0
 */
@Getter
public enum EnvEnum implements IBaseEnum<String> {

    DEV("dev", "开发"),
    PROD("prod", "生产");

    private final String value;

    private final String label;

    EnvEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
