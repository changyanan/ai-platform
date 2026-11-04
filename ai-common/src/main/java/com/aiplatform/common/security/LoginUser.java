package com.aiplatform.common.security;

/**
 * 认证主体契约。各业务模块只依赖该接口即可取得当前用户，不反向依赖启动模块。
 */
public interface LoginUser {

    Long getId();

    String getUsername();

    String getRole();

    boolean isMustChangePassword();
}
