package com.dev.rate_limiter_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds rate-limit.* from application.yml into Java fields.
 * Change limits in yml — no recompilation needed.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Order order = new Order();
    private Auth auth = new Auth();
    private Product product = new Product();

    public static class Order {
        private long globalCapacity = 10000;
        private long perUserCapacity = 5;
        private long refillDurationSeconds = 60;

        public long getGlobalCapacity() { return globalCapacity; }
        public void setGlobalCapacity(long v) { this.globalCapacity = v; }
        public long getPerUserCapacity() { return perUserCapacity; }
        public void setPerUserCapacity(long v) { this.perUserCapacity = v; }
        public long getRefillDurationSeconds() { return refillDurationSeconds; }
        public void setRefillDurationSeconds(long v) { this.refillDurationSeconds = v; }
    }

    public static class Auth {
        private long forgotPasswordCapacity = 3;
        private long loginCapacity = 10;
        private long refillDurationSeconds = 60;

        public long getForgotPasswordCapacity() { return forgotPasswordCapacity; }
        public void setForgotPasswordCapacity(long v) { this.forgotPasswordCapacity = v; }
        public long getLoginCapacity() { return loginCapacity; }
        public void setLoginCapacity(long v) { this.loginCapacity = v; }
        public long getRefillDurationSeconds() { return refillDurationSeconds; }
        public void setRefillDurationSeconds(long v) { this.refillDurationSeconds = v; }
    }

    public static class Product {
        private long capacity = 100;
        private long refillDurationSeconds = 60;

        public long getCapacity() { return capacity; }
        public void setCapacity(long v) { this.capacity = v; }
        public long getRefillDurationSeconds() { return refillDurationSeconds; }
        public void setRefillDurationSeconds(long v) { this.refillDurationSeconds = v; }
    }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}