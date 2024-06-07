package com.youlai.system.model.query;


import com.youlai.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型分页查询对象
 *
 * @author Ray
 * @since 2024/6/7
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典类型分页查询对象")
@Data
public class DictTypePageQuery extends BasePageQuery {

    @Schema(description = "关键字(类型名称/类型编码)")
    private String keywords;

}
