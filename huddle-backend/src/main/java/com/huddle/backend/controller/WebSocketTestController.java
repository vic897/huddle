package com.huddle.backend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketTestController {

    @MessageMapping("/test")       // client sends to /app/test
    @SendTo("/topic/test")         // broadcasts to anyone subscribed to /topic/test
    public String echo(String message) {
        return "Echo: " + message;
    }
}