"use client";

import { useState } from "react";
import { authService } from "@/lib/api";
import { setToken, setInstitutionStatus } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import GoogleSignInButton from "@/app/components/GoogleSignInButton";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [infoMessage, setInfoMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [showResend, setShowResend] = useState(false);
  const [resending, setResending] = useState(false);

  // Account linking state
  const [pendingGoogleToken, setPendingGoogleToken] = useState<string | null>(null);
  const [linkingEmail, setLinkingEmail] = useState("");
  const [linkingPassword, setLinkingPassword] = useState("");
  const [linkingLoading, setLinkingLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    setInfoMessage("");
    setShowResend(false);

    try {
      const res = await authService.login({ email, password });
      if (res.token) {
        setToken(res.token);
        if (res.institutionStatus) setInstitutionStatus(res.institutionStatus);
        router.push("/");
      }
    } catch (err: any) {
      const msg = err.message || "Login failed";
      setError(msg);
      if (msg.toLowerCase().includes("verification") || msg.toLowerCase().includes("unverified")) {
        setShowResend(true);
      }
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (!email) {
      setError("Please enter your email address to resend verification.");
      return;
    }
    setResending(true);
    setError("");
    setInfoMessage("");
    try {
      const res = await authService.resendVerification(email);
      setInfoMessage(res.message || "Verification email sent. Please check your inbox.");
    } catch (err: any) {
      setError(err.message || "Could not resend verification email.");
    } finally {
      setResending(false);
    }
  };

  const handleGoogleSuccess = async (credential: string) => {
    setLoading(true);
    setError("");
    setInfoMessage("");
    setShowResend(false);
    try {
      const { status, data } = await authService.googleAuth(credential);
      if (status === 409 && data.linkingRequired) {
        setPendingGoogleToken(credential);
        setLinkingEmail(data.email || "");
        setLoading(false);
        return;
      }

      if (data.token) {
        setToken(data.token);
        if (data.institutionStatus) setInstitutionStatus(data.institutionStatus);
        router.push("/");
      }
    } catch (err: any) {
      setError(err.message || "Google sign-in failed");
      setLoading(false);
    }
  };

  const handleLinkSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pendingGoogleToken) return;

    setLinkingLoading(true);
    setError("");
    try {
      const data = await authService.googleLink(pendingGoogleToken, linkingPassword);
      if (data.token) {
        setToken(data.token);
        if (data.institutionStatus) setInstitutionStatus(data.institutionStatus);
        router.push("/");
      }
    } catch (err: any) {
      setError(err.message || "Account linking failed");
      setLinkingLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-huddle-black px-6">
      <div className="dashboard-card p-8 rounded-2xl w-full max-w-md">
        <h2 className="text-3xl font-display font-semibold mb-6 text-center text-white">Welcome back</h2>
        {error && <div className="bg-red-500/10 text-red-400 p-3 rounded mb-4 text-sm">{error}</div>}
        {infoMessage && <div className="bg-emerald-500/10 text-emerald-400 p-3 rounded mb-4 text-sm">{infoMessage}</div>}

        {showResend && (
          <div className="mb-4 p-3 bg-zinc-900 border border-zinc-800 rounded-lg flex items-center justify-between text-xs">
            <span className="text-zinc-400">Need a new verification link?</span>
            <button
              type="button"
              onClick={handleResend}
              disabled={resending}
              className="text-huddle-coral hover:underline font-semibold disabled:opacity-50 ml-2"
            >
              {resending ? "Sending..." : "Resend Email"}
            </button>
          </div>
        )}

        {pendingGoogleToken ? (
          <div className="bg-zinc-900/90 border border-zinc-700/60 p-5 rounded-xl">
            <h3 className="text-lg font-semibold text-white mb-2">Link Existing Account</h3>
            <p className="text-sm text-zinc-300 mb-4">
              An account with <strong className="text-white">{linkingEmail}</strong> already exists. Please enter your existing Huddle password once to link your Google account.
            </p>
            <form onSubmit={handleLinkSubmit} className="flex flex-col gap-4">
              <div>
                <label className="form-label">Password</label>
                <input
                  type="password"
                  value={linkingPassword}
                  onChange={e => setLinkingPassword(e.target.value)}
                  className="form-input"
                  required
                  placeholder="Existing Huddle password"
                />
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPendingGoogleToken(null)}
                  className="px-4 py-2 text-sm rounded-lg border border-zinc-700 text-zinc-300 hover:bg-zinc-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={linkingLoading}
                  className="btn-primary flex-1 py-2 text-sm rounded-lg text-white font-semibold disabled:opacity-50"
                >
                  {linkingLoading ? "Linking..." : "Confirm & Link"}
                </button>
              </div>
            </form>
          </div>
        ) : (
          <>
            <GoogleSignInButton onSuccess={handleGoogleSuccess} text="signin_with" />

            <div className="flex items-center my-4">
              <div className="flex-1 border-t border-zinc-800" />
              <span className="px-3 text-xs text-zinc-500 uppercase">or</span>
              <div className="flex-1 border-t border-zinc-800" />
            </div>

            <form onSubmit={handleLogin} className="flex flex-col gap-4">
              <div>
                <label className="form-label">Email</label>
                <input type="email" value={email} onChange={e => setEmail(e.target.value)} className="form-input" required />
              </div>
              <div>
                <div className="flex items-center justify-between">
                  <label className="form-label">Password</label>
                  <Link href="/forgot-password" className="text-xs text-huddle-coral hover:underline mb-1">
                    Forgot Password?
                  </Link>
                </div>
                <input type="password" value={password} onChange={e => setPassword(e.target.value)} className="form-input" required />
              </div>
              <button type="submit" disabled={loading} className="btn-primary w-full py-3 rounded-lg text-white font-semibold mt-4 disabled:opacity-50">
                {loading ? "Logging in..." : "Log in"}
              </button>
            </form>
            <div className="text-center mt-6 text-sm text-gray-400">
              Don't have an account? <Link href="/signup" className="text-huddle-coral hover:underline">Sign up</Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
