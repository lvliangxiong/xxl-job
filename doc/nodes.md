## FreeMarker

[英文文档](https://freemarker.apache.org/docs/index.html)

[中文文档](http://freemarker.foofun.cn/index.html)

- 形如 `${...}` 的表达式会被 Freemarker 解析并插入真实的值

- 以 # 开头的标签是 FTL[^1] 标签（ FTL 指令[^2]）

  ```
  <#if animals.python.price == 0>
    Pythons are free today!
  </#if>
  ```

- 以 @ 开头的标签是用户自定义的标签

- 注释的语法如下：

  ```
  <#-- comments -->
  ```

  FreeMarker 的注释，不会发送给浏览器，因此发送请求的用户无法通过查看网页的源码看到 FreeMarker 的注释，这和 HTML 注释不一样。

FreeMarker 只会解析上述几点内容，模板文件中的其它内容均会被 FreeMarker 认为是静态内容。

## MD5

> MD5信息摘要算法（MD5 Message-Digest Algorithm），一种被广泛使用的密码散列函数，可以产生出一个128位（16字节）的散列值（hash value），用于确保信息传输完整一致。

128 个 bit 位就是 16 个字节，如果使用 16 进制进行表示，那就是**一个 32 位的 16 进制数**。




## XXL 解析

### 登录验证

#### 请求的权限验证

使用了自定义的注解 @PermissionLimit 和拦截器 PermissionInterceptor 对所有的请求进行拦截。

```java
registry.addInterceptor(permissionInterceptor).addPathPatterns("/**");
```

拦截器的配置 `/**` 可以拦截所有的请求，包括 Ajax 请求和对静态资源的请求。

由于对静态资源的访问，一般是不需要权限的，所以作者在拦截器的 preHandler 方法进行了判断：

```java
if (!(handler instanceof HandlerMethod)) {
    logger.debug("no handler method found, no login required");
    return super.preHandle(request, response, handler);
}
```

表示只对 handler 为 HandlerMethod 类型的请求进行后续的权限验证处理。而其它的类型[^3]，如 ResourceHttpRequestHandler 的 handler ，则不进行后续的权限验证。

其实权限验证也比较简单，规则如下：

1. 默认情况下，所有的 HandlerMethod 处理的请求都需要登录（不配置 @PermissionLimit 注解情况下）

2. 如果不需要登录，那么需要显式在该 HandlerMethod 上加上自定义的注解：

   ```java
   @PermissionLimit(limit = false)
   ```

3. ​	如果要求更高，需要管理员身份的用户登录才能让该 HanlderMethod 进行处理，那么需要配置注解：

   ```
   @PermissionLimit(adminuser = true)
   ```

核心代码（com.xxl.job.admin.controller.interceptor.PermissionInterceptor#preHandle）如下：

```java
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
        response.setStatus(302);
        response.setHeader("location", request.getContextPath() + "/toLogin");
        return false;
    }
    if (needAdminuser && loginUser.getRole() != 1) {
        // 如果以普通用户身份登录，但是该资源需要管理员权限，则抛出异常
        throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
    }
    request.setAttribute(LoginService.LOGIN_IDENTITY_KEY, loginUser);
}
// 不需要登录的情况
return super.preHandle(request, response, handler);
```

这个拦截的过程中，有用到 com.xxl.job.admin.service.LoginService#ifLogin 方法，该方法是从 Cookie 信息中判断该请求是否已登录。如果确实已登录，那么 Cookie 中会包含有该登录用户的信息，所以该方法的返回值就是代表登录用户的 XxlJobUser 对象，如果没有登录，那么返回值为 null。

可以看到，如果请求对应的 HandlerMethod 需要登录，而 Cookie 中没有找到该请求用户的信息，那么就会进行重定向，要求用户登录。

#### 密码加密

在 MySQL 数据库中，xxl_job_user 表的用户密码存储的是使用了 md5 的加密算法进行加密后的密码。

具体的加密过程可以从 com.xxl.job.admin.controller.UserController#add 推测出来，核心代码：

```java
xxlJobUser.setPassword(xxlJobUser.getPassword().trim());
xxlJobUser.setPassword(DigestUtils.md5DigestAsHex(xxlJobUser.getPassword().getBytes()));
```

其实就是先去除密码的首尾空格，然后 check 密码的长度是否在 [4, 20] 之间（上述代码没有截取这一部分），然后对密码进行 md5 加密。

例如密码 `123456`（字符串）加密过程如下：

1. 使用平台的默认编码将密码，编码成字节数组

   ```
   [49, 50, 51, 52, 53, 54]
   ```

2. 使用 Spring Framework 的工具类，对该字节数组进行 md5 加密，生成一个 32 位的 16 进制数的字符串表示

   ```
   e10adc3949ba59abbe56e057f20f883e
   ```

   最终数据库中保存的密码就是这个长度为 32 的字符串，它其实是一个 32 位的 16 进制数。

这个加密过程，其实从登录验证的过程 com.xxl.job.admin.service.LoginService#login 也可以看出来：

```java
// password 是表单中提交的密码
String passwordMd5 = DigestUtils.md5DigestAsHex(password.getBytes());
// xxlJobUser 是根据 username 从数据库中查询出来的用户信息
if (!passwordMd5.equals(xxlJobUser.getPassword())) {
    return new ReturnT<String>(500, I18nUtil.getString("login_param_unvalid"));
}
```

#### 用户登录过程

xxl-job 的登录请求，是通过登录页面的 js 脚本发起的 Ajax 请求。

核心代码（login.1.js）：

```js
submitHandler : function(form) {
    // 使用 Ajax 提交 login 表单，登录成功则将 Cookie 保存在浏览器中，然后使用 js 改变 location
    // 登录失败，则使用 js 弹出提示框，避免重新请求
    $.post(base_url + "/login", $("#loginForm").serialize(), function(data, status) {
        if (data.code == "200") {
            layer.msg( I18n.login_success );
            // 设置一定延迟后跳转到首页
            setTimeout(function(){
                window.location.href = base_url;
            }, 500);
        } else {
            layer.open({
                title: I18n.system_tips,
                btn: [ I18n.system_ok ],
                content: (data.msg || I18n.login_fail ),
                icon: '2'
            });
        }
    });
}
```

如果发起的 Ajax 请求，在服务器端验证失败，那么浏览器会根据返回的错误信息，进行各种类型的提示。如果登录成功，那么浏览器会延迟一个极短时间后（500 ms）自动修改页面的 location，实现首页跳转。

该登录请求发送的地址是 `/login`，对应的 handler method 是：

```java
@RequestMapping(value = "login", method = RequestMethod.POST)
@ResponseBody
@PermissionLimit(limit = false)
public ReturnT<String> loginDo(HttpServletRequest request, HttpServletResponse response,
                               String userName, String password, String ifRemember) {
    boolean ifRem = (ifRemember != null && ifRemember.trim().length() > 0 && "on".equals(ifRemember)) ? true : false;
    return loginService.login(request, response, userName, password, ifRem);
}
```

该方法的逻辑很简单，全部委托给了 service 层的 com.xxl.job.admin.service.LoginService#login 方法进行处理。

该业务方法也比较简单，首先对表单提交的数据进行了校验，如果不符合特定规则，直接返回错误信息。然后根据 username 去数据库中查询得到用户信息，比对密码（对表单提交的密码进行 md5 加密后）。如果校验成功，会将数据库中查询出来的 XxlJobUser 对象，进行一定处理（序列化）后，保存在 Cookie 中发送给客户端，保证后续请求不用再次登录。

```java
String loginToken = makeToken(xxlJobUser);

// do login
// 将该 token 放入 Cookie 中，实现类似于 3 天免登录的功能
CookieUtil.set(response, LOGIN_IDENTITY_KEY, loginToken, ifRemember);
return ReturnT.SUCCESS;
```

Cookie 的值是使用 com.xxl.job.admin.service.LoginService#makeToken 方法对 XxlJobUser 对象进行处理后得到的一个字符串。其底层其实是 com.fasterxml.jackson.databind.ObjectMapper#writeValueAsString 对 JXxlJobUserava 对象进行的序列化。

核心代码（com.xxl.job.admin.service.LoginService#makeToken）：

```java
private String makeToken(XxlJobUser xxlJobUser) {
    String tokenJson = JacksonUtil.writeValueAsString(xxlJobUser);
    String tokenHex = new BigInteger(tokenJson.getBytes()).toString(16);
    return tokenHex;
}
```

如果要从该值还原出 XxlJobUser 对象，则需要反向进行处理。所以在验证 Cookie 登录信息时，就是这样做的：

```java
private XxlJobUser parseToken(String tokenHex) {
    XxlJobUser xxlJobUser = null;
    if (tokenHex != null) {
        String tokenJson = new String(new BigInteger(tokenHex, 16).toByteArray());      // username_password(md5)
        xxlJobUser = JacksonUtil.readValue(tokenJson, XxlJobUser.class);
    }
    return xxlJobUser;
}
```

如果在登录时，使用了记住密码，那么该 Cookie 的有效期就是 Integer.MAX_VALUE 秒，如果没有，那么该 Cookie 的有效期就是一次会话（仅仅保存在内存中），关闭浏览器就结束了。














[^1]: FreeMarker Template Language
[^2]: FTL 标签和指令（directive）的关系，类似于 HTML 标签和 HTML 元素的关系一样
[^3]: 笔者也不知道除了 HandlerMethod 和 ResouceHttpRequestHandler（用于处理对静态资源的处理）以外，还有什么类型的，可能还有其它类型，这个需要对 SpringMVC 的底层作更深层次的了解

