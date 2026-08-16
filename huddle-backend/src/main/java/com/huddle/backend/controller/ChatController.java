package com.huddle.backend.controller;

import com.huddle.backend.dto.ChatMessageDto;
import com.huddle.backend.dto.ChatMessageResponse;
import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ChatMessageRepository;
import com.huddle.backend.repository.CollabRequestRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatMessageRepository chatMessageRepository,
                           CollabRequestRepository collabRequestRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{requestId}")
    public void sendMessage(@DestinationVariable String requestId,
                             ChatMessageDto dto,
                             Principal principal) {

        // principal is the UsernamePasswordAuthenticationToken WebSocketAuthInterceptor
        // attached at CONNECT time — same identity, fetched without @AuthenticationPrincipal.
        if (!(principal instanceof UsernamePasswordAuthenticationToken authToken)
                || !(authToken.getPrincipal() instanceof User sender)) {
            throw new SecurityException("No authenticated user on this WebSocket session");
        }

        CollabRequest request = collabRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Collab request not found"));

        boolean isParticipant = request.getRequestor().getId().equals(sender.getId())
                || request.getPoster().getId().equals(sender.getId());

        if (!isParticipant) {
            throw new SecurityException("Not a participant in this collab request");
        }

        if (request.getStatus() != CollabRequest.RequestStatus.ACCEPTED) {
            throw new IllegalStateException("Chat is only available for accepted requests");
        }

        ChatMessage message = new ChatMessage();
        message.setCollabRequest(request);
        message.setSender(sender);
        message.setContent(dto.getContent());

        ChatMessage saved = chatMessageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/chat/" + requestId, new ChatMessageResponse(saved));
    }
}