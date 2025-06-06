package com.gj4.service;

import com.gj4.Gj4Autowire;
import com.gj4.annotations.Component;

@Component
public class UserService {
    private final EmailService emailService;
    private final SessionService sessionService;

    @Gj4Autowire
    public UserService(EmailService emailService, SessionService sessionService) {
        this.emailService = emailService;
        this.sessionService = sessionService;
    }

    public void registerUser(String username) {
        System.out.println("Registering user: " + username);
        emailService.sendEmail("Welcome " + username);
    }

    public EmailService getEmailService() {
        return emailService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }
}
