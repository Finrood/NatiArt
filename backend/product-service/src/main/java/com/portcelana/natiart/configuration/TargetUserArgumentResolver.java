package com.portcelana.natiart.configuration;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.helper.TargetUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link TargetUser @TargetUser} parameters without relying on a SpEL expression over
 * the authentication principal.
 * <p>
 * Spring MVC resolves handler-method arguments <em>before</em> the method-security AOP interceptor
 * runs. With the previous {@code @AuthenticationPrincipal(expression = "username")} meta-annotation,
 * an anonymous request (principal is the string {@code anonymousUser}) crashed argument resolution
 * with {@code EL1008E: Property or field 'username' cannot be found on object of type 'java.lang.String'}
 * before {@code @PreAuthorize} could deny it - i.e. a 500 instead of a clean 401/403 on protected
 * endpoints such as the cart.
 */
public class TargetUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(TargetUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            // Anonymous callers must never hit the protected handler: leave the parameter null so the
            // @PreAuthorize gate (e.g. isFullyAuthenticated()) decides the outcome (401/403).
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthenticationResponseDto.Principal principal) {
            return principal.getUsername();
        }
        return authentication.getName();
    }
}
