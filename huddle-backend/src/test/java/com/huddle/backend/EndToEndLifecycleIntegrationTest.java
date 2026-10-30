package com.huddle.backend;

import com.huddle.backend.dto.*;
import com.huddle.backend.model.*;
import com.huddle.backend.model.CollaborationMember.MemberRole;
import com.huddle.backend.model.CollaborationMember.MemberStatus;
import com.huddle.backend.repository.*;
import com.huddle.backend.security.JwtService;
import com.huddle.backend.security.WebSocketAuthInterceptor;
import com.huddle.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive End-to-End Workflow Integration Test Suite.
 *
 * Verifies all 14 required user journeys across the Huddle lifecycle:
 * 1.  Registration, email verification and authentication.
 * 2.  Project creation and publishing.
 * 3.  Project discovery and matching.
 * 4.  Collaboration request submission.
 * 5.  Owner acceptance for discussion.
 * 6.  Direct messaging.
 * 7.  Formal commitment offer creation.
 * 8.  Recipient acceptance of the offer.
 * 9.  Automatic membership activation.
 * 10. Project chat access after membership activation.
 * 11. Project messaging and read receipts.
 * 12. Member removal and access revocation.
 * 13. Voluntary departure and owner approval.
 * 14. Ownership transfer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EndToEndLifecycleIntegrationTest {

    // Repositories
    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationTokenRepository emailTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private ProjectPostRepository postRepository;
    @Mock private CollabRequestRepository collabRequestRepository;
    @Mock private CollaborationRepository collaborationRepository;
    @Mock private CollaborationMemberRepository memberRepository;
    @Mock private CommitmentOfferRepository commitmentOfferRepository;
    @Mock private OwnershipTransferRequestRepository transferRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatMessageReadRepository chatMessageReadRepository;
    @Mock private ConversationReadStateRepository conversationReadStateRepository;
    @Mock private MembershipAuditEventRepository auditEventRepository;
    @Mock private ProjectFileRepository projectFileRepository;

    // Infrastructure / Helpers
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleTokenVerifierService googleTokenVerifierService;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private BlockService blockService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private com.huddle.backend.config.FileStorageConfig fileStorageConfig;

    // Services Under Test
    private AuthService authService;
    private MatchingService matchingService;
    private CollabRequestService collabRequestService;
    private CollaborationService collaborationService;
    private CommitmentOfferService commitmentOfferService;
    private ChatMessageService chatMessageService;
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    // Shared State for E2E Flow
    private User owner;
    private User candidate;
    private ProjectPost post;
    private Collaboration collaboration;
    private CollaborationMember ownerMember;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                googleTokenVerifierService,
                emailTokenRepository,
                passwordResetTokenRepository,
                emailService
        );

        matchingService = new MatchingService();

        collaborationService = new CollaborationService(
                collaborationRepository,
                memberRepository,
                collabRequestRepository,
                notificationService,
                auditEventRepository,
                commitmentOfferRepository,
                transferRepository,
                projectFileRepository,
                chatMessageRepository,
                fileStorageConfig
        );

        collabRequestService = new CollabRequestService(
                collabRequestRepository,
                postRepository,
                blockService,
                notificationService,
                collaborationService
        );

        commitmentOfferService = new CommitmentOfferService(
                commitmentOfferRepository,
                postRepository,
                userRepository,
                collabRequestRepository,
                memberRepository,
                collaborationRepository,
                collaborationService,
                notificationService,
                auditEventRepository
        );

        chatMessageService = new ChatMessageService(
                chatMessageRepository,
                chatMessageReadRepository,
                conversationReadStateRepository,
                collabRequestRepository,
                collaborationRepository,
                memberRepository,
                messagingTemplate
        );

        webSocketAuthInterceptor = new WebSocketAuthInterceptor(
                jwtService,
                userRepository,
                memberRepository,
                collabRequestRepository
        );

        // Standard setup: Owner and Candidate users
        owner = new User();
        owner.setId("user-owner");
        owner.setEmail("owner@cityengineeringcollege.ac.in");
        owner.setDisplayName("Alice Owner");
        owner.setInstitutionStatus(InstitutionStatus.VERIFIED);
        owner.setEmailVerified(true);

        candidate = new User();
        candidate.setId("user-candidate");
        candidate.setEmail("bob@cityengineeringcollege.ac.in");
        candidate.setDisplayName("Bob Candidate");
        candidate.setInstitutionStatus(InstitutionStatus.VERIFIED);
        candidate.setEmailVerified(true);

        post = new ProjectPost();
        post.setId("post-1");
        post.setPoster(owner);
        post.setTitle("AI Campus Navigator");
        post.setBriefDescription("A smart campus mapping and navigation system");
        post.setDesiredSkills(new HashSet<>(Arrays.asList("React", "TypeScript", "Python")));
        post.setDesiredAvailability(new HashSet<>(Collections.singletonList("Weekends")));
        post.setOpenToCollab(true);

        collaboration = new Collaboration();
        collaboration.setId("collab-1");
        collaboration.setProjectPost(post);
        collaboration.setStatus(Collaboration.CollaborationStatus.ACTIVE);

        ownerMember = new CollaborationMember();
        ownerMember.setId("cm-owner");
        ownerMember.setCollaboration(collaboration);
        ownerMember.setUser(owner);
        ownerMember.setRole(MemberRole.OWNER);
        ownerMember.setStatus(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("Journey 1: Registration, email verification, and authentication")
    void journey1_registrationAndAuth() {
        when(userRepository.existsByEmail("alice@cityengineeringcollege.ac.in")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-new");
            return u;
        });

        // 1a. Register
        SignupRequest signupReq = new SignupRequest();
        signupReq.setEmail("alice@cityengineeringcollege.ac.in");
        signupReq.setPassword("SecurePass123!");
        signupReq.setDisplayName("Alice");

        AuthResponse signupRes = authService.signup(signupReq);
        assertEquals("user-new", signupRes.getUserId());
        assertEquals("alice@cityengineeringcollege.ac.in", signupRes.getEmail());
        verify(emailService).sendVerificationEmail(eq("alice@cityengineeringcollege.ac.in"), eq("Alice"), anyString());

        // 1b. Verify Email with token
        User registeredUser = new User();
        registeredUser.setId("user-new");
        registeredUser.setEmail("alice@cityengineeringcollege.ac.in");
        registeredUser.setPasswordHash("hashed_pass");
        registeredUser.setEmailVerified(false);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setId("tok-1");
        token.setUser(registeredUser);
        token.setExpiresAt(Instant.now().plusSeconds(3600));

        when(emailTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        authService.verifyEmail("valid-raw-token");
        assertTrue(registeredUser.isEmailVerified());
        verify(emailTokenRepository).save(token);

        // 1c. Login with verified account
        when(userRepository.findByEmail("alice@cityengineeringcollege.ac.in")).thenReturn(Optional.of(registeredUser));
        when(passwordEncoder.matches("SecurePass123!", "hashed_pass")).thenReturn(true);
        when(jwtService.generateToken(eq("user-new"), eq("alice@cityengineeringcollege.ac.in"), any())).thenReturn("mock.jwt.token");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("alice@cityengineeringcollege.ac.in");
        loginReq.setPassword("SecurePass123!");

        AuthResponse loginRes = authService.login(loginReq);
        assertEquals("mock.jwt.token", loginRes.getToken());
        assertEquals("user-new", loginRes.getUserId());
    }

    @Test
    @DisplayName("Journey 2 & 3: Project creation, discovery and matching")
    void journey2_and_3_projectCreationAndMatching() {
        // Candidate profile skills
        candidate.setSkills(new HashSet<>(Arrays.asList(
                new Skill("React", SkillCategory.FRONTEND, SkillProficiency.ADVANCED),
                new Skill("TypeScript", SkillCategory.FRONTEND, SkillProficiency.INTERMEDIATE)
        )));
        candidate.setAvailability(new HashSet<>(Collections.singletonList("Weekends")));

        List<MatchingService.MatchResult> ranked = matchingService.rankPostsForCandidate(
                candidate, Collections.singletonList(post));

        assertFalse(ranked.isEmpty());
        MatchingService.MatchResult topMatch = ranked.get(0);
        assertEquals("post-1", topMatch.post().getId());
        assertTrue(topMatch.score() > 0.5, "Expected high composite score based on skill overlap");
        assertTrue(topMatch.matchedSkills().contains("react"));
        assertTrue(topMatch.matchedSkills().contains("typescript"));
    }

    @Test
    @DisplayName("Journey 4 & 5: Collaboration request submission and acceptance for discussion")
    void journey4_and_5_collabRequestAndAcceptForDiscussion() {
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(collabRequestRepository.findTopByRequestorIdAndPosterIdOrderByCreatedAtDesc("user-candidate", "user-owner"))
                .thenReturn(Optional.empty());
        when(blockService.isBlocked("user-candidate", "user-owner")).thenReturn(false);
        when(collabRequestRepository.save(any(CollabRequest.class))).thenAnswer(i -> {
            CollabRequest r = i.getArgument(0);
            r.setId("req-1");
            return r;
        });

        // 4. Submit join request
        CollabRequest request = collabRequestService.sendRequest(candidate, "post-1", "I would love to help build this!");
        assertNotNull(request.getId());
        assertEquals(CollabRequest.RequestStatus.PENDING, request.getStatus());
        assertEquals(candidate, request.getRequestor());
        assertEquals(owner, request.getPoster());

        // 5. Owner accepts request for discussion
        when(collabRequestRepository.findById("req-1")).thenReturn(Optional.of(request));
        when(collaborationRepository.findByProjectPostId("post-1")).thenReturn(Optional.of(collaboration));

        CollabRequest accepted = collabRequestService.respond(owner, "req-1", true);
        assertEquals(CollabRequest.RequestStatus.ACCEPTED, accepted.getStatus());

        // Crucial verification: Accepting for discussion does NOT activate candidate as member
        verify(memberRepository, never()).save(argThat(m -> m.getUser().getId().equals("user-candidate")));
    }

    @Test
    @DisplayName("Journey 6: Direct messaging between discussion participants")
    void journey6_directMessaging() {
        CollabRequest acceptedReq = new CollabRequest();
        acceptedReq.setId("req-1");
        acceptedReq.setPost(post);
        acceptedReq.setPoster(owner);
        acceptedReq.setRequestor(candidate);
        acceptedReq.setStatus(CollabRequest.RequestStatus.ACCEPTED);

        when(collabRequestRepository.findById("req-1")).thenReturn(Optional.of(acceptedReq));
        when(chatMessageRepository.findByCollabRequestIdOrderBySentAtDesc(eq("req-1"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));

        // Both participants have direct chat access
        assertDoesNotThrow(() -> chatMessageService.getConversationMessages("DIRECT", "req-1", 0, 50, owner));
        assertDoesNotThrow(() -> chatMessageService.getConversationMessages("DIRECT", "req-1", 0, 50, candidate));

        // Unauthorized outsider is denied
        User outsider = new User();
        outsider.setId("user-outsider");
        assertThrows(SecurityException.class, () ->
                chatMessageService.getConversationMessages("DIRECT", "req-1", 0, 50, outsider));
    }

    @Test
    @DisplayName("Journey 7, 8 & 9: Formal commitment offer creation, acceptance and automatic membership activation")
    void journey7_8_9_commitmentOfferToActivation() {
        CreateCommitmentOfferDto offerDto = new CreateCommitmentOfferDto();
        offerDto.setProjectId("post-1");
        offerDto.setRecipientId("user-candidate");
        offerDto.setProposedRole("Frontend Lead");
        offerDto.setTerms("10 hours/week, UI architecture");
        offerDto.setDurationDays(7);

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(userRepository.findById("user-candidate")).thenReturn(Optional.of(candidate));
        when(collaborationRepository.findByProjectPostId("post-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-candidate")).thenReturn(Optional.empty());
        when(commitmentOfferRepository.findByProjectIdAndRecipientIdAndStatus("post-1", "user-candidate", CommitmentOfferStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(commitmentOfferRepository.save(any(CommitmentOffer.class))).thenAnswer(i -> {
            CommitmentOffer o = i.getArgument(0);
            o.setId("offer-1");
            return o;
        });

        // 7. Owner sends formal commitment offer
        CommitmentOfferResponse offerRes = commitmentOfferService.createOffer(owner, offerDto);
        assertEquals("offer-1", offerRes.getId());
        assertEquals(CommitmentOfferStatus.PENDING, offerRes.getStatus());
        assertEquals("Frontend Lead", offerRes.getProposedRole());

        // 8 & 9. Candidate accepts offer -> Automatic Membership Activation
        CommitmentOffer pendingOffer = new CommitmentOffer();
        pendingOffer.setId("offer-1");
        pendingOffer.setProjectPost(post);
        pendingOffer.setSender(owner);
        pendingOffer.setRecipient(candidate);
        pendingOffer.setProposedRole("Frontend Lead");
        pendingOffer.setStatus(CommitmentOfferStatus.PENDING);
        pendingOffer.setCreatedAt(Instant.now());
        pendingOffer.setExpiresAt(Instant.now().plusSeconds(86400));

        when(commitmentOfferRepository.findById("offer-1")).thenReturn(Optional.of(pendingOffer));
        when(memberRepository.save(any(CollaborationMember.class))).thenAnswer(i -> i.getArgument(0));

        CommitmentOfferResponse acceptedRes = commitmentOfferService.acceptOffer("offer-1", candidate);
        assertEquals(CommitmentOfferStatus.ACCEPTED, acceptedRes.getStatus());

        // Verified: Membership activated with proposed role
        verify(memberRepository).save(argThat(m ->
                m.getCollaboration().getId().equals("collab-1") &&
                m.getUser().getId().equals("user-candidate") &&
                m.getRole() == MemberRole.MEMBER &&
                m.getStatus() == MemberStatus.ACTIVE
        ));

        // Verified: Audit event logged
        verify(auditEventRepository).save(argThat(event ->
                event.getCollaborationId().equals("collab-1") &&
                event.getUserId().equals("user-candidate") &&
                event.getEventType() == MembershipEventType.JOINED
        ));
    }

    @Test
    @DisplayName("Journey 10 & 11: Project chat access, messaging and read receipts")
    void journey10_11_projectChatAndReadReceipts() {
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-candidate", MemberStatus.ACTIVE))
                .thenReturn(true);

        ChatMessage msg1 = new ChatMessage();
        msg1.setId("msg-1");
        msg1.setCollaboration(collaboration);
        msg1.setSender(owner);
        msg1.setContent("Welcome to the team!");
        msg1.setSentAt(Instant.now());

        when(chatMessageRepository.findByCollaborationIdOrderBySentAtDesc(eq("collab-1"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.singletonList(msg1)));
        when(chatMessageRepository.findFirstByCollaborationIdOrderBySentAtDesc("collab-1"))
                .thenReturn(Optional.of(msg1));
        when(conversationReadStateRepository.findByUserIdAndConversationTypeAndConversationId(
                "user-candidate", "COLLABORATION", "collab-1")).thenReturn(Optional.empty());

        // 10. Candidate retrieves project messages
        List<ChatMessageV2Response> messages = chatMessageService.getConversationMessages(
                "COLLABORATION", "collab-1", 0, 50, candidate);
        assertEquals(1, messages.size());
        assertEquals("Welcome to the team!", messages.get(0).getContent());

        // 11. Mark as read
        chatMessageService.markConversationAsRead("COLLABORATION", "collab-1", candidate);
        verify(conversationReadStateRepository).save(argThat(state ->
                state.getUser().getId().equals("user-candidate") &&
                state.getConversationId().equals("collab-1") &&
                state.getLastReadMessageId().equals("msg-1")
        ));
        verify(chatMessageReadRepository).save(any(ChatMessageRead.class));
    }

    @Test
    @DisplayName("Journey 12: Member removal and instant WebSocket access revocation")
    void journey12_memberRemovalAndAccessRevocation() {
        CollaborationMember activeCandidateMember = new CollaborationMember();
        activeCandidateMember.setId("cm-candidate");
        activeCandidateMember.setCollaboration(collaboration);
        activeCandidateMember.setUser(candidate);
        activeCandidateMember.setRole(MemberRole.MEMBER);
        activeCandidateMember.setStatus(MemberStatus.ACTIVE);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-owner"))
                .thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-candidate"))
                .thenReturn(Optional.of(activeCandidateMember));

        MemberActionWithReasonDto removeDto = new MemberActionWithReasonDto();
        removeDto.setReason("Violation of project code of conduct");

        // Owner removes candidate
        collaborationService.removeMemberWithReason("collab-1", "user-candidate", removeDto, owner);

        assertEquals(MemberStatus.REMOVED, activeCandidateMember.getStatus());
        assertEquals("Violation of project code of conduct", activeCandidateMember.getRemovalReason());
        verify(memberRepository).save(activeCandidateMember);

        // Verify WebSocket broadcast revocation interceptor drops messages for removed member
        ExecutorSubscribableChannel clientInboundChannel = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel clientOutboundChannel = new ExecutorSubscribableChannel();
        ExecutorSubscribableChannel brokerChannel = new ExecutorSubscribableChannel();

        clientInboundChannel.addInterceptor(webSocketAuthInterceptor);
        clientOutboundChannel.addInterceptor(webSocketAuthInterceptor);

        SimpleBrokerMessageHandler broker = new SimpleBrokerMessageHandler(
                clientInboundChannel, clientOutboundChannel, brokerChannel, List.of("/topic")
        );
        broker.start();

        List<Message<?>> deliveredToRevoked = new ArrayList<>();
        clientOutboundChannel.subscribe(msg -> {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(msg);
            if ("sess-candidate".equals(accessor.getSessionId())) {
                deliveredToRevoked.add(msg);
            }
        });

        when(jwtService.isTokenValid("token-candidate")).thenReturn(true);
        when(jwtService.extractUserId("token-candidate")).thenReturn("user-candidate");
        when(userRepository.findById("user-candidate")).thenReturn(Optional.of(candidate));

        // Connect session
        StompHeaderAccessor conn = StompHeaderAccessor.create(StompCommand.CONNECT);
        conn.setSessionId("sess-candidate");
        conn.setNativeHeader("Authorization", "Bearer token-candidate");
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], conn.getMessageHeaders()));

        // Initially active when subscribing
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-candidate", MemberStatus.ACTIVE))
                .thenReturn(true);

        StompHeaderAccessor sub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        sub.setSessionId("sess-candidate");
        sub.setSubscriptionId("sub-collab");
        sub.setDestination("/topic/collaboration-chat/collab-1");
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], sub.getMessageHeaders()));

        deliveredToRevoked.clear();

        // Simulate revocation: candidate is now REMOVED
        when(memberRepository.existsByCollaborationIdAndUserIdAndStatus("collab-1", "user-candidate", MemberStatus.ACTIVE))
                .thenReturn(false);

        // Server broadcasts message to topic
        SimpMessageHeaderAccessor msgHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        msgHeaders.setDestination("/topic/collaboration-chat/collab-1");
        brokerChannel.send(MessageBuilder.createMessage("Confidential Project Update", msgHeaders.getMessageHeaders()));

        assertEquals(0, deliveredToRevoked.size(), "Revoked member must NOT receive broadcast messages");

        broker.stop();
    }

    @Test
    @DisplayName("Journey 13: Voluntary departure and owner approval")
    void journey13_voluntaryDepartureAndApproval() {
        CollaborationMember activeCandidateMember = new CollaborationMember();
        activeCandidateMember.setId("cm-candidate");
        activeCandidateMember.setCollaboration(collaboration);
        activeCandidateMember.setUser(candidate);
        activeCandidateMember.setRole(MemberRole.MEMBER);
        activeCandidateMember.setStatus(MemberStatus.ACTIVE);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-candidate"))
                .thenReturn(Optional.of(activeCandidateMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-owner"))
                .thenReturn(Optional.of(ownerMember));

        // 1. Candidate requests departure
        DepartureRequestDto depDto = new DepartureRequestDto();
        depDto.setReason("Accepted conflicting internship offer");
        collaborationService.requestDeparture("collab-1", candidate, depDto);

        assertEquals(DepartureStatus.PENDING_APPROVAL, activeCandidateMember.getDepartureStatus());
        assertEquals("Accepted conflicting internship offer", activeCandidateMember.getDepartureReason());
        assertNotNull(activeCandidateMember.getDepartureRequestedAt());

        // 2. Owner approves departure
        collaborationService.resolveDeparture("collab-1", "user-candidate", true, owner);

        assertEquals(MemberStatus.LEFT, activeCandidateMember.getStatus());
        assertEquals(DepartureStatus.APPROVED, activeCandidateMember.getDepartureStatus());
        assertNotNull(activeCandidateMember.getDepartureResolvedAt());
        verify(memberRepository, times(2)).save(activeCandidateMember);
    }

    @Test
    @DisplayName("Journey 14: Ownership transfer request, acceptance, and role exchange")
    void journey14_ownershipTransferFlow() {
        CollaborationMember activeCandidateMember = new CollaborationMember();
        activeCandidateMember.setId("cm-candidate");
        activeCandidateMember.setCollaboration(collaboration);
        activeCandidateMember.setUser(candidate);
        activeCandidateMember.setRole(MemberRole.MEMBER);
        activeCandidateMember.setStatus(MemberStatus.ACTIVE);

        when(collaborationRepository.findById("collab-1")).thenReturn(Optional.of(collaboration));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-owner"))
                .thenReturn(Optional.of(ownerMember));
        when(memberRepository.findByCollaborationIdAndUserId("collab-1", "user-candidate"))
                .thenReturn(Optional.of(activeCandidateMember));
        when(transferRepository.save(any(OwnershipTransferRequest.class))).thenAnswer(i -> {
            OwnershipTransferRequest req = i.getArgument(0);
            req.setId("transfer-1");
            return req;
        });

        // 1. Owner initiates transfer
        TransferOwnershipDto transferDto = new TransferOwnershipDto();
        transferDto.setNewOwnerId("user-candidate");

        OwnershipTransferResponse initiateRes = collaborationService.initiateOwnershipTransfer(
                "collab-1", transferDto, owner);
        assertEquals("transfer-1", initiateRes.getId());
        assertEquals("PENDING", initiateRes.getStatus().name());

        // 2. Candidate accepts transfer
        OwnershipTransferRequest pendingReq = new OwnershipTransferRequest();
        pendingReq.setId("transfer-1");
        pendingReq.setCollaboration(collaboration);
        pendingReq.setSender(owner);
        pendingReq.setRecipient(candidate);
        pendingReq.setStatus(OwnershipTransferRequest.TransferStatus.PENDING);

        when(transferRepository.findById("transfer-1")).thenReturn(Optional.of(pendingReq));
        when(commitmentOfferRepository.findByProjectPostIdOrderByCreatedAtDesc("post-1"))
                .thenReturn(Collections.emptyList());

        OwnershipTransferResponse acceptRes = collaborationService.acceptOwnershipTransfer(
                "transfer-1", candidate);

        assertEquals("ACCEPTED", acceptRes.getStatus().name());
        assertEquals(MemberRole.OWNER, activeCandidateMember.getRole(), "Candidate must now be OWNER");
        assertEquals(MemberRole.MEMBER, ownerMember.getRole(), "Previous owner must be demoted to MEMBER");
        assertEquals(candidate, post.getPoster(), "ProjectPost owner must be updated to candidate");
    }
}
