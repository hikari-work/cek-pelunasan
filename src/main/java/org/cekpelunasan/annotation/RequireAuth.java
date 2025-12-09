package org.cekpelunasan.annotation;

import org.cekpelunasan.entity.AccountOfficerRoles;

import java.lang.annotation.*;

/**
 * Annotation to enforce authorization checks on methods.
 * <p>
 * Methods annotated with this will be intercepted to verify if the user has the required roles.
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuth {

	/**
	 * Specifies the required account officer roles for accessing the method.
	 *
	 * @return An array of {@link AccountOfficerRoles} required to access the method.
	 */
	AccountOfficerRoles[] roles() default {};
}
