package com.example.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param target    Temporal frontend address (host:port)
 * @param namespace Temporal namespace
 * @param workers   which service workers this process hosts: order, payment, inventory, shipping, notification
 */
@ConfigurationProperties(prefix = "temporal")
public record TemporalProperties(String target, String namespace, List<String> workers) {
}
