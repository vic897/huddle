package com.huddle.backend.service;

import com.huddle.backend.dto.ChatMessageV2Response;
import com.huddle.backend.dto.EditMessageDto;
import com.huddle.backend.dto.UnifiedConversationDto;
import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageReadRepository chatMessageReadRepository;
    private final ConversationReadStateRepository conversationReadStateRepository;
    private final CollabRequestRepository collabRequestRepository;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationMemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageService(
            ChatMessageRepository chatMessageRepository,
            ChatMessageReadRepository chatMessageReadRepository,
            ConversationReadStateRepository conversationReadStateRepository,
            CollabRequestRepository collabRequestRepository,
            CollaborationRepository collaborationRepository,
            CollaborationMemberRepository memberRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageReadRepository = chatMessageReadRepository;
        this.conversationReadStateRepository = conversationReadStateRepository;
        this.collabRequestRepository = collabRequestRepository;
        this.collaborationRepository = collaborationRepository;
        this.memberRepository = memberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<UnifiedConversationDto> getUnifiedConversations(User currentUser) {
        List<UnifiedConversationDto> list = new ArrayList<>();
        String userId = currentUser.getId();

        // 1. Direct Message Conversations (CollabRequests where user is requestor or poster)
        List<CollabRequest> directRequests = collabRequestRepository.findAllDirectChatsForUser(userId);
        for (CollabRequest req : directRequests) {
            boolean isPoster = req.getPoster().getId().equals(userId);
            User otherUser = isPoster ? req.getRequestor() : req.getPoster();

            Optional<ChatMessage> lastMsgOpt = chatMessageRepository.findFirstByCollabRequestIdOrderBySentAtDesc(req.getId());
            Optional<ConversationReadState> readStateOpt = conversationReadStateRepository
                    .findByUserIdAndConversationTypeAndConversationId(userId, "DIRECT", req.getId());

            Instant lastReadAt = readStateOpt.map(ConversationReadState::getLastReadAt).orElse(Instant.EPOCH);
            long unread = chatMessageRepository.countDirectUnreadSince(req.getId(), userId, lastReadAt);

            String lastMsgText = null;
            Instant lastMsgAt = req.getCreatedAt();
            if (lastMsgOpt.isPresent()) {
                ChatMessage m = lastMsgOpt.get();
                lastMsgText = Boolean.TRUE.equals(m.getIsDeleted()) ? "This message was deleted" : m.getContent();
                lastMsgAt = m.getSentAt();
            }

            list.add(UnifiedConversationDto.builder()
                    .conversationId(req.getId())
                    .conversationType("DIRECT")
                    .title(otherUser.getName() + " (" + req.getPost().getTitle() + ")")
                    .avatarUrl(otherUser.getAvatarUrl())
                    .lastMessage(lastMsgText)
                    .lastMessageAt(lastMsgAt)
                    .unreadCount(unread)
                    .otherUserId(otherUser.getId())
                    .projectId(req.getPost().getId())
                    .status(req.getStatus().name())
                    .build());
        }

        // 2. Collaboration Workspace Group Chats (where user is an ACTIVE member)
        List<CollaborationMember> activeMemberships = memberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE);
        for (CollaborationMember member : activeMemberships) {
            Collaboration collab = member.getCollaboration();
            if (collab.getStatus() == Collaboration.CollaborationStatus.ARCHIVED) {
                continue;
            }

            Optional<ChatMessage> lastMsgOpt = chatMessageRepository.findFirstByCollaborationIdOrderBySentAtDesc(collab.getId());
            Optional<ConversationReadState> readStateOpt = conversationReadStateRepository
                    .findByUserIdAndConversationTypeAndConversationId(userId, "COLLABORATION", collab.getId());

            Instant lastReadAt = readStateOpt.map(ConversationReadState::getLastReadAt).orElse(Instant.EPOCH);
            long unread = chatMessageRepository.countCollabUnreadSince(collab.getId(), userId, lastReadAt);

            String lastMsgText = null;
            Instant lastMsgAt = collab.getCreatedAt();
            if (lastMsgOpt.isPresent()) {
                ChatMessage m = lastMsgOpt.get();
                lastMsgText = (m.getSender() != null ? m.getSender().getName() + ": " : "") +
                        (Boolean.TRUE.equals(m.getIsDeleted()) ? "This message was deleted" : m.getContent());
                lastMsgAt = m.getSentAt();
            }

            list.add(UnifiedConversationDto.builder()
                    .conversationId(collab.getId())
                    .conversationType("COLLABORATION")
                    .title(collab.getProjectPost().getTitle())
                    .avatarUrl(null)
                    .lastMessage(lastMsgText)
                    .lastMessageAt(lastMsgAt)
                    .unreadCount(unread)
                    .otherUserId(null)
                    .projectId(collab.getProjectPost().getId())
                    .status(member.getRole().name())
                    .build());
        }

        list.sort((a, b) -> {
            if (a.getLastMessageAt() == null && b.getLastMessageAt() == null) return 0;
            if (a.getLastMessageAt() == null) return 1;
            if (b.getLastMessageAt() == null) return -1;
            return b.getLastMessageAt().compareTo(a.getLastMessageAt());
        });

        return list;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageV2Response> getConversationMessages(String conversationType, String conversationId, int page, int size, User currentUser) {
        verifyAccess(conversationType, conversationId, currentUser);

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messagePage;

        if ("DIRECT".equalsIgnoreCase(conversationType)) {
            messagePage = chatMessageRepository.findByCollabRequestIdOrderBySentAtDesc(conversationId, pageable);
        } else if ("COLLABORATION".equalsIgnoreCase(conversationType)) {
            messagePage = chatMessageRepository.findByCollaborationIdOrderBySentAtDesc(conversationId, pageable);
        } else {
            throw new IllegalArgumentException("Unknown conversation type: " + conversationType);
        }

        List<ChatMessage> list = new ArrayList<>(messagePage.getContent());
        Collections.reverse(list);

        return list.stream()
                .map(ChatMessageV2Response::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markConversationAsRead(String conversationType, String conversationId, User currentUser) {
        verifyAccess(conversationType, conversationId, currentUser);

        Optional<ChatMessage> latestMsgOpt;
        if ("DIRECT".equalsIgnoreCase(conversationType)) {
            latestMsgOpt = chatMessageRepository.findFirstByCollabRequestIdOrderBySentAtDesc(conversationId);
        } else {
            latestMsgOpt = chatMessageRepository.findFirstByCollaborationIdOrderBySentAtDesc(conversationId);
        }

        ConversationReadState readState = conversationReadStateRepository
                .findByUserIdAndConversationTypeAndConversationId(currentUser.getId(), conversationType.toUpperCase(), conversationId)
                .orElseGet(() -> {
                    ConversationReadState state = new ConversationReadState();
                    state.setUser(currentUser);
                    state.setConversationType(conversationType.toUpperCase());
                    state.setConversationId(conversationId);
                    return state;
                });

        readState.setLastReadAt(Instant.now());
        latestMsgOpt.ifPresent(m -> readState.setLastReadMessageId(m.getId()));
        conversationReadStateRepository.save(readState);

        // Also record individual chat_message_reads for unread messages if any
        if (latestMsgOpt.isPresent()) {
            ChatMessage latest = latestMsgOpt.get();
            if (chatMessageReadRepository.findByMessageIdAndUserId(latest.getId(), currentUser.getId()).isEmpty()) {
                ChatMessageRead read = new ChatMessageRead();
                read.setMessage(latest);
                read.setUser(currentUser);
                read.setReadAt(Instant.now());
                chatMessageReadRepository.save(read);
            }
        }
    }

    @Transactional
    public ChatMessageV2Response editMessage(String messageId, EditMessageDto dto, User currentUser) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only edit your own messages");
        }

        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new IllegalStateException("Deleted messages cannot be edited");
        }

        Duration elapsed = Duration.between(message.getSentAt(), Instant.now());
        if (elapsed.toMinutes() > 15) {
            throw new IllegalStateException("Messages can only be edited within 15 minutes of sending");
        }

        message.setContent(dto.getContent().trim());
        message.setIsEdited(true);
        message.setEditedAt(Instant.now());

        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageV2Response response = ChatMessageV2Response.fromEntity(saved);

        broadcastUpdate(saved, response);
        return response;
    }

    @Transactional
    public ChatMessageV2Response deleteMessage(String messageId, User currentUser) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));

        boolean isSender = message.getSender().getId().equals(currentUser.getId());
        boolean isCollabOwner = false;
        if (message.getCollaboration() != null) {
            isCollabOwner = memberRepository.findByCollaborationIdAndUserId(message.getCollaboration().getId(), currentUser.getId())
                    .filter(cm -> cm.getRole() == CollaborationMember.MemberRole.OWNER && cm.getStatus() == MemberStatus.ACTIVE)
                    .isPresent();
        }

        if (!isSender && !isCollabOwner) {
            throw new SecurityException("Not authorized to delete this message");
        }

        message.setIsDeleted(true);
        message.setDeletedAt(Instant.now());

        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageV2Response response = ChatMessageV2Response.fromEntity(saved);

        broadcastUpdate(saved, response);
        return response;
    }

    private void broadcastUpdate(ChatMessage message, ChatMessageV2Response response) {
        if (message.getCollabRequest() != null) {
            messagingTemplate.convertAndSend("/topic/chat/" + message.getCollabRequest().getId(), response);
        } else if (message.getCollaboration() != null) {
            messagingTemplate.convertAndSend("/topic/collaboration-chat/" + message.getCollaboration().getId(), response);
        }
    }

    private void verifyAccess(String type, String id, User currentUser) {
        if ("DIRECT".equalsIgnoreCase(type)) {
            CollabRequest req = collabRequestRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Direct conversation not found"));
            boolean participant = req.getRequestor().getId().equals(currentUser.getId())
                    || req.getPoster().getId().equals(currentUser.getId());
            if (!participant) {
                throw new SecurityException("You are not a participant in this conversation");
            }
        } else if ("COLLABORATION".equalsIgnoreCase(type)) {
            boolean active = memberRepository.existsByCollaborationIdAndUserIdAndStatus(id, currentUser.getId(), MemberStatus.ACTIVE);
            if (!active) {
                throw new SecurityException("You are not an active member of this collaboration workspace");
            }
        }
    }
}
