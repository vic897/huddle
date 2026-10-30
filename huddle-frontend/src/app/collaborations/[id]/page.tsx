"use client";

import { useEffect, useState, use, useRef } from "react";
import { collaborationService, Collaboration, OwnershipTransferInfo } from "@/lib/api";
import { config } from "@/lib/config";
import { isLoggedIn } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Users,
  ShieldCheck,
  Calendar,
  AlertCircle,
  ShieldAlert,
  Loader2,
  FolderGit2,
  ExternalLink,
  Clock,
  Send,
  MessageSquare,
  Sparkles,
  Trash2,
  LogOut,
  ChevronDown,
  ChevronRight,
  CheckCircle,
  XCircle,
  PauseCircle,
  PlayCircle,
  RefreshCw,
  GitBranch,
  AlertTriangle,
} from "lucide-react";
import { Client, IMessage } from "@stomp/stompjs";

// ─── Modal type union ────────────────────────────────────────────────────────
type ModalKind =
  | "leave"
  | "remove"
  | "suspend"
  | "reinstate"
  | "departure"
  | "departureResolve"
  | "transferOwnership"
  | "acceptTransfer"
  | "declineTransfer"
  | null;

export default function CollaborationWorkspacePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const router = useRouter();
  const { id } = use(params);

  const [collaboration, setCollaboration] = useState<Collaboration | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorCode, setErrorCode] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // User identity
  const [currentUserId, setCurrentUserId] = useState<string>("");

  // Group Chat State
  const [messages, setMessages] = useState<any[]>([]);
  const [input, setInput] = useState("");
  const [stompStatus, setStompStatus] = useState<"idle" | "connecting" | "connected" | "error">("idle");
  const [chatErrorMsg, setChatErrorMsg] = useState<string>("");
  const [client, setClient] = useState<Client | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  // Membership management state
  const [activeModal, setActiveModal] = useState<ModalKind>(null);
  const [modalTarget, setModalTarget] = useState<{ id: string; displayName: string } | null>(null);
  const [modalReason, setModalReason] = useState("");
  const [transferRecipientId, setTransferRecipientId] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [formerMembersOpen, setFormerMembersOpen] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    const token = typeof window !== "undefined" ? localStorage.getItem("huddle_token") : null;
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        setCurrentUserId(payload.sub || "");
      } catch {}
    }
    loadWorkspace();
  }, [id, router]);

  const loadWorkspace = async () => {
    setLoading(true);
    setErrorCode(null);
    setErrorMessage(null);
    try {
      const data = await collaborationService.getCollaboration(id);
      setCollaboration(data);
    } catch (err: any) {
      const msg = err?.message || "";
      if (
        msg.includes("403") ||
        msg.toLowerCase().includes("forbidden") ||
        msg.toLowerCase().includes("not an active member")
      ) {
        setErrorCode(403);
      } else if (msg.includes("404") || msg.toLowerCase().includes("not found")) {
        setErrorCode(404);
      } else {
        setErrorMessage(msg || "Failed to load workspace.");
      }
    } finally {
      setLoading(false);
    }
  };

  // Load collaboration group chat history via REST API
  useEffect(() => {
    if (!collaboration || errorCode) {
      return;
    }

    collaborationService
      .getMessages(id)
      .then((data) => {
        if (Array.isArray(data)) {
          setMessages(data);
          collaborationService
            .markMessagesRead(id)
            .then(() => window.dispatchEvent(new Event("refresh-badges")))
            .catch(() => {});
        }
      })
      .catch((err) => {
        console.error("Failed to fetch collaboration group messages", err);
      });
  }, [id, collaboration, errorCode]);

  // STOMP connection for real-time collaboration group chat
  useEffect(() => {
    if (!collaboration || errorCode) return;

    const token = typeof window !== "undefined" ? localStorage.getItem("huddle_token") : null;
    if (!token) {
      console.warn("No JWT token available for STOMP CONNECT");
      setChatErrorMsg("Missing authentication token");
      setStompStatus("error");
      return;
    }

    let decodedUserId = "";
    try {
      const jwtPayload = JSON.parse(atob(token.split(".")[1]));
      decodedUserId = jwtPayload.sub || "";
    } catch (e) {}

    const wsUrl = config.wsUrl;
    const stompClient = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      debug: () => {},
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.beforeConnect = () => {
      const freshToken = typeof window !== "undefined" ? localStorage.getItem("huddle_token") : null;
      if (!freshToken) {
        console.warn("Token disappeared before STOMP CONNECT");
        setChatErrorMsg("Missing authentication token");
        setStompStatus("error");
        throw new Error("Missing token for CONNECT");
      }
      stompClient.connectHeaders = { Authorization: `Bearer ${freshToken}` };
    };

    stompClient.onConnect = () => {
      setStompStatus("connected");
      // Subscribe to collaboration-level group chat topic
      const subscription = stompClient.subscribe(
        `/topic/collaboration-chat/${id}`,
        (msg: IMessage) => {
          try {
            const payload = JSON.parse(msg.body);
            setMessages((prev) => {
              // Deduplicate if already present
              if (prev.some((m) => m.id === payload.id)) {
                return prev;
              }
              return [...prev, payload];
            });

            if (payload.senderId !== decodedUserId) {
              collaborationService
                .markMessagesRead(id)
                .then(() => window.dispatchEvent(new Event("refresh-badges")))
                .catch(() => {});
            }
          } catch (e) {
            console.error("Failed to parse collaboration group message", e);
          }
        }
      );

      stompClient.onDisconnect = () => {
        subscription.unsubscribe();
      };
    };

    stompClient.onStompError = (frame) => {
      console.error("STOMP error in workspace group chat", frame);
      setChatErrorMsg(frame.body || "STOMP error");
      setStompStatus("error");
    };

    stompClient.onWebSocketError = (ev) => {
      console.error("WebSocket error in workspace group chat", ev);
      setChatErrorMsg("WebSocket connection error");
      setStompStatus("error");
    };

    setStompStatus("connecting");
    stompClient.activate();
    setClient(stompClient);

    return () => {
      stompClient.deactivate();
      setStompStatus("idle");
    };
  }, [id, collaboration, errorCode]);

  // Auto-scroll messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Auto-dismiss action banner after 5 s
  useEffect(() => {
    if (actionSuccess || actionError) {
      const timer = setTimeout(() => {
        setActionSuccess(null);
        setActionError(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [actionSuccess, actionError]);

  const sendMessage = () => {
    if (!client || stompStatus !== "connected") return;
    if (input.trim() === "") return;
    if (collaboration?.status === "ARCHIVED") return;

    const body = JSON.stringify({ content: input.trim() });
    client.publish({ destination: `/app/collaboration-chat/${id}`, body });
    setInput("");
  };

  // ─── Modal helpers ────────────────────────────────────────────────────────
  const closeModal = () => {
    setActiveModal(null);
    setModalTarget(null);
    setModalReason("");
    setTransferRecipientId("");
  };

  // ─── Action handlers ──────────────────────────────────────────────────────

  const handleLeave = async () => {
    setActionLoading(true);
    try {
      await collaborationService.leaveCollaboration(id);
      closeModal();
      setActionSuccess("You have left the collaboration.");
      setTimeout(() => router.push("/collaborations"), 1800);
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to leave collaboration.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleDepartureRequest = async () => {
    setActionLoading(true);
    try {
      await collaborationService.requestDeparture(id, modalReason.trim() || undefined);
      closeModal();
      setActionSuccess("Departure request submitted. The project owner will review it.");
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to submit departure request.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRemove = async () => {
    if (!modalTarget) return;
    setActionLoading(true);
    try {
      await collaborationService.removeMember(id, modalTarget.id, modalReason.trim() || undefined);
      closeModal();
      setActionSuccess(`${modalTarget.displayName} has been removed from the collaboration.`);
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to remove member.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleSuspend = async () => {
    if (!modalTarget) return;
    if (!modalReason.trim()) {
      setActionError("A suspension reason is required.");
      return;
    }
    setActionLoading(true);
    try {
      await collaborationService.suspendMember(id, modalTarget.id, modalReason.trim());
      closeModal();
      setActionSuccess(`${modalTarget.displayName} has been suspended.`);
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to suspend member.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReinstate = async () => {
    if (!modalTarget) return;
    setActionLoading(true);
    try {
      await collaborationService.reinstateMember(id, modalTarget.id);
      closeModal();
      setActionSuccess(`${modalTarget.displayName} has been reinstated.`);
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to reinstate member.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleTransferOwnership = async () => {
    if (!transferRecipientId) {
      setActionError("Please select a recipient.");
      return;
    }
    setActionLoading(true);
    try {
      await collaborationService.initiateOwnershipTransfer(id, transferRecipientId);
      closeModal();
      setActionSuccess("Ownership transfer offer sent. The recipient must accept it.");
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to initiate ownership transfer.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleAcceptTransfer = async (transferId: string) => {
    setActionLoading(true);
    try {
      await collaborationService.acceptOwnershipTransfer(transferId);
      closeModal();
      setActionSuccess("You are now the project owner!");
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to accept ownership transfer.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeclineTransfer = async (transferId: string) => {
    setActionLoading(true);
    try {
      await collaborationService.declineOwnershipTransfer(transferId);
      closeModal();
      setActionSuccess("Ownership transfer declined.");
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to decline ownership transfer.");
    } finally {
      setActionLoading(false);
    }
  };

  const handleResolveDeparture = async (userId: string, displayName: string, approved: boolean) => {
    setActionLoading(true);
    try {
      await collaborationService.resolveDeparture(id, userId, approved);
      closeModal();
      setActionSuccess(approved ? `${displayName}'s departure has been approved.` : `${displayName}'s departure request was declined.`);
      await loadWorkspace();
    } catch (err: any) {
      closeModal();
      setActionError(err?.message || "Failed to resolve departure request.");
    } finally {
      setActionLoading(false);
    }
  };

  // ─── Loading / Error states ───────────────────────────────────────────────

  if (loading) {
    return (
      <div className="max-w-5xl mx-auto py-24 flex items-center justify-center gap-3 text-gray-400">
        <Loader2 className="w-5 h-5 animate-spin text-huddle-coral" />
        <span>Loading workspace...</span>
      </div>
    );
  }

  if (errorCode === 403) {
    return (
      <div className="max-w-md mx-auto py-24 text-center">
        <div className="w-16 h-16 rounded-2xl bg-red-500/10 border border-red-500/20 flex items-center justify-center mx-auto mb-5">
          <ShieldAlert className="w-8 h-8 text-red-400" />
        </div>
        <h2 className="text-xl font-bold text-white mb-2">Access Denied</h2>
        <p className="text-gray-400 text-sm mb-6 leading-relaxed">
          You are not an active member of this collaboration workspace. Only accepted project collaborators can view this space.
        </p>
        <Link href="/collaborations">
          <button className="px-5 py-2.5 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors">
            ← Back to Collaborations
          </button>
        </Link>
      </div>
    );
  }

  if (errorCode === 404) {
    return (
      <div className="max-w-md mx-auto py-24 text-center">
        <div className="w-16 h-16 rounded-2xl bg-gray-500/10 border border-gray-500/20 flex items-center justify-center mx-auto mb-5">
          <AlertCircle className="w-8 h-8 text-gray-400" />
        </div>
        <h2 className="text-xl font-bold text-white mb-2">Workspace Not Found</h2>
        <p className="text-gray-400 text-sm mb-6 leading-relaxed">
          The requested collaboration workspace does not exist or may have been archived.
        </p>
        <Link href="/collaborations">
          <button className="px-5 py-2.5 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors">
            ← Back to Collaborations
          </button>
        </Link>
      </div>
    );
  }

  if (errorMessage || !collaboration) {
    return (
      <div className="max-w-md mx-auto py-24 text-center">
        <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-white mb-2">Something went wrong</h2>
        <p className="text-gray-400 text-sm mb-6">{errorMessage || "Unable to load workspace."}</p>
        <button
          onClick={loadWorkspace}
          className="px-5 py-2.5 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors"
        >
          Try Again
        </button>
      </div>
    );
  }

  const allMembers = collaboration.members || [];
  const activeMembers = allMembers.filter((m) => m.status === "ACTIVE");
  const suspendedMembers = allMembers.filter((m) => m.status === "SUSPENDED");
  const formerMembers = allMembers.filter((m) => m.status === "LEFT" || m.status === "REMOVED");

  const currentMember = allMembers.find(
    (m) => m.user?.id === currentUserId && (m.status === "ACTIVE" || m.status === "SUSPENDED")
  );
  const isOwner = currentMember?.role === "OWNER" && currentMember?.status === "ACTIVE";
  const isArchived = collaboration.status === "ARCHIVED";

  const pendingTransfer = collaboration.pendingOwnershipTransfer;
  const isTransferRecipient = pendingTransfer?.recipientId === currentUserId && pendingTransfer?.status === "PENDING";

  // Departure-pending members (visible to owner)
  const departurePendingMembers = activeMembers.filter(
    (m) => m.departureStatus === "PENDING_APPROVAL" || m.departureStatus === "ESCALATED_ADMIN"
  );

  // My own departure status
  const myMember = allMembers.find((m) => m.user?.id === currentUserId);
  const myDepartureStatus = myMember?.departureStatus;
  const hasPendingDeparture =
    myDepartureStatus === "PENDING_APPROVAL" || myDepartureStatus === "ESCALATED_ADMIN";

  const createdDateStr = new Date(collaboration.createdAt).toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  // Selectable transfer recipients (active non-owner members)
  const transferCandidates = activeMembers.filter(
    (m) => m.role !== "OWNER" && m.user?.id !== currentUserId
  );

  return (
    <div className="max-w-6xl mx-auto pb-16">
      {/* Action Banner */}
      {actionSuccess && (
        <div className="mb-4 flex items-center gap-2.5 px-4 py-3 rounded-xl bg-green-500/10 border border-green-500/20 text-green-400 text-sm">
          <CheckCircle className="w-4 h-4 flex-shrink-0" />
          <span>{actionSuccess}</span>
        </div>
      )}
      {actionError && (
        <div className="mb-4 flex items-center gap-2.5 px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
          <XCircle className="w-4 h-4 flex-shrink-0" />
          <span>{actionError}</span>
        </div>
      )}

      {/* Ownership Transfer Banner (recipient) */}
      {isTransferRecipient && pendingTransfer && (
        <div className="mb-4 flex flex-col sm:flex-row items-start sm:items-center gap-3 px-4 py-3 rounded-xl bg-purple-500/10 border border-purple-500/30 text-purple-300 text-sm">
          <div className="flex items-center gap-2 flex-1">
            <GitBranch className="w-4 h-4 flex-shrink-0 text-purple-400" />
            <span>
              <span className="font-semibold text-white">{pendingTransfer.senderName}</span> has offered you ownership of this collaboration.
            </span>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              onClick={() => handleAcceptTransfer(pendingTransfer.id)}
              disabled={actionLoading}
              className="px-3 py-1.5 rounded-lg bg-purple-500/80 hover:bg-purple-500 text-white text-xs font-medium transition-colors disabled:opacity-50 flex items-center gap-1.5"
            >
              {actionLoading ? <Loader2 className="w-3 h-3 animate-spin" /> : <CheckCircle className="w-3 h-3" />}
              Accept
            </button>
            <button
              onClick={() => handleDeclineTransfer(pendingTransfer.id)}
              disabled={actionLoading}
              className="px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-gray-300 text-xs font-medium transition-colors disabled:opacity-50 flex items-center gap-1.5"
            >
              <XCircle className="w-3 h-3" />
              Decline
            </button>
          </div>
        </div>
      )}

      {/* Pending Transfer Notice (owner — waiting for acceptance) */}
      {pendingTransfer && !isTransferRecipient && isOwner && (
        <div className="mb-4 flex items-center gap-2.5 px-4 py-3 rounded-xl bg-yellow-500/10 border border-yellow-500/20 text-yellow-400 text-sm">
          <Clock className="w-4 h-4 flex-shrink-0" />
          <span>
            Ownership transfer pending — waiting for{" "}
            <span className="font-semibold text-white">{pendingTransfer.recipientName}</span> to accept.
          </span>
        </div>
      )}

      {/* Navigation Header */}
      <div className="mb-6 flex items-center justify-between">
        <Link
          href="/collaborations"
          className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Collaborations</span>
        </Link>

        <div className="flex items-center gap-3">
          <Link
            href="/chat"
            className="text-xs text-gray-400 hover:text-white px-3 py-1.5 rounded-lg bg-white/5 hover:bg-white/10 border border-white/5 transition-colors flex items-center gap-1.5"
          >
            <MessageSquare className="w-3.5 h-3.5" />
            <span>Messages</span>
          </Link>
          <span
            className={`px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1.5 ${
              isArchived
                ? "bg-gray-500/20 text-gray-400 border border-gray-500/30"
                : "bg-green-500/20 text-green-400 border border-green-500/30"
            }`}
          >
            <span className={`w-1.5 h-1.5 rounded-full ${isArchived ? "bg-gray-400" : "bg-green-400 animate-pulse"}`} />
            {collaboration.status} Workspace
          </span>
        </div>
      </div>

      {/* Main Workspace Card */}
      <div className="dashboard-card rounded-2xl overflow-hidden border border-white/5 bg-huddle-charcoal/40 mb-8">
        {/* Banner */}
        <div className="h-32 relative bg-huddle-charcoal overflow-hidden p-8 flex flex-col justify-end">
          <div className="absolute inset-0 bg-gradient-to-r from-purple-900/40 via-indigo-900/30 to-pink-900/20" />
          <div className="relative z-10">
            <div className="text-xs uppercase tracking-wider text-huddle-coral font-semibold mb-1 flex items-center gap-2">
              <FolderGit2 className="w-3.5 h-3.5" />
              Collaboration Workspace
            </div>
            <h1 className="text-2xl md:text-3xl font-bold text-white">
              {collaboration.projectTitle}
            </h1>
          </div>
        </div>

        {/* Project Overview Details */}
        <div className="p-8">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6 pb-6 border-b border-white/5">
            <div>
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block mb-1">
                Project Link
              </span>
              <Link
                href={`/post/${collaboration.projectId}`}
                className="inline-flex items-center gap-1.5 text-sm text-huddle-coral hover:text-huddle-pink transition-colors font-medium"
              >
                <span>View Original Post</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </Link>
            </div>

            <div>
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block mb-1">
                Workspace Created
              </span>
              <div className="flex items-center gap-2 text-sm text-gray-300">
                <Calendar className="w-3.5 h-3.5 text-gray-400" />
                <span>{createdDateStr}</span>
              </div>
            </div>

            <div>
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block mb-1">
                Team Size
              </span>
              <div className="flex items-center gap-2 text-sm text-gray-300">
                <Users className="w-3.5 h-3.5 text-gray-400" />
                <span>{activeMembers.length} active {activeMembers.length === 1 ? "collaborator" : "collaborators"}</span>
              </div>
            </div>
          </div>

          {collaboration.projectBriefDescription && (
            <div>
              <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                Project Summary
              </h3>
              <p className="text-sm text-gray-300 leading-relaxed max-w-3xl">
                {collaboration.projectBriefDescription}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Two Column Section: Team (Left) & Workspace Group Chat (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Team Members List (5 columns) */}
        <div className="lg:col-span-5 flex flex-col gap-6">
          <div className="dashboard-card rounded-2xl p-6 border border-white/5 bg-huddle-charcoal/40">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h2 className="text-base font-bold text-white mb-0.5 flex items-center gap-2">
                  <Users className="w-4 h-4 text-huddle-purple" />
                  Workspace Team
                </h2>
                <p className="text-[11px] text-gray-400">
                  All active collaborators contributing here.
                </p>
              </div>
              <span className="text-xs font-medium text-gray-400 bg-white/5 border border-white/5 px-2.5 py-0.5 rounded-full">
                {activeMembers.length}
              </span>
            </div>

            {/* Departure requests pending owner resolution */}
            {isOwner && departurePendingMembers.length > 0 && (
              <div className="mb-4 p-3 rounded-xl bg-yellow-500/5 border border-yellow-500/20">
                <p className="text-[11px] font-semibold text-yellow-400 mb-2 flex items-center gap-1.5">
                  <AlertTriangle className="w-3.5 h-3.5" />
                  Pending departure requests ({departurePendingMembers.length})
                </p>
                <div className="space-y-2">
                  {departurePendingMembers.map((m) => {
                    const name = m.user?.displayName || "Member";
                    return (
                      <div key={m.id} className="flex items-center justify-between gap-2">
                        <span className="text-xs text-gray-300 truncate">{name}</span>
                        <div className="flex items-center gap-1.5 flex-shrink-0">
                          <button
                            onClick={() => handleResolveDeparture(m.user?.id || "", name, true)}
                            disabled={actionLoading}
                            className="px-2 py-1 rounded-md bg-green-500/20 hover:bg-green-500/30 text-green-400 text-[10px] font-medium transition-colors disabled:opacity-50"
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => handleResolveDeparture(m.user?.id || "", name, false)}
                            disabled={actionLoading}
                            className="px-2 py-1 rounded-md bg-red-500/10 hover:bg-red-500/20 text-red-400 text-[10px] font-medium transition-colors disabled:opacity-50"
                          >
                            Decline
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Active members list */}
            <div className="space-y-3">
              {activeMembers.map((member) => {
                const displayName = member.user?.displayName || "Anonymous Collaborator";
                const initials = displayName.charAt(0).toUpperCase();
                const joinedDate = member.joinedAt
                  ? new Date(member.joinedAt).toLocaleDateString(undefined, {
                      month: "short",
                      day: "numeric",
                    })
                  : null;
                const isSelf = member.user?.id === currentUserId;
                const isMemberOwner = member.role === "OWNER";
                const hasDeparturePending =
                  member.departureStatus === "PENDING_APPROVAL" ||
                  member.departureStatus === "ESCALATED_ADMIN";

                return (
                  <div
                    key={member.id}
                    className="p-3.5 rounded-xl bg-white/[0.02] border border-white/5 flex items-start justify-between gap-3 hover:border-white/10 transition-colors"
                  >
                    <div className="flex items-start gap-3 min-w-0">
                      <div className="w-9 h-9 rounded-full bg-gradient-to-br from-huddle-coral to-huddle-purple flex items-center justify-center text-white font-bold text-sm flex-shrink-0 shadow-md">
                        {initials}
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                          <Link
                            href={`/people/${member.user?.id}`}
                            className="text-xs font-semibold text-white hover:text-huddle-coral transition-colors"
                          >
                            {displayName}
                          </Link>
                          {isMemberOwner ? (
                            <span className="px-1.5 rounded-full text-[9px] font-semibold bg-huddle-purple/20 text-huddle-purple border border-huddle-purple/30">
                              Owner
                            </span>
                          ) : (
                            <span className="px-1.5 rounded-full text-[9px] font-semibold bg-white/5 text-gray-400 border border-white/10">
                              Member
                            </span>
                          )}
                          {isSelf && (
                            <span className="px-1.5 rounded-full text-[9px] font-semibold bg-huddle-coral/20 text-huddle-coral border border-huddle-coral/30">
                              You
                            </span>
                          )}
                          {hasDeparturePending && (
                            <span className="px-1.5 rounded-full text-[9px] font-semibold bg-yellow-500/20 text-yellow-400 border border-yellow-500/30">
                              Leaving
                            </span>
                          )}
                        </div>

                        {member.user?.bio && (
                          <p className="text-[11px] text-gray-400 line-clamp-1 mb-1">
                            {member.user.bio}
                          </p>
                        )}

                        {joinedDate && (
                          <div className="text-[10px] text-gray-500 flex items-center gap-1">
                            <Clock className="w-2.5 h-2.5" />
                            <span>Joined {joinedDate}</span>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-1 flex-shrink-0">
                      {/* Owner actions on non-owner active members (not self) when not archived */}
                      {isOwner && !isSelf && !isMemberOwner && !isArchived && (
                        <>
                          {/* Suspend */}
                          <button
                            onClick={() => {
                              setModalTarget({ id: member.user?.id || "", displayName });
                              setModalReason("");
                              setActiveModal("suspend");
                            }}
                            className="p-1.5 rounded-lg text-gray-500 hover:text-yellow-400 hover:bg-yellow-400/10 transition-colors"
                            title={`Suspend ${displayName}`}
                          >
                            <PauseCircle className="w-3.5 h-3.5" />
                          </button>
                          {/* Remove */}
                          <button
                            onClick={() => {
                              setModalTarget({ id: member.user?.id || "", displayName });
                              setModalReason("");
                              setActiveModal("remove");
                            }}
                            className="p-1.5 rounded-lg text-gray-500 hover:text-red-400 hover:bg-red-400/10 transition-colors"
                            title={`Remove ${displayName}`}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </>
                      )}
                      <Link
                        href={`/people/${member.user?.id}`}
                        className="p-1.5 text-gray-500 hover:text-gray-300 transition-colors"
                        title="View Public Profile"
                      >
                        <ExternalLink className="w-3 h-3" />
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Suspended members section */}
            {suspendedMembers.length > 0 && (
              <div className="mt-4">
                <p className="text-[11px] font-semibold text-yellow-500/80 uppercase tracking-wider mb-2">
                  Suspended ({suspendedMembers.length})
                </p>
                <div className="space-y-2">
                  {suspendedMembers.map((member) => {
                    const displayName = member.user?.displayName || "Anonymous";
                    const initials = displayName.charAt(0).toUpperCase();
                    return (
                      <div
                        key={member.id}
                        className="p-3 rounded-xl bg-yellow-500/[0.04] border border-yellow-500/20 flex items-center gap-3"
                      >
                        <div className="w-7 h-7 rounded-full bg-yellow-500/10 flex items-center justify-center text-yellow-400 font-bold text-xs flex-shrink-0">
                          {initials}
                        </div>
                        <div className="min-w-0 flex-1">
                          <span className="text-xs text-gray-300 font-medium block">{displayName}</span>
                          {member.suspensionReason && (
                            <span className="text-[10px] text-gray-500 line-clamp-1">{member.suspensionReason}</span>
                          )}
                        </div>
                        <div className="flex items-center gap-1 flex-shrink-0">
                          {isOwner && !isArchived && (
                            <>
                              <button
                                onClick={() => {
                                  setModalTarget({ id: member.user?.id || "", displayName });
                                  setActiveModal("reinstate");
                                }}
                                className="p-1.5 rounded-lg text-gray-500 hover:text-green-400 hover:bg-green-400/10 transition-colors"
                                title={`Reinstate ${displayName}`}
                              >
                                <PlayCircle className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => {
                                  setModalTarget({ id: member.user?.id || "", displayName });
                                  setModalReason("");
                                  setActiveModal("remove");
                                }}
                                className="p-1.5 rounded-lg text-gray-500 hover:text-red-400 hover:bg-red-400/10 transition-colors"
                                title={`Remove ${displayName}`}
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Member actions footer */}
            <div className="mt-4 pt-4 border-t border-white/5 flex flex-col gap-2">
              {isOwner ? (
                <>
                  <div className="flex items-center gap-2 text-[11px] text-gray-500">
                    <ShieldCheck className="w-3.5 h-3.5 text-huddle-purple" />
                    <span>You are the Project Lead. Archive the project to close the workspace.</span>
                  </div>
                  {/* Transfer Ownership button — only shown when no pending transfer */}
                  {!pendingTransfer && !isArchived && transferCandidates.length > 0 && (
                    <button
                      onClick={() => {
                        setTransferRecipientId("");
                        setActiveModal("transferOwnership");
                      }}
                      className="flex items-center gap-2 text-xs text-purple-400 hover:text-purple-300 transition-colors px-3 py-2 rounded-lg bg-purple-400/5 hover:bg-purple-400/10 border border-purple-400/10 w-full justify-center mt-1"
                    >
                      <GitBranch className="w-3.5 h-3.5" />
                      Transfer Ownership
                    </button>
                  )}
                </>
              ) : (
                !isArchived && (
                  <>
                    {hasPendingDeparture ? (
                      <div className="flex items-center gap-2 text-xs text-yellow-400 px-3 py-2 rounded-lg bg-yellow-400/5 border border-yellow-400/10 w-full justify-center">
                        <Clock className="w-3.5 h-3.5" />
                        Departure request pending owner review
                      </div>
                    ) : (
                      <button
                        onClick={() => {
                          setModalReason("");
                          setActiveModal("departure");
                        }}
                        className="flex items-center gap-2 text-xs text-red-400 hover:text-red-300 transition-colors px-3 py-2 rounded-lg bg-red-400/5 hover:bg-red-400/10 border border-red-400/10 w-full justify-center"
                      >
                        <LogOut className="w-3.5 h-3.5" />
                        Request to Leave
                      </button>
                    )}
                  </>
                )
              )}
            </div>

            {/* Former Members Collapsible */}
            {formerMembers.length > 0 && (
              <div className="mt-4">
                <button
                  onClick={() => setFormerMembersOpen((v) => !v)}
                  className="flex items-center gap-2 text-[11px] text-gray-500 hover:text-gray-300 transition-colors w-full"
                >
                  {formerMembersOpen ? (
                    <ChevronDown className="w-3.5 h-3.5" />
                  ) : (
                    <ChevronRight className="w-3.5 h-3.5" />
                  )}
                  <span>Former members ({formerMembers.length})</span>
                </button>

                {formerMembersOpen && (
                  <div className="mt-3 space-y-2">
                    {formerMembers.map((member) => {
                      const displayName = member.user?.displayName || "Anonymous";
                      const initials = displayName.charAt(0).toUpperCase();
                      const statusLabel =
                        member.status === "LEFT"
                          ? "Left"
                          : member.status === "REMOVED"
                          ? "Removed"
                          : "Suspended";
                      const reason = member.removalReason || member.departureReason;
                      return (
                        <div
                          key={member.id}
                          className="p-3 rounded-xl bg-white/[0.01] border border-white/[0.04] flex items-center gap-3 opacity-60"
                        >
                          <div className="w-7 h-7 rounded-full bg-white/10 flex items-center justify-center text-gray-400 font-bold text-xs flex-shrink-0">
                            {initials}
                          </div>
                          <div className="min-w-0 flex-1">
                            <span className="text-xs text-gray-400 font-medium">{displayName}</span>
                            {reason && (
                              <p className="text-[10px] text-gray-600 line-clamp-1 mt-0.5">{reason}</p>
                            )}
                          </div>
                          <span className="text-[10px] text-gray-600 bg-white/5 px-2 py-0.5 rounded-full flex-shrink-0">
                            {statusLabel}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Workspace Group Chat Section (7 columns) */}
        <div className="lg:col-span-7">
          <div className="dashboard-card rounded-2xl border border-white/5 bg-huddle-charcoal/40 flex flex-col h-[580px] overflow-hidden">
            {/* Chat Header */}
            <div className="p-4 border-b border-white/5 flex items-center justify-between bg-huddle-charcoal/60">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-huddle-coral/10 border border-huddle-coral/20 flex items-center justify-center">
                  <MessageSquare className="w-4 h-4 text-huddle-coral" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-white">Collaboration Group Chat</h3>
                  <div className="flex items-center gap-2">
                    {stompStatus === "connecting" && (
                      <span className="text-[11px] text-yellow-400 flex items-center gap-1">
                        <Loader2 className="w-3 h-3 animate-spin" /> Connecting...
                      </span>
                    )}
                    {stompStatus === "connected" && (
                      <span className="text-[11px] text-green-400 flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" /> Live Group
                      </span>
                    )}
                    {stompStatus === "error" && (
                      <span className="text-[11px] text-red-400 flex items-center gap-1">
                        <AlertCircle className="w-3 h-3" /> Offline ({chatErrorMsg || "Error"})
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <span className="text-xs text-gray-400 bg-white/5 border border-white/5 px-2.5 py-1 rounded-lg">
                {activeMembers.length} Participants
              </span>
            </div>

            {/* Chat Messages */}
            <div className="flex-1 overflow-y-auto p-5 space-y-3.5 custom-scrollbar">
              {messages.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full text-center p-6 text-gray-500">
                  <Sparkles className="w-8 h-8 text-huddle-coral/50 mb-2" />
                  <p className="text-sm text-gray-300 font-medium">Collaboration group chat is ready</p>
                  <p className="text-xs text-gray-500 mt-1">
                    Send a message to collaborate with the entire project team.
                  </p>
                </div>
              ) : (
                messages.map((msg, idx) => {
                  const isMe = msg.senderId === currentUserId;
                  const senderName = msg.senderDisplayName || "Teammate";
                  return (
                    <div
                      key={msg.id || idx}
                      className={`flex flex-col ${isMe ? "items-end" : "items-start"} max-w-[85%] ${
                        isMe ? "ml-auto" : "mr-auto"
                      }`}
                    >
                      {!isMe && (
                        <span className="text-[11px] text-huddle-coral font-semibold mb-1 px-1">
                          {senderName}
                        </span>
                      )}
                      <div
                        className={`${
                          isMe
                            ? "bg-huddle-coral/20 border border-huddle-coral/30 rounded-2xl rounded-tr-sm"
                            : "bg-white/5 border border-white/10 rounded-2xl rounded-tl-sm"
                        } p-3 shadow-sm`}
                      >
                        <p className={`text-xs md:text-sm ${isMe ? "text-white" : "text-gray-200"} whitespace-pre-wrap leading-relaxed`}>
                          {msg.content}
                        </p>
                        {msg.sentAt && (
                          <span className="text-[10px] text-gray-500 block mt-1 text-right">
                            {new Date(msg.sentAt).toLocaleTimeString([], {
                              hour: "2-digit",
                              minute: "2-digit",
                            })}
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Chat Input */}
            <div className="p-3 bg-huddle-charcoal/80 border-t border-white/5">
              <div className="flex items-center gap-2 relative">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && sendMessage()}
                  className="flex-1 bg-huddle-dark border border-white/10 rounded-full px-4 py-2.5 pr-11 text-white text-xs md:text-sm outline-none focus:border-huddle-coral transition-colors"
                  placeholder={
                    isArchived
                      ? "Workspace is archived (read-only)"
                      : stompStatus === "connected"
                      ? "Message workspace team..."
                      : "Connecting to group chat..."
                  }
                  disabled={isArchived || stompStatus !== "connected"}
                />
                <button
                  onClick={sendMessage}
                  className="absolute right-1.5 p-2 bg-gradient-to-r from-huddle-coral to-huddle-purple rounded-full text-white disabled:opacity-40 hover:opacity-90 transition-opacity"
                  disabled={isArchived || stompStatus !== "connected" || !input.trim()}
                >
                  <Send className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ─────────────────────────── MODALS ────────────────────────────────── */}

      {activeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">

            {/* ── Departure Request (member) ── */}
            {activeModal === "departure" && (
              <>
                <div className="flex items-start gap-4 mb-5">
                  <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center flex-shrink-0">
                    <LogOut className="w-5 h-5 text-red-400" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-white mb-1">Request to Leave</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">
                      The project owner will review your request. You keep access until it is approved.
                    </p>
                  </div>
                </div>
                <textarea
                  value={modalReason}
                  onChange={(e) => setModalReason(e.target.value)}
                  className="w-full bg-huddle-dark border border-white/10 rounded-xl px-4 py-3 text-sm text-white outline-none focus:border-huddle-coral transition-colors resize-none mb-4"
                  rows={3}
                  placeholder="Reason for leaving (optional)"
                />
                <div className="flex gap-3 justify-end">
                  <button onClick={closeModal} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-white text-sm font-medium transition-colors disabled:opacity-50">
                    Cancel
                  </button>
                  <button onClick={handleDepartureRequest} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-red-500/80 hover:bg-red-500 text-white text-sm font-medium transition-colors disabled:opacity-50 flex items-center gap-2">
                    {actionLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <LogOut className="w-3.5 h-3.5" />}
                    Submit Request
                  </button>
                </div>
              </>
            )}

            {/* ── Remove Member ── */}
            {activeModal === "remove" && modalTarget && (
              <>
                <div className="flex items-start gap-4 mb-5">
                  <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center flex-shrink-0">
                    <Trash2 className="w-5 h-5 text-red-400" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-white mb-1">Remove Member?</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">
                      <span className="text-white font-semibold">{modalTarget.displayName}</span> will immediately lose access to this workspace. Their past messages will remain in chat history.
                    </p>
                  </div>
                </div>
                <textarea
                  value={modalReason}
                  onChange={(e) => setModalReason(e.target.value)}
                  className="w-full bg-huddle-dark border border-white/10 rounded-xl px-4 py-3 text-sm text-white outline-none focus:border-huddle-coral transition-colors resize-none mb-4"
                  rows={3}
                  placeholder="Reason for removal (optional)"
                />
                <div className="flex gap-3 justify-end">
                  <button onClick={closeModal} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-white text-sm font-medium transition-colors disabled:opacity-50">
                    Cancel
                  </button>
                  <button onClick={handleRemove} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-red-500/80 hover:bg-red-500 text-white text-sm font-medium transition-colors disabled:opacity-50 flex items-center gap-2">
                    {actionLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
                    Remove {modalTarget.displayName}
                  </button>
                </div>
              </>
            )}

            {/* ── Suspend Member ── */}
            {activeModal === "suspend" && modalTarget && (
              <>
                <div className="flex items-start gap-4 mb-5">
                  <div className="w-10 h-10 rounded-xl bg-yellow-500/10 border border-yellow-500/20 flex items-center justify-center flex-shrink-0">
                    <PauseCircle className="w-5 h-5 text-yellow-400" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-white mb-1">Suspend Member?</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">
                      <span className="text-white font-semibold">{modalTarget.displayName}</span> will lose chat access temporarily. You can reinstate them at any time.
                    </p>
                  </div>
                </div>
                <textarea
                  value={modalReason}
                  onChange={(e) => setModalReason(e.target.value)}
                  className="w-full bg-huddle-dark border border-white/10 rounded-xl px-4 py-3 text-sm text-white outline-none focus:border-yellow-500 transition-colors resize-none mb-4"
                  rows={3}
                  placeholder="Reason for suspension (required)"
                />
                <div className="flex gap-3 justify-end">
                  <button onClick={closeModal} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-white text-sm font-medium transition-colors disabled:opacity-50">
                    Cancel
                  </button>
                  <button onClick={handleSuspend} disabled={actionLoading || !modalReason.trim()} className="px-4 py-2 rounded-lg bg-yellow-500/80 hover:bg-yellow-500 text-white text-sm font-medium transition-colors disabled:opacity-50 flex items-center gap-2">
                    {actionLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <PauseCircle className="w-3.5 h-3.5" />}
                    Suspend
                  </button>
                </div>
              </>
            )}

            {/* ── Reinstate Member ── */}
            {activeModal === "reinstate" && modalTarget && (
              <>
                <div className="flex items-start gap-4 mb-5">
                  <div className="w-10 h-10 rounded-xl bg-green-500/10 border border-green-500/20 flex items-center justify-center flex-shrink-0">
                    <PlayCircle className="w-5 h-5 text-green-400" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-white mb-1">Reinstate Member?</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">
                      <span className="text-white font-semibold">{modalTarget.displayName}</span> will regain full workspace access immediately.
                    </p>
                  </div>
                </div>
                <div className="flex gap-3 justify-end">
                  <button onClick={closeModal} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-white text-sm font-medium transition-colors disabled:opacity-50">
                    Cancel
                  </button>
                  <button onClick={handleReinstate} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-green-500/80 hover:bg-green-500 text-white text-sm font-medium transition-colors disabled:opacity-50 flex items-center gap-2">
                    {actionLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <PlayCircle className="w-3.5 h-3.5" />}
                    Reinstate
                  </button>
                </div>
              </>
            )}

            {/* ── Transfer Ownership ── */}
            {activeModal === "transferOwnership" && (
              <>
                <div className="flex items-start gap-4 mb-5">
                  <div className="w-10 h-10 rounded-xl bg-purple-500/10 border border-purple-500/20 flex items-center justify-center flex-shrink-0">
                    <GitBranch className="w-5 h-5 text-purple-400" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-white mb-1">Transfer Ownership</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">
                      Select a team member to become the new project owner. They must accept before the transfer takes effect. You will become a regular member.
                    </p>
                  </div>
                </div>
                <select
                  value={transferRecipientId}
                  onChange={(e) => setTransferRecipientId(e.target.value)}
                  className="w-full bg-huddle-dark border border-white/10 rounded-xl px-4 py-2.5 text-sm text-white outline-none focus:border-purple-500 transition-colors mb-4"
                >
                  <option value="">— Select new owner —</option>
                  {transferCandidates.map((m) => (
                    <option key={m.user?.id} value={m.user?.id}>
                      {m.user?.displayName || "Member"}
                    </option>
                  ))}
                </select>
                <div className="flex gap-3 justify-end">
                  <button onClick={closeModal} disabled={actionLoading} className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-white text-sm font-medium transition-colors disabled:opacity-50">
                    Cancel
                  </button>
                  <button onClick={handleTransferOwnership} disabled={actionLoading || !transferRecipientId} className="px-4 py-2 rounded-lg bg-purple-500/80 hover:bg-purple-500 text-white text-sm font-medium transition-colors disabled:opacity-50 flex items-center gap-2">
                    {actionLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <GitBranch className="w-3.5 h-3.5" />}
                    Send Offer
                  </button>
                </div>
              </>
            )}

          </div>
        </div>
      )}
    </div>
  );
}
