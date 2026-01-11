package com.oriole.wisepen.common.log.annotation;

import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import java.lang.annotation.*;

@Target({ ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    /** 模块名称 */
    String title() default "";

    /** 功能类别 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否保存请求参数 */
    boolean isSaveRequestData() default true;

    /** 是否保存响应参数 */
    boolean isSaveResponseData() default true;
}