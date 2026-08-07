package com.featureflag.notification_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendEmail(String recipient, String subject, String message) {

        SimpleMailMessage mail = new SimpleMailMessage();

        mail.setTo(recipient);
        mail.setSubject(subject);
        mail.setText(message);

        mailSender.send(mail);
    }
}