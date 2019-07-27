package org.aoju.bus.validate.annotation;

import org.aoju.bus.validate.Builder;
import org.aoju.bus.validate.strategy.NotBlankStrategy;

import java.lang.annotation.*;

/**
 * 字符串不为空，不为null校验
 *
 * @author aoju.org
 * @version 3.0.1
 * @group 839128
 * @since JDK 1.8
 */
@Documented
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Complex(value = Builder._NOT_BLANK, clazz = NotBlankStrategy.class)
public @interface NotBlank {

    /**
     * 默认使用的异常码
     */
    String errcode() default Builder.DEFAULT_ERRCODE;

    /**
     * 默认使用的异常信息
     */
    String errmsg() default "${field}字符串不能为空";

    /**
     * 校验器组
     */
    String[] group() default {};

    /**
     * 被校验字段名称
     */
    String field() default Builder.DEFAULT_FIELD;

}