"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Check,
  X,
  Loader2,
  Inbox,
  Send,
  MessageSquare,
  Zap,
  CheckCircle2,
  Clock,
  XCircle,
} from "lucide-react";
import { isLoggedIn } from "@/lib/auth";
import { requestService } from "@/lib/api";

/* ── helpers ────────────────────────────────────────────────── */

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString();
}

function getInitials(name: string) {
  return (name || "?")
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
  "from-emerald-500 to-teal-600",
];
function avatarGradient(seed: string) {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h += seed.charCodeAt(i);
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}

/* ── status badge ─────────────────────────────────────────── */
function StatusBadge({ status }: { status: string }) {
  const s = (status || "").toUpperCase();
  if (s === "ACCEPTED")
    return (
      <span className="inline-flex items-center gap-1 text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20 text-[11px] uppercase tracking-wider font-bold">
        <CheckCircle2 className="w-3 h-3" />
        Status: ACCEPTED
      </span>
    );
  if (s === "REJECTED")
    return (
      <span className="inline-flex items-center gap-1 text-red-400 bg-red-500/10 px-2 py-0.5 rounded border border-red-500/20 text-[11px] uppercase tracking-wider font-bold">
        <XCircle className="w-3 h-3" />
        Status: REJECTED
      </span>
    );
  // PENDING
  return (
    <span className="inline-flex items-center gap-1 text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20 text-[11px] uppercase tracking-wider font-bold">
      <Clock className="w-3 h-3" />
      Status: PENDING
    </span>
  );
}

/* ── request card ─────────────────────────────────────────── */
interface RequestCardProps {
  req: any;
  view: "received" | "sent";
  onAction: (id: string, action: "accept" | "reject" | "withdraw") => void;
  loading: boolean;
  router: ReturnType<typeof useRouter>;
}

function RequestCard({ req, view, onAction, loading, router }: RequestCardProps) {
  const status = (req.status || "").toUpperCase();
  const isRejected = status === "REJECTED";
  const isAccepted = status === "ACCEPTED";
  const isPending = status === "PENDING";

  const displayName = view === "received"
    ? (req.requestorDisplayName || "Unknown")
    : "You";

  const initials = view === "received"
    ? getInitials(req.requestorDisplayName || "?")
    : "ME";

  const gradientClass = avatarGradient(req.id || req.requestorDisplayName || "x");

  return (
    <div
      className={`group relative rounded-2xl border transition-all duration-300 cursor-default
        bg-gradient-to-br from-[rgba(21,21,28,0.7)] to-[rgba(13,13,18,0.9)]
        border-white/[0.06] backdrop-blur-xl
        hover:-translate-y-0.5 hover:border-white/[0.14]
        hover:shadow-[0_12px_35px_-5px_rgba(0,0,0,0.5),0_0_20px_-5px_rgba(155,81,224,0.1)]
        p-6 flex flex-col sm:flex-row sm:items-start justify-between gap-5
        ${isRejected ? "opacity-60" : ""}
      `}
    >
      {/* Left: avatar + content */}
      <div className="flex items-start gap-4 flex-1 min-w-0">
        {/* Avatar */}
        <div
          className={`w-12 h-12 rounded-full bg-gradient-to-br ${gradientClass} flex items-center justify-center text-white font-bold text-sm border border-white/10 flex-shrink-0 mt-0.5 shadow-md`}
        >
          {initials}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Who + project chip */}
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span className="font-display font-bold text-base text-white">
              {displayName}
            </span>
            <span className="text-xs text-gray-400 font-medium">
              {view === "received" ? "wants to join" : "requested to join"}
            </span>
            <span className="text-xs font-extrabold text-huddle-coral bg-huddle-coral/10 px-2 py-0.5 rounded border border-huddle-coral/20 truncate max-w-[200px]">
              &ldquo;{req.postTitle}&rdquo;
            </span>
          </div>

          {/* Message bubble */}
          {req.previewMessage && (
            <p className="text-sm text-gray-200 font-medium bg-black/40 px-3.5 py-2.5 rounded-xl border border-white/[0.06] mb-3 italic">
              &ldquo;{req.previewMessage}&rdquo;
            </p>
          )}

          {/* Timestamp + status */}
          <div className="flex flex-wrap items-center gap-2 text-[11px] font-semibold text-gray-400">
            {req.createdAt && <span>{formatDate(req.createdAt)}</span>}
            <span className="text-gray-600">•</span>
            <StatusBadge status={req.status} />
          </div>
        </div>
      </div>

      {/* Right: actions */}
      <div className="flex sm:flex-col justify-end gap-2 flex-shrink-0 pt-3 sm:pt-0 border-t sm:border-t-0 border-white/[0.06]">
        {/* RECEIVED + PENDING → Accept / Reject */}
        {view === "received" && isPending && (
          <>
            <button
              onClick={() => onAction(req.id, "accept")}
              disabled={loading}
              className="flex items-center justify-center gap-1.5 px-5 py-2.5 rounded-xl bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/20 text-xs font-bold transition-all shadow-sm disabled:opacity-50"
            >
              <Check className="w-3.5 h-3.5" />
              Accept
            </button>
            <button
              onClick={() => onAction(req.id, "reject")}
              disabled={loading}
              className="flex items-center justify-center gap-1.5 px-5 py-2.5 rounded-xl bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs font-bold transition-all shadow-sm disabled:opacity-50"
            >
              <X className="w-3.5 h-3.5" />
              Reject
            </button>
          </>
        )}

        {/* ACCEPTED → Open Chat */}
        {isAccepted && (
          <button
            onClick={() => router.push(`/chat?requestId=${req.id}`)}
            className="flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-white border border-white/10 text-xs font-bold transition-all shadow-md"
          >
            <MessageSquare className="w-4 h-4 text-huddle-pink" />
            Open Chat
          </button>
        )}

        {/* SENT + PENDING → Awaiting / Withdraw */}
        {view === "sent" && isPending && (
          <button
            onClick={() => onAction(req.id, "withdraw")}
            disabled={loading}
            className="flex items-center justify-center gap-1.5 px-5 py-2.5 rounded-xl bg-white/5 hover:bg-red-500/10 text-gray-400 hover:text-red-400 border border-white/[0.06] hover:border-red-500/20 text-xs font-bold transition-all disabled:opacity-50"
          >
            <X className="w-3.5 h-3.5" />
            Withdraw
          </button>
        )}
      </div>
    </div>
  );
}

/* ── empty state ──────────────────────────────────────────── */
function EmptyState({ view }: { view: "received" | "sent" }) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <div className="w-16 h-16 rounded-2xl bg-white/[0.04] border border-white/[0.06] flex items-center justify-center mx-auto mb-5">
        {view === "received"
          ? <Inbox className="w-7 h-7 text-gray-600" />
          : <Send className="w-7 h-7 text-gray-600" />
        }
      </div>
      <h3 className="text-lg font-display font-semibold text-white mb-2">
        {view === "received" ? "No received requests" : "No sent requests"}
      </h3>
      <p className="text-sm text-gray-500 max-w-xs leading-relaxed">
        {view === "received"
          ? "When someone sends you a collaboration request, it will appear here."
          : "Requests you send to project owners will be tracked here."}
      </p>
    </div>
  );
}

/* ── main page ────────────────────────────────────────────── */
export default function RequestsPage() {
  const router = useRouter();
  const [view, setView] = useState<"received" | "sent">("received");
  const [received, setReceived] = useState<any[]>([]);
  const [sent, setSent] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchBoth = async () => {
    setLoading(true);
    setError(null);
    try {
      const [r, s] = await Promise.all([
        requestService.getMyRequests("received").catch(() => []),
        requestService.getMyRequests("sent").catch(() => []),
      ]);
      setReceived(Array.isArray(r) ? r : []);
      setSent(Array.isArray(s) ? s : []);
    } catch (e: any) {
      setError(e.message ?? "Failed to load requests");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    fetchBoth();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);

  const handleAction = async (id: string, action: "accept" | "reject" | "withdraw") => {
    setLoading(true);
    try {
      if (action === "accept") await requestService.accept(id);
      else if (action === "reject") await requestService.reject(id);
      else if (action === "withdraw") await requestService.withdraw(id);
      await fetchBoth();
    } catch (e: any) {
      setError(e.message ?? "Action failed");
    } finally {
      setLoading(false);
    }
  };

  const activeRequests = view === "received" ? received : sent;

  return (
    <div className="max-w-4xl mx-auto w-full relative">
      {/* Ambient glow */}
      <div className="absolute -top-32 right-0 w-[500px] h-[500px] bg-huddle-purple opacity-[0.07] blur-[140px] rounded-full pointer-events-none" />
      <div className="absolute top-60 -left-20 w-[400px] h-[400px] bg-huddle-coral opacity-[0.05] blur-[120px] rounded-full pointer-events-none" />

      {/* ── Page Header ── */}
      <div className="mb-8 relative z-10">
        {/* Label pill */}
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-white/10 bg-white/[0.04] backdrop-blur-sm mb-4">
          <Zap className="w-3.5 h-3.5 text-huddle-coral" />
          <span className="text-[11px] font-bold tracking-widest text-gray-300 uppercase">
            Collaboration Hub
          </span>
        </div>

        <h1 className="font-display font-extrabold text-4xl text-white tracking-tight mb-2">
          Collaboration{" "}
          <span
            style={{
              background: "linear-gradient(135deg, #FF6B6B 0%, #FF4785 50%, #9B51E0 100%)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              backgroundClip: "text",
            }}
          >
            Requests
          </span>
        </h1>
        <p className="text-sm font-medium text-gray-400">
          Manage who wants to build with you and track the status of your
          outgoing requests.
        </p>
      </div>

      {/* ── Error banner ── */}
      {error && (
        <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 text-red-300 rounded-xl text-sm">
          {error}
        </div>
      )}

      {/* ── Segmented Tabs ── */}
      <div className="inline-flex p-1.5 rounded-xl bg-huddle-charcoal/90 border border-white/10 mb-8 backdrop-blur-md shadow-lg relative z-10">
        <button
          onClick={() => setView("received")}
          className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-xs font-bold transition-all ${
            view === "received"
              ? "bg-white/10 text-white shadow-[0_4px_15px_rgba(0,0,0,0.3)] border border-white/[0.08]"
              : "text-gray-400 hover:text-gray-200"
          }`}
        >
          <Inbox className={`w-4 h-4 ${view === "received" ? "text-huddle-coral" : ""}`} />
          Received Requests
          {received.length > 0 && (
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                view === "received"
                  ? "bg-huddle-coral/20 text-huddle-coral"
                  : "bg-white/10 text-gray-300"
              }`}
            >
              {received.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setView("sent")}
          className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-xs font-bold transition-all ${
            view === "sent"
              ? "bg-white/10 text-white shadow-[0_4px_15px_rgba(0,0,0,0.3)] border border-white/[0.08]"
              : "text-gray-400 hover:text-gray-200"
          }`}
        >
          <Send className={`w-4 h-4 ${view === "sent" ? "text-huddle-coral" : ""}`} />
          Sent Requests
          {sent.length > 0 && (
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                view === "sent"
                  ? "bg-huddle-coral/20 text-huddle-coral"
                  : "bg-white/10 text-gray-300"
              }`}
            >
              {sent.length}
            </span>
          )}
        </button>
      </div>

      {/* ── Content ── */}
      <div className="relative z-10">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3 text-gray-400">
            <Loader2 className="w-6 h-6 animate-spin text-huddle-coral" />
            <span className="text-sm font-medium">Loading requests…</span>
          </div>
        ) : activeRequests.length === 0 ? (
          <EmptyState view={view} />
        ) : (
          <div className="space-y-4">
            {activeRequests.map((req) => (
              <RequestCard
                key={req.id}
                req={req}
                view={view}
                onAction={handleAction}
                loading={loading}
                router={router}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
