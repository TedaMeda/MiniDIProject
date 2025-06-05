package com.gj4.container;

import com.gj4.Gj4Autowire;
import com.gj4.annotations.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class SimpleContainer {
    private final BeanContextHolder beanContextHolder;
    private final ThreadLocal<Set<Class<?>>> classStack;
    private final Map<Class<?>, Set<Class<?>>> dependencyGraph;
    private final Map<String, Object> namedBeans;

    public SimpleContainer(Set<Class<?>> classToScan) throws Exception {
        this.beanContextHolder = new BeanContextHolder();
        this.classStack = ThreadLocal.withInitial(HashSet::new);
        this.dependencyGraph = new HashMap<>();
        namedBeans = new HashMap<>();

        for(Class<?> clazz: classToScan){
            if(clazz.isAnnotationPresent(Component.class)){
                Object bean = clazz.cast(getInstance(clazz));
                String name = clazz.getAnnotation(Component.class).value();
                namedBeans.put(name, bean);
            }
        }
    }

    public Object getBean(String name){
        return namedBeans.get(name);
    }

    private <T> T getInstance(Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if(isSingleton(clazz) && beanContextHolder.containsKey(clazz)){
            return clazz.cast(beanContextHolder.getBeanOfClass(clazz));
        }
        if (classStack.get().contains(clazz)) {
            throw new RuntimeException("Circular dependency detected while constructing: " + clazz.getName());
        }
        try {
            classStack.get().add(clazz);
            T instance = createFullyInjectedInstance(clazz);
            if (isSingleton(clazz)) {
                beanContextHolder.add(clazz, instance);
            }
            if(clazz.isAnnotationPresent(Component.class)){
                String name = clazz.getAnnotation(Component.class).value();
                if(name.isBlank()){
                    name = clazz.getSimpleName();
                }
                namedBeans.put(name, instance);
            }
            return instance;
        } finally {
            classStack.get().remove(clazz);
        }
    }

    private <T> boolean isSingleton(Class<T> clazz) {
        Component.Scope scope = clazz.getAnnotation(Component.class).scope();
        return scope==Component.Scope.SINGLETON;
    }

    private <T> T createFullyInjectedInstance(Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        T instance = createInstanceWithConstructorInjection(clazz);

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Gj4Autowire.class)) {
                trackDependency(clazz, field.getType());
                field.setAccessible(true);
                field.set(instance, getInstance(field.getType()));
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Gj4Autowire.class)) {
                Class<?>[] paramType = method.getParameterTypes();
                if (paramType.length == 1) {
                    trackDependency(clazz, paramType[0]);
                    Object obj = getInstance(paramType[0]);
                    method.setAccessible(true);
                    method.invoke(instance, obj);
                }
            }
        }

        return instance;
    }

    private <T> T createInstanceWithConstructorInjection(Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        long count = Arrays.stream(clazz.getConstructors()).filter((c) -> c.isAnnotationPresent(Gj4Autowire.class)).count();
        if (count > 1) {
            throw new RuntimeException("Gj4Autowire allowed on only one constructor");
        }
        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (constructor.isAnnotationPresent(Gj4Autowire.class)) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                int sz = paramTypes.length;
                Object[] objects = new Object[sz];
                for (int i = 0; i < sz; i++) {
                    trackDependency(clazz, paramTypes[i]);
                    objects[i] = getInstance(paramTypes[i]);
                }
                return clazz.cast(constructor.newInstance(objects));
            }
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    private void trackDependency(Class<?> from, Class<?> to) {
        dependencyGraph.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    public void printDependencyGraph() {
        System.out.println("=== Dependency Graph ===");
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : dependencyGraph.entrySet()) {
            System.out.println(entry.getKey().getSimpleName() + " depends on:");
            for (Class<?> dep : entry.getValue()) {
                System.out.println("  -> " + dep.getSimpleName());
            }
        }
    }
}
