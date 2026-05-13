package com.superchat.gateway.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

@Configuration
public class MdcContextLifterConfig {

    private static final String HOOK_KEY = "mdc-context-lifter";

    @PostConstruct
    public void init() {
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((sc, sub) -> new MdcLifter<>(sub)));
    }

    @PreDestroy
    public void destroy() {
        Hooks.resetOnEachOperator(HOOK_KEY);
    }

    private static final class MdcLifter<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<T> delegate;

        MdcLifter(CoreSubscriber<T> delegate) {
            this.delegate = delegate;
        }

        @Override public Context currentContext() { return delegate.currentContext(); }

        @Override
        public void onSubscribe(Subscription s) { sync(); delegate.onSubscribe(s); }

        @Override
        public void onNext(T t) { sync(); delegate.onNext(t); }

        @Override
        public void onError(Throwable t) { sync(); delegate.onError(t); }

        @Override
        public void onComplete() { sync(); delegate.onComplete(); }

        private void sync() {
            Context ctx = currentContext();
            if (ctx.hasKey("requestId")) {
                MDC.put("requestId", ctx.get("requestId"));
            } else {
                MDC.remove("requestId");
            }
        }
    }
}
