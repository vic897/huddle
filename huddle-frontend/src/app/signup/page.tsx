"use client";

import { useState } from "react";
import { authService } from "@/lib/api";
import { setToken, setInstitutionStatus } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import GoogleSignInButton from "@/app/components/GoogleSignInButton";

export default function SignupPage() {
  const router = useRouter();
  const [formData, setFormData] = useState({
    email: "",
    password: "",
    displayName: "",
    bio: "",
    skills: "",
    availability: ""
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [signupComplete, setSignupComplete] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState("");

  // Account linking state
  const [pendingGoogleToken, setPendingGoogleToken] = useState<string | null>(null);
  const [linkingEmail, setLinkingEmail] = useState("");
  const [linkingPassword, setLinkingPassword] = useState("");
  const [linkingLoading, setLinkingLoading] = useState(false);

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    setResendMessage("");
    try {
      const res = await authService.signup(formData);
      // Account created, email verification link sent
      setSignupComplete(true);
      setLoading(false);
    } catch (err: any) {
      setError(err.message || "Signup failed");
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setResending(true);
    setError("");
    setResendMessage("");
    try {
      const res = await authService.resendVerification(formData.email);
      setResendMessage(res.message || "Verification email resent successfully.");
    } catch (err: any) {
      setError(err.message || "Failed to resend verification email.");
    } finally {
      setResending(false);
    }
  };

  const handleGoogleSuccess = async (credential: string) => {
    setLoading(true);
    setError("");
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
    <div className="min-h-screen flex items-center justify-center bg-huddle-black py-12 px-6">
      <div className="dashboard-card p-8 rounded-2xl w-full max-w-md">
        <h2 className="text-3xl font-display font-semibold mb-6 text-center text-white">Join Huddle</h2>
        {error && <div className="bg-red-500/10 text-red-400 p-3 rounded mb-4 text-sm">{error}</div>}
        {resendMessage && <div className="bg-emerald-500/10 text-emerald-400 p-3 rounded mb-4 text-sm">{resendMessage}</div>}

        {signupComplete ? (
          <div className="text-center py-4">
            <div className="w-16 h-16 bg-huddle-coral/10 border border-huddle-coral/30 rounded-full flex items-center justify-center mx-auto mb-4 text-2xl">
              ✉️
            </div>
            <h3 className="text-xl font-bold text-white mb-2">Check your inbox</h3>
            <p className="text-sm text-zinc-300 mb-6">
              We sent a verification link to <strong className="text-white">{formData.email}</strong>. Please click the link in that email to activate your account.
            </p>
            <div className="flex flex-col gap-3">
              <button
                type="button"
                onClick={handleResend}
                disabled={resending}
                className="w-full py-2.5 rounded-lg border border-zinc-700 hover:border-zinc-500 text-sm font-medium text-zinc-300 hover:text-white transition-colors disabled:opacity-50"
              >
                {resending ? "Resending..." : "Resend Verification Email"}
              </button>
              <Link
                href="/login"
                className="btn-primary w-full py-2.5 rounded-lg text-white font-semibold text-sm text-center block"
              >
                Proceed to Log In
              </Link>
            </div>
          </div>
        ) : pendingGoogleToken ? (
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
            <GoogleSignInButton onSuccess={handleGoogleSuccess} text="signup_with" />

            <div className="flex items-center my-4">
              <div className="flex-1 border-t border-zinc-800" />
              <span className="px-3 text-xs text-zinc-500 uppercase">or</span>
              <div className="flex-1 border-t border-zinc-800" />
            </div>

            <form onSubmit={handleSignup} className="flex flex-col gap-4">
              <div>
                <label className="form-label">Email (College Domain)</label>
                <input type="email" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} className="form-input" required />
              </div>
              <div>
                <label className="form-label">Password</label>
                <input type="password" value={formData.password} onChange={e => setFormData({...formData, password: e.target.value})} className="form-input" required />
              </div>
              <div>
                <label className="form-label">Display Name</label>
                <input type="text" value={formData.displayName} onChange={e => setFormData({...formData, displayName: e.target.value})} className="form-input" required />
              </div>
              <div>
                <label className="form-label">Bio</label>
                <input type="text" value={formData.bio} onChange={e => setFormData({...formData, bio: e.target.value})} className="form-input" />
              </div>
              <div>
                <label className="form-label">Skills (comma separated)</label>
                <input type="text" value={formData.skills} onChange={e => setFormData({...formData, skills: e.target.value})} className="form-input" placeholder="React, UI/UX" />
              </div>
              <button type="submit" disabled={loading} className="btn-primary w-full py-3 rounded-lg text-white font-semibold mt-4 disabled:opacity-50">
                {loading ? "Creating Account..." : "Create Account"}
              </button>
            </form>
            <div className="text-center mt-6 text-sm text-gray-400">
              Already have an account? <Link href="/login" className="text-huddle-coral hover:underline">Log in</Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
