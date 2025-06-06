package com.gj4.container;

public interface Container {
    <T> T getInstance(Class<T> clazz);
    Object getBeanByName(String name);
    void shutdown();
}
