package com.gj4.container;

import com.gj4.Gj4Autowire;
import com.gj4.annotations.Component;
import com.gj4.annotations.PostConstruct;
import com.gj4.annotations.PreDestroy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleContainer {
    private final ThreadLocal<Set<Class<?>>> classStack;
    private final Map<String, Class<?>> namedBean;
    private final Map<Class<?>, Object> singletonCache;
    private final List<Object> destroyables;

    public SimpleContainer() {
        this.classStack = ThreadLocal.withInitial(HashSet::new);
        this.namedBean = new HashMap<>();
        this.singletonCache = new ConcurrentHashMap<>();
        this.destroyables = new ArrayList<>();
    }

    public <T> T getInstance(Class<T> clazz) throws Exception {
        if (!clazz.isAnnotationPresent(Component.class)) {
            throw new RuntimeException("Pela Component to lagav dofa");
        }

        if (isSingleton(clazz)) {
            if (singletonCache.containsKey(clazz))
                return clazz.cast(singletonCache.get(clazz));
            //double-checking lock
            synchronized (singletonCache) {
                if (singletonCache.containsKey(clazz))
                    return clazz.cast(singletonCache.get(clazz));

                if (classStack.get().contains(clazz)) {
                    throw new RuntimeException("Circular dependency detected while construction: " + clazz.getName());
                }

                try {
                    this.classStack.get().add(clazz);
                    T instance = createFullyInjectedInstance(clazz);
                    String beanName = getBeanName(clazz);
                    if (namedBean.containsKey(beanName) && namedBean.get(beanName).getName().equals(clazz.getName())) {
                        instance = null;
                        throw new RuntimeException("Two beans can not have same name");
                    }
                    if (!namedBean.containsKey(beanName)) {
                        namedBean.put(beanName, clazz);
                    }
                    singletonCache.put(clazz, instance);
                    invokePostConstruct(instance);
                    addToDestroyable(instance);
                    return instance;
                } finally {
                    this.classStack.get().remove(clazz);
                }
            }
        } else {
            if (classStack.get().contains(clazz)) {
                throw new RuntimeException("Circular dependency detected while construction: " + clazz.getName());
            }

            try {
                this.classStack.get().add(clazz);
                T instance = createFullyInjectedInstance(clazz);
                String beanName = getBeanName(clazz);
                if (namedBean.containsKey(beanName) && namedBean.get(beanName).getName().equals(clazz.getName())) {
                    instance = null;
                    throw new RuntimeException("Two beans can not have same name");
                }
                if (!namedBean.containsKey(beanName)) {
                    namedBean.put(beanName, clazz);
                }
                invokePostConstruct(instance);
                addToDestroyable(instance);
                return instance;
            } finally {
                this.classStack.get().remove(clazz);
            }
        }
    }

    public Object getBeanByName(String name) throws Exception {
        if (name.isBlank()) {
            throw new RuntimeException("Empty bean name");
        }
        if (!namedBean.containsKey(name)) {
            throw new RuntimeException("Bean with name: " + name + " does not exist");
        }
        Class<?> clazz = namedBean.get(name);
        return clazz.cast(getInstance(clazz));
    }

    public void shutdown() throws InvocationTargetException, IllegalAccessException {
        for(Object obj:destroyables){
            destroy(obj);
        }
    }

    private void destroy(Object instance) throws InvocationTargetException, IllegalAccessException {
        Class<?> clazz = instance.getClass();
        for(Method method:clazz.getDeclaredMethods()){
            if(method.isAnnotationPresent(PreDestroy.class)){
                method.setAccessible(true);
                method.invoke(instance);
            }
        }
    }

    private <T> void addToDestroyable(Object instance) throws InvocationTargetException, IllegalAccessException {
        Class<?> clazz = instance.getClass();
        for(Method method:clazz.getDeclaredMethods()){
            if(method.isAnnotationPresent(PreDestroy.class)){
                method.setAccessible(true);
                int params = method.getParameterCount();
                if(params>0){
                    throw new RuntimeException("Failed to invoke @PreDestroy: Method has parameters");
                }
                destroyables.add(instance);
            }
        }
    }

    private void invokePostConstruct(Object instance) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                if (method.getParameterCount() > 0)
                    throw new RuntimeException("@PostConstruct methods must have no parameters");

                method.setAccessible(true);
                try {
                    method.invoke(instance);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to invoke @PostConstruct on " + instance.getClass(), e);
                }
            }
        }
    }

    private <T> T createFullyInjectedInstance(Class<T> clazz) throws Exception {
        T instance = createInstanceWithConstructorInjection(clazz);

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Gj4Autowire.class)) {
                field.setAccessible(true);
                field.set(instance, getInstance(field.getType()));
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Gj4Autowire.class)) {
                Class<?>[] paramType = method.getParameterTypes();
                if (paramType.length == 1) {
                    Object obj = getInstance(paramType[0]);
                    method.setAccessible(true);
                    method.invoke(instance, obj);
                }
            }
        }

        return instance;
    }

    private <T> T createInstanceWithConstructorInjection(Class<T> clazz) throws Exception {
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
                    objects[i] = getInstance(paramTypes[i]);
                }
                return clazz.cast(constructor.newInstance(objects));
            }
        }

        return clazz.getDeclaredConstructor().newInstance();
    }

    private <T> String getBeanName(Class<T> clazz) {
        String name = clazz.getAnnotation(Component.class).value();
        if (name.isBlank())
            name = Character.toLowerCase(clazz.getSimpleName().charAt(0)) + clazz.getSimpleName().substring(1);
        return name;
    }

    private <T> boolean isPrototype(Class<T> clazz) {
        Component component = clazz.getAnnotation(Component.class);
        return component != null && (component.scope() == Component.Scope.PROTOTYPE);
    }

    private <T> boolean isSingleton(Class<T> clazz) {
        Component component = clazz.getAnnotation(Component.class);
        return component != null && (component.scope() == Component.Scope.SINGLETON);
    }
}
