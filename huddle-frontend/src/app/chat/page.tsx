"use client";

import React, { Suspense, useEffect, useState, useMemo, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { isLoggedIn } from "@/lib/auth";
import { requestService, getWsUrl } from "@/lib/api";
import { config } from "@/lib/config";
import {
  Send,
  MessageSquare,
  MessageSquarePlus,
  Loader2,
  ArrowLeft,
  Compass,
  Bell,
  ExternalLink,
  Paperclip,
  Code,
  Smile,
  XCircle,
  Filter,
  Target,
  ChevronRight,
  PenSquare,
} from "lucide-react";
import { Client } from "@stomp/stompjs";
import Link from "next/link";

export const dynamic = "force-dynamic";

interface CollabReq {
  id: string;
  requestorId: string;
  requestorDisplayName: string;
  posterId: string;
  postId: string;
  postTitle: string;
  previewMessage: string;
  status: string;
  createdAt: string;
}

function formatTime(dateStr: string) {
  const d = new Date(dateStr);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatDate(dateStr: string) {
  const d = new Date(dateStr);
  const now = new Date();
  const diffDays = Math.floor((now.getTime() - d.getTime()) / 86400000);
  if (diffDays === 0) return formatTime(dateStr);
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 7) return d.toLocaleDateString([], { weekday: "short" });
  return d.toLocaleDateString([], { month: "short", day: "numeric" });
}

function getInitials(name: string) {
  return (name || "P")
    .split(" ")
    .map((w) => w[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

const AVATAR_COLORS = [
  "from-huddle-coral to-huddle-purple",
  "from-huddle-purple to-huddle-blue",
  "from-huddle-blue to-huddle-coral",
  "from-huddle-pink to-huddle-purple",
  "from-emerald-500 to-teal-500",
];

function avatarColor(id: string) {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash += id.charCodeAt(i);
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function ChatContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const requestId = searchParams?.get("requestId");

  const [conversations, setConversations] = useState<CollabReq[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [filterQuery, setFilterQuery] = useState("");
  const [messages, setMessages] = useState<any[]>([]);
  const [input, setInput] = useState("");
  const [status, setStatus] = useState<
    "idle" | "connecting" | "connected" | "error" | "disconnected"
  >("idle");
  const [errorMsg, setErrorMsg] = useState<string>("");
  const [client, setClient] = useState<Client | null>(null);
  const [currentUserId, setCurrentUserId] = useState<string>("");
  const [unreadCounts, setUnreadCounts] = useState<Record<string, number>>({});

  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Check login and decode userId
  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    const token =
      typeof window !== "undefined"
        ? localStorage.getItem("huddle_token")
        : null;
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        setCurrentUserId(payload.sub || "");
      } catch {}
    }
  }, [router]);

  // Fetch unread counts
  useEffect(() => {
    if (!isLoggedIn()) return;
    const fetchUnreadCounts = () => {
      requestService
        .getUnreadMessageCounts()
        .then((data) => {
          const map: Record<string, number> = {};
          data.forEach((item: any) => {
            map[item.requestId] = item.count;
          });
          setUnreadCounts(map);
        })
        .catch(() => {});
    };
    fetchUnreadCounts();
    const interval = setInterval(fetchUnreadCounts, 30000);
    window.addEventListener("refresh-badges", fetchUnreadCounts);
    return () => {
      clearInterval(interval);
      window.removeEventListener("refresh-badges", fetchUnreadCounts);
    };
  }, []);

  // Fetch accepted conversations
  useEffect(() => {
    if (!isLoggedIn()) return;
    const fetchConversations = async () => {
      setLoadingConversations(true);
      try {
        const [received, sent] = await Promise.all([
          requestService.getMyRequests("received").catch(() => []),
          requestService.getMyRequests("sent").catch(() => []),
        ]);
        const allRequests: CollabReq[] = [
          ...(Array.isArray(received) ? received : []),
          ...(Array.isArray(sent) ? sent : []),
        ];
        const accepted = allRequests.filter(
          (r) => r.status?.toUpperCase() === "ACCEPTED"
        );
        const uniqueMap = new Map<string, CollabReq>();
        for (const req of accepted) {
          if (!uniqueMap.has(req.id)) uniqueMap.set(req.id, req);
        }
        const sorted = Array.from(uniqueMap.values()).sort(
          (a, b) =>
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        setConversations(sorted);
      } catch (err) {
        console.error("Failed to load conversations:", err);
      } finally {
        setLoadingConversations(false);
      }
    };
    fetchConversations();
  }, []);

  const filteredConversations = useMemo(() => {
    if (!filterQuery.trim()) return conversations;
    const q = filterQuery.toLowerCase();
    return conversations.filter(
      (c) =>
        c.postTitle.toLowerCase().includes(q) ||
        c.requestorDisplayName.toLowerCase().includes(q)
    );
  }, [conversations, filterQuery]);

  const activeConversation = useMemo(() => {
    if (!requestId) return null;
    return conversations.find((c) => c.id === requestId) || null;
  }, [conversations, requestId]);

  // Load chat history
  useEffect(() => {
    if (!requestId) {
      setMessages([]);
      return;
    }
    const token =
      typeof window !== "undefined"
        ? localStorage.getItem("huddle_token")
        : null;
    if (!token) return;
    fetch(`${config.apiUrl}/requests/${requestId}/messages`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.json())
      .then((data) => {
        if (Array.isArray(data)) {
          setMessages(data);
          requestService
            .markMessagesRead(requestId)
            .then(() => window.dispatchEvent(new Event("refresh-badges")))
            .catch(() => {});
        }
      })
      .catch((err) => console.error("Failed to fetch messages", err));
  }, [requestId]);

  // Auto-scroll
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // STOMP connection
  useEffect(() => {
    if (!requestId) return;
    const token =
      typeof window !== "undefined"
        ? localStorage.getItem("huddle_token")
        : null;
    if (!token) return;

    setStatus("connecting");
    const stompClient = new Client({
      brokerURL: getWsUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        setStatus("connected");
        stompClient.subscribe(`/topic/chat/${requestId}`, (msg) => {
          try {
            const parsed = JSON.parse(msg.body);
            setMessages((prev) => [...prev, parsed]);
          } catch {}
        });
      },
      onDisconnect: () => setStatus("disconnected"),
      onStompError: (frame) => {
        setStatus("error");
        setErrorMsg(frame.headers?.message || "Connection error");
      },
      reconnectDelay: 5000,
    });
    stompClient.activate();
    setClient(stompClient);
    return () => {
      stompClient.deactivate();
      setStatus("idle");
    };
  }, [requestId]);

  const sendMessage = () => {
    if (!client || !requestId || !input.trim()) return;
    client.publish({
      destination: `/app/chat/${requestId}`,
      body: JSON.stringify({ content: input.trim() }),
    });
    setInput("");
  };

  return (
    <div className="flex h-full overflow-hidden">
      {/* ─── LEFT SIDEBAR ─── */}
      <div
        className={`${
          requestId ? "hidden md:flex" : "flex"
        } w-full md:w-[320px] lg:w-[340px] flex-col flex-shrink-0 border-r border-white/[0.06] bg-[#0e0e14] h-full`}
      >
        {/* Sidebar Header */}
        <div className="h-[72px] flex items-center justify-between px-6 flex-shrink-0 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <h2 className="font-display font-bold text-[17px] text-white tracking-tight">
              Conversations
            </h2>
            {conversations.length > 0 && (
              <span className="bg-huddle-coral/20 text-huddle-coral text-[11px] font-bold px-2 py-0.5 rounded-full min-w-[22px] text-center">
                {conversations.length}
              </span>
            )}
          </div>
          <button
            className="p-1.5 text-gray-500 hover:text-white hover:bg-white/5 rounded-lg transition-colors"
            title="New conversation"
          >
            <PenSquare className="w-4 h-4" />
          </button>
        </div>

        {/* Filter */}
        <div className="px-4 py-3 flex-shrink-0">
          <div className="relative flex items-center">
            <Filter className="w-3.5 h-3.5 text-gray-500 absolute left-3.5 pointer-events-none" />
            <input
              type="text"
              value={filterQuery}
              onChange={(e) => setFilterQuery(e.target.value)}
              placeholder="Filter conversations..."
              className="w-full bg-white/[0.04] border border-white/[0.06] rounded-xl pl-9 pr-3 py-2.5 text-[13px] text-white placeholder-gray-500 outline-none focus:border-white/20 transition-colors"
            />
          </div>
        </div>

        {/* Conversation List */}
        <div className="flex-1 overflow-y-auto hide-scrollbar">
          {loadingConversations ? (
            <div className="flex flex-col items-center justify-center py-20 gap-3 text-gray-500">
              <Loader2 className="w-5 h-5 animate-spin text-huddle-coral" />
              <span className="text-xs">Loading conversations...</span>
            </div>
          ) : filteredConversations.length === 0 ? (
            <div className="text-center py-16 px-6">
              <MessageSquare className="w-8 h-8 mx-auto mb-3 opacity-20 text-white" />
              <p className="text-[13px] font-semibold text-gray-400">
                {filterQuery ? "No matching chats" : "No active chats"}
              </p>
              <p className="text-xs text-gray-600 mt-1 leading-relaxed">
                {filterQuery
                  ? "Try a different query."
                  : "Accepted collaboration requests appear here."}
              </p>
            </div>
          ) : (
            <div className="py-1">
              {filteredConversations.map((conv) => {
                const isSelected = conv.id === requestId;
                const isOwner = conv.posterId === currentUserId;
                const partnerName = isOwner
                  ? conv.requestorDisplayName
                  : "Project Lead";
                const unreadCount = unreadCounts[conv.id] || 0;
                const color = avatarColor(conv.id);

                return (
                  <div
                    key={conv.id}
                    onClick={() => router.push(`/chat?requestId=${conv.id}`)}
                    className={`relative cursor-pointer px-4 py-3.5 transition-all group ${
                      isSelected
                        ? "bg-white/[0.06]"
                        : "hover:bg-white/[0.03]"
                    }`}
                  >
                    {/* Active indicator */}
                    {isSelected && (
                      <div className="absolute left-0 top-3 bottom-3 w-[3px] bg-huddle-coral rounded-r" />
                    )}

                    <div className="flex items-start gap-3">
                      {/* Avatar */}
                      <div className="relative flex-shrink-0">
                        <div
                          className={`w-10 h-10 rounded-full bg-gradient-to-br ${color} flex items-center justify-center text-white font-bold text-sm`}
                        >
                          {getInitials(partnerName)}
                        </div>
                        <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-emerald-400 border-2 border-[#0e0e14] rounded-full" />
                      </div>

                      <div className="flex-1 min-w-0">
                        {/* Title row */}
                        <div className="flex items-baseline justify-between gap-1 mb-0.5">
                          <span
                            className={`text-[13px] font-semibold truncate ${
                              isSelected
                                ? "text-white"
                                : "text-gray-200 group-hover:text-white"
                            } transition-colors`}
                          >
                            {conv.postTitle}
                          </span>
                          <span className="text-[11px] text-gray-500 whitespace-nowrap flex-shrink-0">
                            {formatDate(conv.createdAt)}
                          </span>
                        </div>

                        {/* Partner + status badge */}
                        <div className="flex items-center gap-1.5 mb-1">
                          <span className="text-[12px] text-gray-400 truncate">
                            {partnerName}
                          </span>
                          <span className="text-[10px] text-emerald-400 border border-emerald-400/30 bg-emerald-400/10 px-1.5 py-px rounded font-semibold uppercase tracking-wide flex-shrink-0">
                            Accepted
                          </span>
                          {unreadCount > 0 && (
                            <span className="ml-auto bg-huddle-coral text-white text-[10px] font-bold px-1.5 py-px rounded-full flex-shrink-0">
                              {unreadCount}
                            </span>
                          )}
                        </div>

                        {/* Preview */}
                        {conv.previewMessage && (
                          <p className="text-[12px] text-gray-500 truncate">
                            "{conv.previewMessage}"
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* ─── RIGHT PANEL ─── */}
      <div className="flex-1 flex flex-col h-full min-w-0 bg-[#070709]">
        {requestId ? (
          <>
            {/* Conversation Header */}
            <div className="h-[72px] flex items-center justify-between px-6 border-b border-white/[0.06] flex-shrink-0">
              <div className="flex items-center gap-3">
                {/* Mobile back */}
                <button
                  onClick={() => router.push("/chat")}
                  className="md:hidden p-2 text-gray-400 hover:text-white rounded-lg hover:bg-white/5"
                >
                  <ArrowLeft className="w-4 h-4" />
                </button>

                {/* Avatar */}
                <div className="relative flex-shrink-0">
                  <div
                    className={`w-10 h-10 rounded-full bg-gradient-to-br ${
                      activeConversation
                        ? avatarColor(activeConversation.id)
                        : "from-huddle-coral to-huddle-purple"
                    } flex items-center justify-center text-white font-bold text-sm`}
                  >
                    {getInitials(
                      activeConversation?.requestorDisplayName || "P"
                    )}
                  </div>
                  <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-emerald-400 border-2 border-[#070709] rounded-full" />
                </div>

                {/* Info */}
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="font-semibold text-[15px] text-white leading-tight">
                      {activeConversation?.postTitle || "Project Chat"}
                    </h2>
                    <span className="text-[11px] bg-emerald-400/10 text-emerald-400 border border-emerald-400/20 font-semibold px-2 py-0.5 rounded-full flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                      Active Collaboration
                    </span>
                  </div>
                  <p className="text-[12px] text-gray-400 mt-0.5 flex items-center gap-2">
                    {activeConversation && (
                      <>
                        <span>
                          Collaborator:{" "}
                          <strong className="text-gray-200 font-semibold">
                            {activeConversation.requestorDisplayName}
                          </strong>
                        </span>
                        <span className="text-gray-600">•</span>
                      </>
                    )}
                    {status === "connecting" && (
                      <span className="text-yellow-400 flex items-center gap-1">
                        <Loader2 className="w-3 h-3 animate-spin" />
                        Connecting...
                      </span>
                    )}
                    {status === "connected" && (
                      <span className="text-emerald-400 flex items-center gap-1 font-medium">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                        Live
                      </span>
                    )}
                    {status === "error" && (
                      <span className="text-red-400">Error: {errorMsg}</span>
                    )}
                  </p>
                </div>
              </div>

              {/* Right actions */}
              <div className="flex items-center gap-2">
                {activeConversation && (
                  <Link
                    href={`/post/${activeConversation.postId}`}
                    className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-white/[0.06] hover:bg-white/[0.1] border border-white/[0.08] text-[13px] font-semibold text-white transition-all"
                  >
                    <ExternalLink className="w-3.5 h-3.5 text-huddle-coral" />
                    View Project
                  </Link>
                )}
                <button
                  onClick={() => router.push("/chat")}
                  className="p-2 text-gray-500 hover:text-white rounded-lg hover:bg-white/5 transition-colors hidden sm:flex"
                  title="Close conversation"
                >
                  <XCircle className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Milestone Ribbon */}
            <div className="flex items-center justify-between px-6 py-2.5 border-b border-white/[0.06] bg-white/[0.02] flex-shrink-0">
              <div className="flex items-center gap-2 text-[12px] text-gray-400">
                <Target className="w-3.5 h-3.5 text-huddle-pink flex-shrink-0" />
                <span>
                  Current Milestone:{" "}
                  <strong className="text-gray-200 font-semibold">
                    Phase 2: Active Collaboration &amp; Execution
                  </strong>
                </span>
              </div>
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider hidden sm:block">
                Target: Active
              </span>
            </div>

            {/* Messages Feed */}
            <div className="flex-1 overflow-y-auto px-6 py-6 space-y-5 hide-scrollbar">
              {messages.length === 0 && (
                <div className="flex flex-col items-center justify-center h-full text-center text-gray-500">
                  <MessageSquare className="w-10 h-10 mb-3 opacity-20" />
                  <p className="text-sm font-medium text-gray-400">
                    No messages yet
                  </p>
                  <p className="text-xs text-gray-600 mt-1">
                    Say hello and start collaborating!
                  </p>
                </div>
              )}

              {messages.map((msg, idx) => {
                const isMe = msg.senderId === currentUserId;
                const senderName =
                  msg.senderDisplayName || (isMe ? "You" : "Collaborator");
                return (
                  <div
                    key={idx}
                    className={`flex ${isMe ? "flex-row-reverse" : "flex-row"} items-end gap-2.5`}
                  >
                    {/* Sender avatar */}
                    {!isMe && (
                      <div
                        className={`w-8 h-8 rounded-full bg-gradient-to-br ${
                          activeConversation
                            ? avatarColor(activeConversation.id)
                            : "from-huddle-coral to-huddle-purple"
                        } flex items-center justify-center text-white font-bold text-xs flex-shrink-0`}
                      >
                        {getInitials(senderName)}
                      </div>
                    )}

                    <div
                      className={`flex flex-col max-w-[65%] ${isMe ? "items-end" : "items-start"}`}
                    >
                      {/* Sender label + time */}
                      <div
                        className={`flex items-baseline gap-1.5 mb-1 px-1 ${isMe ? "flex-row-reverse" : ""}`}
                      >
                        <span className="text-[12px] font-semibold text-gray-300">
                          {isMe ? "You" : senderName}
                        </span>
                        {msg.sentAt && (
                          <span className="text-[11px] text-gray-500">
                            {formatTime(msg.sentAt)}
                          </span>
                        )}
                      </div>

                      {/* Bubble */}
                      <div
                        className={`px-4 py-3 rounded-2xl text-[14px] leading-relaxed ${
                          isMe
                            ? "bg-gradient-to-br from-huddle-coral to-huddle-pink text-white rounded-br-sm"
                            : "bg-[#1a1a24] border border-white/[0.06] text-gray-100 rounded-bl-sm"
                        }`}
                      >
                        <p className="whitespace-pre-wrap">{msg.content}</p>
                      </div>
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {/* Composer */}
            <div className="px-5 py-4 border-t border-white/[0.06] flex-shrink-0 bg-[#0a0a10]">
              <div className="flex items-center gap-2 bg-[#141420] border border-white/[0.08] rounded-2xl px-3 py-2.5">
                <button
                  type="button"
                  className="p-1.5 text-gray-500 hover:text-gray-200 transition-colors"
                  title="Attach file"
                >
                  <Paperclip className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  className="p-1.5 text-gray-500 hover:text-gray-200 transition-colors hidden sm:block"
                  title="Code snippet"
                >
                  <Code className="w-4 h-4" />
                </button>

                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && sendMessage()}
                  className="flex-1 bg-transparent border-none outline-none text-[14px] text-white placeholder-gray-500 px-2 py-0.5"
                  placeholder={
                    status === "connected"
                      ? "Type a message to your team..."
                      : "Connecting to chat..."
                  }
                  disabled={status !== "connected"}
                />

                <button
                  type="button"
                  className="p-1.5 text-gray-500 hover:text-gray-200 transition-colors hidden sm:block"
                  title="Emoji"
                >
                  <Smile className="w-4 h-4" />
                </button>

                <button
                  type="button"
                  onClick={sendMessage}
                  disabled={status !== "connected" || !input.trim()}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-gradient-to-r from-huddle-coral to-huddle-pink text-white text-[13px] font-bold disabled:opacity-40 transition-all hover:opacity-90 flex-shrink-0 shadow-lg"
                >
                  <span>Send</span>
                  <Send className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="flex items-center justify-between text-[11px] text-gray-600 mt-2 px-1">
                <span>
                  Collaborating on{" "}
                  <strong className="text-gray-400">
                    {activeConversation?.postTitle || "Project Workspace"}
                  </strong>
                </span>
                <span>
                  Press <strong className="text-gray-400">Enter</strong> to send
                </span>
              </div>
            </div>
          </>
        ) : (
          /* ─── EMPTY / NO CONVERSATION SELECTED ─── */
          <div className="flex-1 flex flex-col items-center justify-center px-8 text-center">
            {loadingConversations ? (
              <div className="flex flex-col items-center gap-3 text-gray-400">
                <Loader2 className="w-8 h-8 animate-spin text-huddle-coral" />
                <p className="text-sm font-medium">Loading your conversations...</p>
              </div>
            ) : conversations.length > 0 ? (
              <div className="w-full max-w-[520px]">
                {/* Icon */}
                <div className="w-[88px] h-[88px] rounded-full bg-gradient-to-br from-huddle-coral/10 via-huddle-pink/10 to-huddle-purple/10 border border-white/[0.08] flex items-center justify-center mx-auto mb-7 shadow-[0_0_60px_rgba(155,81,224,0.12)]">
                  <MessageSquarePlus className="w-9 h-9 text-huddle-coral opacity-70" />
                </div>

                <h2 className="font-display font-bold text-[26px] text-white mb-3 tracking-tight">
                  Select a Collaboration Conversation
                </h2>
                <p className="text-[14px] text-gray-400 leading-relaxed mb-10 max-w-[420px] mx-auto">
                  Choose an accepted project collaboration from the sidebar to chat,
                  coordinate tasks, and build with your team members.
                </p>

                {/* Quick Jump */}
                <div className="text-left space-y-2.5">
                  <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-widest mb-3 px-1">
                    Or quick jump to an active project
                  </p>
                  {conversations.slice(0, 3).map((c) => {
                    const partnerName =
                      c.posterId === currentUserId
                        ? c.requestorDisplayName
                        : "Project Lead";
                    const color = avatarColor(c.id);
                    return (
                      <div
                        key={c.id}
                        onClick={() => router.push(`/chat?requestId=${c.id}`)}
                        className="flex items-center justify-between p-4 rounded-2xl bg-white/[0.03] hover:bg-white/[0.06] border border-white/[0.06] hover:border-white/[0.12] cursor-pointer transition-all group"
                      >
                        <div className="flex items-center gap-3">
                          <div
                            className={`w-10 h-10 rounded-full bg-gradient-to-br ${color} flex items-center justify-center text-white font-bold text-sm`}
                          >
                            {getInitials(partnerName)}
                          </div>
                          <div>
                            <div className="text-[14px] font-semibold text-white group-hover:text-huddle-coral transition-colors">
                              {c.postTitle}
                            </div>
                            <div className="text-[12px] text-gray-500">
                              with {partnerName}
                            </div>
                          </div>
                        </div>
                        <ChevronRight className="w-4 h-4 text-gray-600 group-hover:text-gray-300 group-hover:translate-x-0.5 transition-all flex-shrink-0" />
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : (
              <div className="max-w-sm">
                <div className="w-16 h-16 rounded-2xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center mx-auto mb-5">
                  <MessageSquare className="w-7 h-7 text-gray-600" />
                </div>
                <h3 className="text-xl font-display font-semibold text-white mb-2">
                  No Active Conversations
                </h3>
                <p className="text-sm text-gray-500 mb-6 leading-relaxed">
                  Collaboration chats unlock when a request is accepted. Explore
                  projects or manage your requests.
                </p>
                <div className="flex items-center justify-center gap-3">
                  <Link
                    href="/"
                    className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-huddle-coral to-huddle-pink text-white text-[13px] font-bold shadow-lg hover:opacity-90 transition-opacity"
                  >
                    <Compass className="w-4 h-4" />
                    Explore Projects
                  </Link>
                  <Link
                    href="/requests"
                    className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/[0.05] hover:bg-white/[0.08] border border-white/[0.08] text-[13px] font-semibold text-gray-300 hover:text-white transition-colors"
                  >
                    <Bell className="w-4 h-4" />
                    View Requests
                  </Link>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default function ChatPage() {
  return (
    <Suspense fallback={<div className="p-8 text-white">Loading chat…</div>}>
      <ChatContent />
    </Suspense>
  );
}
