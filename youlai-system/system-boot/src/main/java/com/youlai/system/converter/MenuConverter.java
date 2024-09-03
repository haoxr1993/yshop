package com.youlai.system.converter;

import com.youlai.system.model.entity.Menu;
import com.youlai.system.model.form.MenuForm;
import com.youlai.system.model.vo.MenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 菜单对象转换器
 *
 * @author Ray
 * @since 2022/7/29
 */
@Mapper(componentModel = "spring")
public interface MenuConverter {

    MenuVO entity2Vo(Menu entity);

    @Mapping(target = "params", ignore = true)
    MenuForm convertToForm(Menu entity);

    @Mapping(target = "params", ignore = true)
    Menu toEntity(MenuForm menuForm);
}