package com.gj4.service;

public class EmailService {
    public EmailService() {
        System.out.println("Email Service Created");
    }

    public void sendEmail(String username) {
        System.out.println("Email sent to: " + username);
    }
}
