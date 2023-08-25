package com.youlai.mall.oms.converter;

import com.youlai.mall.oms.model.entity.OmsOrder;
import com.youlai.mall.oms.model.form.OrderSubmitForm;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


/**
 * 订单对象转化器
 *
 * @author haoxr
 * @since 2.0.0
 */
@Mapper(componentModel = "spring")
public interface OrderConverter {

    @Mappings({
            @Mapping(target = "orderSn", source = "orderToken"),
            @Mapping(target = "totalQuantity", expression = "java(orderSubmitForm.getOrderItems().stream().map(OrderItemDTO::getCount).reduce(0, Integer::sum))"),
            @Mapping(target = "totalAmount", expression = "java(orderSubmitForm.getOrderItems().stream().map(item -> item.getPrice() * item.getCount()).reduce(0L, Long::sum))"),
    })
    OmsOrder form2Entity(OrderSubmitForm orderSubmitForm);
}