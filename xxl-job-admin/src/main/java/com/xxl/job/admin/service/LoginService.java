package com.xxl.job.admin.service;

import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.CookieUtil;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.core.util.JacksonUtil;
import com.xxl.job.admin.dao.XxlJobUserDao;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;

/**
 * @author xuxueli 2019-05-04 22:13:264
 */
@Configuration
public class LoginService {

    /**
     * 登录用户的浏览器端存储登录信息的 Coookie 的 name
     */
    public static final String LOGIN_IDENTITY_KEY = "XXL_JOB_LOGIN_IDENTITY";

    @Resource
    private XxlJobUserDao xxlJobUserDao;


    /**
     * 先将 XxlJobUser 序列化为一个 Json 字符串，然后将该字符串编码为字节数组，然后将该字节数组转为一个 BigInteger 对象，该
     * BigInteger 对象的 16 进制字符串表示即为该 XxlJobUser 的 token。
     * <p>
     * 该 token 可以反向构建出原 Java 对象。
     *
     * @param xxlJobUser
     * @return
     */
    private String makeToken(XxlJobUser xxlJobUser) {
        String tokenJson = JacksonUtil.writeValueAsString(xxlJobUser);
        String tokenHex = new BigInteger(tokenJson.getBytes()).toString(16);
        return tokenHex;
    }

    private XxlJobUser parseToken(String tokenHex) {
        XxlJobUser xxlJobUser = null;
        if (tokenHex != null) {
            String tokenJson = new String(new BigInteger(tokenHex, 16).toByteArray());      // username_password(md5)
            xxlJobUser = JacksonUtil.readValue(tokenJson, XxlJobUser.class);
        }
        return xxlJobUser;
    }


    public ReturnT<String> login(HttpServletRequest request, HttpServletResponse response,
                                 String username, String password, boolean ifRemember) {

        // param
        if (username == null || username.trim().length() == 0 || password == null || password.trim().length() == 0) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_empty"));
        }

        // valid password
        XxlJobUser xxlJobUser = xxlJobUserDao.loadByUserName(username);
        if (xxlJobUser == null) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }
        // 数据库中用户的密码信息是使用 MD5 算法进行加密过的
        String passwordMd5 = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!passwordMd5.equals(xxlJobUser.getPassword())) {
            return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
        }

        String loginToken = makeToken(xxlJobUser);

        // do login
        // 将该 token 放入 Cookie 中，实现类似于 3 天免登录的功能
        CookieUtil.set(response, LOGIN_IDENTITY_KEY, loginToken, ifRemember);
        return ReturnT.SUCCESS;
    }

    /**
     * logout
     *
     * @param request
     * @param response
     */
    public ReturnT<String> logout(HttpServletRequest request, HttpServletResponse response) {
        CookieUtil.remove(request, response, LOGIN_IDENTITY_KEY);
        return ReturnT.SUCCESS;
    }

    /**
     * 通过请求中的 Cookie 信息，解析出用户信息。
     *
     * @param request
     * @return
     */
    public XxlJobUser ifLogin(HttpServletRequest request, HttpServletResponse response) {
        String cookieToken = CookieUtil.getValue(request, LOGIN_IDENTITY_KEY);
        if (cookieToken != null) {
            XxlJobUser cookieUser = null;
            try {
                cookieUser = parseToken(cookieToken);
            } catch (Exception e) {
                logout(request, response);
            }
            if (cookieUser != null) {
                // 即使在 Cookie 中反序列化，得到了 XxlJobUser 对象，还需要将它与数据库中的信息进行对比，保证密码正确才能返回
                XxlJobUser dbUser = xxlJobUserDao.loadByUserName(cookieUser.getUsername());
                if (dbUser != null) {
                    if (cookieUser.getPassword().equals(dbUser.getPassword())) {
                        return dbUser;
                    }
                }
            }
        }
        return null;
    }


}
