package com.xxl.job.admin.controller.interceptor;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.service.LoginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 权限拦截
 *
 * @author xuxueli 2015-12-12 18:09:04
 */
@Component
public class PermissionInterceptor extends HandlerInterceptorAdapter {

    private static Logger logger = LoggerFactory.getLogger(PermissionInterceptor.class);

    @Resource
    private LoginService loginService;

    /**
     * 在 HandlerMapping 确定了 handler 对象之后，但是 HandlerAdapter 还没有调用该 handler 时调用
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        logger.debug("checking permission of request url: {}", request.getRequestURI());

        // handler 可能是 ResourceHttpRequestHandler 类型或者 HandlerMethod 类型（不知道是否还有其它的类型），
        // 前者用于静态资源处理，由于我们对此拦截器进行配置时，取消了对 static 静态资源的拦截，因此这里应该只会出现
        // HandlerMethod 类型的 handler，

        if (!(handler instanceof HandlerMethod)) {
            logger.debug("no handler method found, no login required");
            return super.preHandle(request, response, handler);
        }

        // check whether need login
        boolean needLogin = true;
        boolean needAdminuser = false;
        HandlerMethod method = (HandlerMethod) handler;
        PermissionLimit permission = method.getMethodAnnotation(PermissionLimit.class);
        if (permission != null) {
            needLogin = permission.limit();
            needAdminuser = permission.adminuser();
        }
        // 默认（没有配置 @PermissionLimit 注解的 handler method）需要登录，但是不需要管理员权限

        // 需要登录的情况
        if (needLogin) {
            XxlJobUser loginUser = loginService.ifLogin(request, response);
            if (loginUser == null) {
                // 没有登录，则重定向到登录页面
                logger.debug("needs login, redirect to login page");
                response.setStatus(302);
                response.setHeader("location", request.getContextPath() + "/toLogin");
                return false;
            }
            if (needAdminuser && loginUser.getRole() != 1) {
                // 如果以普通用户身份登录，但是该资源需要管理员权限，则抛出异常
                logger.debug("needs admin user login");
                throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
            }
            request.setAttribute(LoginService.LOGIN_IDENTITY_KEY, loginUser);
        }
        logger.debug(needLogin ? "needs login but already login" : "no login required");
        // 不需要登录的情况
        return super.preHandle(request, response, handler);
    }

}
