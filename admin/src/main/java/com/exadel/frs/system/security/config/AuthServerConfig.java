/*
 * Copyright (c) 2020 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.exadel.frs.system.security.config;

import static java.util.stream.Collectors.toList;
import com.exadel.frs.system.security.AuthenticationKeyGeneratorImpl;
import com.exadel.frs.system.security.CustomOAuth2Exception;
import com.exadel.frs.system.security.CustomUserDetailsService;
import com.exadel.frs.system.security.TokenServicesImpl;
import com.exadel.frs.system.security.client.Client;
import com.exadel.frs.system.security.client.ClientService;
import com.exadel.frs.system.security.client.OAuthClientProperties;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableAuthorizationServer
@RequiredArgsConstructor
public class AuthServerConfig extends AuthorizationServerConfigurerAdapter {

    @Qualifier("authenticationManagerBean")
    private final AuthenticationManager authenticationManager;
    private final ClientService clientService;
    private final CustomUserDetailsService userDetailsService;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final OAuthClientProperties authClientProperties;

    @Bean
    public JdbcTokenStore tokenStore() {
        JdbcTokenStore tokenStore = new JdbcTokenStore(dataSource);
        tokenStore.setAuthenticationKeyGenerator(new AuthenticationKeyGeneratorImpl());
        return tokenStore;
    }

    @Bean
    public DefaultTokenServices tokenServices() {
        TokenServicesImpl tokenServices = new TokenServicesImpl(tokenStore());
        tokenServices.setClientDetailsService(clientService);
        return tokenServices;
    }

    @Override
    public void configure(final AuthorizationServerSecurityConfigurer oauthServer) {
        oauthServer.tokenKeyAccess("permitAll()")
                   .checkTokenAccess("isAuthenticated()");
    }

    @Override
    @Transactional
    public void configure(final ClientDetailsServiceConfigurer clients) throws Exception {
        clients.withClientDetails(clientService);

        var appClients = authClientProperties.getClients().values()
                                             .stream()
                                             .map(it ->
                                                     new Client()
                                                             .setClientId(it.getClientId())
                                                             .setAuthorizedGrantTypes(String.join(",", it.getAuthorizedGrantTypes()))
                                                             .setAuthorities(String.join(",", it.getAuthorities()))
                                                             .setResourceIds(String.join(",", it.getResourceIds()))
                                                             .setScope(String.join(",", it.getClientScope()))
                                                             .setClientSecret(passwordEncoder.encode(it.getClientSecret()))
                                                             .setAccessTokenValidity(it.getAccessTokenValidity())
                                                             .setRefreshTokenValidity(it.getRefreshTokenValidity())
                                                             .setAutoApprove("*"))
                                             .collect(toList());
        clientService.saveAll(appClients);
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {
        endpoints
                .tokenStore(tokenStore())
                .tokenServices(tokenServices())
                .authenticationManager(authenticationManager)
                .userDetailsService(userDetailsService);

        endpoints.exceptionTranslator(exception -> {
            if (exception instanceof OAuth2Exception) {
                OAuth2Exception oAuth2Exception = (OAuth2Exception) exception;
                return ResponseEntity
                        .status(oAuth2Exception.getHttpErrorCode())
                        .body(new CustomOAuth2Exception(oAuth2Exception.getMessage()));
            } else {
                throw exception;
            }
        });
    }
}