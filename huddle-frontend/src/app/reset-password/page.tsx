"use client";

import { useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { authService } from "@/lib/api";

function ResetPasswordContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const token = searchParams.get("token");

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) {
      setError("Missing reset token. Please request a new password reset link.");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    if (password.length < 6) {
      setError("Password must be at least 6 characters long.");
      return;
    }

    setLoading(true);
    setError("");

    try {
      await authService.resetPassword(token, password);
      setSuccess(true);
    } catch (err: any) {
      setError(err.message || "Failed to reset password. The link may have expired or was already used.");
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-huddle-black px-6 py-12">
        <div className="dashboard-card p-8 rounded-2xl w-full max-w-md text-center">
          <h2 className="text-2xl font-bold text-white mb-3">Invalid Link</h2>
          <p className="text-sm text-zinc-400 mb-6">
            No reset token was found in the link. Please request a new password reset link.
          </p>
          <Link href="/forgot-password" className="btn-primary w-full py-2.5 rounded-lg text-white font-semibold text-sm block">
            Request Password Reset
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-huddle-black px-6 py-12">
      <div className="dashboard-card p-8 rounded-2xl w-full max-w-md">
        <h2 className="text-3xl font-display font-semibold mb-2 text-center text-white">Create New Password</h2>
        <p className="text-sm text-zinc-400 mb-6 text-center">
          Enter a secure new password for your Huddle account.
        </p>

        {error && <div className="bg-red-500/10 text-red-400 p-3 rounded mb-4 text-sm">{error}</div>}

        {success ? (
          <div className="text-center py-4">
            <div className="w-16 h-16 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 rounded-full flex items-center justify-center mx-auto mb-4 text-3xl">
              ✓
            </div>
            <h3 className="text-lg font-semibold text-white mb-2">Password Updated!</h3>
            <p className="text-sm text-zinc-300 mb-6">
              Your password has been reset successfully. Existing sessions have been invalidated. Please log in with your new password.
            </p>
            <Link
              href="/login"
              className="btn-primary w-full py-3 rounded-lg text-white font-semibold text-sm text-center block"
            >
              Log in with New Password
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div>
              <label className="form-label">New Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="form-input"
                required
                minLength={6}
                placeholder="At least 6 characters"
              />
            </div>
            <div>
              <label className="form-label">Confirm New Password</label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="form-input"
                required
                minLength={6}
                placeholder="Re-enter password"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3 rounded-lg text-white font-semibold mt-2 disabled:opacity-50"
            >
              {loading ? "Resetting Password..." : "Update Password"}
            </button>
            <div className="text-center mt-4 text-sm text-zinc-400">
              <Link href="/login" className="text-huddle-coral hover:underline">
                Back to Login
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-huddle-black text-white">
        Loading...
      </div>
    }>
      <ResetPasswordContent />
    </Suspense>
  );
}
