"use client";

import React, { useState, useEffect, useRef } from "react";
import { 
  messagingService, 
  UnifiedConversation, 
  ChatMessageV2, 
  commitmentOfferService,
  CommitmentOffer,
  fileService
} from "@/lib/api";
import { getWsUrl } from "@/lib/api";
import { Client } from "@stomp/stompjs";
import { getToken } from "@/lib/auth";
import Link from "next/link";
import { 
  Send, Reply, Edit2, Trash2, Check, CheckCheck, Users, 
  MessageSquare, User, Clock, AlertCircle, FileText, CheckCircle, XCircle,
  Award, Shield, Calendar, X, PlusCircle, ArrowRight
} from "lucide-react";

export default function MessagesPage() {
  const [conversations, setConversations] = useState<UnifiedConversation[]>([]);
  const [selectedConv, setSelectedConv] = useState<UnifiedConversation | null>(null);
  const [messages, setMessages] = useState<ChatMessageV2[]>([]);
  const [inputMessage, setInputMessage] = useState("");
  const [replyTo, setReplyTo] = useState<ChatMessageV2 | null>(null);
  const [editingMessage, setEditingMessage] = useState<ChatMessageV2 | null>(null);
  const [editInput, setEditInput] = useState("");
  const [typingUsers, setTypingUsers] = useState<string[]>([]);
  const [activeTab, setActiveTab] = useState<"ALL" | "DIRECT" | "COLLABORATION">("ALL");
  const [loading, setLoading] = useState(true);

  // Commitment offer state
  const [receivedOffers, setReceivedOffers] = useState<CommitmentOffer[]>([]);
  const [sentOffers, setSentOffers] = useState<CommitmentOffer[]>([]);
  const [actionOfferId, setActionOfferId] = useState<string | null>(null);
  const [offerActionMsg, setOfferActionMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);

  // File reference state
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);
  const [showFilePicker, setShowFilePicker] = useState(false);
  const [availableFiles, setAvailableFiles] = useState<any[]>([]);

  // Create Offer Modal state
  const [showCreateOfferModal, setShowCreateOfferModal] = useState(false);
  const [offerRole, setOfferRole] = useState("");
  const [offerTerms, setOfferTerms] = useState("");
  const [offerDuration, setOfferDuration] = useState("7");
  const [offerSubmitting, setOfferSubmitting] = useState(false);
  const [offerError, setOfferError] = useState<string | null>(null);

  const stompClientRef = useRef<Client | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [currentUserId, setCurrentUserId] = useState<string>("");

  // Helper: Format file size
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  useEffect(() => {
    const token = getToken();
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        setCurrentUserId(payload.sub || "");
      } catch {}
    }
  }, []);

  // 1. Fetch conversations & offers
  const loadConversations = async () => {
    try {
      const convs = await messagingService.getConversations();
      setConversations(convs);
      if (convs.length > 0 && !selectedConv) {
        setSelectedConv(convs[0]);
      }
    } catch (err) {
      console.error("Failed to load conversations:", err);
    } finally {
      setLoading(false);
    }
  };

  const loadOffers = async () => {
    try {
      const [received, sent] = await Promise.all([
        commitmentOfferService.getReceivedOffers().catch(() => []),
        commitmentOfferService.getSentOffers().catch(() => [])
      ]);
      setReceivedOffers(received);
      setSentOffers(sent);
    } catch (err) {
      console.error("Failed to load commitment offers:", err);
    }
  };

  useEffect(() => {
    loadConversations();
    loadOffers();
  }, []);

  // Auto-dismiss offer action message
  useEffect(() => {
    if (offerActionMsg) {
      const timer = setTimeout(() => setOfferActionMsg(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [offerActionMsg]);

  // Load available files when file picker opens or conversation changes
  useEffect(() => {
    if (showFilePicker && selectedConv?.conversationType === "COLLABORATION" && selectedConv.projectId) {
      const loadFiles = async () => {
        try {
          const response = await fileService.listFiles(selectedConv.projectId!);
          // Filter out folders and trashed files
          const accessibleFiles = response.files.filter(
            (f: any) => !f.isFolder && !f.isInTrash && f.safetyState === "CLEARED"
          );
          setAvailableFiles(accessibleFiles);
        } catch (err) {
          console.error("Failed to load files:", err);
          setAvailableFiles([]);
        }
      };
      loadFiles();
    }
  }, [showFilePicker, selectedConv]);

  // Check if current collaboration is archived
  const isArchived = selectedConv?.conversationType === "COLLABORATION" && selectedConv?.status === "ARCHIVED";

  // 2. Fetch messages when conversation changes
  useEffect(() => {
    if (!selectedConv) return;

    messagingService.getMessages(selectedConv.conversationType, selectedConv.conversationId)
      .then((msgs) => {
        setMessages(msgs);
        messagingService.markRead(selectedConv.conversationType, selectedConv.conversationId)
          .catch(() => {});
      })
      .catch((err) => console.error("Failed to load messages:", err));

    setReplyTo(null);
    setEditingMessage(null);
    setTypingUsers([]);
  }, [selectedConv]);

  // 3. WebSocket Setup
  useEffect(() => {
    if (!selectedConv) return;

    const token = getToken();
    const wsUrl = getWsUrl();

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        const topic = selectedConv.conversationType === "DIRECT"
          ? `/topic/chat/${selectedConv.conversationId}`
          : `/topic/collaboration-chat/${selectedConv.conversationId}`;

        client.subscribe(topic, (msg) => {
          const newMsg: ChatMessageV2 = JSON.parse(msg.body);
          setMessages((prev) => {
            const idx = prev.findIndex((m) => m.id === newMsg.id);
            if (idx >= 0) {
              const updated = [...prev];
              updated[idx] = newMsg;
              return updated;
            }
            return [...prev, newMsg];
          });
        });
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [selectedConv, currentUserId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputMessage.trim() || !selectedConv || !stompClientRef.current) return;

    const destination = selectedConv.conversationType === "DIRECT"
      ? `/app/chat/${selectedConv.conversationId}`
      : `/app/collaboration-chat/${selectedConv.conversationId}`;

    stompClientRef.current.publish({
      destination,
      body: JSON.stringify({
        content: inputMessage.trim(),
        replyToId: replyTo?.id,
        referencedFileId: selectedFileId,
      }),
    });

    setInputMessage("");
    setReplyTo(null);
    setSelectedFileId(null);
  };

  const handleEditSubmit = async (messageId: string) => {
    if (!editInput.trim()) return;
    try {
      const updated = await messagingService.editMessage(messageId, editInput.trim());
      setMessages((prev) => prev.map((m) => (m.id === updated.id ? updated : m)));
      setEditingMessage(null);
      setEditInput("");
    } catch (err: any) {
      alert(err.message || "Failed to edit message");
    }
  };

  const handleDeleteMessage = async (messageId: string) => {
    if (!confirm("Are you sure you want to delete this message?")) return;
    try {
      const updated = await messagingService.deleteMessage(messageId);
      setMessages((prev) => prev.map((m) => (m.id === updated.id ? updated : m)));
    } catch (err: any) {
      alert(err.message || "Failed to delete message");
    }
  };

  const handleAcceptOffer = async (offerId: string) => {
    try {
      setActionOfferId(offerId);
      await commitmentOfferService.acceptOffer(offerId);
      setOfferActionMsg({ type: "success", text: "Commitment offer accepted! Workspace access activated." });
      await Promise.all([loadOffers(), loadConversations()]);
    } catch (err: any) {
      setOfferActionMsg({ type: "error", text: err.message || "Failed to accept offer" });
    } finally {
      setActionOfferId(null);
    }
  };

  const handleDeclineOffer = async (offerId: string) => {
    try {
      setActionOfferId(offerId);
      await commitmentOfferService.declineOffer(offerId);
      setOfferActionMsg({ type: "success", text: "Commitment offer declined." });
      await loadOffers();
    } catch (err: any) {
      setOfferActionMsg({ type: "error", text: err.message || "Failed to decline offer" });
    } finally {
      setActionOfferId(null);
    }
  };

  const handleWithdrawOffer = async (offerId: string) => {
    try {
      setActionOfferId(offerId);
      await commitmentOfferService.withdrawOffer(offerId);
      setOfferActionMsg({ type: "success", text: "Commitment offer withdrawn." });
      await loadOffers();
    } catch (err: any) {
      setOfferActionMsg({ type: "error", text: err.message || "Failed to withdraw offer" });
    } finally {
      setActionOfferId(null);
    }
  };

  const handleCreateOffer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedConv || !selectedConv.projectId || !selectedConv.otherUserId) return;
    if (!offerRole.trim()) {
      setOfferError("Proposed role is required");
      return;
    }

    setOfferSubmitting(true);
    setOfferError(null);
    try {
      await commitmentOfferService.createOffer({
        projectId: selectedConv.projectId,
        recipientId: selectedConv.otherUserId,
        proposedRole: offerRole.trim(),
        terms: offerTerms.trim() || undefined,
        collabRequestId: selectedConv.conversationId,
        durationDays: parseInt(offerDuration, 10) || 7,
      });
      setOfferActionMsg({ type: "success", text: "Formal commitment offer sent!" });
      setShowCreateOfferModal(false);
      setOfferRole("");
      setOfferTerms("");
      setOfferDuration("7");
      await loadOffers();
    } catch (err: any) {
      setOfferError(err.message || "Failed to send offer");
    } finally {
      setOfferSubmitting(false);
    }
  };

  const filteredConversations = conversations.filter((c) => {
    if (activeTab === "ALL") return true;
    return c.conversationType === activeTab;
  });

  // Offers relevant to the currently selected conversation
  const relevantOffers = selectedConv?.conversationType === "DIRECT"
    ? [...receivedOffers, ...sentOffers].filter(o => 
        (o.collabRequestId && o.collabRequestId === selectedConv.conversationId) ||
        (o.projectId === selectedConv.projectId && (o.recipientId === selectedConv.otherUserId || o.senderId === selectedConv.otherUserId))
      )
    : [];

  const pendingReceivedOffers = receivedOffers.filter(o => o.status === "PENDING");

  // Can the current user send an offer in this conversation?
  // Only the project owner/poster in a DIRECT conversation where no active PENDING offer exists
  const isDirectOwner = Boolean(
    selectedConv && 
    selectedConv.conversationType === "DIRECT" && 
    (selectedConv.isPoster === true || (selectedConv.posterId && selectedConv.posterId === currentUserId))
  );

  const hasPendingOfferInSelectedConv = relevantOffers.some(o => o.status === "PENDING");

  return (
    <>
      <div className="flex h-full bg-huddle-black text-white relative overflow-hidden">
        {/* Left Rail: Conversations */}
        <div className="w-80 md:w-96 border-r border-white/[0.06] flex flex-col bg-[#0d0d12]/95 backdrop-blur-md flex-shrink-0 z-10">
          {/* Header & Tabs */}
          <div className="p-4 border-b border-white/[0.06] flex-shrink-0">
            <h1 className="text-xl font-bold tracking-tight text-white mb-3">Messages</h1>
            <div className="flex gap-1.5 p-1 bg-white/[0.04] border border-white/[0.06] rounded-xl text-xs font-medium">
              <button
                onClick={() => setActiveTab("ALL")}
                className={`flex-1 py-1.5 rounded-lg transition-all duration-200 ${activeTab === "ALL" ? "bg-white/10 text-white shadow-sm font-semibold border border-white/[0.06]" : "text-gray-400 hover:text-white hover:bg-white/[0.03]"}`}
              >
                All
              </button>
              <button
                onClick={() => setActiveTab("DIRECT")}
                className={`flex-1 py-1.5 rounded-lg transition-all duration-200 ${activeTab === "DIRECT" ? "bg-white/10 text-white shadow-sm font-semibold border border-white/[0.06]" : "text-gray-400 hover:text-white hover:bg-white/[0.03]"}`}
              >
                Direct
              </button>
              <button
                onClick={() => setActiveTab("COLLABORATION")}
                className={`flex-1 py-1.5 rounded-lg transition-all duration-200 ${activeTab === "COLLABORATION" ? "bg-white/10 text-white shadow-sm font-semibold border border-white/[0.06]" : "text-gray-400 hover:text-white hover:bg-white/[0.03]"}`}
              >
                Workspaces
              </button>
            </div>
          </div>

          {/* Pending Offers Banner (if any received) */}
          {pendingReceivedOffers.length > 0 && (
            <div className="p-3 bg-gradient-to-r from-huddle-purple/10 via-huddle-pink/10 to-transparent border-b border-huddle-purple/20 backdrop-blur-sm">
              <div className="flex items-center gap-2 text-purple-300 text-xs font-semibold mb-2">
                <FileText className="w-4 h-4 text-huddle-coral" />
                <span>Pending Commitment Offers ({pendingReceivedOffers.length})</span>
              </div>
              <div className="space-y-2">
                {pendingReceivedOffers.map((offer) => (
                  <div key={offer.id} className="p-2.5 rounded-xl bg-white/[0.04] border border-white/[0.08] text-xs shadow-sm">
                    <div className="flex items-start justify-between gap-1 mb-1">
                      <p className="font-semibold text-white">{offer.projectTitle}</p>
                      <span className="text-[10px] text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20 font-bold uppercase">
                        Pending
                      </span>
                    </div>
                    <p className="text-gray-400 text-[11px]">Role: <span className="text-purple-300 font-medium">{offer.proposedRole}</span></p>
                    {offer.terms && <p className="text-gray-300 text-[11px] mt-1 bg-black/30 p-2 rounded border border-white/5 italic">"{offer.terms}"</p>}
                    <div className="flex gap-2 mt-2.5">
                      <button
                        onClick={() => handleAcceptOffer(offer.id)}
                        disabled={actionOfferId === offer.id}
                        className="flex-1 py-1.5 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/30 font-semibold flex items-center justify-center gap-1 transition-all text-xs disabled:opacity-50"
                      >
                        <CheckCircle className="w-3.5 h-3.5" /> Accept &amp; Join
                      </button>
                      <button
                        onClick={() => handleDeclineOffer(offer.id)}
                        disabled={actionOfferId === offer.id}
                        className="py-1.5 px-3 rounded-lg bg-white/10 hover:bg-red-500/20 hover:text-red-400 text-gray-300 font-semibold flex items-center justify-center gap-1 transition-colors text-xs disabled:opacity-50"
                      >
                        <XCircle className="w-3.5 h-3.5" /> Decline
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Conversations List */}
          <div className="flex-1 overflow-y-auto divide-y divide-white/5">
            {loading ? (
              <div className="p-6 text-center text-gray-500 text-sm">Loading conversations...</div>
            ) : filteredConversations.length === 0 ? (
              <div className="p-6 text-center text-gray-500 text-sm">No conversations found</div>
            ) : (
              filteredConversations.map((c) => {
                const isSelected = selectedConv?.conversationId === c.conversationId && selectedConv.conversationType === c.conversationType;
                return (
                  <button
                    key={`${c.conversationType}-${c.conversationId}`}
                    onClick={() => setSelectedConv(c)}
                    className={`w-full text-left p-3.5 flex items-start gap-3 transition-all duration-200 relative ${
                      isSelected
                        ? "bg-white/[0.08] shadow-[inset_0_1px_0_0_rgba(255,255,255,0.05)]"
                        : "hover:bg-white/[0.03]"
                    }`}
                  >
                    {isSelected && (
                      <div className="absolute left-0 top-2 bottom-2 w-1 bg-gradient-to-b from-huddle-coral to-huddle-pink rounded-r shadow-[0_0_8px_rgba(255,71,133,0.5)]" />
                    )}
                    <div className="w-10 h-10 rounded-full bg-white/[0.06] border border-white/[0.08] flex items-center justify-center flex-shrink-0 text-gray-300">
                      {c.conversationType === "COLLABORATION" ? (
                        <Users className="w-5 h-5 text-purple-400" />
                      ) : (
                        <User className="w-5 h-5 text-huddle-pink" />
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium text-sm text-white truncate">{c.title}</span>
                        {c.unreadCount > 0 && (
                          <span className="px-2 py-0.5 text-[10px] font-bold bg-gradient-to-r from-huddle-coral to-huddle-pink text-white rounded-full shadow-[0_0_8px_rgba(255,71,133,0.4)]">
                            {c.unreadCount}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-400 truncate">{c.lastMessage || "No messages yet"}</p>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>

        {/* Right Pane: Chat Window */}
        <div className="flex-1 flex flex-col bg-[#0a0a0f] relative overflow-hidden">
          {/* Subtle purple & pink ambient lighting */}
          <div className="absolute top-10 right-1/4 w-[380px] h-[380px] bg-huddle-purple opacity-[0.04] blur-[100px] rounded-full pointer-events-none z-0" />
          <div className="absolute bottom-20 left-1/4 w-[320px] h-[320px] bg-huddle-pink opacity-[0.03] blur-[90px] rounded-full pointer-events-none z-0" />

          {selectedConv ? (
            <>
              {/* Header */}
              <div className="h-16 border-b border-white/[0.06] px-6 flex items-center justify-between bg-[#0e0e14]/90 backdrop-blur-md flex-shrink-0 z-10">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-white/[0.06] border border-white/[0.08] flex items-center justify-center text-gray-300">
                    {selectedConv.conversationType === "COLLABORATION" ? (
                      <Users className="w-4 h-4 text-purple-400" />
                    ) : (
                      <User className="w-4 h-4 text-huddle-pink" />
                    )}
                  </div>
                  <div>
                    <h2 className="font-semibold text-sm text-white">{selectedConv.title}</h2>
                    <span className="text-xs text-gray-400">
                      {selectedConv.conversationType === "COLLABORATION" ? "Workspace Group Chat" : "Direct Message Discussion"}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {/* Send Formal Offer button — only visible to the project owner in DIRECT chats */}
                  {isDirectOwner && (
                    <button
                      onClick={() => {
                        setOfferError(null);
                        setShowCreateOfferModal(true);
                      }}
                      disabled={hasPendingOfferInSelectedConv}
                      className="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-huddle-coral via-huddle-pink to-huddle-purple hover:opacity-95 disabled:opacity-40 text-white text-xs font-semibold flex items-center gap-1.5 shadow-[0_0_15px_rgba(255,71,133,0.3)] transition-all duration-200"
                      title={hasPendingOfferInSelectedConv ? "A pending offer already exists" : "Send Formal Commitment Offer"}
                    >
                      <Award className="w-3.5 h-3.5" />
                      {hasPendingOfferInSelectedConv ? "Offer Pending" : "Send Formal Offer"}
                    </button>
                  )}

                  {selectedConv.projectId && (
                    <Link
                      href={`/post/${selectedConv.projectId}`}
                      className="px-3 py-1.5 rounded-xl bg-white/[0.04] hover:bg-white/[0.08] text-gray-300 text-xs font-medium border border-white/[0.08] transition"
                    >
                      View Post
                    </Link>
                  )}
                </div>
              </div>

              {/* Action Banner Message */}
              {offerActionMsg && (
                <div className={`px-6 py-2.5 text-xs font-medium flex items-center gap-2 ${
                  offerActionMsg.type === "success" 
                    ? "bg-emerald-500/10 text-emerald-400 border-b border-emerald-500/20" 
                    : "bg-red-500/10 text-red-400 border-b border-red-500/20"
                }`}>
                  {offerActionMsg.type === "success" ? <CheckCircle className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                  <span>{offerActionMsg.text}</span>
                </div>
              )}

              {/* Contextual Offer Ribbon for this Direct Chat */}
              {relevantOffers.length > 0 && (
                <div className="px-6 py-3 bg-white/[0.02] border-b border-white/[0.06] space-y-2 flex-shrink-0 z-10">
                  {relevantOffers.map((offer) => {
                    const isSender = offer.senderId === currentUserId;
                    const isRecipient = offer.recipientId === currentUserId;
                    const isPending = offer.status === "PENDING";
                    const isAccepted = offer.status === "ACCEPTED";
                    const isDeclined = offer.status === "DECLINED";
                    const isWithdrawn = offer.status === "WITHDRAWN";
                    const isExpired = offer.status === "EXPIRED";

                    return (
                      <div 
                        key={offer.id} 
                        className={`p-3 rounded-xl border flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs ${
                          isPending 
                            ? "bg-huddle-purple/10 border-huddle-purple/30 backdrop-blur-sm" 
                            : isAccepted 
                            ? "bg-emerald-950/20 border-emerald-500/30" 
                            : "bg-white/[0.02] border-white/5 opacity-75"
                        }`}
                      >
                        <div className="flex items-start gap-2.5">
                          <Award className={`w-4 h-4 flex-shrink-0 mt-0.5 ${
                            isPending ? "text-purple-400" : isAccepted ? "text-emerald-400" : "text-gray-400"
                          }`} />
                          <div>
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="font-semibold text-white">Formal Commitment Offer</span>
                              <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${
                                isPending ? "bg-amber-400/10 text-amber-400 border border-amber-400/20" :
                                isAccepted ? "bg-emerald-400/10 text-emerald-400 border border-emerald-400/20" :
                                isDeclined ? "bg-red-400/10 text-red-400 border border-red-400/20" :
                                isWithdrawn ? "bg-gray-400/10 text-gray-400 border border-gray-400/20" :
                                "bg-gray-500/10 text-gray-400"
                              }`}>
                                {offer.status}
                              </span>
                            </div>
                            <p className="text-gray-300 mt-1">
                              Role: <span className="font-semibold text-purple-300">{offer.proposedRole}</span>
                              {offer.terms && <span className="text-gray-400"> — "{offer.terms}"</span>}
                            </p>
                            {offer.expiresAt && isPending && (
                              <p className="text-[11px] text-gray-500 mt-0.5 flex items-center gap-1">
                                <Clock className="w-3 h-3" />
                                Expires: {new Date(offer.expiresAt).toLocaleDateString()}
                              </p>
                            )}
                          </div>
                        </div>

                        {/* Actions for this offer */}
                        <div className="flex items-center gap-2 flex-shrink-0 self-end md:self-center">
                          {isPending && isRecipient && (
                            <>
                              <button
                                onClick={() => handleAcceptOffer(offer.id)}
                                disabled={actionOfferId === offer.id}
                                className="px-3 py-1.5 rounded-lg bg-emerald-500/80 hover:bg-emerald-500 text-white font-semibold flex items-center gap-1 shadow-sm transition disabled:opacity-50"
                              >
                                <CheckCircle className="w-3.5 h-3.5" /> Accept
                              </button>
                              <button
                                onClick={() => handleDeclineOffer(offer.id)}
                                disabled={actionOfferId === offer.id}
                                className="px-3 py-1.5 rounded-lg bg-white/10 hover:bg-red-500/20 hover:text-red-400 text-gray-300 font-semibold flex items-center gap-1 transition disabled:opacity-50"
                              >
                                <XCircle className="w-3.5 h-3.5" /> Decline
                              </button>
                            </>
                          )}
                          {isPending && isSender && (
                            <button
                              onClick={() => handleWithdrawOffer(offer.id)}
                              disabled={actionOfferId === offer.id}
                              className="px-3 py-1.5 rounded-lg bg-white/10 hover:bg-red-500/20 hover:text-red-400 text-gray-300 font-semibold flex items-center gap-1 transition disabled:opacity-50"
                            >
                              <X className="w-3.5 h-3.5" /> Withdraw Offer
                            </button>
                          )}
                          {isAccepted && (
                            <span className="text-emerald-400 font-semibold text-xs flex items-center gap-1">
                              <CheckCircle className="w-3.5 h-3.5" /> Membership Activated
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Messages Body */}
              <div className="flex-1 overflow-y-auto p-6 space-y-4 custom-scrollbar z-10 relative">
                {messages.map((msg) => {
                  const isMe = msg.senderId === currentUserId;
                  const isDeleted = Boolean(msg.isDeleted);
                  const isEditing = editingMessage?.id === msg.id;

                  return (
                    <div key={msg.id} className={`flex flex-col ${isMe ? "items-end" : "items-start"}`}>
                      {/* Reply preview if replying to another message */}
                      {msg.replyToContent && (
                        <div className="mb-1 text-xs text-gray-400 flex items-center gap-1.5 pl-2.5 border-l-2 border-huddle-pink/60">
                          <Reply className="w-3 h-3" />
                          <span>Replying to <span className="text-gray-300 font-medium">{msg.replyToSenderName || "User"}</span>: {msg.replyToContent}</span>
                        </div>
                      )}

                      <div className={`group relative max-w-xl rounded-2xl px-4 py-2.5 text-sm transition-all shadow-sm ${
                        isMe
                          ? "bg-gradient-to-br from-huddle-coral/90 via-huddle-pink/90 to-huddle-purple/90 text-white shadow-[0_4px_16px_rgba(255,71,133,0.15)] rounded-br-sm border border-white/10"
                          : "bg-huddle-charcoal/80 text-gray-100 border border-white/[0.08] shadow-[0_4px_12px_rgba(0,0,0,0.3)] rounded-bl-sm backdrop-blur-sm"
                      }`}>
                        {!isMe && selectedConv.conversationType === "COLLABORATION" && (
                          <p className="text-xs font-semibold text-purple-300 mb-1">{msg.senderName}</p>
                        )}

                        {isEditing ? (
                          <div className="flex items-center gap-2 mt-1">
                            <input
                              type="text"
                              value={editInput}
                              onChange={(e) => setEditInput(e.target.value)}
                              className="bg-black/30 border border-white/20 rounded px-2 py-1 text-xs text-white focus:outline-none"
                              autoFocus
                            />
                            <button
                              onClick={() => handleEditSubmit(msg.id)}
                              className="p-1 rounded bg-green-600 hover:bg-green-500 text-white"
                            >
                              <Check className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => setEditingMessage(null)}
                              className="p-1 rounded bg-white/10 hover:bg-white/20 text-gray-300 text-xs"
                            >
                              Cancel
                            </button>
                          </div>
                        ) : (
                          <>
                            <p className={`${isDeleted ? "italic text-gray-400" : ""}`}>{msg.content}</p>
                            
                            {/* File Reference Display */}
                            {msg.referencedFileId && (
                              <div className="mt-2 p-2.5 rounded-lg bg-black/20 border border-white/10">
                                <div className="flex items-center gap-2">
                                  <FileText className="w-4 h-4 text-purple-400" />
                                  <div className="flex-1 min-w-0">
                                    <p className="text-xs font-medium text-white truncate">{msg.referencedFileName}</p>
                                    <p className="text-[10px] text-gray-400">
                                      {msg.referencedFileSizeBytes ? formatFileSize(msg.referencedFileSizeBytes) : ''}
                                      {msg.referencedFileType && ` • ${msg.referencedFileType.split('/')[1]?.toUpperCase() || 'FILE'}`}
                                    </p>
                                  </div>
                                  {msg.referencedFileAccessible === false && (
                                    <div className="flex items-center gap-1 text-[10px] text-amber-400">
                                      <AlertCircle className="w-3 h-3" />
                                      <span>{msg.referencedFileStatus === 'IN_TRASH' ? 'In Trash' : msg.referencedFileStatus === 'SAFETY_BLOCKED' ? 'Blocked' : 'Unavailable'}</span>
                                    </div>
                                  )}
                                  {msg.referencedFileAccessible === true && selectedConv.projectId && (
                                    <a
                                      href={`/collaborations/${selectedConv.projectId}#files`}
                                      className="p-1.5 rounded bg-white/10 hover:bg-white/20 text-gray-300 hover:text-white transition"
                                      title="View in Files"
                                    >
                                      <ArrowRight className="w-3.5 h-3.5" />
                                    </a>
                                  )}
                                </div>
                              </div>
                            )}
                          </>
                        )}

                        <div className="flex items-center justify-end gap-1.5 mt-1 text-[10px] text-gray-300/70">
                          <span>{new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                          {msg.isEdited && <span>(edited)</span>}
                          {isMe && (
                            msg.isRead ? <CheckCheck className="w-3 h-3 text-cyan-300" /> : <Check className="w-3 h-3 text-gray-400" />
                          )}
                        </div>

                        {/* Action buttons (hover) */}
                        {!isDeleted && !isEditing && (
                          <div className={`absolute top-1/2 -translate-y-1/2 hidden group-hover:flex items-center gap-1 p-1 bg-huddle-dark/95 backdrop-blur-md rounded-xl border border-white/[0.08] shadow-[0_4px_20px_rgba(0,0,0,0.5)] ${
                            isMe ? "-left-20" : "-right-20"
                          }`}>
                            <button
                              onClick={() => setReplyTo(msg)}
                              title="Reply"
                              className="p-1 hover:bg-white/10 rounded text-gray-300"
                            >
                              <Reply className="w-3.5 h-3.5" />
                            </button>
                            {isMe && (
                              <>
                                <button
                                  onClick={() => {
                                    setEditingMessage(msg);
                                    setEditInput(msg.content);
                                  }}
                                  title="Edit"
                                  className="p-1 hover:bg-white/10 rounded text-gray-300"
                                >
                                  <Edit2 className="w-3.5 h-3.5" />
                                </button>
                                <button
                                  onClick={() => handleDeleteMessage(msg.id)}
                                  title="Delete"
                                  className="p-1 hover:bg-red-500/20 text-red-400 rounded"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
                <div ref={messagesEndRef} />
              </div>

              {/* Typing indicator */}
              {typingUsers.length > 0 && (
                <div className="px-6 py-1 text-xs text-purple-300/80 italic z-10">
                  {typingUsers.join(", ")} {typingUsers.length === 1 ? "is" : "are"} typing...
                </div>
              )}

              {/* Archive Banner */}
              {isArchived && (
                <div className="px-6 py-2 bg-amber-500/10 border-t border-amber-500/20 flex items-center gap-2 text-xs backdrop-blur-md z-10">
                  <AlertCircle className="w-3.5 h-3.5 text-amber-400" />
                  <span className="text-amber-200">This project is archived. Chat is read-only.</span>
                </div>
              )}

              {/* Reply Header Banner */}
              {replyTo && (
                <div className="px-6 py-2.5 bg-huddle-purple/10 border-t border-huddle-purple/20 flex items-center justify-between text-xs backdrop-blur-md z-10">
                  <div className="flex items-center gap-2 text-purple-300">
                    <Reply className="w-3.5 h-3.5" />
                    <span>Replying to <strong className="text-white">{replyTo.senderName || "User"}</strong>: {replyTo.content}</span>
                  </div>
                  <button onClick={() => setReplyTo(null)} className="text-gray-400 hover:text-white">✕</button>
                </div>
              )}

              {/* Input Box */}
              <form onSubmit={handleSendMessage} className="p-4 bg-[#0e0e14]/90 backdrop-blur-md border-t border-white/[0.06] flex items-center gap-3 z-10">
                <div className="flex-1 relative flex items-center">
                  {/* File attachment button - only for collaboration chats that are not archived */}
                  {selectedConv.conversationType === "COLLABORATION" && selectedConv.projectId && !isArchived && (
                    <button
                      type="button"
                      onClick={() => setShowFilePicker(true)}
                      className="mr-2 p-2 rounded-lg bg-white/[0.04] hover:bg-white/[0.08] text-gray-400 hover:text-purple-400 transition"
                      title="Attach project file"
                    >
                      <FileText className="w-4 h-4" />
                    </button>
                  )}
                  
                  {/* Selected file indicator */}
                  {selectedFileId && (
                    <div className="mr-2 px-2 py-1 rounded-md bg-purple-500/20 border border-purple-500/30 flex items-center gap-1 text-[10px] text-purple-300">
                      <FileText className="w-3 h-3" />
                      <span className="max-w-[100px] truncate">File attached</span>
                      <button
                        type="button"
                        onClick={() => setSelectedFileId(null)}
                        className="ml-1 hover:text-white"
                      >
                        ✕
                      </button>
                    </div>
                  )}
                  
                  <input
                    type="text"
                    value={inputMessage}
                    onChange={(e) => setInputMessage(e.target.value)}
                    placeholder={isArchived ? "Chat is read-only (project archived)" : "Type a message..."}
                    disabled={isArchived}
                    className={`flex-1 bg-white/[0.04] border border-white/[0.06] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:border-purple-500/50 transition-colors ${isArchived ? 'opacity-50 cursor-not-allowed' : ''}`}
                  />
                </div>
                <button
                  type="submit"
                  disabled={(!inputMessage.trim() && !selectedFileId)}
                  className="p-3 rounded-xl bg-gradient-to-r from-huddle-coral to-huddle-pink text-white font-medium shadow-[0_0_12px_rgba(255,71,133,0.3)] hover:opacity-95 active:scale-95 disabled:opacity-30 disabled:pointer-events-none transition-all duration-200"
                >
                  <Send className="w-4 h-4" />
                </button>
              </form>
            </>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-gray-500 z-10">
              <div className="w-16 h-16 rounded-2xl bg-white/[0.03] border border-white/[0.06] flex items-center justify-center mb-4 text-gray-400 shadow-inner">
                <MessageSquare className="w-8 h-8 stroke-1 text-purple-400/80" />
              </div>
              <p className="text-sm font-medium text-gray-400">Select a conversation</p>
              <p className="text-xs text-gray-600 mt-1">Direct message discussions and workspaces will appear here</p>
            </div>
          )}
        </div>
      </div>

      {/* ─── Send Formal Commitment Offer Modal ─── */}
      {showCreateOfferModal && selectedConv && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-huddle-dark/95 backdrop-blur-xl border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl relative">
            <button
              onClick={() => setShowCreateOfferModal(false)}
              className="absolute top-5 right-5 text-gray-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-huddle-coral to-huddle-purple flex items-center justify-center text-white shadow-lg">
                <Award className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Send Commitment Offer</h3>
                <p className="text-xs text-gray-400">Formal agreement to join your workspace</p>
              </div>
            </div>

            <p className="text-xs text-gray-400 mb-5 leading-relaxed bg-white/[0.03] p-3 rounded-xl border border-white/5">
              Sending this offer allows <strong className="text-white">{selectedConv.title.split(" (")[0]}</strong> to review proposed role and terms. Their membership will activate only when they accept.
            </p>

            {offerError && (
              <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 text-red-400 rounded-xl text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{offerError}</span>
              </div>
            )}

            <form onSubmit={handleCreateOffer} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
                  Proposed Role <span className="text-huddle-coral">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={offerRole}
                  onChange={(e) => setOfferRole(e.target.value)}
                  placeholder="e.g. Frontend Engineer, Product Designer"
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:border-huddle-coral transition-colors"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
                  Terms &amp; Responsibilities <span className="text-gray-500">(Optional)</span>
                </label>
                <textarea
                  rows={3}
                  value={offerTerms}
                  onChange={(e) => setOfferTerms(e.target.value)}
                  placeholder="e.g. 10 hrs/week commitment, weekly sprint review on Mondays..."
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 outline-none focus:border-huddle-coral transition-colors resize-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1.5">
                  Offer Validity
                </label>
                <select
                  value={offerDuration}
                  onChange={(e) => setOfferDuration(e.target.value)}
                  className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-4 py-2.5 text-sm text-white outline-none focus:border-huddle-coral transition-colors"
                >
                  <option value="3">3 Days</option>
                  <option value="7">7 Days (Standard)</option>
                  <option value="14">14 Days</option>
                  <option value="30">30 Days</option>
                </select>
              </div>

              <div className="flex gap-3 justify-end pt-3 border-t border-white/5">
                <button
                  type="button"
                  onClick={() => setShowCreateOfferModal(false)}
                  disabled={offerSubmitting}
                  className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-gray-300 text-xs font-semibold transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={offerSubmitting || !offerRole.trim()}
                  className="px-5 py-2 rounded-xl bg-gradient-to-r from-huddle-coral to-huddle-purple hover:opacity-90 disabled:opacity-50 text-white text-xs font-bold transition flex items-center gap-1.5 shadow-md"
                >
                  {offerSubmitting ? "Sending..." : "Issue Offer"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── File Picker Modal ─── */}
      {showFilePicker && selectedConv && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="bg-huddle-dark/95 backdrop-blur-xl border border-white/10 rounded-2xl p-6 max-w-lg w-full shadow-2xl relative max-h-[80vh] flex flex-col">
            <button
              onClick={() => setShowFilePicker(false)}
              className="absolute top-5 right-5 text-gray-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-purple-500 to-pink-500 flex items-center justify-center text-white shadow-lg">
                <FileText className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Attach Project File</h3>
                <p className="text-xs text-gray-400">Share a file from your workspace</p>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto custom-scrollbar mb-4">
              {availableFiles.length === 0 ? (
                <div className="text-center py-8 text-gray-400 text-sm">
                  <FileText className="w-8 h-8 mx-auto mb-2 opacity-50" />
                  <p>No files available in this workspace</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {availableFiles.map((file) => (
                    <button
                      key={file.id}
                      onClick={() => {
                        setSelectedFileId(file.id);
                        setShowFilePicker(false);
                      }}
                      className="w-full p-3 rounded-xl bg-white/[0.03] hover:bg-white/[0.08] border border-white/[0.08] hover:border-purple-500/30 text-left transition-all flex items-center gap-3"
                    >
                      <FileText className="w-5 h-5 text-purple-400 flex-shrink-0" />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-white truncate">{file.name}</p>
                        <p className="text-xs text-gray-400">
                          {file.sizeBytes ? formatFileSize(file.sizeBytes) : ''}
                          {file.fileType && ` • ${file.fileType.split('/')[1]?.toUpperCase() || 'FILE'}`}
                        </p>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              onClick={() => setShowFilePicker(false)}
              className="w-full py-2.5 rounded-xl bg-white/[0.05] hover:bg-white/[0.10] text-gray-300 text-sm font-medium border border-white/10 transition"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </>
  );
}
