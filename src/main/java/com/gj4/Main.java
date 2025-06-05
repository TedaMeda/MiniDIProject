package com.gj4;

import com.gj4.container.SimpleContainer;
import com.gj4.service.EmailService;
import com.gj4.service.SessionService;
import com.gj4.service.UserService;
import com.sun.tools.jconsole.JConsoleContext;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception{
        Set<Class<?>> classToScan = Set.of(UserService.class);
        SimpleContainer container = new SimpleContainer(classToScan);
        UserService userService = (UserService) container.getBean("userServiceBean");
        System.out.println(userService);
        System.out.println(container.getBean("emailServiceBean"));
        System.out.println(container.getBean(""));
    }
}