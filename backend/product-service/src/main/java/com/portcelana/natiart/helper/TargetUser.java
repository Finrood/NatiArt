package com.portcelana.natiart.helper;

import java.lang.annotation.*;

/**
 * Marks a handler-method parameter that should be bound to the authenticated user's username.
 * <p>
 * Intentionally a plain marker annotation (NOT meta-annotated with
 * {@code @AuthenticationPrincipal(expression = "username")}): that SpEL expression throws
 * {@code EL1008E} when it is evaluated against the anonymous principal (the string
 * {@code anonymousUser}) during handler-argument resolution, turning public storefront endpoints
 * into 500s for anonymous visitors. {@link com.portcelana.natiart.configuration.TargetUserArgumentResolver}
 * resolves this annotation and returns {@code null} for anonymous/absent authentication so the
 * {@code @PreAuthorize} gates - or the public endpoint behaviour - decide the outcome.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TargetUser {}
