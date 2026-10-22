"use client";

import { useEffect, useState } from "react";
import {
  userService,
  postService,
  UserProfile,
  SkillItem,
  EducationItem,
  ExperienceItem,
  AchievementItem,
  SkillCategory,
  SkillProficiency,
  UpdateProfilePayload,
} from "@/lib/api";
import { isLoggedIn, logout } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Edit2,
  X,
  Check,
  Loader2,
  Share2,
  CheckCircle,
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
  ArrowUpRight,
  FolderGit2,
  Bot,
  ShieldCheck,
  Plus,
  Trash2,
  AlertCircle,
  Link as LinkIcon,
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

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [myProjects, setMyProjects] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // Edit Mode state
  const [isEditing, setIsEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [copyFeedback, setCopyFeedback] = useState(false);

  // Edit Form state
  const [editForm, setEditForm] = useState<{
    displayName: string;
    bio: string;
    avatarUrl: string;
    college: string;
    course: string;
    graduationYear: string;
    location: string;
    githubUrl: string;
    linkedinUrl: string;
    portfolioUrl: string;
    twitterUrl: string;
    openToCollaboration: boolean;
    preferredCollaboration: string;
    lookingFor: string;
    availability: string;
    skills: { name: string; category: SkillCategory; proficiency: SkillProficiency }[];
    education: EducationItem[];
    experience: ExperienceItem[];
    achievements: AchievementItem[];
  }>({
    displayName: "",
    bio: "",
    avatarUrl: "",
    college: "",
    course: "",
    graduationYear: "",
    location: "",
    githubUrl: "",
    linkedinUrl: "",
    portfolioUrl: "",
    twitterUrl: "",
    openToCollaboration: true,
    preferredCollaboration: "Remote Preferred",
    lookingFor: "",
    availability: "",
    skills: [],
    education: [],
    experience: [],
    achievements: [],
  });

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    fetchProfileData();
  }, [router]);

  const fetchProfileData = async () => {
    setLoading(true);
    try {
      const [profileData, projectsData] = await Promise.all([
        userService.getCurrentUser(),
        postService.getMyPosts().catch(() => []),
      ]);
      setProfile(profileData);
      setMyProjects(projectsData || []);
    } catch (err) {
      console.error("Failed to load profile data", err);
    } finally {
      setLoading(false);
    }
  };

  const handleEditClick = () => {
    if (!profile) return;
    setSaveError(null);

    // Normalize skills to structured list
    const normalizedSkills: { name: string; category: SkillCategory; proficiency: SkillProficiency }[] = [];
    if (Array.isArray(profile.skills)) {
      profile.skills.forEach((s) => {
        if (typeof s === "string") {
          normalizedSkills.push({ name: s, category: "OTHER", proficiency: "BEGINNER" });
        } else if (s && typeof s === "object") {
          normalizedSkills.push({
            name: s.name || "",
            category: s.category || "OTHER",
            proficiency: s.proficiency || "BEGINNER",
          });
        }
      });
    }

    setEditForm({
      displayName: profile.displayName || "",
      bio: profile.bio || "",
      avatarUrl: profile.avatarUrl || "",
      college: profile.college || "",
      course: profile.course || "",
      graduationYear: profile.graduationYear ? String(profile.graduationYear) : "",
      location: profile.location || "",
      githubUrl: profile.githubUrl || "",
      linkedinUrl: profile.linkedinUrl || "",
      portfolioUrl: profile.portfolioUrl || "",
      twitterUrl: profile.twitterUrl || "",
      openToCollaboration: profile.openToCollaboration ?? true,
      preferredCollaboration: profile.preferredCollaboration || "Remote Preferred",
      lookingFor: Array.isArray(profile.lookingFor) ? profile.lookingFor.join(", ") : "",
      availability: Array.isArray(profile.availability) ? profile.availability.join(", ") : "",
      skills: normalizedSkills,
      education: profile.education ? [...profile.education] : [],
      experience: profile.experience ? [...profile.experience] : [],
      achievements: profile.achievements ? [...profile.achievements] : [],
    });

    setIsEditing(true);
  };

  const handleSave = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      const payload: UpdateProfilePayload = {
        displayName: editForm.displayName.trim() || undefined,
        bio: editForm.bio.trim() || undefined,
        avatarUrl: editForm.avatarUrl.trim() || undefined,
        college: editForm.college.trim() || undefined,
        course: editForm.course.trim() || undefined,
        graduationYear: editForm.graduationYear ? parseInt(editForm.graduationYear, 10) : undefined,
        location: editForm.location.trim() || undefined,
        githubUrl: editForm.githubUrl.trim() || undefined,
        linkedinUrl: editForm.linkedinUrl.trim() || undefined,
        portfolioUrl: editForm.portfolioUrl.trim() || undefined,
        twitterUrl: editForm.twitterUrl.trim() || undefined,
        openToCollaboration: editForm.openToCollaboration,
        preferredCollaboration: editForm.preferredCollaboration.trim() || undefined,
        lookingFor: editForm.lookingFor
          ? editForm.lookingFor.split(",").map((s) => s.trim()).filter(Boolean)
          : [],
        availability: editForm.availability
          ? editForm.availability.split(",").map((s) => s.trim()).filter(Boolean)
          : [],
        skills: editForm.skills
          .filter((s) => s.name.trim().length > 0)
          .map((s) => ({
            name: s.name.trim(),
            category: s.category,
            proficiency: s.proficiency,
          })),
        education: editForm.education.filter((e) => e.institution?.trim()),
        experience: editForm.experience.filter((e) => e.title?.trim() && e.organization?.trim()),
        achievements: editForm.achievements.filter((a) => a.title?.trim()),
      };

      const updated = await userService.updateProfile(payload);
      setProfile(updated);
      setIsEditing(false);
    } catch (err: any) {
      console.error("Failed to update profile", err);
      setSaveError(err?.message || "Failed to update profile. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const handleShare = () => {
    if (typeof window !== "undefined") {
      navigator.clipboard.writeText(window.location.href);
      setCopyFeedback(true);
      setTimeout(() => setCopyFeedback(false), 3000);
    }
  };

  // Skill row proficiency visualizer (1-4 small bars)
  const renderProficiencyBars = (proficiency?: SkillProficiency, category?: SkillCategory) => {
    let activeBars = 2; // default intermediate
    if (proficiency === "BEGINNER") activeBars = 1;
    if (proficiency === "INTERMEDIATE") activeBars = 2;
    if (proficiency === "ADVANCED") activeBars = 3;

    // Color based on category / accent
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

  // Group skills by category for display
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

  if (!profile) return null;

  const groupedSkills = groupSkillsByCategory(profile.skills || []);
  const avatarImage =
    profile.avatarUrl ||
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80";

  return (
    <div className="w-full relative pb-20">
      {/* Ambient background glows */}
      <div className="ambient-orb bg-huddle-purple opacity-[0.06] w-[800px] h-[800px] top-[-10%] right-[-10%]"></div>
      <div className="ambient-orb bg-huddle-blue opacity-[0.04] w-[600px] h-[600px] bottom-[-20%] left-[-10%]"></div>

      {/* Save Error Alert */}
      {saveError && (
        <div className="max-w-6xl mx-auto px-5 md:px-10 mb-4 pt-4">
          <div className="p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-300 text-sm flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0" />
              <span>{saveError}</span>
            </div>
            <button onClick={() => setSaveError(null)} className="text-gray-400 hover:text-white">
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Copy link feedback banner */}
      {copyFeedback && (
        <div className="max-w-6xl mx-auto px-5 md:px-10 mb-4 pt-4">
          <div className="p-3 rounded-xl bg-green-500/10 border border-green-500/20 text-green-300 text-xs flex items-center gap-2 animate-fade-in">
            <Check className="w-4 h-4 text-green-400" />
            <span>Profile link copied to clipboard!</span>
          </div>
        </div>
      )}

      {/* ================= HERO & COVER ART ================= */}
      <div className="w-full relative">
        <div className="h-48 md:h-64 w-full bg-mesh-pattern border-b border-white/5 relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-t from-huddle-black to-transparent opacity-80"></div>
          
          {/* Top Right Action Buttons */}
          <div className="absolute top-6 right-6 flex gap-3 z-20">
            <button
              onClick={handleShare}
              className="w-9 h-9 rounded-full bg-black/40 border border-white/10 backdrop-blur-md flex items-center justify-center text-gray-300 hover:text-white hover:bg-white/10 transition-colors"
              title="Share Profile"
            >
              <Share2 className="w-4 h-4" />
            </button>
            {!isEditing ? (
              <button
                onClick={handleEditClick}
                className="w-9 h-9 rounded-full bg-black/40 border border-white/10 backdrop-blur-md flex items-center justify-center text-gray-300 hover:text-white hover:bg-white/10 transition-colors"
                title="Edit Profile"
              >
                <Edit2 className="w-4 h-4" />
              </button>
            ) : (
              <button
                onClick={() => setIsEditing(false)}
                className="w-9 h-9 rounded-full bg-black/40 border border-white/10 backdrop-blur-md flex items-center justify-center text-gray-300 hover:text-white hover:bg-white/10 transition-colors"
                title="Cancel Edit"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
        </div>

        <div className="max-w-6xl mx-auto px-5 md:px-10 relative">
          
          {/* Identity Header Row */}
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
                  {profile.emailVerified && (
                    <span className="bg-blue-500/10 text-blue-400 border border-blue-500/20 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-widest flex items-center gap-1">
                      <CheckCircle className="w-3 h-3" /> Verified Student
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

                {/* Social Links */}
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
                  {!profile.githubUrl && !profile.linkedinUrl && !profile.twitterUrl && !profile.portfolioUrl && (
                    <span className="text-xs text-gray-500 italic">No social links added</span>
                  )}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-3 flex-shrink-0 w-full md:w-auto">
                {!isEditing ? (
                  <>
                    <button
                      onClick={handleEditClick}
                      className="flex-1 md:flex-none btn-glass px-6 py-2.5 rounded-xl text-sm font-bold text-white shadow-sm flex items-center justify-center gap-2"
                    >
                      <Edit2 className="w-4 h-4" /> Edit Profile
                    </button>
                    <Link
                      href="/projects"
                      className="flex-1 md:flex-none btn-glow px-6 py-2.5 rounded-xl text-sm font-bold text-white shadow-lg flex items-center justify-center gap-2"
                    >
                      <Zap className="w-4 h-4" /> Browse Projects
                    </Link>
                  </>
                ) : (
                  <div className="flex gap-2 w-full md:w-auto">
                    <button
                      onClick={() => setIsEditing(false)}
                      disabled={saving}
                      className="flex-1 md:flex-none btn-glass px-5 py-2.5 rounded-xl text-sm font-bold text-gray-300 hover:text-white"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={handleSave}
                      disabled={saving}
                      className="flex-1 md:flex-none btn-glow px-6 py-2.5 rounded-xl text-sm font-bold text-white flex items-center justify-center gap-2"
                    >
                      {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                      Save Profile
                    </button>
                  </div>
                )}
              </div>

            </div>
          </div>

          {/* ================= MAIN PROFILE CONTENT GRID ================= */}
          {!isEditing ? (
            <div className="grid grid-cols-1 xl:grid-cols-12 gap-6 lg:gap-8">
              
              {/* LEFT COLUMN: Main details (Span 8) */}
              <div className="xl:col-span-8 space-y-6 lg:space-y-8">
                
                {/* About Section */}
                <section className="dashboard-card p-6 md:p-8">
                  <h3 className="text-xs font-bold text-gray-500 uppercase tracking-widest mb-4">About</h3>
                  <p className="text-gray-300 text-sm md:text-base leading-relaxed font-medium whitespace-pre-line">
                    {profile.bio || "No bio provided yet. Add a short bio to let other collaborators know about your background and interests."}
                  </p>
                </section>

                {/* Projects Showcase */}
                <section>
                  <div className="flex items-center justify-between mb-5 px-1">
                    <h3 className="text-lg font-display font-extrabold text-white flex items-center gap-2">
                      <FolderGit2 className="w-5 h-5 text-huddle-coral" /> My Projects
                    </h3>
                    <Link
                      href="/projects"
                      className="text-sm font-semibold text-gray-400 hover:text-white flex items-center gap-1 transition-colors"
                    >
                      View All <ArrowUpRight className="w-4 h-4" />
                    </Link>
                  </div>

                  {myProjects.length === 0 ? (
                    <div className="dashboard-card p-8 text-center">
                      <p className="text-sm text-gray-400 mb-4">You haven&apos;t created any projects yet.</p>
                      <Link
                        href="/projects"
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-white transition-colors"
                      >
                        <Zap className="w-3.5 h-3.5 text-huddle-coral" /> Start a Project
                      </Link>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                      {myProjects.slice(0, 4).map((post: any, idx: number) => (
                        <Link
                          key={post.id || idx}
                          href={`/posts/${post.id}`}
                          className="dashboard-card interactive p-5 flex flex-col h-full cursor-pointer relative overflow-hidden group"
                        >
                          <div className="flex items-start justify-between mb-4 relative z-10">
                            <div className="w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center text-huddle-purple shadow-inner">
                              <Bot className="w-6 h-6" />
                            </div>
                            <span className="bg-huddle-coral/10 text-huddle-coral border border-huddle-coral/20 px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-huddle-coral animate-pulse"></span>
                              {post.openToCollab ? "Recruiting" : "Closed"}
                            </span>
                          </div>

                          <h4 className="text-base font-bold text-white mb-2 group-hover:text-huddle-purple transition-colors relative z-10">
                            {post.title}
                          </h4>
                          <p className="text-sm text-gray-400 mb-5 line-clamp-2 flex-1 relative z-10">
                            {post.briefDescription || "Collaborative project built on Huddle."}
                          </p>

                          <div className="relative z-10 mt-auto">
                            <div className="flex flex-wrap gap-1.5 mb-4">
                              {post.desiredSkills?.slice(0, 3).map((sk: string) => (
                                <span
                                  key={sk}
                                  className="px-2 py-1 rounded bg-black/40 border border-white/5 text-[10px] text-gray-300 font-medium"
                                >
                                  {sk}
                                </span>
                              ))}
                            </div>
                            <div className="flex items-center justify-between pt-4 border-t border-white/5">
                              <span className="text-xs font-semibold text-gray-400">Project Creator</span>
                              <ArrowUpRight className="w-4 h-4 text-gray-500 group-hover:text-white transition-colors" />
                            </div>
                          </div>
                        </Link>
                      ))}
                    </div>
                  )}
                </section>

                {/* Experience & Education Timelines */}
                <section className="dashboard-card p-6 md:p-8">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-10 md:gap-6">
                    
                    {/* Experience */}
                    <div>
                      <div className="flex items-center gap-2 mb-6">
                        <Briefcase className="w-5 h-5 text-gray-400" />
                        <h3 className="text-lg font-display font-bold text-white">Experience</h3>
                      </div>
                      
                      {(!profile.experience || profile.experience.length === 0) ? (
                        <p className="text-sm text-gray-500 italic">No experience added yet.</p>
                      ) : (
                        <div className="timeline">
                          {profile.experience.map((exp, eIdx) => (
                            <div key={eIdx} className="timeline-item">
                              <div className="timeline-dot"></div>
                              <h4 className="text-base font-bold text-white mb-0.5">{exp.title}</h4>
                              <div className="text-sm text-huddle-purple font-medium mb-1.5">
                                {exp.organization}
                              </div>
                              <div className="text-[11px] text-gray-500 mb-3 font-semibold uppercase tracking-wider">
                                {exp.startDate || "Start"} — {exp.endDate || "Present"}
                              </div>
                              {exp.description && (
                                <p className="text-sm text-gray-400 leading-relaxed font-medium">
                                  {exp.description}
                                </p>
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
                        <p className="text-sm text-gray-500 italic">No education added yet.</p>
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
                                <p className="text-sm text-gray-400 leading-relaxed font-medium">
                                  {edu.description}
                                </p>
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
                      <p className="text-sm text-gray-500 italic">No achievements added yet.</p>
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

              {/* RIGHT COLUMN: Sidebar Stats, Readiness & Skills (Span 4) */}
              <div className="xl:col-span-4 space-y-6 lg:space-y-8">
                
                {/* Mini Stats Row */}
                <div className="dashboard-card p-5 flex justify-between divide-x divide-white/10 text-center">
                  <div className="flex-1">
                    <div className="text-2xl font-display font-extrabold text-white">
                      {profile.projectsCreatedCount ?? myProjects.length ?? 0}
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

                {/* Collaboration Readiness Card */}
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
                      {/* Looking For */}
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

                      {/* Collaboration Style */}
                      <div>
                        <div className="text-[10px] text-gray-500 uppercase font-bold tracking-widest mb-2">
                          Collaboration Style
                        </div>
                        <div className="text-sm text-gray-200 font-medium flex items-center gap-2">
                          <Laptop className="w-4 h-4 text-gray-400" />
                          {profile.preferredCollaboration || "Remote Preferred"}
                        </div>
                      </div>

                      {/* Availability */}
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

                {/* Technical Skills Categorized Card */}
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
          ) : (
            /* ================= FULL EDIT FORM VIEW ================= */
            <div className="dashboard-card p-8 animate-fade-in mb-12">
              <div className="flex items-center justify-between pb-6 border-b border-white/10 mb-8">
                <div>
                  <h2 className="text-2xl font-display font-extrabold text-white">Edit Your Profile</h2>
                  <p className="text-sm text-gray-400">Update your identity, links, skills, and background.</p>
                </div>
                <div className="flex gap-3">
                  <button
                    onClick={() => setIsEditing(false)}
                    disabled={saving}
                    className="btn-glass px-5 py-2 rounded-xl text-sm font-semibold text-gray-300"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleSave}
                    disabled={saving}
                    className="btn-glow px-6 py-2 rounded-xl text-sm font-bold text-white flex items-center gap-2"
                  >
                    {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                    Save Changes
                  </button>
                </div>
              </div>

              <div className="space-y-8">
                
                {/* Basic Identity */}
                <div>
                  <h3 className="text-sm font-bold text-white uppercase tracking-wider mb-4 pb-2 border-b border-white/5">
                    Basic Identity
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="form-label">Display Name</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.displayName}
                        onChange={(e) => setEditForm({ ...editForm, displayName: e.target.value })}
                        placeholder="e.g. Sarah Chen"
                      />
                    </div>
                    <div>
                      <label className="form-label">Avatar Image URL</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.avatarUrl}
                        onChange={(e) => setEditForm({ ...editForm, avatarUrl: e.target.value })}
                        placeholder="https://images.unsplash.com/..."
                      />
                    </div>
                    <div>
                      <label className="form-label">College / University</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.college}
                        onChange={(e) => setEditForm({ ...editForm, college: e.target.value })}
                        placeholder="e.g. Stanford University"
                      />
                    </div>
                    <div>
                      <label className="form-label">Major / Course</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.course}
                        onChange={(e) => setEditForm({ ...editForm, course: e.target.value })}
                        placeholder="e.g. B.S. in Computer Science"
                      />
                    </div>
                    <div>
                      <label className="form-label">Graduation Year</label>
                      <input
                        type="number"
                        className="form-input"
                        value={editForm.graduationYear}
                        onChange={(e) => setEditForm({ ...editForm, graduationYear: e.target.value })}
                        placeholder="e.g. 2025"
                      />
                    </div>
                    <div>
                      <label className="form-label">Location</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.location}
                        onChange={(e) => setEditForm({ ...editForm, location: e.target.value })}
                        placeholder="e.g. San Francisco, CA"
                      />
                    </div>
                    <div className="md:col-span-2">
                      <label className="form-label">Bio</label>
                      <textarea
                        rows={3}
                        className="form-input resize-none"
                        value={editForm.bio}
                        onChange={(e) => setEditForm({ ...editForm, bio: e.target.value })}
                        placeholder="Tell potential collaborators about your background and interests..."
                      />
                    </div>
                  </div>
                </div>

                {/* Social Links */}
                <div>
                  <h3 className="text-sm font-bold text-white uppercase tracking-wider mb-4 pb-2 border-b border-white/5">
                    Social & Portfolio Links
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="form-label">GitHub URL</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.githubUrl}
                        onChange={(e) => setEditForm({ ...editForm, githubUrl: e.target.value })}
                        placeholder="https://github.com/username"
                      />
                    </div>
                    <div>
                      <label className="form-label">LinkedIn URL</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.linkedinUrl}
                        onChange={(e) => setEditForm({ ...editForm, linkedinUrl: e.target.value })}
                        placeholder="https://linkedin.com/in/username"
                      />
                    </div>
                    <div>
                      <label className="form-label">Twitter / X URL</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.twitterUrl}
                        onChange={(e) => setEditForm({ ...editForm, twitterUrl: e.target.value })}
                        placeholder="https://twitter.com/username"
                      />
                    </div>
                    <div>
                      <label className="form-label">Portfolio Website URL</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.portfolioUrl}
                        onChange={(e) => setEditForm({ ...editForm, portfolioUrl: e.target.value })}
                        placeholder="https://yourportfolio.dev"
                      />
                    </div>
                  </div>
                </div>

                {/* Collaboration Preferences */}
                <div>
                  <h3 className="text-sm font-bold text-white uppercase tracking-wider mb-4 pb-2 border-b border-white/5">
                    Collaboration Preferences
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="form-label">Collaboration Style</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.preferredCollaboration}
                        onChange={(e) => setEditForm({ ...editForm, preferredCollaboration: e.target.value })}
                        placeholder="e.g. Remote Preferred, In-person, Hybrid"
                      />
                    </div>
                    <div>
                      <label className="form-label">Looking For (Comma separated)</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.lookingFor}
                        onChange={(e) => setEditForm({ ...editForm, lookingFor: e.target.value })}
                        placeholder="Frontend Developer, UI/UX Designer, ML Engineer"
                      />
                    </div>
                    <div className="md:col-span-2">
                      <label className="form-label">Availability Slots (Comma separated)</label>
                      <input
                        type="text"
                        className="form-input"
                        value={editForm.availability}
                        onChange={(e) => setEditForm({ ...editForm, availability: e.target.value })}
                        placeholder="Weekday Evenings, Weekends, 10 hrs/week"
                      />
                    </div>
                  </div>
                </div>

                {/* Structured Technical Skills */}
                <div>
                  <div className="flex items-center justify-between mb-4 pb-2 border-b border-white/5">
                    <h3 className="text-sm font-bold text-white uppercase tracking-wider">Technical Skills</h3>
                    <button
                      type="button"
                      onClick={() =>
                        setEditForm({
                          ...editForm,
                          skills: [
                            ...editForm.skills,
                            { name: "", category: "FRONTEND", proficiency: "INTERMEDIATE" },
                          ],
                        })
                      }
                      className="text-xs text-huddle-coral hover:text-huddle-pink flex items-center gap-1 font-semibold"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add Skill
                    </button>
                  </div>

                  {editForm.skills.length === 0 ? (
                    <p className="text-xs text-gray-500 italic">No skills added. Click &quot;Add Skill&quot; above.</p>
                  ) : (
                    <div className="space-y-3">
                      {editForm.skills.map((skill, sIndex) => (
                        <div key={sIndex} className="flex items-center gap-3 bg-huddle-dark p-3 rounded-xl border border-white/5">
                          <input
                            type="text"
                            className="form-input flex-1 py-1.5 text-sm"
                            value={skill.name}
                            onChange={(e) => {
                              const updated = [...editForm.skills];
                              updated[sIndex].name = e.target.value;
                              setEditForm({ ...editForm, skills: updated });
                            }}
                            placeholder="Skill name (e.g. React, Python)"
                          />
                          <select
                            className="bg-huddle-charcoal border border-white/10 rounded-lg px-3 py-2 text-xs text-white outline-none"
                            value={skill.category}
                            onChange={(e) => {
                              const updated = [...editForm.skills];
                              updated[sIndex].category = e.target.value as SkillCategory;
                              setEditForm({ ...editForm, skills: updated });
                            }}
                          >
                            <option value="FRONTEND">Frontend</option>
                            <option value="BACKEND">Backend</option>
                            <option value="AI_ML">AI / ML</option>
                            <option value="MOBILE">Mobile</option>
                            <option value="DATABASES">Databases</option>
                            <option value="DEVOPS">DevOps</option>
                            <option value="CYBERSECURITY">Cybersecurity</option>
                            <option value="DESIGN">Design</option>
                            <option value="LANGUAGES">Languages</option>
                            <option value="OTHER">Other</option>
                          </select>
                          <select
                            className="bg-huddle-charcoal border border-white/10 rounded-lg px-3 py-2 text-xs text-white outline-none"
                            value={skill.proficiency}
                            onChange={(e) => {
                              const updated = [...editForm.skills];
                              updated[sIndex].proficiency = e.target.value as SkillProficiency;
                              setEditForm({ ...editForm, skills: updated });
                            }}
                          >
                            <option value="BEGINNER">Beginner</option>
                            <option value="INTERMEDIATE">Intermediate</option>
                            <option value="ADVANCED">Advanced</option>
                          </select>
                          <button
                            type="button"
                            onClick={() => {
                              const updated = editForm.skills.filter((_, i) => i !== sIndex);
                              setEditForm({ ...editForm, skills: updated });
                            }}
                            className="text-gray-500 hover:text-red-400 p-1.5 transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Experience Entries */}
                <div>
                  <div className="flex items-center justify-between mb-4 pb-2 border-b border-white/5">
                    <h3 className="text-sm font-bold text-white uppercase tracking-wider">Experience</h3>
                    <button
                      type="button"
                      onClick={() =>
                        setEditForm({
                          ...editForm,
                          experience: [
                            ...editForm.experience,
                            { title: "", organization: "", startDate: "", endDate: "", description: "" },
                          ],
                        })
                      }
                      className="text-xs text-huddle-purple hover:text-huddle-pink flex items-center gap-1 font-semibold"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add Experience
                    </button>
                  </div>

                  {editForm.experience.length === 0 ? (
                    <p className="text-xs text-gray-500 italic">No experience added.</p>
                  ) : (
                    <div className="space-y-4">
                      {editForm.experience.map((exp, expIdx) => (
                        <div key={expIdx} className="bg-huddle-dark p-4 rounded-xl border border-white/5 space-y-3 relative">
                          <button
                            type="button"
                            onClick={() => {
                              const updated = editForm.experience.filter((_, i) => i !== expIdx);
                              setEditForm({ ...editForm, experience: updated });
                            }}
                            className="absolute top-4 right-4 text-gray-500 hover:text-red-400"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pr-8">
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={exp.title}
                              onChange={(e) => {
                                const updated = [...editForm.experience];
                                updated[expIdx].title = e.target.value;
                                setEditForm({ ...editForm, experience: updated });
                              }}
                              placeholder="Job Title (e.g. Software Engineer Intern)"
                            />
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={exp.organization}
                              onChange={(e) => {
                                const updated = [...editForm.experience];
                                updated[expIdx].organization = e.target.value;
                                setEditForm({ ...editForm, experience: updated });
                              }}
                              placeholder="Organization / Company"
                            />
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={exp.startDate || ""}
                              onChange={(e) => {
                                const updated = [...editForm.experience];
                                updated[expIdx].startDate = e.target.value;
                                setEditForm({ ...editForm, experience: updated });
                              }}
                              placeholder="Start Date (e.g. Jun 2024)"
                            />
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={exp.endDate || ""}
                              onChange={(e) => {
                                const updated = [...editForm.experience];
                                updated[expIdx].endDate = e.target.value;
                                setEditForm({ ...editForm, experience: updated });
                              }}
                              placeholder="End Date (or Present)"
                            />
                          </div>
                          <textarea
                            rows={2}
                            className="form-input text-sm resize-none"
                            value={exp.description || ""}
                            onChange={(e) => {
                              const updated = [...editForm.experience];
                              updated[expIdx].description = e.target.value;
                              setEditForm({ ...editForm, experience: updated });
                            }}
                            placeholder="Responsibilities and key accomplishments..."
                          />
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Education Entries */}
                <div>
                  <div className="flex items-center justify-between mb-4 pb-2 border-b border-white/5">
                    <h3 className="text-sm font-bold text-white uppercase tracking-wider">Education</h3>
                    <button
                      type="button"
                      onClick={() =>
                        setEditForm({
                          ...editForm,
                          education: [
                            ...editForm.education,
                            { institution: "", degree: "", field: "", startYear: undefined, endYear: undefined, description: "" },
                          ],
                        })
                      }
                      className="text-xs text-huddle-blue hover:text-huddle-pink flex items-center gap-1 font-semibold"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add Education
                    </button>
                  </div>

                  {editForm.education.length === 0 ? (
                    <p className="text-xs text-gray-500 italic">No education added.</p>
                  ) : (
                    <div className="space-y-4">
                      {editForm.education.map((edu, eduIdx) => (
                        <div key={eduIdx} className="bg-huddle-dark p-4 rounded-xl border border-white/5 space-y-3 relative">
                          <button
                            type="button"
                            onClick={() => {
                              const updated = editForm.education.filter((_, i) => i !== eduIdx);
                              setEditForm({ ...editForm, education: updated });
                            }}
                            className="absolute top-4 right-4 text-gray-500 hover:text-red-400"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pr-8">
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={edu.institution}
                              onChange={(e) => {
                                const updated = [...editForm.education];
                                updated[eduIdx].institution = e.target.value;
                                setEditForm({ ...editForm, education: updated });
                              }}
                              placeholder="Institution (e.g. Stanford)"
                            />
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={edu.degree || ""}
                              onChange={(e) => {
                                const updated = [...editForm.education];
                                updated[eduIdx].degree = e.target.value;
                                setEditForm({ ...editForm, education: updated });
                              }}
                              placeholder="Degree (e.g. B.S.)"
                            />
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={edu.field || ""}
                              onChange={(e) => {
                                const updated = [...editForm.education];
                                updated[eduIdx].field = e.target.value;
                                setEditForm({ ...editForm, education: updated });
                              }}
                              placeholder="Field of Study (e.g. CS)"
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Achievement Entries */}
                <div>
                  <div className="flex items-center justify-between mb-4 pb-2 border-b border-white/5">
                    <h3 className="text-sm font-bold text-white uppercase tracking-wider">Achievements</h3>
                    <button
                      type="button"
                      onClick={() =>
                        setEditForm({
                          ...editForm,
                          achievements: [
                            ...editForm.achievements,
                            { title: "", description: "", date: "" },
                          ],
                        })
                      }
                      className="text-xs text-yellow-500 hover:text-yellow-400 flex items-center gap-1 font-semibold"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add Achievement
                    </button>
                  </div>

                  {editForm.achievements.length === 0 ? (
                    <p className="text-xs text-gray-500 italic">No achievements added.</p>
                  ) : (
                    <div className="space-y-4">
                      {editForm.achievements.map((ach, achIdx) => (
                        <div key={achIdx} className="bg-huddle-dark p-4 rounded-xl border border-white/5 space-y-3 relative">
                          <button
                            type="button"
                            onClick={() => {
                              const updated = editForm.achievements.filter((_, i) => i !== achIdx);
                              setEditForm({ ...editForm, achievements: updated });
                            }}
                            className="absolute top-4 right-4 text-gray-500 hover:text-red-400"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pr-8">
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={ach.title}
                              onChange={(e) => {
                                const updated = [...editForm.achievements];
                                updated[achIdx].title = e.target.value;
                                setEditForm({ ...editForm, achievements: updated });
                              }}
                              placeholder="Achievement Title"
                            />
                            <input
                              type="text"
                              className="form-input text-sm"
                              value={ach.date || ""}
                              onChange={(e) => {
                                const updated = [...editForm.achievements];
                                updated[achIdx].date = e.target.value;
                                setEditForm({ ...editForm, achievements: updated });
                              }}
                              placeholder="Date (e.g. Oct 2024)"
                            />
                          </div>
                          <textarea
                            rows={2}
                            className="form-input text-sm resize-none"
                            value={ach.description || ""}
                            onChange={(e) => {
                              const updated = [...editForm.achievements];
                              updated[achIdx].description = e.target.value;
                              setEditForm({ ...editForm, achievements: updated });
                            }}
                            placeholder="Brief description of the award or recognition..."
                          />
                        </div>
                      ))}
                    </div>
                  )}
                </div>

              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}
