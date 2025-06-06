package com.gj4.service;

import com.gj4.annotations.Component;

import java.util.UUID;

@Component(scope = Component.Scope.PROTOTYPE)
public class SessionService {
    public UUID id = UUID.randomUUID();
}
