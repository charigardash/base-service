package com.common.base.ratelimit.filter;

import com.common.base.ratelimit.enums.RateLimitType;
import com.common.base.ratelimit.service.SecurityService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

//implement this filter class in importing repo
@Component
public class RateLimitingFilter implements Filter {

    private final SecurityService securityService;

    @Value("${app.rate-limit.window-minutes:15}")
    private int windowMinutes;

    public RateLimitingFilter(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String clientIpAddress = getClientIpAddress(httpRequest);
        String requestPath = httpRequest.getRequestURI();

        // Determine rate limit type based on request path
        RateLimitType type = getRateLimitTypeFromRequestedPath(requestPath);

        if(securityService.isRateLimited(clientIpAddress+":"+requestPath, type)){
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.getWriter().write("Too many requests. Please try again later.");
            httpResponse.setContentType("application/json");
            String errorResponse = String.format(
                    "{\"success\": false, \"message\": \"Rate limit exceeded. Try again in %d minutes.\"}",
                    windowMinutes
            );
            httpResponse.getWriter().write(errorResponse);
            return;
        }
        chain.doFilter(request, response);
    }

    private String getClientIpAddress(HttpServletRequest request){
        String xfHeader = request.getHeader("X-Forwarded-For");
        if(xfHeader != null){
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private RateLimitType getRateLimitTypeFromRequestedPath(String requestPath){
        if(requestPath.contains("/communication/auth/otp/send")){
            return RateLimitType.OTP_EMAIL;
        } else if (requestPath.contains("/login")){
            return RateLimitType.LOGIN_ATTEMPT;
        }else {
            return RateLimitType.API_REQUEST;
        }
    }
}
