package com.gj4.container;

import java.util.HashMap;
import java.util.Map;


public class BeanContextHolder {
    private final Map<Class<?>, Object> beanContextHolder = new HashMap<>();

    public Object getBeanOfClass(Class<?> clazz) {
        return beanContextHolder.get(clazz);
    }

    public boolean containsKey(Class<?> clazz) {
        return this.beanContextHolder.containsKey(clazz);
    }

    public void add(Class<?> clazz, Object obj) {
        this.beanContextHolder.put(clazz, obj);
    }
}
