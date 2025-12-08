package org.cekpelunasan.annotation;

import org.cekpelunasan.entity.AccountOfficerRoles;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuth {

	AccountOfficerRoles[] roles() default {};
}
