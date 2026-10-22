"use client";

import { useEffect, useState, use } from "react";
import { requestService } from "@/lib/api";
import {
  userService,
  reportUser,
  UserProfile,
  SkillItem,
  SkillCategory,
  SkillProficiency,
} from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { ReportModal } from "@/app/components/ReportModal";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  UserX,
  ShieldAlert,
  ShieldCheck,
  Flag,
  X,
  Check,
  GraduationCap,
  MapPin,
  Calendar,
  Globe,
  MessageSquare,
  Zap,
  Briefcase,
  Award,
  Clock,
  Laptop,
  Share2,
} from "lucide-react";

// Clean inline SVGs for brand icons
function GithubIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
    </svg>
  );
}

function LinkedinIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.28 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.75M6.46 8.76a1.69 1.69 0 1 0 0-3.38 1.69 1.69 0 0 0 0 3.38m1.4 9.74V9.97H5.06v8.53h2.8z" />
    </svg>
  );
}

function TwitterIcon({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
    </svg>
  );
}

export default function PublicProfilePage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const { id } = use(params);

  const [profile, setProfile] = useState<UserProfile | null>(null);
  // Accepted collaboration request ID for chat navigation
  const [chatRequestId, setChatRequestId] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [blockedUnavailable, setBlockedUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Current viewer ID
  const [currentUserId, setCurrentUserId] = useState<string>("");

  // Block state
  const [isBlocked, setIsBlocked] = useState(false);
  const [blockLoading, setBlockLoading] = useState(false);
  const [blockSuccessMsg, setBlockSuccessMsg] = useState<string | null>(null);
  const [blockErrorMsg, setBlockErrorMsg] = useState<string | null>(null);
  const [showConfirmModal, setShowConfirmModal] = useState(false);

  // Report state
  const [showReportModal, setShowReportModal] = useState(false);
  const [reportSuccessMsg, setReportSuccessMsg] = useState<string | null>(null);
  const [copyFeedback, setCopyFeedback] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    // Decode user ID from JWT token to check if viewing own profile
    const token = typeof window !== "undefined" ? localStorage.getItem("huddle_token") : null;
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        setCurrentUserId(payload.sub || "");
      } catch {}
    }

    loadProfile();
  }, [id]);

  const loadProfile = async () => {
    setLoading(true);
    setError(null);
    setNotFound(false);
    setBlockedUnavailable(false);
    setBlockErrorMsg(null);

    try {
      // Fetch profile & block status concurrently
      const [profileData, blockStatus] = await Promise.all([
        userService.getUserById(id),
        userService.getBlockStatus(id).catch(() => null),
      ]);

      setProfile(profileData);
      if (blockStatus) {
        setIsBlocked(!!blockStatus.isBlocked);
      }

      // Find an accepted collaboration request between the viewer and this profile user
      try {
        const [received, sent] = await Promise.all([
          requestService.getMyRequests("received").catch(() => []),
          requestService.getMyRequests("sent").catch(() => []),
        ]);
        const allRequests = [
          ...(Array.isArray(received) ? received : []),
          ...(Array.isArray(sent) ? sent : []),
        ];
        const match = allRequests.find(
          (r: any) =>
            r.status?.toUpperCase() === "ACCEPTED" &&
            (r.requestorId === profileData.id || r.posterId === profileData.id)
        );
        if (match) setChatRequestId(match.id);
      } catch {
        // Non-fatal — chat button will simply be hidden
      }
    } catch (err: any) {
      const msg = err?.message || "";
      if (msg.includes("Profile not available") || msg.includes("Forbidden") || msg.includes("403")) {
        try {
          const status = await userService.getBlockStatus(id);
          if (status?.isBlocked) {
            setIsBlocked(true);
          }
        } catch {}
        setBlockedUnavailable(true);
      } else if (msg.includes("User not found") || msg.includes("404")) {
        setNotFound(true);
      } else {
        setError("Failed to load profile. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleBlockConfirm = async () => {
    if (blockLoading) return;
    setBlockLoading(true);
    setBlockErrorMsg(null);
    setBlockSuccessMsg(null);

    try {
      await userService.blockUser(id);
      setIsBlocked(true);
      setShowConfirmModal(false);
      setBlockSuccessMsg("User blocked. They will not appear in People discovery or matching.");
      setTimeout(() => setBlockSuccessMsg(null), 5000);
    } catch (err: any) {
      setBlockErrorMsg(err?.message || "Failed to block user. Please try again.");
    } finally {
      setBlockLoading(false);
    }
  };

  const handleUnblock = async () => {
    if (blockLoading) return;
    setBlockLoading(true);
    setBlockErrorMsg(null);
    setBlockSuccessMsg(null);

    try {
      await userService.unblockUser(id);
      setIsBlocked(false);
      setBlockSuccessMsg("User unblocked. They can now appear in People discovery and matching.");
      setTimeout(() => setBlockSuccessMsg(null), 5000);

      if (blockedUnavailable) {
        setBlockedUnavailable(false);
        loadProfile();
      }
    } catch (err: any) {
      setBlockErrorMsg(err?.message || "Failed to unblock user. Please try again.");
    } finally {
      setBlockLoading(false);
    }
  };

  const handleReportSubmit = async (reason: string, description?: string) => {
    await reportUser(id, { reason, description });
    setReportSuccessMsg("Report submitted. Thank you for helping keep Huddle safe.");
    setTimeout(() => setReportSuccessMsg(null), 6000);
  };

  const handleShare = () => {
    if (typeof window !== "undefined") {
      navigator.clipboard.writeText(window.location.href);
      setCopyFeedback(true);
      setTimeout(() => setCopyFeedback(false), 3000);
    }
  };

  const renderProficiencyBars = (proficiency?: SkillProficiency, category?: SkillCategory) => {
    let activeBars = 2;
    if (proficiency === "BEGINNER") activeBars = 1;
    if (proficiency === "INTERMEDIATE") activeBars = 2;
    if (proficiency === "ADVANCED") activeBars = 3;

    let colorClass = "bg-huddle-purple shadow-[0_0_8px_rgba(155,81,224,0.5)]";
    if (category === "FRONTEND") colorClass = "bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.5)]";
    if (category === "AI_ML") colorClass = "bg-huddle-coral shadow-[0_0_8px_rgba(255,107,107,0.5)]";
    if (category === "BACKEND") colorClass = "bg-huddle-purple shadow-[0_0_8px_rgba(155,81,224,0.5)]";
    if (category === "MOBILE") colorClass = "bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]";
    if (category === "DATABASES") colorClass = "bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.5)]";

    return (
      <div className="flex gap-1" title={proficiency || "Intermediate"}>
        {[1, 2, 3, 4].map((bar) => (
          <div
            key={bar}
            className={`w-4 h-1.5 rounded-sm ${
              bar <= activeBars ? colorClass : "bg-white/10"
            }`}
          />
        ))}
      </div>
    );
  };

  const groupSkillsByCategory = (skillsList: any[]) => {
    const groups: { [key: string]: SkillItem[] } = {};
    if (!Array.isArray(skillsList)) return groups;

    skillsList.forEach((item) => {
      let skillObj: SkillItem;
      if (typeof item === "string") {
        skillObj = { name: item, category: "OTHER", proficiency: "BEGINNER" };
      } else {
        skillObj = {
          name: item.name || "",
          category: item.category || "OTHER",
          proficiency: item.proficiency || "BEGINNER",
        };
      }
      const cat = skillObj.category || "OTHER";
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(skillObj);
    });

    return groups;
  };

  if (loading) {
    return (
      <div className="min-h-[60vh] flex flex-col items-center justify-center text-gray-400 gap-3">
        <Loader2 className="w-8 h-8 text-huddle-coral animate-spin" />
        <span className="text-sm font-medium">Loading profile...</span>
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="max-w-lg mx-auto py-20 text-center">
        <AlertCircle className="w-12 h-12 text-gray-600 mx-auto mb-4" />
        <h2 className="text-xl font-semibold text-white mb-2">User Not Found</h2>
        <p className="text-gray-400 mb-6 text-sm">This account may have been removed or the link is incorrect.</p>
        <Link href="/projects" className="text-huddle-coral hover:text-huddle-pink text-sm font-medium transition-colors">
          ← Back to Discover
        </Link>
      </div>
    );
  }

  if (blockedUnavailable) {
    return (
      <div className="max-w-lg mx-auto py-20 text-center">
        {isBlocked ? (
          <>
            <ShieldAlert className="w-12 h-12 text-red-500/80 mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-white mb-2">User Blocked</h2>
            <p className="text-gray-400 mb-6 text-sm">
              You have blocked this user. They are excluded from discovery and matching.
            </p>
            <div className="flex items-center justify-center gap-4 mt-6">
              <button
                type="button"
                onClick={handleUnblock}
                disabled={blockLoading}
                className="px-4 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-all flex items-center gap-2"
              >
                {blockLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShieldCheck className="w-4 h-4 text-green-400" />}
                Unblock User
              </button>
              <Link href="/projects" className="text-huddle-coral hover:text-huddle-pink text-sm font-medium transition-colors">
                ← Back to Discover
              </Link>
            </div>
          </>
        ) : (
          <>
            <UserX className="w-12 h-12 text-gray-600 mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-white mb-2">Profile Unavailable</h2>
            <p className="text-gray-400 mb-6 text-sm">This profile is not available.</p>
            <Link href="/projects" className="text-huddle-coral hover:text-huddle-pink text-sm font-medium transition-colors">
              ← Back to Discover
            </Link>
          </>
        )}
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="max-w-lg mx-auto py-20 text-center">
        <AlertCircle className="w-12 h-12 text-red-500/60 mx-auto mb-4" />
        <h2 className="text-xl font-semibold text-white mb-2">Something went wrong</h2>
        <p className="text-gray-400 mb-6 text-sm">{error || "Failed to load profile."}</p>
        <button onClick={loadProfile} className="text-huddle-coral hover:text-huddle-pink text-sm font-medium">
          Try Again
        </button>
      </div>
    );
  }

  const isOwnProfile = currentUserId && currentUserId === profile.id;
  const groupedSkills = groupSkillsByCategory(profile.skills || []);
  const avatarImage =
    profile.avatarUrl ||
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80";

  return (
    <div className="w-full relative pb-20">
      {/* Ambient Orbs */}
      <div className="ambient-orb bg-huddle-purple opacity-[0.06] w-[800px] h-[800px] top-[-10%] right-[-10%]"></div>
      <div className="ambient-orb bg-huddle-blue opacity-[0.04] w-[600px] h-[600px] bottom-[-20%] left-[-10%]"></div>

      {/* Notifications */}
      {blockSuccessMsg && (
        <div className="max-w-6xl mx-auto px-5 md:px-10 mb-4 pt-4">
          <div className="p-3 bg-green-500/10 border border-green-500/20 text-green-300 rounded-xl text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Check className="w-4 h-4 text-green-400" />
              <span>{blockSuccessMsg}</span>
            </div>
            <button onClick={() => setBlockSuccessMsg(null)} className="text-gray-400 hover:text-white">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}

      {reportSuccessMsg && (
        <div className="max-w-6xl mx-auto px-5 md:px-10 mb-4 pt-4">
          <div className="p-3 bg-orange-500/10 border border-orange-500/20 text-orange-300 rounded-xl text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Flag className="w-4 h-4 text-orange-400" />
              <span>{reportSuccessMsg}</span>
            </div>
            <button onClick={() => setReportSuccessMsg(null)} className="text-gray-400 hover:text-white">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}

      {copyFeedback && (
        <div className="max-w-6xl mx-auto px-5 md:px-10 mb-4 pt-4">
          <div className="p-3 bg-green-500/10 border border-green-500/20 text-green-300 rounded-xl text-xs flex items-center gap-2">
            <Check className="w-4 h-4 text-green-400" />
            <span>Profile link copied to clipboard!</span>
          </div>
        </div>
      )}

      {/* HERO / COVER ART */}
      <div className="w-full relative">
        <div className="h-48 md:h-64 w-full bg-mesh-pattern border-b border-white/5 relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-t from-huddle-black to-transparent opacity-80"></div>
          
          <div className="absolute top-6 left-6 z-20">
            <Link
              href="/projects"
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-black/40 border border-white/10 backdrop-blur-md text-xs font-semibold text-gray-300 hover:text-white transition-colors"
            >
              <ArrowLeft className="w-3.5 h-3.5" /> Back to Discover
            </Link>
          </div>

          <div className="absolute top-6 right-6 flex gap-3 z-20">
            <button
              onClick={handleShare}
              className="w-9 h-9 rounded-full bg-black/40 border border-white/10 backdrop-blur-md flex items-center justify-center text-gray-300 hover:text-white transition-colors"
              title="Share Profile"
            >
              <Share2 className="w-4 h-4" />
            </button>
            {!isOwnProfile && (
              <>
                <button
                  onClick={() => setShowReportModal(true)}
                  className="w-9 h-9 rounded-full bg-black/40 border border-white/10 backdrop-blur-md flex items-center justify-center text-orange-400 hover:bg-orange-500/10 transition-colors"
                  title="Report User"
                >
                  <Flag className="w-4 h-4" />
                </button>
                <button
                  onClick={() => (isBlocked ? handleUnblock() : setShowConfirmModal(true))}
                  className={`w-9 h-9 rounded-full bg-black/40 border border-white/10 backdrop-blur-md flex items-center justify-center transition-colors ${
                    isBlocked ? "text-green-400 hover:bg-green-500/10" : "text-red-400 hover:bg-red-500/10"
                  }`}
                  title={isBlocked ? "Unblock User" : "Block User"}
                >
                  {isBlocked ? <ShieldCheck className="w-4 h-4" /> : <ShieldAlert className="w-4 h-4" />}
                </button>
              </>
            )}
          </div>
        </div>

        <div className="max-w-6xl mx-auto px-5 md:px-10 relative">
          
          {/* Identity Header */}
          <div className="flex flex-col md:flex-row gap-6 md:gap-8 -mt-16 md:-mt-24 relative z-20 mb-8 lg:mb-12 animate-slide-up">
            
            {/* Avatar */}
            <div className="relative flex-shrink-0 self-start">
              <div className="w-32 h-32 md:w-44 md:h-44 rounded-2xl p-1 bg-huddle-black border border-white/10 shadow-2xl relative group overflow-hidden">
                <img
                  src={avatarImage}
                  alt={profile.displayName || "User"}
                  className="w-full h-full rounded-xl object-cover"
                />
                <div className="absolute bottom-2 right-2 w-3.5 h-3.5 bg-green-500 rounded-full border-2 border-huddle-black shadow-[0_0_10px_#10B981]"></div>
              </div>
            </div>

            {/* Meta & Actions */}
            <div className="pt-2 md:pt-28 flex-1 flex flex-col md:flex-row md:items-start justify-between gap-6">
              
              <div>
                <div className="flex items-center gap-3 mb-1">
                  <h1 className="font-display font-extrabold text-3xl md:text-4xl text-white tracking-tight">
                    {profile.displayName || "Anonymous User"}
                  </h1>
                  {isBlocked && (
                    <span className="bg-red-500/10 text-red-400 border border-red-500/20 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-widest">
                      Blocked
                    </span>
                  )}
                </div>

                <h2 className="text-lg md:text-xl font-semibold text-gray-300 mb-3">
                  {profile.course || profile.bio || "Student & Collaborator"}
                </h2>

                <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm font-medium text-gray-400 mb-5">
                  {profile.college && (
                    <span className="flex items-center gap-1.5">
                      <GraduationCap className="w-4 h-4 text-gray-500" /> {profile.college}
                    </span>
                  )}
                  {profile.college && profile.location && (
                    <span className="w-1 h-1 rounded-full bg-white/20 hidden md:block"></span>
                  )}
                  {profile.location && (
                    <span className="flex items-center gap-1.5">
                      <MapPin className="w-4 h-4 text-gray-500" /> {profile.location}
                    </span>
                  )}
                  {profile.graduationYear && (
                    <>
                      <span className="w-1 h-1 rounded-full bg-white/20 hidden md:block"></span>
                      <span className="flex items-center gap-1.5">
                        <Calendar className="w-4 h-4 text-gray-500" /> Class of {profile.graduationYear}
                      </span>
                    </>
                  )}
                </div>

                {/* Social links */}
                <div className="flex gap-3">
                  {profile.githubUrl && (
                    <a
                      href={profile.githubUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-gray-400 hover:text-white transition-colors"
                      title="GitHub"
                    >
                      <GithubIcon className="w-5 h-5" />
                    </a>
                  )}
                  {profile.linkedinUrl && (
                    <a
                      href={profile.linkedinUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-gray-400 hover:text-white transition-colors"
                      title="LinkedIn"
                    >
                      <LinkedinIcon className="w-5 h-5" />
                    </a>
                  )}
                  {profile.twitterUrl && (
                    <a
                      href={profile.twitterUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-gray-400 hover:text-white transition-colors"
                      title="Twitter/X"
                    >
                      <TwitterIcon className="w-5 h-5" />
                    </a>
                  )}
                  {profile.portfolioUrl && (
                    <a
                      href={profile.portfolioUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-gray-400 hover:text-white transition-colors"
                      title="Portfolio"
                    >
                      <Globe className="w-5 h-5" />
                    </a>
                  )}
                </div>
              </div>

              {/* Public Action Buttons */}
              {!isOwnProfile ? (
                <div className="flex gap-3 flex-shrink-0 w-full md:w-auto">
                  {chatRequestId ? (
                    <Link
                      href={`/chat?requestId=${chatRequestId}`}
                      className="flex-1 md:flex-none btn-glass px-6 py-2.5 rounded-xl text-sm font-bold text-white shadow-sm flex items-center justify-center gap-2"
                    >
                      <MessageSquare className="w-4 h-4" /> Message
                    </Link>
                  ) : (
                    <span
                      title="Start a collaboration first to unlock messaging"
                      className="flex-1 md:flex-none btn-glass px-6 py-2.5 rounded-xl text-sm font-bold text-white/40 shadow-sm flex items-center justify-center gap-2 cursor-not-allowed select-none"
                    >
                      <MessageSquare className="w-4 h-4" /> Message
                    </span>
                  )}
                  <Link
                    href="/matched"
                    className="flex-1 md:flex-none btn-glow px-6 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg flex items-center justify-center gap-2"
                  >
                    <Zap className="w-4 h-4" /> Collaborate
                  </Link>
                </div>
              ) : (
                <div className="flex gap-3 flex-shrink-0 w-full md:w-auto">
                  <Link
                    href="/profile"
                    className="btn-glow px-6 py-2.5 rounded-xl text-sm font-bold text-white"
                  >
                    Edit My Profile
                  </Link>
                </div>
              )}

            </div>
          </div>

          {/* MAIN PROFILE BODY GRID */}
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 lg:gap-8">
            
            {/* Left Col (Span 8) */}
            <div className="xl:col-span-8 space-y-6 lg:space-y-8">
              
              {/* About */}
              <section className="dashboard-card p-6 md:p-8">
                <h3 className="text-xs font-bold text-gray-500 uppercase tracking-widest mb-4">About</h3>
                <p className="text-gray-300 text-sm md:text-base leading-relaxed font-medium whitespace-pre-line">
                  {profile.bio || "No bio provided."}
                </p>
              </section>

              {/* Experience & Education */}
              <section className="dashboard-card p-6 md:p-8">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-10 md:gap-6">
                  
                  {/* Experience */}
                  <div>
                    <div className="flex items-center gap-2 mb-6">
                      <Briefcase className="w-5 h-5 text-gray-400" />
                      <h3 className="text-lg font-display font-bold text-white">Experience</h3>
                    </div>
                    {(!profile.experience || profile.experience.length === 0) ? (
                      <p className="text-sm text-gray-500 italic">No experience added.</p>
                    ) : (
                      <div className="timeline">
                        {profile.experience.map((exp, eIdx) => (
                          <div key={eIdx} className="timeline-item">
                            <div className="timeline-dot"></div>
                            <h4 className="text-base font-bold text-white mb-0.5">{exp.title}</h4>
                            <div className="text-sm text-huddle-purple font-medium mb-1.5">{exp.organization}</div>
                            <div className="text-[11px] text-gray-500 mb-3 font-semibold uppercase tracking-wider">
                              {exp.startDate || "Start"} — {exp.endDate || "Present"}
                            </div>
                            {exp.description && (
                              <p className="text-sm text-gray-400 leading-relaxed font-medium">{exp.description}</p>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Education */}
                  <div>
                    <div className="flex items-center gap-2 mb-6">
                      <GraduationCap className="w-5 h-5 text-gray-400" />
                      <h3 className="text-lg font-display font-bold text-white">Education</h3>
                    </div>
                    {(!profile.education || profile.education.length === 0) ? (
                      <p className="text-sm text-gray-500 italic">No education added.</p>
                    ) : (
                      <div className="timeline">
                        {profile.education.map((edu, eduIdx) => (
                          <div key={eduIdx} className="timeline-item">
                            <div className="timeline-dot"></div>
                            <h4 className="text-base font-bold text-white mb-0.5">{edu.institution}</h4>
                            <div className="text-sm text-huddle-blue font-medium mb-1.5">
                              {edu.degree} {edu.field ? `in ${edu.field}` : ""}
                            </div>
                            <div className="text-[11px] text-gray-500 mb-3 font-semibold uppercase tracking-wider">
                              {edu.startYear || "Year"} — {edu.endYear || "Present"}
                            </div>
                            {edu.description && (
                              <p className="text-sm text-gray-400 leading-relaxed font-medium">{edu.description}</p>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                </div>
              </section>

              {/* Achievements */}
              <section>
                <div className="flex items-center gap-2 mb-5 px-1">
                  <Award className="w-5 h-5 text-huddle-coral" />
                  <h3 className="text-lg font-display font-extrabold text-white">Achievements</h3>
                </div>

                {(!profile.achievements || profile.achievements.length === 0) ? (
                  <div className="dashboard-card p-6 text-center">
                    <p className="text-sm text-gray-500 italic">No achievements added.</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {profile.achievements.map((ach, achIdx) => (
                      <div key={achIdx} className="dashboard-card p-5 flex items-start gap-4">
                        <div className="w-10 h-10 rounded-full bg-yellow-500/10 border border-yellow-500/20 flex items-center justify-center flex-shrink-0 text-yellow-500">
                          <Award className="w-5 h-5" />
                        </div>
                        <div>
                          <h4 className="text-sm font-bold text-white mb-1">{ach.title}</h4>
                          {ach.description && (
                            <p className="text-xs text-gray-400 font-medium mb-2">{ach.description}</p>
                          )}
                          {ach.date && (
                            <span className="text-[10px] text-gray-500 font-bold uppercase tracking-wider">
                              {ach.date}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

            </div>

            {/* Right Col (Span 4) */}
            <div className="xl:col-span-4 space-y-6 lg:space-y-8">
              
              {/* Mini Stats Row */}
              <div className="dashboard-card p-5 flex justify-between divide-x divide-white/10 text-center">
                <div className="flex-1">
                  <div className="text-2xl font-display font-extrabold text-white">
                    {profile.projectsCreatedCount ?? 0}
                  </div>
                  <div className="text-[10px] text-gray-400 uppercase tracking-widest font-bold mt-1">
                    Projects
                  </div>
                </div>
                <div className="flex-1">
                  <div className="text-2xl font-display font-extrabold text-white">
                    {profile.collaborationsCount ?? 0}
                  </div>
                  <div className="text-[10px] text-gray-400 uppercase tracking-widest font-bold mt-1">
                    Collabs
                  </div>
                </div>
                <div className="flex-1">
                  <div className="text-2xl font-display font-extrabold text-white">
                    {profile.skillsCount ?? (Array.isArray(profile.skills) ? profile.skills.length : 0)}
                  </div>
                  <div className="text-[10px] text-gray-400 uppercase tracking-widest font-bold mt-1">
                    Skills
                  </div>
                </div>
              </div>

              {/* Ready to Build Card */}
              <div className="dashboard-card border-huddle-coral/30 relative overflow-hidden shadow-[0_10px_40px_-10px_rgba(255,107,107,0.15)]">
                <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-huddle-coral via-huddle-pink to-huddle-purple"></div>

                <div className="p-6">
                  <div className="flex items-center gap-2 mb-6">
                    <div className="w-2.5 h-2.5 rounded-full bg-huddle-coral shadow-[0_0_10px_#FF6B6B] animate-pulse"></div>
                    <h3 className="text-xs font-extrabold text-huddle-coral uppercase tracking-widest">
                      Ready to Build
                    </h3>
                  </div>

                  <div className="space-y-5">
                    <div>
                      <div className="text-[10px] text-gray-500 uppercase font-bold tracking-widest mb-2">
                        Looking For
                      </div>
                      {profile.lookingFor && profile.lookingFor.length > 0 ? (
                        <div className="flex flex-wrap gap-2">
                          {profile.lookingFor.map((role) => (
                            <span
                              key={role}
                              className="px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-xs text-white font-medium"
                            >
                              {role}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-xs text-gray-500 italic">Open to all roles</span>
                      )}
                    </div>

                    <div className="h-px w-full bg-white/5"></div>

                    <div>
                      <div className="text-[10px] text-gray-500 uppercase font-bold tracking-widest mb-2">
                        Collaboration Style
                      </div>
                      <div className="text-sm text-gray-200 font-medium flex items-center gap-2">
                        <Laptop className="w-4 h-4 text-gray-400" />
                        {profile.preferredCollaboration || "Remote Preferred"}
                      </div>
                    </div>

                    <div>
                      <div className="text-[10px] text-gray-500 uppercase font-bold tracking-widest mb-2">
                        Availability
                      </div>
                      <div className="text-sm text-gray-200 font-medium flex items-center gap-2">
                        <Clock className="w-4 h-4 text-gray-400" />
                        {Array.isArray(profile.availability) && profile.availability.length > 0
                          ? profile.availability.join(", ")
                          : "Flexible"}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Technical Skills Card */}
              <div className="dashboard-card p-6">
                <h3 className="text-xs font-bold text-gray-500 uppercase tracking-widest mb-5">
                  Technical Skills
                </h3>

                {Object.keys(groupedSkills).length === 0 ? (
                  <p className="text-xs text-gray-500 italic">No technical skills added yet.</p>
                ) : (
                  <div className="space-y-6">
                    {Object.entries(groupedSkills).map(([cat, skillsArr], cIdx) => (
                      <div key={cat}>
                        {cIdx > 0 && <div className="h-px w-full bg-white/5 mb-6"></div>}
                        <div className="text-[10px] text-gray-400 font-bold mb-3 tracking-widest uppercase">
                          {cat.replace("_", " ")}
                        </div>
                        <div className="space-y-3">
                          {skillsArr.map((sk, sIdx) => (
                            <div key={sIdx} className="flex items-center justify-between group">
                              <span className="text-sm font-medium text-gray-200 group-hover:text-white transition-colors">
                                {sk.name}
                              </span>
                              {renderProficiencyBars(sk.proficiency, sk.category)}
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

            </div>

          </div>

        </div>
      </div>

      {/* Block Confirm Modal */}
      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl max-w-md w-full p-6 shadow-2xl">
            <div className="flex items-start gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-red-500/10 border border-red-500/20 flex items-center justify-center flex-shrink-0">
                <ShieldAlert className="w-5 h-5 text-red-400" />
              </div>
              <div>
                <h3 className="text-lg font-semibold text-white">
                  Block {profile.displayName || "this user"}?
                </h3>
                <p className="text-xs text-gray-400 mt-1">
                  Are you sure you want to block this user?
                </p>
              </div>
            </div>

            <div className="bg-white/5 border border-white/10 rounded-xl p-3.5 mb-6 text-xs text-gray-300 space-y-2">
              <p className="font-medium text-white">Blocking will:</p>
              <ul className="list-disc list-inside space-y-1 text-gray-400 pl-1">
                <li>Remove them from your People discovery</li>
                <li>Prevent them from appearing in your project matching</li>
                <li>Prevent them from interacting with your collaboration posts</li>
              </ul>
            </div>

            <div className="flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowConfirmModal(false)}
                disabled={blockLoading}
                className="px-4 py-2 rounded-lg text-xs font-medium text-gray-300 hover:text-white hover:bg-white/5 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleBlockConfirm}
                disabled={blockLoading}
                className="px-4 py-2 rounded-lg text-xs font-medium bg-red-500 hover:bg-red-600 text-white transition-colors flex items-center gap-1.5 disabled:opacity-50"
              >
                {blockLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                Block User
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Report Modal */}
      <ReportModal
        isOpen={showReportModal}
        onClose={() => setShowReportModal(false)}
        targetName={profile.displayName || "this user"}
        onSubmit={handleReportSubmit}
      />
    </div>
  );
}
