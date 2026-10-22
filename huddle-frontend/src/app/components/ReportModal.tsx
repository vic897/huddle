"use client";

import { useState } from "react";
import { Flag, Loader2, AlertCircle, X } from "lucide-react";

const REPORT_REASONS = [
  { value: "SPAM", label: "Spam" },
  { value: "HARASSMENT", label: "Harassment or bullying" },
  { value: "INAPPROPRIATE_CONTENT", label: "Inappropriate content" },
  { value: "FAKE_PROFILE", label: "Fake or impersonation" },
  { value: "OTHER", label: "Other" },
];

interface ReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  targetName: string;
  onSubmit: (reason: string, description?: string) => Promise<void>;
}

export function ReportModal({ isOpen, onClose, targetName, onSubmit }: ReportModalProps) {
  const [reason, setReason] = useState("");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleClose = () => {
    setReason("");
    setDescription("");
    setError(null);
    onClose();
  };

  const handleSubmit = async () => {
    if (!reason) { setError("Please select a reason."); return; }
    setLoading(true);
    setError(null);
    try {
      await onSubmit(reason, description.trim() || undefined);
      handleClose();
    } catch (e: any) {
      const msg = (e as any)?.message || "";
      if (msg.includes("409") || msg.toLowerCase().includes("already reported")) {
        setError("You have already reported this. Our team is reviewing it.");
      } else {
        setError(msg || "Failed to submit report. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div className="bg-huddle-charcoal border border-white/10 rounded-2xl max-w-md w-full p-6 shadow-2xl">
        <div className="flex items-start gap-3 mb-5">
          <div className="w-10 h-10 rounded-full bg-orange-500/10 border border-orange-500/20 flex items-center justify-center flex-shrink-0">
            <Flag className="w-5 h-5 text-orange-400" />
          </div>
          <div className="flex-1">
            <h3 className="text-lg font-semibold text-white">Report {targetName}</h3>
            <p className="text-xs text-gray-400 mt-1">Reports are anonymous. Our team will review and take action.</p>
          </div>
          <button onClick={handleClose} className="text-gray-500 hover:text-white transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        {error && (
          <div className="mb-4 flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 text-red-300 rounded-lg text-xs">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            {error}
          </div>
        )}

        <div className="mb-4">
          <label className="block text-xs font-medium text-gray-400 mb-2">Reason *</label>
          <select
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-orange-500/50"
          >
            <option value="" disabled>Select a reason...</option>
            {REPORT_REASONS.map((r) => (
              <option key={r.value} value={r.value} className="bg-huddle-charcoal">{r.label}</option>
            ))}
          </select>
        </div>

        <div className="mb-6">
          <label className="block text-xs font-medium text-gray-400 mb-2">Additional details (optional)</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={1000}
            rows={3}
            placeholder="Describe what happened..."
            className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-orange-500/50 resize-none"
          />
          <p className="text-right text-xs text-gray-600 mt-1">{description.length}/1000</p>
        </div>

        <div className="flex items-center justify-end gap-3">
          <button type="button" onClick={handleClose} disabled={loading}
            className="px-4 py-2 rounded-lg text-xs font-medium text-gray-300 hover:text-white hover:bg-white/5 transition-colors">
            Cancel
          </button>
          <button type="button" onClick={handleSubmit} disabled={loading || !reason}
            className="px-4 py-2 rounded-lg text-xs font-medium bg-orange-500 hover:bg-orange-600 text-white transition-colors flex items-center gap-1.5 disabled:opacity-50">
            {loading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
            Submit Report
          </button>
        </div>
      </div>
    </div>
  );
}
