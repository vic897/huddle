"use client";

import { useEffect, useState } from "react";
import { adminService, AdminReport } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ShieldAlert,
  CheckCircle,
  XCircle,
  Loader2,
  AlertTriangle,
  FolderGit2,
  User,
  Clock,
  Filter,
} from "lucide-react";

export default function AdminDashboardPage() {
  const router = useRouter();
  const [reports, setReports] = useState<AdminReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "PENDING" | "RESOLVED" | "DISMISSED">("ALL");

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    loadReports();
  }, [router]);

  const loadReports = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await adminService.getReports();
      setReports(data || []);
    } catch (err: any) {
      setError(err?.message || "Failed to load reports. Ensure you have administrator privileges.");
    } finally {
      setLoading(false);
    }
  };

  const [actionError, setActionError] = useState<string | null>(null);

  const handleResolve = async (id: string) => {
    setActionLoading(id);
    setActionError(null);
    try {
      const updated = await adminService.resolveReport(id);
      setReports((prev) => prev.map((r) => (r.id === id ? updated : r)));
    } catch (err: any) {
      setActionError("Failed to resolve report: " + (err?.message || "Unknown error"));
    } finally {
      setActionLoading(null);
    }
  };

  const handleDismiss = async (id: string) => {
    setActionLoading(id);
    setActionError(null);
    try {
      const updated = await adminService.dismissReport(id);
      setReports((prev) => prev.map((r) => (r.id === id ? updated : r)));
    } catch (err: any) {
      setActionError("Failed to dismiss report: " + (err?.message || "Unknown error"));
    } finally {
      setActionLoading(null);
    }
  };


  const pendingCount = reports.filter((r) => r.status === "PENDING").length;
  const resolvedCount = reports.filter((r) => r.status === "RESOLVED").length;
  const dismissedCount = reports.filter((r) => r.status === "DISMISSED").length;

  const filteredReports = reports.filter((r) => {
    if (statusFilter === "ALL") return true;
    return r.status === statusFilter;
  });

  return (
    <div className="max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="px-2.5 py-0.5 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-semibold uppercase tracking-wider">
              Admin Area
            </span>
          </div>
          <h1 className="text-3xl md:text-4xl font-display font-bold text-white">
            Moderation Dashboard
          </h1>
          <p className="text-gray-400 text-sm mt-1">
            Review and resolve reported users and projects across the Huddle platform.
          </p>
        </div>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="dashboard-card p-5 rounded-xl border border-yellow-500/20 bg-yellow-500/5 flex items-center justify-between">
          <div>
            <div className="text-xs text-yellow-400/80 font-medium uppercase tracking-wider">Pending Action</div>
            <div className="text-3xl font-bold text-yellow-400 mt-1">{pendingCount}</div>
          </div>
          <AlertTriangle className="w-8 h-8 text-yellow-400/40" />
        </div>
        <div className="dashboard-card p-5 rounded-xl border border-green-500/20 bg-green-500/5 flex items-center justify-between">
          <div>
            <div className="text-xs text-green-400/80 font-medium uppercase tracking-wider">Resolved</div>
            <div className="text-3xl font-bold text-green-400 mt-1">{resolvedCount}</div>
          </div>
          <CheckCircle className="w-8 h-8 text-green-400/40" />
        </div>
        <div className="dashboard-card p-5 rounded-xl border border-white/10 bg-white/5 flex items-center justify-between">
          <div>
            <div className="text-xs text-gray-400 font-medium uppercase tracking-wider">Dismissed</div>
            <div className="text-3xl font-bold text-gray-300 mt-1">{dismissedCount}</div>
          </div>
          <XCircle className="w-8 h-8 text-gray-500/40" />
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 mb-6 border-b border-white/10 pb-4 overflow-x-auto hide-scrollbar">
        {(["ALL", "PENDING", "RESOLVED", "DISMISSED"] as const).map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={`px-4 py-2 rounded-lg text-xs font-semibold transition-colors whitespace-nowrap ${
              statusFilter === status
                ? "bg-white text-huddle-black"
                : "bg-white/5 text-gray-400 hover:text-white hover:bg-white/10"
            }`}
          >
            {status === "ALL" ? `All (${reports.length})` : `${status} (${
              status === "PENDING" ? pendingCount : status === "RESOLVED" ? resolvedCount : dismissedCount
            })`}
          </button>
        ))}
      </div>

      {/* Error state */}
      {error && (
        <div className="dashboard-card p-6 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 text-sm mb-6 flex items-center gap-3">
          <ShieldAlert className="w-6 h-6 flex-shrink-0 text-red-400" />
          <div className="flex-1">
            <div className="font-semibold text-white">Access Error</div>
            <div className="text-red-300/80 text-xs mt-0.5">{error}</div>
          </div>
          <button
            onClick={loadReports}
            className="px-3 py-1.5 rounded-lg bg-red-500/20 hover:bg-red-500/30 text-white text-xs font-semibold transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {actionError && (
        <div className="dashboard-card p-4 rounded-xl border border-red-500/30 bg-red-500/10 text-red-300 text-sm mb-6 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-red-400 flex-shrink-0" />
            <span>{actionError}</span>
          </div>
          <button onClick={() => setActionError(null)} className="text-gray-400 hover:text-white">
            <XCircle className="w-4 h-4" />
          </button>
        </div>
      )}


      {/* Loading state */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-24 text-gray-400 gap-3">
          <Loader2 className="w-6 h-6 animate-spin text-huddle-coral" />
          <span className="text-sm">Loading reports...</span>
        </div>
      ) : filteredReports.length === 0 ? (
        /* Empty state */
        <div className="dashboard-card p-12 rounded-2xl flex flex-col items-center justify-center text-center border border-white/5">
          <CheckCircle className="w-12 h-12 text-gray-500 mb-3" />
          <h3 className="text-lg font-semibold text-white">No reports found</h3>
          <p className="text-gray-400 text-xs mt-1 max-w-sm">
            {statusFilter === "ALL"
              ? "There are currently no reports filed on the platform."
              : `There are no reports with status "${statusFilter}".`}
          </p>
        </div>
      ) : (
        /* Reports Table / Cards */
        <div className="space-y-4">
          {filteredReports.map((report) => (
            <div
              key={report.id}
              className="dashboard-card p-6 rounded-xl border border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4"
            >
              <div className="flex-1 min-w-0">
                <div className="flex flex-wrap items-center gap-2 mb-2">
                  <span
                    className={`px-2.5 py-0.5 rounded text-[11px] font-bold uppercase tracking-wider ${
                      report.status === "PENDING"
                        ? "bg-yellow-500/20 text-yellow-300 border border-yellow-500/30"
                        : report.status === "RESOLVED"
                        ? "bg-green-500/20 text-green-300 border border-green-500/30"
                        : "bg-gray-500/20 text-gray-400 border border-white/10"
                    }`}
                  >
                    {report.status}
                  </span>
                  <span className="px-2 py-0.5 rounded bg-white/5 border border-white/10 text-gray-300 text-xs flex items-center gap-1.5">
                    {report.projectId ? (
                      <>
                        <FolderGit2 className="w-3.5 h-3.5 text-huddle-purple" />
                        Project Report
                      </>
                    ) : (
                      <>
                        <User className="w-3.5 h-3.5 text-huddle-coral" />
                        User Report
                      </>
                    )}
                  </span>
                  <span className="text-xs text-gray-500 flex items-center gap-1">
                    <Clock className="w-3 h-3" />
                    {new Date(report.createdAt).toLocaleString()}
                  </span>
                </div>

                <div className="text-sm font-semibold text-white mb-1">
                  Reason: <span className="text-huddle-coral">{report.reason}</span>
                </div>

                {report.description && (
                  <p className="text-xs text-gray-400 leading-relaxed mb-3 bg-huddle-dark/60 p-3 rounded-lg border border-white/5">
                    {report.description}
                  </p>
                )}

                <div className="flex flex-wrap items-center gap-4 text-xs text-gray-400">
                  <span>
                    Reported by: <span className="text-gray-200 font-medium">{report.reporterDisplayName}</span>
                  </span>
                  {report.reportedDisplayName && (
                    <span>
                      Target user: <span className="text-gray-200 font-medium">{report.reportedDisplayName}</span>
                    </span>
                  )}
                  {report.projectId && (
                    <Link
                      href={`/post/${report.projectId}`}
                      className="text-huddle-purple hover:underline flex items-center gap-1"
                    >
                      View Reported Project
                    </Link>
                  )}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center gap-2 flex-shrink-0 self-end md:self-center">
                {report.status === "PENDING" && (
                  <>
                    <button
                      onClick={() => handleResolve(report.id)}
                      disabled={actionLoading === report.id}
                      className="px-4 py-2 rounded-lg bg-green-500/10 hover:bg-green-500/20 text-green-400 border border-green-500/30 text-xs font-semibold flex items-center gap-1.5 transition-colors disabled:opacity-50"
                    >
                      {actionLoading === report.id ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <CheckCircle className="w-3.5 h-3.5" />
                      )}
                      Resolve
                    </button>
                    <button
                      onClick={() => handleDismiss(report.id)}
                      disabled={actionLoading === report.id}
                      className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 border border-white/10 text-xs font-semibold flex items-center gap-1.5 transition-colors disabled:opacity-50"
                    >
                      {actionLoading === report.id ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : (
                        <XCircle className="w-3.5 h-3.5" />
                      )}
                      Dismiss
                    </button>
                  </>
                )}
                {report.status !== "PENDING" && (
                  <span className="text-xs text-gray-500 italic">No further actions needed</span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
