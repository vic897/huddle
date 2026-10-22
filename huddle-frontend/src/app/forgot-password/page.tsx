"use client";

import { useState } from "react";
import Link from "next/link";
import { authService } from "@/lib/api";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const res = await authService.forgotPassword(email);
      setSubmitted(true);
      setMessage(res.message || "If an account with this email exists, a password reset link has been dispatched.");
    } catch (err: any) {
      // Even on error, show safe generic feedback
      setSubmitted(true);
      setMessage("If an account with this email exists, a password reset link has been dispatched.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-huddle-black px-6 py-12">
      <div className="dashboard-card p-8 rounded-2xl w-full max-w-md">
        <h2 className="text-3xl font-display font-semibold mb-2 text-center text-white">Reset Password</h2>
        <p className="text-sm text-zinc-400 mb-6 text-center">
          Enter your registered email address and we'll send you a link to reset your password.
        </p>

        {error && <div className="bg-red-500/10 text-red-400 p-3 rounded mb-4 text-sm">{error}</div>}

        {submitted ? (
          <div className="text-center py-4">
            <div className="w-16 h-16 bg-huddle-coral/10 border border-huddle-coral/30 rounded-full flex items-center justify-center mx-auto mb-4 text-2xl">
              ✉️
            </div>
            <h3 className="text-lg font-semibold text-white mb-2">Check your email</h3>
            <p className="text-sm text-zinc-300 mb-6">{message}</p>
            <Link
              href="/login"
              className="btn-primary w-full py-2.5 rounded-lg text-white font-semibold text-sm text-center block"
            >
              Return to Login
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div>
              <label className="form-label">Email Address</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="form-input"
                required
                placeholder="student@college.edu"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full py-3 rounded-lg text-white font-semibold mt-2 disabled:opacity-50"
            >
              {loading ? "Sending link..." : "Send Reset Link"}
            </button>
            <div className="text-center mt-4 text-sm text-zinc-400">
              Remember your password?{" "}
              <Link href="/login" className="text-huddle-coral hover:underline">
                Log in
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
