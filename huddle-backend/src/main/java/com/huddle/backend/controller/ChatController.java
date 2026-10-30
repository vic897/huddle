package com.huddle.backend.controller;

import com.huddle.backend.dto.*;
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
import com.huddle.backend.service.ChatMessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;
    private final com.huddle.backend.repository.ProjectFileRepository projectFileRepository;
    private final com.huddle.backend.service.CollaborationService collaborationService;

    public ChatController(ChatMessageRepository chatMessageRepository,
                          CollabRequestRepository collabRequestRepository,
                          CollaborationRepository collaborationRepository,
                          CollaborationMemberRepository memberRepository,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate,
                          ChatMessageService chatMessageService,
                          com.huddle.backend.repository.ProjectFileRepository projectFileRepository,
                          com.huddle.backend.service.CollaborationService collaborationService) {
        this.chatMessageRepository = chatMessageRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.collaborationRepository = collaborationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.chatMessageService = chatMessageService;
        this.projectFileRepository = projectFileRepository;
        this.collaborationService = collaborationService;
    }

    // --- REST Endpoints for Unified Messaging 2.0 ---

    @GetMapping("/api/messages/conversations")
    @ResponseBody
    public ResponseEntity<List<UnifiedConversationDto>> getUnifiedConversations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(chatMessageService.getUnifiedConversations(currentUser));
    }

    @GetMapping("/api/messages/conversation/{type}/{id}")
    @ResponseBody
    public ResponseEntity<List<ChatMessageV2Response>> getConversationMessages(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String type,
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatMessageService.getConversationMessages(type, id, page, size, currentUser));
    }

    @PutMapping("/api/messages/conversation/{type}/{id}/read")
    @ResponseBody
    public ResponseEntity<Void> markConversationRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String type,
            @PathVariable String id) {
        chatMessageService.markConversationAsRead(type, id, currentUser);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/messages/{messageId}")
    @ResponseBody
    public ResponseEntity<ChatMessageV2Response> editMessage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String messageId,
            @Valid @RequestBody EditMessageDto dto) {
        return ResponseEntity.ok(chatMessageService.editMessage(messageId, dto, currentUser));
    }

    @DeleteMapping("/api/messages/{messageId}")
    @ResponseBody
    public ResponseEntity<ChatMessageV2Response> deleteMessage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String messageId) {
        return ResponseEntity.ok(chatMessageService.deleteMessage(messageId, currentUser));
    }

    // --- WebSocket Message Handlers ---

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

        // Handle file reference in DM
        com.huddle.backend.model.ProjectFile referencedFile = null;
        if (dto.getReferencedFileId() != null && !dto.getReferencedFileId().isEmpty()) {
            referencedFile = projectFileRepository.findById(dto.getReferencedFileId())
                    .orElseThrow(() -> new IllegalArgumentException("Referenced file not found"));

            // Verify recipient has access to the file's collaboration
            User recipient = sender.getId().equals(request.getRequestor().getId()) 
                    ? request.getPoster() 
                    : request.getRequestor();
            
            // For archived collaborations, check archive eligibility
            String fileCollabId = referencedFile.getCollaboration().getId();
            if (collaborationService.isCollaborationArchived(fileCollabId)) {
                boolean recipientHasAccess = collaborationService.canAccessArchive(fileCollabId, recipient.getId());
                if (!recipientHasAccess) {
                    throw new SecurityException("Recipient does not have access to this archived project file");
                }
            } else {
                // For active collaborations, check active membership
                boolean recipientHasAccess = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                        fileCollabId, 
                        recipient.getId(), 
                        MemberStatus.ACTIVE);
                if (!recipientHasAccess) {
                    throw new SecurityException("Recipient does not have access to this project file");
                }
            }

            // Verify file is accessible (not in trash, not blocked by safety)
            if (referencedFile.getIsInTrash()) {
                throw new IllegalStateException("Cannot share a file that is in trash");
            }
            if (referencedFile.getSafetyState() != com.huddle.backend.model.SafetyState.CLEARED) {
                throw new IllegalStateException("Cannot share a file pending safety review");
            }
        }

        // Allow messages with either content, file reference, or both
        if ((dto.getContent() == null || dto.getContent().trim().isEmpty()) && referencedFile == null) {
            return;
        }

        String content = dto.getContent() != null ? dto.getContent().trim() : "";
        if (content.length() > 2000) {
            content = content.substring(0, 2000);
        }

        ChatMessage message = new ChatMessage();
        message.setCollabRequest(request);
        message.setSender(sender);
        message.setContent(content);
        message.setReferencedFile(referencedFile);

        if (dto.getReplyToId() != null) {
            chatMessageRepository.findById(dto.getReplyToId()).ifPresent(replyMsg -> {
                if (replyMsg.getCollabRequest() != null && replyMsg.getCollabRequest().getId().equals(requestId)) {
                    message.setReplyTo(replyMsg);
                }
            });
        }

        ChatMessage saved = chatMessageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/chat/" + requestId, ChatMessageV2Response.fromEntity(saved));
    }

    @MessageMapping("/chat/{requestId}/typing")
    public void handleDirectTyping(@DestinationVariable String requestId,
                                   TypingNotificationDto typingDto,
                                   Principal principal) {
        User user = getAuthenticatedUser(principal);

        CollabRequest request = collabRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Collab request not found"));

        boolean isParticipant = request.getRequestor().getId().equals(user.getId())
                || request.getPoster().getId().equals(user.getId());

        if (!isParticipant) {
            throw new SecurityException("Not a participant in this collab request");
        }

        if (request.getStatus() != CollabRequest.RequestStatus.ACCEPTED) {
            throw new IllegalStateException("Chat is only available for accepted requests");
        }

        typingDto.setUserId(user.getId());
        typingDto.setUserName(user.getName());
        typingDto.setConversationType("DIRECT");
        typingDto.setConversationId(requestId);
        messagingTemplate.convertAndSend("/topic/chat/" + requestId + "/typing", typingDto);
    }

    @MessageMapping("/collaboration-chat/{collaborationId}")
    public void sendCollaborationMessage(@DestinationVariable String collaborationId,
                                         ChatMessageDto dto,
                                         Principal principal) {

        User sender = getAuthenticatedUser(principal);

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new IllegalArgumentException("Collaboration not found"));

        boolean isActiveMember = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                collaborationId, sender.getId(), MemberStatus.ACTIVE);

        if (!isActiveMember) {
            throw new SecurityException("You are not an active member of this collaboration workspace");
        }

        if (collaboration.getStatus() == Collaboration.CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("This collaboration workspace is archived and read-only");
        }

        // Handle file reference
        com.huddle.backend.model.ProjectFile referencedFile = null;
        if (dto.getReferencedFileId() != null && !dto.getReferencedFileId().isEmpty()) {
            referencedFile = projectFileRepository.findById(dto.getReferencedFileId())
                    .orElseThrow(() -> new IllegalArgumentException("Referenced file not found"));

            // Verify file belongs to this collaboration
            if (!referencedFile.getCollaboration().getId().equals(collaborationId)) {
                throw new SecurityException("Referenced file does not belong to this collaboration");
            }

            // Verify file is accessible (not in trash, not blocked by safety)
            if (referencedFile.getIsInTrash()) {
                throw new IllegalStateException("Cannot share a file that is in trash");
            }
            if (referencedFile.getSafetyState() != com.huddle.backend.model.SafetyState.CLEARED) {
                throw new IllegalStateException("Cannot share a file pending safety review");
            }
        }

        // Allow messages with either content, file reference, or both
        if ((dto.getContent() == null || dto.getContent().trim().isEmpty()) && referencedFile == null) {
            return;
        }

        String content = dto.getContent() != null ? dto.getContent().trim() : "";
        if (content.length() > 2000) {
            content = content.substring(0, 2000);
        }

        ChatMessage message = new ChatMessage();
        message.setCollaboration(collaboration);
        message.setSender(sender);
        message.setContent(content);
        message.setReferencedFile(referencedFile);

        if (dto.getReplyToId() != null) {
            chatMessageRepository.findById(dto.getReplyToId()).ifPresent(replyMsg -> {
                if (replyMsg.getCollaboration() != null && replyMsg.getCollaboration().getId().equals(collaborationId)) {
                    message.setReplyTo(replyMsg);
                }
            });
        }

        ChatMessage saved = chatMessageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/collaboration-chat/" + collaborationId, ChatMessageV2Response.fromEntity(saved));
    }

    @MessageMapping("/collaboration-chat/{collaborationId}/typing")
    public void handleCollaborationTyping(@DestinationVariable String collaborationId,
                                          TypingNotificationDto typingDto,
                                          Principal principal) {
        User user = getAuthenticatedUser(principal);

        Collaboration collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new IllegalArgumentException("Collaboration not found"));

        boolean isActiveMember = memberRepository.existsByCollaborationIdAndUserIdAndStatus(
                collaborationId, user.getId(), MemberStatus.ACTIVE);

        if (!isActiveMember) {
            throw new SecurityException("You are not an active member of this collaboration workspace");
        }

        if (collaboration.getStatus() == Collaboration.CollaborationStatus.ARCHIVED) {
            throw new IllegalStateException("This collaboration workspace is archived and read-only");
        }

        typingDto.setUserId(user.getId());
        typingDto.setUserName(user.getName());
        typingDto.setConversationType("COLLABORATION");
        typingDto.setConversationId(collaborationId);
        messagingTemplate.convertAndSend("/topic/collaboration-chat/" + collaborationId + "/typing", typingDto);
    }

    private User getAuthenticatedUser(Principal principal) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken authToken)
                || !(authToken.getPrincipal() instanceof User sender)) {
            throw new SecurityException("No authenticated user on this WebSocket session");
        }

        User freshUser = userRepository.findById(sender.getId())
                .orElseThrow(() -> new SecurityException("User not found"));

        if (freshUser.getInstitutionStatus() != InstitutionStatus.VERIFIED) {
            throw new AccessDeniedException("Institution verification required to send chat messages");
        }

        return freshUser;
    }
}
