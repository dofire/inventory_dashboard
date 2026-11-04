package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.validation.executable.ExecutableValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class TracingWebConfiguration implements WebMvcConfigurer {
    private final ObservationRegistry registry;

    public TracingWebConfiguration(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Bean
    public LocalValidatorFactoryBean tracingValidator() {
        return new LocalValidatorFactoryBean() {
            @Override
            public void validate(Object target, Errors errors, Object... hints) {
                Observation.createNotStarted("request.validation", registry)
                        .observe(() -> super.validate(target, errors, hints));
            }

            @Override
            public void validate(Object target, Errors errors) {
                validate(target, errors, new Object[0]);
            }

            @Override
            public ExecutableValidator forExecutables() {
                ExecutableValidator delegate = super.forExecutables();
                return (ExecutableValidator) Proxy.newProxyInstance(ExecutableValidator.class.getClassLoader(),
                        new Class<?>[]{ExecutableValidator.class}, (proxy, method, args) ->
                                invokeObserved("request.validation", delegate, method, args));
            }
        };
    }

    @Override
    public Validator getValidator() {
        return tracingValidator();
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(this::traceMessageConverters);
    }

    void traceMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Preserve generic/smart converter capabilities and existing Jackson configuration.
        // The write span measures actual response serialization, including output writes.
        converters.replaceAll(converter -> {
            List<Class<?>> interfaces = new ArrayList<>();
            interfaces.add(HttpMessageConverter.class);
            if (converter instanceof GenericHttpMessageConverter<?>) interfaces.add(GenericHttpMessageConverter.class);
            if (converter instanceof SmartHttpMessageConverter<?>) interfaces.add(SmartHttpMessageConverter.class);
            return (HttpMessageConverter<?>) Proxy.newProxyInstance(HttpMessageConverter.class.getClassLoader(),
                    interfaces.toArray(Class<?>[]::new), (proxy, method, args) -> {
                        if (method.getName().equals("write")) {
                            return invokeObserved("response.mapping", converter, method, args);
                        }
                        return invoke(converter, method, args);
                    });
        });
    }

    private Object invokeObserved(String name, Object target, Method method, Object[] args) throws Throwable {
        Observation observation = Observation.start(name, registry);
        try (Observation.Scope scope = observation.openScope()) {
            return invoke(target, method, args);
        } catch (Throwable error) {
            observation.lowCardinalityKeyValue("error.type", error.getClass().getSimpleName());
            throw error;
        } finally {
            observation.stop();
        }
    }

    private Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
