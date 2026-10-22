"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  CheckCheck,
  Check,
  Loader2,
  BellOff,
  UserPlus,
  CheckCircle2,
  MessageSquare,
  FolderGit2,
  XCircle,
} from "lucide-react";
import { isLoggedIn } from "@/lib/auth";
import { notificationService, Notification } from "@/lib/api";

function isNotificationRead(n: Notification): boolean {
  return Boolean(n.isRead || n.read);
}

function timeAgo(dateString: string): string {
  const now = new Date();
  const then = new Date(dateString);
  const diffMs = now.getTime() - then.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) return "Yesterday";
  return `${diffDays}d ago`;
}

interface NotificationTypeStyle {
  icon: typeof Bell;
  iconBoxClass: string;
  badgeText?: string;
  badgeClass?: string;
}

function getNotificationStyle(type?: string, title?: string): NotificationTypeStyle {
  const t = (type || "").toUpperCase();
  const ttl = (title || "").toLowerCase();

  if (t.includes("COLLAB_REQUEST_RECEIVED") || t === "COLLAB_REQUEST" || ttl.includes("request")) {
    return {
      icon: UserPlus,
      iconBoxClass: "bg-huddle-coral/20 border-huddle-coral/30 text-huddle-coral",
      badgeText: "Pending Request",
      badgeClass: "text-huddle-coral bg-huddle-coral/10 border-huddle-coral/20",
    };
  }
  if (t.includes("ACCEPTED") || ttl.includes("accepted")) {
    return {
      icon: CheckCircle2,
      iconBoxClass: "bg-green-500/10 border-green-500/20 text-green-400",
    };
  }
  if (t.includes("REJECTED") || ttl.includes("declined") || ttl.includes("rejected")) {
    return {
      icon: XCircle,
      iconBoxClass: "bg-red-500/10 border-red-500/20 text-red-400",
    };
  }
  if (t.includes("MESSAGE") || ttl.includes("message")) {
    return {
      icon: MessageSquare,
      iconBoxClass: "bg-blue-500/10 border-blue-500/20 text-blue-400",
    };
  }
  if (t.includes("MEMBER") || t.includes("PROJECT") || ttl.includes("project") || ttl.includes("joined")) {
    return {
      icon: FolderGit2,
      iconBoxClass: "bg-purple-500/10 border-purple-500/20 text-purple-400",
    };
  }
  return {
    icon: Bell,
    iconBoxClass: "bg-white/10 border-white/20 text-gray-300",
  };
}

export default function NotificationsPage() {
  const router = useRouter();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);

  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await notificationService.getNotifications();
      setNotifications(Array.isArray(data) ? data : []);
    } catch (e: any) {
      setError(e.message || "Failed to load notifications");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    fetchNotifications();
  }, [fetchNotifications, router]);

  const handleClick = async (notification: Notification) => {
    const wasUnread = !isNotificationRead(notification);

    if (wasUnread) {
      // Optimistically update local state immediately
      setNotifications((prev) =>
        prev.map((n) =>
          n.id === notification.id ? { ...n, isRead: true, read: true } : n
        )
      );

      // Persist to backend and await before navigating so the network request isn't aborted
      try {
        await notificationService.markRead(notification.id);
        if (typeof window !== "undefined") {
          window.dispatchEvent(new Event("refresh-badges"));
        }
      } catch (err) {
        console.error("Failed to mark notification as read", err);
      }
    }

    if (notification.targetUrl) {
      router.push(notification.targetUrl);
    }
  };

  const handleMarkAllRead = async () => {
    setMarkingAll(true);
    try {
      await notificationService.markAllRead();
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, isRead: true, read: true }))
      );
      if (typeof window !== "undefined") {
        window.dispatchEvent(new Event("refresh-badges"));
      }
    } catch (e: any) {
      setError(e.message || "Failed to mark all as read");
    } finally {
      setMarkingAll(false);
    }
  };

  const unreadCount = notifications.filter((n) => !isNotificationRead(n)).length;

  return (
    <div className="max-w-3xl mx-auto w-full relative">
      {/* Ambient background glows */}
      <div className="absolute top-[-10%] right-[-10%] w-[600px] h-[600px] bg-huddle-purple opacity-[0.04] blur-[140px] rounded-full pointer-events-none" />
      <div className="absolute bottom-[0%] left-[-10%] w-[500px] h-[500px] bg-huddle-coral opacity-[0.03] blur-[120px] rounded-full pointer-events-none" />

      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8 pb-6 border-b border-white/5 relative z-10">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-huddle-coral/10 border border-huddle-coral/30 flex items-center justify-center text-huddle-coral shadow-lg flex-shrink-0">
            <Bell className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="font-display font-extrabold text-2xl md:text-3xl text-white tracking-tight">
                Notifications
              </h1>
              {unreadCount > 0 ? (
                <span className="bg-huddle-coral/20 text-huddle-coral border border-huddle-coral/30 text-xs font-bold px-2.5 py-0.5 rounded-full">
                  {unreadCount} unread
                </span>
              ) : (
                <span className="bg-white/5 text-gray-400 border border-white/10 text-xs font-bold px-2.5 py-0.5 rounded-full">
                  0 unread
                </span>
              )}
            </div>
            <p className="text-sm text-gray-400 mt-1 font-medium">
              Stay updated on collaboration requests, messages, and project activity.
            </p>
          </div>
        </div>

        {unreadCount > 0 ? (
          <button
            onClick={handleMarkAllRead}
            disabled={markingAll}
            className="px-4 py-2.5 rounded-xl text-xs font-bold text-gray-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 flex items-center gap-2 self-start sm:self-center transition-all shadow-sm disabled:opacity-50 flex-shrink-0"
          >
            {markingAll ? (
              <Loader2 className="w-4 h-4 animate-spin text-huddle-coral" />
            ) : (
              <CheckCheck className="w-4 h-4 text-huddle-coral" />
            )}
            Mark all as read
          </button>
        ) : (
          <div className="px-4 py-2.5 rounded-xl text-xs font-bold text-gray-500 bg-white/[0.02] border border-white/5 flex items-center gap-2 self-start sm:self-center">
            <Check className="w-4 h-4 text-emerald-400" />
            All caught up
          </div>
        )}
      </div>

      {/* Error banner */}
      {error && (
        <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 text-red-300 rounded-xl text-sm relative z-10">
          {error}
        </div>
      )}

      {/* Content Feed */}
      <div className="relative z-10">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3 text-gray-400">
            <Loader2 className="w-6 h-6 animate-spin text-huddle-coral" />
            <span className="text-sm font-medium">Loading notifications…</span>
          </div>
        ) : notifications.length === 0 ? (
          /* Empty state */
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="w-16 h-16 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center text-gray-500 mb-4 shadow-inner">
              <BellOff className="w-8 h-8" />
            </div>
            <h3 className="font-display font-bold text-lg text-white mb-1">
              You're all caught up.
            </h3>
            <p className="text-sm text-gray-400 max-w-sm">
              New collaboration activity and updates will appear here.
            </p>
          </div>
        ) : (
          /* Notification Cards */
          <div className="space-y-3.5">
            {notifications.map((notification) => {
              const isRead = isNotificationRead(notification);
              const style = getNotificationStyle(notification.type, notification.title);
              const IconComp = style.icon;

              return (
                <div
                  key={notification.id}
                  onClick={() => handleClick(notification)}
                  className={`p-5 flex items-start gap-4 rounded-2xl border transition-all duration-300 relative group cursor-pointer ${
                    !isRead
                      ? "bg-gradient-to-br from-[#FF6B6B]/[0.06] to-[#15151C]/60 border-huddle-coral/20 shadow-[0_4px_20px_rgba(255,107,107,0.05)] hover:border-huddle-coral/30"
                      : "bg-[#15151C]/40 border-white/[0.06] hover:bg-[#15151C]/70 hover:border-white/10 hover:shadow-[0_10px_30px_-10px_rgba(0,0,0,0.5)]"
                  } hover:-translate-y-0.5`}
                >
                  {/* Left Type Icon Box */}
                  <div
                    className={`w-10 h-10 rounded-xl border flex items-center justify-center flex-shrink-0 mt-0.5 shadow-inner ${style.iconBoxClass}`}
                  >
                    <IconComp className="w-5 h-5" />
                  </div>

                  {/* Body */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-1">
                      <h3
                        className={`text-sm font-bold flex items-center gap-2 ${
                          !isRead
                            ? "text-white"
                            : "text-gray-200 group-hover:text-white transition-colors"
                        }`}
                      >
                        {notification.title}
                        {!isRead && (
                          <span className="w-2 h-2 rounded-full bg-huddle-coral shadow-[0_0_8px_#FF6B6B] flex-shrink-0" />
                        )}
                      </h3>
                      <span className="text-xs font-medium text-gray-500 whitespace-nowrap ml-2">
                        {timeAgo(notification.createdAt)}
                      </span>
                    </div>

                    <p
                      className={`text-sm leading-relaxed font-medium mb-2 ${
                        !isRead ? "text-gray-300" : "text-gray-400"
                      }`}
                    >
                      {notification.message}
                    </p>

                    {style.badgeText && (
                      <div className="flex items-center gap-2">
                        <span
                          className={`text-[10px] font-bold uppercase tracking-wider border px-2 py-0.5 rounded-md ${style.badgeClass}`}
                        >
                          {style.badgeText}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
