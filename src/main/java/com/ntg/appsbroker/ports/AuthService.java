package com.ntg.appsbroker.ports;

import com.ntg.appsbroker.infrastructure.auth.LoginResult;

/**
 * Port: Authentication service interface.
 */
public interface AuthService {
    LoginResult login(String username, String password, String companyname);
}

