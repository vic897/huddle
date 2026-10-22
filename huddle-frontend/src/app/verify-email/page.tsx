"use client";

import { useEffect, useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { authService } from "@/lib/api";

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  const [loading, setLoading] = useState(true);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");
  const [resendEmail, setResendEmail] = useState("");
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState("");

  useEffect(() => {
    if (!token) {
      setLoading(false);
      setError("No verification token found in URL. Please check your link or request a new one.");
      return;
    }

    authService.verifyEmail(token)
      .then(() => {
        setSuccess(true);
      })
      .catch((err: any) => {
        setError(err.message || "Failed to verify email. The token may be expired or already used.");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [token]);

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resendEmail) return;

    setResending(true);
    setResendMessage("");
    setError("");

    try {
      const res = await authService.resendVerification(resendEmail);
      setResendMessage(res.message || "If an account exists, a new verification email has been sent.");
    } catch (err: any) {
      setError(err.message || "Failed to resend verification email.");
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-huddle-black px-6 py-12">
      <div className="dashboard-card p-8 rounded-2xl w-full max-w-md text-center">
        {loading ? (
          <div>
            <div className="w-12 h-12 border-2 border-huddle-coral border-t-transparent rounded-full animate-spin mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-white mb-2">Verifying your email...</h2>
            <p className="text-sm text-zinc-400">Please wait while we validate your token.</p>
          </div>
        ) : success ? (
          <div>
            <div className="w-16 h-16 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 rounded-full flex items-center justify-center mx-auto mb-4 text-3xl">
              ✓
            </div>
            <h2 className="text-2xl font-display font-semibold text-white mb-2">Email Verified!</h2>
            <p className="text-sm text-zinc-300 mb-6">
              Your email has been verified successfully. Your account is active and you can now log in to Huddle.
            </p>
            <Link
              href="/login"
              className="btn-primary w-full py-3 rounded-lg text-white font-semibold text-sm text-center block"
            >
              Log in to Huddle
            </Link>
          </div>
        ) : (
          <div>
            <div className="w-16 h-16 bg-red-500/10 border border-red-500/30 text-red-400 rounded-full flex items-center justify-center mx-auto mb-4 text-3xl">
              ✕
            </div>
            <h2 className="text-2xl font-display font-semibold text-white mb-2">Verification Failed</h2>
            <p className="text-sm text-red-400 mb-6 bg-red-500/10 p-3 rounded-lg text-left">
              {error}
            </p>

            {resendMessage && (
              <p className="text-sm text-emerald-400 mb-4 bg-emerald-500/10 p-3 rounded-lg text-left">
                {resendMessage}
              </p>
            )}

            <form onSubmit={handleResend} className="flex flex-col gap-3 text-left mb-6">
              <label className="form-label text-xs">Request a new verification link</label>
              <input
                type="email"
                value={resendEmail}
                onChange={(e) => setResendEmail(e.target.value)}
                placeholder="Enter your email"
                className="form-input text-sm"
                required
              />
              <button
                type="submit"
                disabled={resending}
                className="btn-primary w-full py-2.5 rounded-lg text-white font-semibold text-sm disabled:opacity-50"
              >
                {resending ? "Sending..." : "Resend Link"}
              </button>
            </form>

            <Link href="/login" className="text-sm text-zinc-400 hover:text-white underline">
              Back to Login
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-huddle-black text-white">
        Loading...
      </div>
    }>
      <VerifyEmailContent />
    </Suspense>
  );
}
