package com.huddle.backend.controller;

import com.huddle.backend.dto.ChatMessageDto;
import com.huddle.backend.dto.ChatMessageResponse;
import com.huddle.backend.model.ChatMessage;
import com.huddle.backend.model.CollabRequest;
import com.huddle.backend.model.Collaboration;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import com.huddle.backend.repository.ChatMessageRepository;
import com.huddle.backend.repository.CollabRequestRepository;
import com.huddle.backend.repository.CollaborationMemberRepository;
import com.huddle.backend.repository.CollaborationRepository;
import com.huddle.backend.repository.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatMessageRepository chatMessageRepository,
                           CollabRequestRepository collabRequestRepository,
                           CollaborationRepository collaborationRepository,
                           CollaborationMemberRepository memberRepository,
                           UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.collaborationRepository = collaborationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{requestId}")
    public void sendMessage(@DestinationVariable String requestId,
                             ChatMessageDto dto,
                             Principal principal) {

        User sender = getAuthenticatedUser(principal);

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

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return;
        }

        String content = dto.getContent().trim();
        if (content.length() > 2000) {
            content = content.substring(0, 2000);
        }

        ChatMessage message = new ChatMessage();
        message.setCollabRequest(request);
        message.setSender(sender);
        message.setContent(content);

        ChatMessage saved = chatMessageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/chat/" + requestId, new ChatMessageResponse(saved));
    }

    @MessageMapping("/collaboration-chat/{collaborationId}")
    public void sendCollaborationMessage(@DestinationVariable String collaborationId,
                                         ChatMessageDto dto,
                                         Principal principal) {

        User sender = getAuthenticatedUser(principal);

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new IllegalArgumentException("Collaboration not found"));

        // Only ACTIVE members can send messages
        boolean isActiveMember = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                collaborationId, sender.getId(), MemberStatus.ACTIVE);

        if (!isActiveMember) {
            throw new SecurityException("You are not an active member of this collaboration workspace");
        }

        // If collaboration is archived, chat is read-only
        if (collaboration.getStatus() == Collaboration.CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("This collaboration workspace is archived and read-only");
        }

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return;
        }

        String content = dto.getContent().trim();
        if (content.length() > 2000) {
            content = content.substring(0, 2000);
        }

        ChatMessage message = new ChatMessage();
        message.setCollaboration(collaboration);
        message.setSender(sender);
        message.setContent(content);

        ChatMessage saved = chatMessageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/collaboration-chat/" + collaborationId, new ChatMessageResponse(saved));
    }

    private User getAuthenticatedUser(Principal principal) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken authToken)
                || !(authToken.getPrincipal() instanceof User sender)) {
            throw new SecurityException("No authenticated user on this WebSocket session");
        }

        // Re-check current institution status from DB to deny revoked/pending accounts
        User freshUser = userRepository.findById(sender.getId())
                .orElseThrow(() -> new SecurityException("User not found"));

        if (freshUser.getInstitutionStatus() != InstitutionStatus.VERIFIED) {
            throw new AccessDeniedException("Institution verification required to send chat messages");
        }

        return freshUser;
    }
}
