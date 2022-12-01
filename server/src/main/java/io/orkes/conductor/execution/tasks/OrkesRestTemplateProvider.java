/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.orkes.conductor.execution.tasks;

import com.netflix.conductor.tasks.http.HttpTask;
import com.netflix.conductor.tasks.http.providers.RestTemplateProvider;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Provider for a customized RestTemplateBuilder. This class provides a default {@link RestTemplateBuilder} which can be
 * configured or extended as needed.
 */
@Component
@Primary
public class OrkesRestTemplateProvider implements RestTemplateProvider {

    @AllArgsConstructor
    private static class RestTemplateHolder {
        RestTemplate restTemplate;
        HttpComponentsClientHttpRequestFactory requestFactory;
        int readTimeout;
        int connectTimeout;
    }

    private final ThreadLocal<RestTemplateHolder> threadLocalRestTemplate;
    private final int defaultReadTimeout;
    private final int defaultConnectTimeout;

    @Autowired
    public OrkesRestTemplateProvider(@Value("${conductor.tasks.http.readTimeout:250ms}") Duration readTimeout,
                                     @Value("${conductor.tasks.http.connectTimeout:250ms}") Duration connectTimeout,
                                     Optional<ClientHttpRequestInterceptor> interceptor) {
        this.defaultReadTimeout = (int) readTimeout.toMillis();
        this.defaultConnectTimeout = (int) connectTimeout.toMillis();
        this.threadLocalRestTemplate = ThreadLocal.withInitial(() ->
        {
            RestTemplate restTemplate = new RestTemplate();
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setReadTimeout(defaultReadTimeout);
            requestFactory.setConnectTimeout(defaultConnectTimeout);
            restTemplate.setRequestFactory(requestFactory);
            interceptor.ifPresent(it -> addInterceptor(restTemplate, it));
            return new RestTemplateHolder(restTemplate, requestFactory, defaultReadTimeout, defaultConnectTimeout);
        });

    }

    @Override
    public RestTemplate getRestTemplate(HttpTask.Input input) {
        RestTemplateHolder holder = threadLocalRestTemplate.get();
        RestTemplate restTemplate = holder.restTemplate;
        HttpComponentsClientHttpRequestFactory requestFactory = holder.requestFactory;

        int newReadTimeout = Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout);
        int newConnectTimeout = Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeout);
        if (newReadTimeout != holder.readTimeout || newConnectTimeout != holder.connectTimeout) {
            holder.readTimeout = newReadTimeout;
            holder.connectTimeout = newConnectTimeout;
            requestFactory.setReadTimeout(newReadTimeout);
            requestFactory.setConnectTimeout(newConnectTimeout);
        }

        return restTemplate;
    }

    private void addInterceptor(RestTemplate restTemplate, ClientHttpRequestInterceptor interceptor) {
        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (!interceptors.contains(interceptor)) {
            interceptors.add(interceptor);
        }
    }
}
