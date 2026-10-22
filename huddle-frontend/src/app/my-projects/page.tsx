"use client";

import { useEffect, useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Folder,
  Crown,
  Handshake,
  Users,
  ArrowRight,
  Filter,
  Loader2,
  AlertCircle,
  Brain,
  HeartPulse,
  Globe,
  Server,
  Ghost,
  MapPin,
  Music,
  Code2,
  Sparkles,
  UserCheck,
  FolderPlus,
} from "lucide-react";
import { postService, collaborationService, Collaboration } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";

interface ProjectItem {
  id: string;
  title: string;
  briefDescription?: string;
  desiredSkills?: string[];
  desiredAvailability?: string[];
  openToCollab?: boolean;
  status?: string;
  createdAt?: string;
  posterId?: string;
  posterDisplayName?: string;
  role?: "Project Owner" | "Collaborator";
}

interface ProjectTheme {
  icon: typeof Folder;
  color: string;
}

const THEMES: ProjectTheme[] = [
  { icon: Brain, color: "from-pink-500 to-indigo-600" },
  { icon: HeartPulse, color: "from-rose-500 to-pink-600" },
  { icon: Globe, color: "from-cyan-500 to-blue-600" },
  { icon: Server, color: "from-amber-500 to-orange-600" },
  { icon: Ghost, color: "from-violet-500 to-purple-800" },
  { icon: MapPin, color: "from-emerald-500 to-teal-700" },
  { icon: Music, color: "from-pink-500 to-rose-700" },
  { icon: Code2, color: "from-indigo-500 to-blue-600" },
  { icon: Sparkles, color: "from-purple-500 to-pink-600" },
];

function getProjectTheme(title: string = "", skills: string[] = []): ProjectTheme {
  const t = (title + " " + skills.join(" ")).toLowerCase();
  if (
    t.includes("ai") ||
    t.includes("companion") ||
    t.includes("llm") ||
    t.includes("learn") ||
    t.includes("study") ||
    t.includes("brain")
  ) {
    return { icon: Brain, color: "from-pink-500 to-indigo-600" };
  }
  if (
    t.includes("health") ||
    t.includes("heart") ||
    t.includes("fit") ||
    t.includes("med") ||
    t.includes("bio") ||
    t.includes("tracker")
  ) {
    return { icon: HeartPulse, color: "from-rose-500 to-pink-600" };
  }
  if (
    t.includes("world") ||
    t.includes("3d") ||
    t.includes("terrain") ||
    t.includes("webgl") ||
    t.includes("globe") ||
    t.includes("procedural")
  ) {
    return { icon: Globe, color: "from-cyan-500 to-blue-600" };
  }
  if (
    t.includes("devops") ||
    t.includes("kubernetes") ||
    t.includes("docker") ||
    t.includes("cloud") ||
    t.includes("server") ||
    t.includes("cluster")
  ) {
    return { icon: Server, color: "from-amber-500 to-orange-600" };
  }
  if (
    t.includes("game") ||
    t.includes("horror") ||
    t.includes("godot") ||
    t.includes("unity") ||
    t.includes("indie")
  ) {
    return { icon: Ghost, color: "from-violet-500 to-purple-800" };
  }
  if (
    t.includes("nav") ||
    t.includes("campus") ||
    t.includes("map") ||
    t.includes("ar") ||
    t.includes("location")
  ) {
    return { icon: MapPin, color: "from-emerald-500 to-teal-700" };
  }
  if (
    t.includes("music") ||
    t.includes("audio") ||
    t.includes("sound") ||
    t.includes("daw") ||
    t.includes("jam")
  ) {
    return { icon: Music, color: "from-pink-500 to-rose-700" };
  }

  // Deterministic fallback hash based on title
  let hash = 0;
  for (let i = 0; i < title.length; i++) {
    hash = (hash << 5) - hash + title.charCodeAt(i);
  }
  const index = Math.abs(hash) % THEMES.length;
  return THEMES[index];
}

function timeAgo(dateString?: string | number | Date): string {
  if (!dateString) return "Recently";
  const date = new Date(dateString);
  if (isNaN(date.getTime())) return "Recently";
  const now = new Date();
  const diffSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);
  if (diffSeconds < 60) return "Just now";
  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} ${diffHours === 1 ? "hour" : "hours"} ago`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 7) return `${diffDays} days ago`;
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function MyProjectsPage() {
  const router = useRouter();
  const [createdProjects, setCreatedProjects] = useState<ProjectItem[]>([]);
  const [collaboratingProjects, setCollaboratingProjects] = useState<ProjectItem[]>([]);
  const [collabMap, setCollabMap] = useState<Map<string, Collaboration>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<"created" | "collaborating">("created");
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<"recent" | "oldest" | "name">("recent");

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    fetchWorkspaceProjects();
  }, [router]);

  const fetchWorkspaceProjects = async () => {
    setLoading(true);
    setError(null);
    try {
      const [owned, accepted, collabs] = await Promise.all([
        postService.getMyPosts().catch(() => []),
        postService.getAcceptedPosts().catch(() => []),
        collaborationService.getMyCollaborations().catch(() => []),
      ]);

      // Map collaborations by projectId
      const cMap = new Map<string, Collaboration>();
      if (Array.isArray(collabs)) {
        collabs.forEach((c: Collaboration) => {
          if (c.projectId) {
            cMap.set(c.projectId, c);
          }
        });
      }
      setCollabMap(cMap);

      // Owned projects
      const ownedList: ProjectItem[] = (Array.isArray(owned) ? owned : []).map((p: any) => ({
        ...p,
        desiredSkills: Array.isArray(p.desiredSkills)
          ? p.desiredSkills
          : Array.from(p.desiredSkills || []),
        desiredAvailability: Array.isArray(p.desiredAvailability)
          ? p.desiredAvailability
          : Array.from(p.desiredAvailability || []),
        role: "Project Owner",
      }));
      setCreatedProjects(ownedList);

      // Accepted collaboration projects
      const acceptedList: ProjectItem[] = (Array.isArray(accepted) ? accepted : []).map((p: any) => ({
        ...p,
        desiredSkills: Array.isArray(p.desiredSkills)
          ? p.desiredSkills
          : Array.from(p.desiredSkills || []),
        desiredAvailability: Array.isArray(p.desiredAvailability)
          ? p.desiredAvailability
          : Array.from(p.desiredAvailability || []),
        role: "Collaborator",
      }));

      // Also incorporate active collaborations where user is a participant if not already present
      if (Array.isArray(collabs)) {
        collabs.forEach((c: Collaboration) => {
          const alreadyPresent =
            ownedList.some((p) => p.id === c.projectId) ||
            acceptedList.some((p) => p.id === c.projectId);
          if (!alreadyPresent && c.projectId) {
            acceptedList.push({
              id: c.projectId,
              title: c.projectTitle || "Collaboration Workspace",
              briefDescription: c.projectBriefDescription || "",
              desiredSkills: Array.isArray(c.projectDesiredSkills)
                ? c.projectDesiredSkills
                : Array.from(c.projectDesiredSkills || []),
              openToCollab: false,
              createdAt: c.createdAt,
              role: "Collaborator",
            });
          }
        });
      }

      setCollaboratingProjects(acceptedList);
    } catch (err: any) {
      console.error("Failed to load workspace projects", err);
      setError(err?.message || "Failed to load projects.");
    } finally {
      setLoading(false);
    }
  };

  // Stats calculation
  const totalCount = createdProjects.length + collaboratingProjects.length;
  const recruitingCount = useMemo(() => {
    const all = [...createdProjects, ...collaboratingProjects];
    return all.filter((p) => p.openToCollab === true).length;
  }, [createdProjects, collaboratingProjects]);

  // Filter & sort
  const currentTabProjects = activeTab === "created" ? createdProjects : collaboratingProjects;

  const filteredProjects = useMemo(() => {
    let list = [...currentTabProjects];

    const q = searchQuery.toLowerCase().trim();
    if (q) {
      list = list.filter((p) => {
        const titleMatch = (p.title || "").toLowerCase().includes(q);
        const descMatch = (p.briefDescription || "").toLowerCase().includes(q);
        const skillMatch = (p.desiredSkills || []).some((s) => s.toLowerCase().includes(q));
        return titleMatch || descMatch || skillMatch;
      });
    }

    if (sortBy === "name") {
      list.sort((a, b) => (a.title || "").localeCompare(b.title || ""));
    } else if (sortBy === "oldest") {
      list.sort((a, b) => {
        const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return timeA - timeB;
      });
    } else {
      // recent default
      list.sort((a, b) => {
        const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return timeB - timeA;
      });
    }

    return list;
  }, [currentTabProjects, searchQuery, sortBy]);

  return (
    <div className="max-w-7xl mx-auto w-full relative pb-16">
      {/* Ambient Glow Orbs matching Huddle design references */}
      <div className="ambient-orb bg-huddle-purple opacity-[0.04] w-[700px] h-[700px] top-[0%] right-[5%]" />
      <div
        className="ambient-orb bg-huddle-coral opacity-[0.03] w-[500px] h-[500px] bottom-[10%] left-[10%]"
        style={{ animationDelay: "-3s" }}
      />

      {/* ── 1. Hero Header matching Reference ── */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 pb-6 border-b border-white/[0.06] relative z-10">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-pink-500/10 text-pink-400 border border-pink-500/20 text-xs font-semibold tracking-wide uppercase mb-3">
            <Folder className="w-3.5 h-3.5 text-pink-400" />
            <span>Project Workspace</span>
          </div>
          <h1 className="font-display text-4xl font-extrabold tracking-tight text-white">
            My <span className="text-gradient">Projects.</span>
          </h1>
          <p className="text-sm text-zinc-400 mt-2 max-w-xl font-medium">
            Manage the ambitious projects you’ve initiated and track your active collaborations in
            real-time.
          </p>
        </div>

        {/* Stats / Quick Summary Pills */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="bg-[#111115] border border-white/5 px-4 py-2.5 rounded-xl text-center min-w-[100px]">
            <div className="text-lg font-bold text-white">{totalCount}</div>
            <div className="text-[10px] text-zinc-400 uppercase tracking-wider font-semibold mt-0.5">
              Total Projects
            </div>
          </div>
          <div className="bg-[#111115] border border-white/5 px-4 py-2.5 rounded-xl text-center min-w-[100px]">
            <div className="text-lg font-bold text-pink-400">{recruitingCount}</div>
            <div className="text-[10px] text-zinc-400 uppercase tracking-wider font-semibold mt-0.5">
              Recruiting
            </div>
          </div>
        </div>
      </div>

      {/* ── 2. Filter & Navigation Toolbar ── */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4 mt-8 mb-6 relative z-10">
        {/* Segmented Tabs */}
        <div className="flex items-center bg-[#111115] p-1.5 rounded-xl border border-white/5 w-fit shadow-lg gap-1.5">
          <button
            onClick={() => setActiveTab("created")}
            className={`px-5 py-2 rounded-lg text-sm font-semibold transition-all flex items-center gap-2 ${
              activeTab === "created"
                ? "text-white bg-gradient-to-r from-huddle-coral via-huddle-pink to-huddle-purple shadow-md"
                : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            <Crown
              className={`w-3.5 h-3.5 ${activeTab === "created" ? "text-amber-300" : "text-zinc-500"}`}
            />
            <span>Created by Me</span>
            <span
              className={`ml-1 text-xs px-2 py-0.5 rounded-full font-mono ${
                activeTab === "created" ? "bg-black/30 text-white" : "bg-zinc-800 text-zinc-400"
              }`}
            >
              {createdProjects.length}
            </span>
          </button>
          <button
            onClick={() => setActiveTab("collaborating")}
            className={`px-5 py-2 rounded-lg text-sm font-semibold transition-all flex items-center gap-2 ${
              activeTab === "collaborating"
                ? "text-white bg-gradient-to-r from-huddle-coral via-huddle-pink to-huddle-purple shadow-md"
                : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            <Handshake
              className={`w-3.5 h-3.5 ${activeTab === "collaborating" ? "text-white" : "text-pink-400"}`}
            />
            <span>Collaborating</span>
            <span
              className={`ml-1 text-xs px-2 py-0.5 rounded-full font-mono ${
                activeTab === "collaborating"
                  ? "bg-black/30 text-white"
                  : "bg-zinc-800 text-zinc-400"
              }`}
            >
              {collaboratingProjects.length}
            </span>
          </button>
        </div>

        {/* Search, Filter & Sort Controls */}
        <div className="flex items-center gap-3">
          <div className="relative flex-1 sm:w-64">
            <Filter className="w-3.5 h-3.5 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Filter projects by title..."
              className="w-full bg-[#111115] border border-white/10 rounded-xl pl-9 pr-4 py-2 text-sm text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-pink-500/50 transition-all font-medium"
            />
          </div>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as any)}
            className="bg-[#111115] border border-white/10 rounded-xl px-3.5 py-2 text-sm text-zinc-300 focus:outline-none focus:border-pink-500/50 transition-all cursor-pointer font-medium"
          >
            <option value="recent">Recently Updated</option>
            <option value="oldest">Oldest First</option>
            <option value="name">Alphabetical</option>
          </select>
        </div>
      </div>

      {/* ── 3. Content Area ── */}
      <div className="relative z-10">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-24 text-zinc-400 gap-3">
            <Loader2 className="w-6 h-6 animate-spin text-huddle-coral" />
            <span className="text-sm font-medium">Loading your projects...</span>
          </div>
        ) : error ? (
          <div className="bg-[#111115] border border-red-500/20 p-10 rounded-2xl flex flex-col items-center justify-center text-center">
            <AlertCircle className="w-12 h-12 text-red-500 mb-4" />
            <h3 className="text-xl font-semibold text-white mb-2">Could not load projects</h3>
            <p className="text-zinc-400 mb-6 max-w-sm text-sm">{error}</p>
            <button
              onClick={fetchWorkspaceProjects}
              className="px-5 py-2.5 bg-white/10 hover:bg-white/20 text-white rounded-xl transition-colors text-sm font-semibold"
            >
              Try Again
            </button>
          </div>
        ) : filteredProjects.length === 0 ? (
          /* Empty State matching Reference */
          <div className="flex flex-col items-center justify-center py-20 px-4 text-center bg-[#111115]/50 border border-white/5 rounded-2xl">
            <div className="w-16 h-16 rounded-2xl bg-pink-500/10 border border-pink-500/20 flex items-center justify-center text-pink-400 text-2xl mb-4 shadow-lg shadow-pink-500/10">
              <FolderPlus className="w-7 h-7 text-pink-400" />
            </div>
            <h3 className="text-lg font-semibold text-white">
              {searchQuery ? "No matching projects" : "No projects found"}
            </h3>
            <p className="text-sm text-zinc-400 max-w-sm mt-1 mb-6 leading-relaxed">
              {searchQuery
                ? `No projects matching "${searchQuery}". Try a different filter keyword.`
                : activeTab === "created"
                ? "You haven't created any projects yet. Start building something extraordinary today."
                : "You don't have any active project collaborations yet. Discover projects or accept collaboration requests to get started."}
            </p>
            {searchQuery ? (
              <button
                onClick={() => setSearchQuery("")}
                className="px-4 py-2 bg-white/10 hover:bg-white/15 text-white text-xs font-semibold rounded-xl transition-all"
              >
                Clear Search Filter
              </button>
            ) : activeTab === "created" ? (
              <Link href="/create-post">
                <button className="bg-gradient-to-r from-huddle-coral via-huddle-pink to-huddle-purple text-white text-sm font-semibold px-5 py-2.5 rounded-xl shadow-lg shadow-pink-500/20 hover:opacity-95 transition-all">
                  Create your first project
                </button>
              </Link>
            ) : (
              <Link href="/projects">
                <button className="bg-gradient-to-r from-huddle-coral via-huddle-pink to-huddle-purple text-white text-sm font-semibold px-5 py-2.5 rounded-xl shadow-lg shadow-pink-500/20 hover:opacity-95 transition-all">
                  Browse Projects
                </button>
              </Link>
            )}
          </div>
        ) : (
          /* Projects Grid Container */
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {filteredProjects.map((project) => {
              const theme = getProjectTheme(project.title, project.desiredSkills);
              const IconComponent = theme.icon;

              const isOwner = project.role === "Project Owner";
              const collab = collabMap.get(project.id);
              const activeMembers = collab
                ? (collab.members || []).filter((m) => m.status === "ACTIVE")
                : [];
              const buildersCount = activeMembers.length > 0 ? activeMembers.length : 1;

              // Project status determination
              let statusText = "Active";
              if (project.openToCollab === true) {
                statusText = "Recruiting";
              } else if (
                project.status?.toLowerCase() === "planning" ||
                project.status?.toLowerCase() === "plan"
              ) {
                statusText = "Planning";
              }

              // Workspace destination: if active collaboration workspace exists, link to it; otherwise link to post details
              const workspaceHref = collab ? `/collaborations/${collab.id}` : `/post/${project.id}`;

              return (
                <div
                  key={project.id}
                  className="bg-[#0f0f13] border border-white/[0.07] rounded-2xl p-6 flex flex-col justify-between group hover:border-pink-500/30 hover:shadow-[0_12px_40px_-10px_rgba(236,72,153,0.15)] hover:-translate-y-1 transition-all duration-300 relative overflow-hidden"
                >
                  {/* Top subtle accent line */}
                  <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-pink-500/40 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

                  <div>
                    {/* Card Header */}
                    <div className="flex items-start justify-between gap-3 mb-4">
                      <div className="flex items-center gap-3 min-w-0">
                        <div
                          className={`w-11 h-11 rounded-xl bg-gradient-to-br ${theme.color} flex items-center justify-center text-white text-base shadow-lg group-hover:scale-105 transition-transform shrink-0`}
                        >
                          <IconComponent className="w-5 h-5" />
                        </div>
                        <div className="min-w-0">
                          <Link href={`/post/${project.id}`}>
                            <h3 className="font-bold text-white text-base tracking-tight group-hover:text-pink-300 transition-colors line-clamp-1">
                              {project.title}
                            </h3>
                          </Link>
                          <span className="text-xs text-zinc-400 flex items-center gap-1.5 mt-0.5 font-medium">
                            {isOwner ? (
                              <>
                                <Crown className="w-3 h-3 text-amber-400 shrink-0" />
                                <span>Project Owner</span>
                              </>
                            ) : (
                              <>
                                <UserCheck className="w-3 h-3 text-pink-400 shrink-0" />
                                <span>Collaborator</span>
                              </>
                            )}
                          </span>
                        </div>
                      </div>

                      {/* Status Badge */}
                      <span
                        className={`text-[11px] font-semibold px-2.5 py-1 rounded-full shrink-0 ${
                          statusText === "Recruiting"
                            ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                            : statusText === "Planning"
                            ? "bg-purple-500/10 text-purple-400 border border-purple-500/20"
                            : "bg-pink-500/10 text-pink-400 border border-pink-500/20"
                        }`}
                      >
                        {statusText}
                      </span>
                    </div>

                    {/* Description */}
                    <p className="text-sm text-zinc-400 line-clamp-2 mb-5 leading-relaxed font-normal">
                      {project.briefDescription || "No description provided."}
                    </p>

                    {/* Skill Chips */}
                    {project.desiredSkills && project.desiredSkills.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mb-6">
                        {project.desiredSkills.map((skill: string) => (
                          <span
                            key={skill}
                            className="text-xs px-2.5 py-1 rounded-lg bg-[#070709] border border-zinc-800 text-zinc-300 font-mono"
                          >
                            {skill.trim()}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Card Footer */}
                  <div className="pt-4 border-t border-white/[0.06] flex items-center justify-between text-xs text-zinc-400">
                    <div className="flex items-center gap-2">
                      <span className="flex items-center gap-1.5">
                        <Users className="w-3.5 h-3.5 text-zinc-500 shrink-0" />
                        <span>
                          {buildersCount} {buildersCount === 1 ? "builder" : "builders"}
                        </span>
                      </span>
                      <span>•</span>
                      <span>{timeAgo(project.createdAt)}</span>
                    </div>

                    <Link
                      href={workspaceHref}
                      className="text-xs font-semibold text-huddle-coral hover:text-pink-300 flex items-center gap-1.5 group-hover:translate-x-1 transition-all"
                    >
                      <span>Workspace</span>
                      <ArrowRight className="w-3 h-3" />
                    </Link>
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
