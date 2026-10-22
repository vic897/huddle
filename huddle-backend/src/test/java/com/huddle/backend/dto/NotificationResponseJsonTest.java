package com.huddle.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationResponseJsonTest {

    @Test
    public void testSerialization() throws Exception {
        NotificationResponse res = new NotificationResponse();
        res.setId("1");
        res.setTitle("Test");
        res.setRead(true);
        res.setCreatedAt(LocalDateTime.now());

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        String json = mapper.writeValueAsString(res);
        System.out.println("SERIALIZED JSON: " + json);

        NotificationResponse deserialized = mapper.readValue(json, NotificationResponse.class);
        System.out.println("DESERIALIZED getRead(): " + deserialized.getRead());
        assertTrue(deserialized.getRead());
    }

    @Test
    public void testSerializationWithFalse() throws Exception {
        NotificationResponse res = new NotificationResponse();
        res.setId("2");
        res.setTitle("Test 2");
        res.setRead(false);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(res);
        System.out.println("SERIALIZED JSON FALSE: " + json);

        assertFalse(res.getRead());
    }
}
