"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  userService,
  collaborationService,
  postService,
  notificationService,
  requestService,
  Collaboration,
  Notification as HuddleNotification,
  UserProfile,
} from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import {
  Plus,
  Search,
  Compass,
  MessageSquare,
  Calendar,
  Bell,
  ArrowRight,
  Bot,
  Activity,
  MapPin,
  Check,
  Zap,
  Sparkles,
  Database,
  PenTool,
  Layers,
  Code2,
  Users,
  MoreHorizontal,
  FolderGit2,
} from "lucide-react";

export default function HomePage() {
  const router = useRouter();
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(null);
  const [collaborations, setCollaborations] = useState<Collaboration[]>([]);
  const [myPosts, setMyPosts] = useState<any[]>([]);
  const [notifications, setNotifications] = useState<HuddleNotification[]>([]);
  const [receivedRequests, setReceivedRequests] = useState<any[]>([]);
  const [recommendedPosts, setRecommendedPosts] = useState<any[]>([]);
  const [unreadMsgCount, setUnreadMsgCount] = useState<number>(0);
  const [unreadNotifCount, setUnreadNotifCount] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }

    const loadDashboardData = async () => {
      try {
        const [
          userRes,
          collabRes,
          myPostsRes,
          notifRes,
          reqRes,
          matchedRes,
          unreadMsgRes,
          unreadNotifRes,
        ] = await Promise.allSettled([
          userService.getCurrentUser(),
          collaborationService.getMyCollaborations(),
          postService.getMyPosts(),
          notificationService.getNotifications(),
          requestService.getMyRequests("received"),
          postService.getMatched(),
          requestService.getUnreadMessageCount(),
          notificationService.getUnreadCount(),
        ]);

        if (userRes.status === "fulfilled") setCurrentUser(userRes.value);
        if (collabRes.status === "fulfilled") setCollaborations(collabRes.value || []);
        if (myPostsRes.status === "fulfilled") setMyPosts(myPostsRes.value || []);
        if (notifRes.status === "fulfilled") setNotifications(notifRes.value || []);
        if (reqRes.status === "fulfilled") setReceivedRequests(reqRes.value || []);
        if (matchedRes.status === "fulfilled" && matchedRes.value?.length > 0) {
          setRecommendedPosts(matchedRes.value);
        } else {
          // Fallback to top public posts if no matched yet
          try {
            const fallbackPosts = await postService.getPosts();
            setRecommendedPosts(fallbackPosts?.slice(0, 3) || []);
          } catch {}
        }
        if (unreadMsgRes.status === "fulfilled") setUnreadMsgCount(unreadMsgRes.value || 0);
        if (unreadNotifRes.status === "fulfilled") setUnreadNotifCount(unreadNotifRes.value || 0);
      } catch (err) {
        console.error("Failed to load dashboard data", err);
      } finally {
        setLoading(false);
      }
    };

    loadDashboardData();
  }, [router]);

  const firstName = currentUser?.displayName?.trim()?.split(" ")[0] || "Builder";
  
  // Format current date
  const todayFormatted = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date());

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return "Good morning";
    if (hour < 18) return "Good afternoon";
    return "Good evening";
  };

  const getRelativeTime = (dateStr?: string) => {
    if (!dateStr) return "Recently";
    try {
      const now = new Date();
      const past = new Date(dateStr);
      const diffMs = now.getTime() - past.getTime();
      const diffHrs = Math.floor(diffMs / (1000 * 60 * 60));
      if (diffHrs < 1) return "Just now";
      if (diffHrs < 24) return `${diffHrs} hour${diffHrs > 1 ? "s" : ""} ago`;
      const diffDays = Math.floor(diffHrs / 24);
      if (diffDays === 1) return "Yesterday";
      if (diffDays < 7) return `${diffDays} days ago`;
      return past.toLocaleDateString();
    } catch {
      return "Recently";
    }
  };

  return (
    <div className="relative min-h-full pb-20">
      {/* Ambient Glowing Orbs */}
      <div className="ambient-orb bg-huddle-purple opacity-[0.05] w-[700px] h-[700px] -top-[10%] -right-[5%]"></div>
      <div className="ambient-orb bg-huddle-coral opacity-[0.03] w-[600px] h-[600px] bottom-[20%] -left-[10%]"></div>

      <div className="max-w-7xl mx-auto w-full relative z-10">
        
        {/* Header greeting */}
        <header className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-10 animate-fade-in-up">
          <div>
            <h1 className="font-display font-extrabold text-3xl md:text-5xl text-white tracking-tight mb-2 flex items-center gap-3">
              {getGreeting()}, <span className="text-gradient">{firstName}.</span>
            </h1>
            <p className="text-gray-400 text-base md:text-lg font-medium">
              Here&apos;s what&apos;s happening in your Huddle today.
            </p>
          </div>
          
          <div className="flex items-center gap-4 bg-huddle-charcoal/50 border border-white/10 rounded-2xl p-2 backdrop-blur-md self-start md:self-auto">
            <div className="flex items-center gap-2 px-3 py-1.5">
              <Calendar className="w-4 h-4 text-gray-400" />
              <span className="text-sm font-semibold text-gray-200">{todayFormatted}</span>
            </div>
            <div className="w-px h-6 bg-white/10"></div>
            <Link
              href="/notifications"
              className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-gray-300 hover:text-white transition-colors relative group"
            >
              <Bell className="w-4 h-4" />
              {unreadNotifCount > 0 && (
                <span className="absolute top-1.5 right-2 w-2 h-2 bg-huddle-coral rounded-full border border-huddle-charcoal"></span>
              )}
            </Link>
          </div>
        </header>

        {/* Quick Actions */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-12 animate-fade-in-up" style={{ animationDelay: "0.1s" }}>
          
          {/* Start a Project */}
          <Link
            href="/create-post"
            className="dashboard-card interactive p-5 flex flex-col justify-between h-32 relative overflow-hidden group text-left border-huddle-coral/30 hover:border-huddle-coral/60"
          >
            <div className="absolute inset-0 bg-gradient-to-br from-huddle-coral/10 to-huddle-purple/10 opacity-50 group-hover:opacity-100 transition-opacity"></div>
            <div className="w-10 h-10 rounded-full bg-huddle-coral/20 border border-huddle-coral/30 flex items-center justify-center text-huddle-coral relative z-10 group-hover:scale-110 transition-transform">
              <Plus className="w-5 h-5" />
            </div>
            <div className="relative z-10">
              <h3 className="text-base font-bold text-white mb-0.5">Start a Project</h3>
              <p className="text-[11px] font-medium text-gray-400">Draft your idea and build a team.</p>
            </div>
          </Link>

          {/* Find Collaborators */}
          <Link
            href="/people"
            className="dashboard-card interactive p-5 flex flex-col justify-between h-32 group text-left"
          >
            <div className="w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-gray-300 group-hover:text-blue-400 group-hover:bg-blue-400/10 group-hover:border-blue-400/30 transition-all">
              <Search className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white mb-0.5">Find Collaborators</h3>
              <p className="text-[11px] font-medium text-gray-400">Search for specific skills.</p>
            </div>
          </Link>

          {/* Browse Projects */}
          <Link
            href="/projects"
            className="dashboard-card interactive p-5 flex flex-col justify-between h-32 group text-left"
          >
            <div className="w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-gray-300 group-hover:text-green-400 group-hover:bg-green-400/10 group-hover:border-green-400/30 transition-all">
              <Compass className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white mb-0.5">Browse Projects</h3>
              <p className="text-[11px] font-medium text-gray-400">Discover new ideas to join.</p>
            </div>
          </Link>

          {/* Open Messages */}
          <Link
            href="/chat"
            className="dashboard-card interactive p-5 flex flex-col justify-between h-32 group text-left"
          >
            <div className="w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-gray-300 group-hover:text-huddle-pink group-hover:bg-huddle-pink/10 group-hover:border-huddle-pink/30 transition-all relative">
              <MessageSquare className="w-5 h-5" />
              {unreadMsgCount > 0 && (
                <span className="absolute -top-1 -right-1 w-3 h-3 bg-huddle-pink rounded-full border-2 border-huddle-charcoal"></span>
              )}
            </div>
            <div>
              <h3 className="text-base font-bold text-white mb-0.5">Open Messages</h3>
              <p className="text-[11px] font-medium text-gray-400">
                {unreadMsgCount > 0 ? `${unreadMsgCount} unread message${unreadMsgCount > 1 ? "s" : ""}.` : "Collaborate with your team."}
              </p>
            </div>
          </Link>

        </section>

        {/* Dashboard Columns Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* LEFT COLUMN (Span 8) */}
          <div className="lg:col-span-8 flex flex-col gap-10">
            
            {/* Active Huddles */}
            <section className="animate-fade-in-up" style={{ animationDelay: "0.2s" }}>
              <div className="flex items-center justify-between mb-5 px-1">
                <div>
                  <h2 className="text-xl font-bold text-white tracking-wide">Active Huddles</h2>
                  <p className="text-sm font-medium text-gray-400 mt-0.5">Projects you are currently collaborating on.</p>
                </div>
              </div>

              <div className="space-y-4">
                {collaborations.length > 0 ? (
                  collaborations.map((collab, idx) => {
                    const myRole = collab.members?.find((m) => m.user?.id === currentUser?.id)?.role || "Member";
                    const isOwner = myRole === "OWNER";
                    const roleLabel = isOwner ? "Project Lead" : "Contributor";
                    const memberCount = collab.members?.length || 1;
                    const displayMembers = collab.members?.slice(0, 2) || [];
                    const extraMembers = Math.max(0, memberCount - 2);
                    const colorVariants = [
                      { bg: "from-purple-900/40 to-black/40", text: "text-purple-400", drop: "rgba(168,85,247,0.5)", dot: "bg-huddle-green shadow-[0_0_8px_#10B981]" },
                      { bg: "from-green-900/40 to-black/40", text: "text-green-400", drop: "rgba(74,222,128,0.5)", dot: "bg-gray-500" },
                      { bg: "from-blue-900/40 to-black/40", text: "text-blue-400", drop: "rgba(59,130,246,0.5)", dot: "bg-blue-400 shadow-[0_0_8px_#3B82F6]" },
                    ];
                    const variant = colorVariants[idx % colorVariants.length];

                    return (
                      <div
                        key={collab.id}
                        className="dashboard-card p-6 flex flex-col md:flex-row md:items-center justify-between gap-6 group hover:border-white/15"
                      >
                        <div className="flex items-start gap-4 flex-1">
                          <div className={`w-14 h-14 rounded-2xl bg-gradient-to-br ${variant.bg} border border-white/10 flex items-center justify-center flex-shrink-0 shadow-inner`}>
                            <Bot className={`w-7 h-7 ${variant.text}`} />
                          </div>
                          <div>
                            <div className="flex items-center gap-3 mb-1">
                              <h3 className={`text-lg font-bold text-white group-hover:${variant.text} transition-colors`}>
                                {collab.projectTitle}
                              </h3>
                              <span className="px-2 py-0.5 rounded bg-white/5 border border-white/10 text-[10px] font-bold text-gray-300">
                                {roleLabel}
                              </span>
                            </div>
                            <p className="text-sm font-medium text-gray-400 mb-4 line-clamp-1">
                              {collab.projectBriefDescription || "Collaborative project workspace on Huddle."}
                            </p>
                            
                            <div className="flex items-center gap-4 text-xs font-semibold text-gray-500">
                              <div className="flex items-center gap-1.5">
                                <div className={`w-2 h-2 rounded-full ${variant.dot}`}></div> Active
                              </div>
                              <div className="flex items-center gap-1.5">
                                <MessageSquare className="w-3.5 h-3.5" /> Workspace Ready
                              </div>
                            </div>
                          </div>
                        </div>
                        
                        <div className="flex flex-row md:flex-col items-center md:items-end justify-between gap-4 md:gap-3 flex-shrink-0 border-t md:border-t-0 border-white/5 pt-4 md:pt-0">
                          <div className="flex -space-x-2.5">
                            {displayMembers.map((m, mIdx) => (
                              <div
                                key={m.id || mIdx}
                                className="w-8 h-8 rounded-full border-2 border-huddle-charcoal bg-huddle-dark flex items-center justify-center text-[10px] font-bold text-gray-300 relative z-20 overflow-hidden"
                              >
                                {m.user?.displayName ? m.user.displayName.charAt(0).toUpperCase() : "U"}
                              </div>
                            ))}
                            {extraMembers > 0 && (
                              <div className="w-8 h-8 rounded-full border-2 border-huddle-charcoal bg-huddle-dark flex items-center justify-center text-[10px] font-bold text-gray-300 z-10 relative">
                                +{extraMembers}
                              </div>
                            )}
                          </div>
                          <Link
                            href={`/collaborations/${collab.id}`}
                            className="px-5 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-sm font-bold text-white transition-colors flex items-center gap-2 whitespace-nowrap"
                          >
                            Workspace <ArrowRight className="w-4 h-4 text-gray-400" />
                          </Link>
                        </div>
                      </div>
                    );
                  })
                ) : (
                  <div className="dashboard-card p-8 flex flex-col items-center justify-center text-center">
                    <div className="w-12 h-12 rounded-full bg-white/5 flex items-center justify-center text-gray-400 mb-3">
                      <Users className="w-6 h-6" />
                    </div>
                    <h3 className="text-base font-bold text-white mb-1">No Active Huddles Yet</h3>
                    <p className="text-xs text-gray-400 max-w-sm mb-4">
                      Join a project from Browse or accept a collaborator request to build your first team workspace.
                    </p>
                    <Link
                      href="/projects"
                      className="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/15 text-xs font-semibold text-white transition-colors flex items-center gap-1.5"
                    >
                      Browse Projects <ArrowRight className="w-3.5 h-3.5" />
                    </Link>
                  </div>
                )}
              </div>
            </section>

            {/* My Projects */}
            <section className="animate-fade-in-up" style={{ animationDelay: "0.3s" }}>
              <div className="flex items-center justify-between mb-5 px-1">
                <div>
                  <h2 className="text-xl font-bold text-white tracking-wide">My Projects</h2>
                  <p className="text-sm font-medium text-gray-400 mt-0.5">Projects created and managed by you.</p>
                </div>
                <Link
                  href="/my-projects"
                  className="text-sm font-semibold text-gray-400 hover:text-white transition-colors flex items-center gap-1"
                >
                  Manage All <ArrowRight className="w-4 h-4" />
                </Link>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                
                {/* User's Created Projects */}
                {myPosts.slice(0, 1).map((proj) => (
                  <div
                    key={proj.id}
                    className="dashboard-card p-5 group flex flex-col h-full border border-white/5 bg-black/20"
                  >
                    <div className="flex items-start justify-between mb-3">
                      <div className="w-10 h-10 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center">
                        <FolderGit2 className="w-5 h-5 text-blue-400" />
                      </div>
                      <Link
                        href={`/post/${proj.id}`}
                        className="text-gray-500 hover:text-white p-1 transition-colors"
                      >
                        <MoreHorizontal className="w-4 h-4" />
                      </Link>
                    </div>
                    <h3 className="text-base font-bold text-white mb-1 group-hover:text-blue-400 transition-colors">
                      {proj.title}
                    </h3>
                    <p className="text-xs font-medium text-gray-400 mb-5 line-clamp-2">
                      {proj.description || "Project created by you on Huddle."}
                    </p>
                    
                    <div className="mt-auto border-t border-white/5 pt-4">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-[10px] font-bold text-huddle-coral uppercase tracking-widest bg-huddle-coral/10 px-2 py-0.5 rounded border border-huddle-coral/20">
                          Recruiting
                        </span>
                        <span className="text-xs font-semibold text-gray-400">Active</span>
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {proj.desiredSkills?.slice(0, 3).map((skill: string) => (
                          <span
                            key={skill}
                            className="px-2 py-1 rounded bg-huddle-dark border border-white/5 text-[10px] font-medium text-gray-300"
                          >
                            Need {skill}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                ))}

                {/* Second slot or Draft Idea card */}
                <Link
                  href="/create-post"
                  className="dashboard-card p-5 flex flex-col items-center justify-center text-center h-full border border-dashed border-white/10 bg-transparent opacity-80 hover:opacity-100 hover:border-white/20 hover:bg-white/5 transition-all cursor-pointer min-h-[220px]"
                >
                  <div className="w-12 h-12 rounded-full bg-white/5 flex items-center justify-center mb-4">
                    <Plus className="w-5 h-5 text-gray-400" />
                  </div>
                  <h3 className="text-sm font-bold text-white mb-1">Draft a New Idea</h3>
                  <p className="text-xs font-medium text-gray-400 max-w-[200px]">
                    Have another idea? Start drafting it now to find your next team.
                  </p>
                </Link>

              </div>
            </section>
          </div>

          {/* RIGHT COLUMN (Span 4) */}
          <div className="lg:col-span-4 flex flex-col gap-10">
            
            {/* Recent Activity */}
            <section className="animate-fade-in-up" style={{ animationDelay: "0.3s" }}>
              <div className="flex items-center justify-between mb-5 px-1">
                <h2 className="text-lg font-bold text-white tracking-wide">Recent Activity</h2>
              </div>

              <div className="dashboard-card bg-black/20 overflow-hidden">
                <div className="flex flex-col">
                  
                  {/* Pending collaboration request items */}
                  {receivedRequests.filter(r => r.status === "PENDING").slice(0, 1).map((req) => (
                    <div
                      key={req.id}
                      className="p-4 border-b border-white/5 flex items-start gap-3 hover:bg-white/5 transition-colors bg-huddle-coral/5 relative"
                    >
                      <div className="absolute left-0 top-0 bottom-0 w-1 bg-huddle-coral"></div>
                      <div className="w-8 h-8 rounded-full bg-huddle-coral/10 border border-huddle-coral/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                        <Zap className="w-4 h-4 text-huddle-coral" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-200 leading-snug">
                          <span className="font-bold text-white">{req.sender?.displayName || "A collaborator"}</span> sent a request to join <span className="font-bold text-white">{req.post?.title || "your project"}</span>.
                        </p>
                        <span className="text-[10px] font-semibold text-gray-500 mt-1 block mb-2">
                          {getRelativeTime(req.createdAt)}
                        </span>
                        <Link
                          href="/requests"
                          className="inline-block text-xs font-bold text-huddle-coral hover:text-white transition-colors bg-huddle-coral/10 px-2 py-1 rounded border border-huddle-coral/20"
                        >
                          Review Request
                        </Link>
                      </div>
                    </div>
                  ))}

                  {/* Notification items */}
                  {notifications.slice(0, 3).map((notif, nIdx) => (
                    <div
                      key={notif.id || nIdx}
                      className="p-4 border-b last:border-b-0 border-white/5 flex items-start gap-3 hover:bg-white/5 transition-colors"
                    >
                      <div className="w-8 h-8 rounded-full bg-blue-500/10 border border-blue-500/20 flex items-center justify-center flex-shrink-0 mt-0.5">
                        <Check className="w-4 h-4 text-blue-400" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-200 leading-snug">
                          {notif.message}
                        </p>
                        <span className="text-[10px] font-semibold text-gray-500 mt-1 block">
                          {getRelativeTime(notif.createdAt)}
                        </span>
                      </div>
                    </div>
                  ))}

                  {receivedRequests.length === 0 && notifications.length === 0 && (
                    <div className="p-6 text-center text-xs text-gray-500">
                      No recent activity yet.
                    </div>
                  )}

                </div>
              </div>
            </section>

            {/* Recommended */}
            <section className="animate-fade-in-up" style={{ animationDelay: "0.4s" }}>
              <div className="flex items-center justify-between mb-5 px-1">
                <div>
                  <h2 className="text-lg font-bold text-white tracking-wide">Recommended</h2>
                  <p className="text-xs font-medium text-gray-400 mt-0.5">Based on your skills.</p>
                </div>
              </div>

              <div className="space-y-3">
                {recommendedPosts.slice(0, 2).map((rec, rIdx) => {
                  const neededSkill = rec.desiredSkills?.[0] || "Collaborators";
                  const icons = [Database, PenTool, Sparkles];
                  const IconComp = icons[rIdx % icons.length];
                  const gradients = [
                    "from-amber-900/60 to-black/60 text-amber-400",
                    "from-pink-900/60 to-black/60 text-pink-400",
                    "from-purple-900/60 to-black/60 text-purple-400",
                  ];
                  const grad = gradients[rIdx % gradients.length];

                  return (
                    <Link
                      key={rec.id || rIdx}
                      href={`/post/${rec.id}`}
                      className="dashboard-card p-4 flex gap-4 group cursor-pointer border-white/5 bg-black/20 hover:bg-white/5 transition-all block"
                    >
                      <div className="w-12 h-12 rounded-xl bg-huddle-charcoal overflow-hidden flex-shrink-0 relative">
                        <div className={`absolute inset-0 bg-gradient-to-br ${grad.split(" ")[0]}`}></div>
                        <IconComp className={`absolute inset-0 m-auto w-5 h-5 ${grad.split(" ")[1]} opacity-60`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <h4 className="text-sm font-bold text-white truncate group-hover:text-amber-400 transition-colors mb-1">
                          {rec.title}
                        </h4>
                        <div className="flex items-center gap-2 mb-2">
                          <span className="px-2 py-0.5 rounded bg-white/5 border border-white/10 text-[9px] font-bold text-gray-300">
                            Needs {neededSkill}
                          </span>
                          <span className="text-[10px] font-bold text-gray-500">
                            90% Match
                          </span>
                        </div>
                      </div>
                    </Link>
                  );
                })}

                <Link
                  href="/projects"
                  className="w-full py-3 rounded-xl bg-transparent border border-white/10 text-xs font-bold text-gray-400 hover:text-white hover:bg-white/5 transition-colors block text-center"
                >
                  Explore More Projects
                </Link>

              </div>
            </section>

          </div>
        </div>
        
      </div>
    </div>
  );
}
