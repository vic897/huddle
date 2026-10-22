"use client";

import { useEffect, useState, Suspense } from "react";
import { postService, userService, UserProfile } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  FolderGit2,
  Users,
  Zap,
  Sparkles,
  Flame,
  CheckCircle2,
  MinusCircle,
  Clock,
  Code2,
  ExternalLink,
  MessageSquare,
  Radar,
  Loader2,
  Plus,
  ArrowRight,
  Bot,
  MapPin,
  ShieldCheck,
  Store,
  Terminal,
  CalendarClock,
} from "lucide-react";

interface PersonMatchResult {
  user: {
    id: string;
    displayName: string;
    bio: string | null;
    githubUrl: string | null;
    skills: (string | { name: string })[];
    availability: string[];
  };
  score: number;
  matchedSkills: string[];
}

function MatchedContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialTab = searchParams.get("tab") === "people" ? "people" : "projects";
  const initialPostId = searchParams.get("postId") || "";

  const [activeTab, setActiveTab] = useState<"projects" | "people">(initialTab);
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(null);

  // Matched Projects state
  const [projectMatches, setProjectMatches] = useState<any[]>([]);
  const [projectsLoading, setProjectsLoading] = useState(true);

  // Matched People state
  const [myProjects, setMyProjects] = useState<any[]>([]);
  const [selectedPostId, setSelectedPostId] = useState<string>(initialPostId);
  const [peopleMatches, setPeopleMatches] = useState<PersonMatchResult[]>([]);
  const [peopleLoading, setPeopleLoading] = useState(false);
  const [myProjectsLoading, setMyProjectsLoading] = useState(true);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    userService.getCurrentUser()
      .then((u) => setCurrentUser(u))
      .catch(() => {});

    fetchMatchedProjects();
    fetchMyProjects();
  }, [router]);

  useEffect(() => {
    const tabParam = searchParams.get("tab");
    if (tabParam === "people") {
      setActiveTab("people");
    } else if (tabParam === "projects") {
      setActiveTab("projects");
    }
    const postIdParam = searchParams.get("postId");
    if (postIdParam) {
      setSelectedPostId(postIdParam);
    }
  }, [searchParams]);

  useEffect(() => {
    if (activeTab === "people" && selectedPostId) {
      fetchMatchedPeople(selectedPostId);
    }
  }, [activeTab, selectedPostId]);

  const fetchMatchedProjects = async () => {
    try {
      setProjectsLoading(true);
      const data = await postService.getMatched();
      setProjectMatches(data || []);
    } catch (err) {
      console.error("Failed to fetch matched projects", err);
    } finally {
      setProjectsLoading(false);
    }
  };

  const fetchMyProjects = async () => {
    try {
      setMyProjectsLoading(true);
      const data = await postService.getMyPosts();
      const posts = data || [];
      setMyProjects(posts);
      if (posts.length > 0 && !selectedPostId) {
        setSelectedPostId(posts[0].id);
      }
    } catch (err) {
      console.error("Failed to fetch user projects", err);
    } finally {
      setMyProjectsLoading(false);
    }
  };

  const fetchMatchedPeople = async (postId: string) => {
    try {
      setPeopleLoading(true);
      const data = await postService.getMatchedPeople(postId);
      setPeopleMatches(data || []);
    } catch (err) {
      console.error("Failed to fetch matched people", err);
      setPeopleMatches([]);
    } finally {
      setPeopleLoading(false);
    }
  };

  const selectedProject = myProjects.find((p) => p.id === selectedPostId);

  // Computed match stats
  const totalMatchesCount = activeTab === "projects" ? projectMatches.length : peopleMatches.length;
  const highMatchesCount = activeTab === "projects"
    ? projectMatches.filter((p) => (p.score !== undefined ? p.score >= 0.8 : true)).length
    : peopleMatches.filter((p) => p.score >= 0.8).length;

  const userSkills = currentUser?.skills || [];
  const userAvailability = currentUser?.availability || [];

  const getVisualMeshClass = (idx: number) => {
    const meshes = ["mesh-1", "mesh-2", "mesh-3"];
    return meshes[idx % meshes.length];
  };

  const getVisualIcon = (idx: number) => {
    const icons = [Bot, MapPin, ShieldCheck, Store, Terminal, CalendarClock];
    const IconComp = icons[idx % icons.length];
    return <IconComp className="w-10 h-10 text-white/30 absolute inset-0 m-auto" />;
  };

  return (
    <div className="relative min-h-full pb-20">
      {/* Ambient Glowing Orbs */}
      <div className="ambient-orb bg-huddle-pink opacity-[0.05] w-[700px] h-[700px] -top-[10%] right-[10%]"></div>
      <div className="ambient-orb bg-huddle-purple opacity-[0.04] w-[600px] h-[600px] bottom-[20%] -left-[10%]"></div>

      <div className="max-w-[1400px] mx-auto w-full relative z-10 flex flex-col min-h-full">
        
        {/* Header Area */}
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-8 mb-10 animate-fade-in-up">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-white/10 bg-white/5 backdrop-blur-sm mb-4">
              <Zap className="w-3.5 h-3.5 text-huddle-coral" />
              <span className="text-xs font-semibold tracking-wide text-gray-300 uppercase">
                Intelligent Matching
              </span>
            </div>

            <h1 className="font-display font-extrabold text-4xl md:text-5xl text-white tracking-tight mb-3">
              Matching <span className="text-gradient">Hub.</span>
            </h1>
            <p className="text-gray-400 text-base md:text-lg font-medium max-w-xl leading-relaxed">
              Discover high-potential projects and people based on your specialized skills and availability.
            </p>
          </div>

          {/* Controls & Stats */}
          <div className="flex flex-col items-start lg:items-end gap-4">
            <div className="flex gap-4 text-sm font-medium">
              <div className="flex flex-col">
                <span className="text-white font-bold text-lg">{totalMatchesCount}</span>
                <span className="text-gray-500 text-xs">Total Matches</span>
              </div>
              <div className="w-px h-8 bg-white/10"></div>
              <div className="flex flex-col">
                <span className="text-huddle-coral font-bold text-lg">{highMatchesCount}</span>
                <span className="text-gray-500 text-xs">High Match</span>
              </div>
            </div>

            {/* Segmented Switcher */}
            <div className="segmented-control">
              <button
                onClick={() => setActiveTab("projects")}
                className={`segment-btn ${activeTab === "projects" ? "active" : ""}`}
              >
                <FolderGit2 className="w-4 h-4" /> Matched Projects
              </button>
              <button
                onClick={() => setActiveTab("people")}
                className={`segment-btn ${activeTab === "people" ? "active" : ""}`}
              >
                <Users className="w-4 h-4" /> Matched People
              </button>
            </div>
          </div>
        </div>

        {/* ================= MATCHED PROJECTS VIEW ================= */}
        {activeTab === "projects" && (
          <div className="w-full animate-fade-in-up" style={{ animationDelay: "0.1s" }}>
            
            {/* Match Insight Summary Bar */}
            <div className="flex items-center gap-3 mb-8 bg-white/5 border border-white/10 p-4 rounded-2xl backdrop-blur-md">
              <div className="w-10 h-10 rounded-xl bg-huddle-dark border border-white/10 flex items-center justify-center shrink-0">
                <Radar className="w-5 h-5 text-huddle-purple" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white">Why you&apos;re seeing these projects</h3>
                <p className="text-xs font-medium text-gray-400 mt-0.5">
                  {userSkills.length > 0
                    ? `We analyzed active projects looking for ${userSkills.slice(0, 3).join(", ")} and aligned availability.`
                    : "Discover active projects looking for skilled collaborators."}
                </p>
              </div>
            </div>

            {projectsLoading ? (
              <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                <Loader2 className="w-8 h-8 text-huddle-coral animate-spin mb-3" />
                <p className="text-sm font-medium">Discovering project matches...</p>
              </div>
            ) : projectMatches.length === 0 ? (
              <div className="dashboard-card p-12 text-center max-w-lg mx-auto my-12 border border-white/10">
                <Sparkles className="w-12 h-12 text-huddle-coral mx-auto mb-4" />
                <h3 className="text-lg font-bold text-white mb-2">No Matched Projects Yet</h3>
                <p className="text-xs text-gray-400 mb-6 leading-relaxed">
                  We couldn&apos;t find projects matching your current profile. Add more skills or update your availability to discover compatible teams.
                </p>
                <Link
                  href="/profile"
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-huddle-coral hover:bg-huddle-coral/90 text-white text-xs font-bold transition-all shadow-lg"
                >
                  Update Profile <ArrowRight className="w-4 h-4" />
                </Link>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-16">
                {projectMatches.map((result, idx) => {
                  const post = result.post || result;
                  const rawScore = result.score !== undefined ? result.score : 0.85;
                  const matchPct = Math.round(rawScore * 100);
                  const isHighMatch = matchPct >= 80;
                  const matchedSkills: string[] = result.matchedSkills || [];
                  const desiredSkills: string[] = Array.isArray(post.desiredSkills) ? post.desiredSkills : [];
                  const creatorName = post.author?.displayName || post.creator?.displayName || "Alex M.";
                  const creatorInitial = creatorName.charAt(0).toUpperCase();
                  const meshClass = getVisualMeshClass(idx);

                  return (
                    <div key={post.id || idx} className="match-card group">
                      
                      {/* Visual Header */}
                      <div className={`card-visual ${meshClass}`}>
                        {getVisualIcon(idx)}
                        <div className="absolute inset-0 bg-gradient-to-t from-huddle-charcoal to-transparent"></div>
                      </div>

                      {/* Floating Match Badge */}
                      <div className="relative">
                        <div className={`match-badge absolute top-[-14px] right-6 ${isHighMatch ? "high" : "med"} flex items-center gap-1.5`}>
                          {isHighMatch ? <Flame className="w-3.5 h-3.5" /> : <Sparkles className="w-3.5 h-3.5" />}
                          {matchPct}% Match
                        </div>
                      </div>

                      {/* Card Body */}
                      <div className="p-6 flex flex-col flex-1 bg-huddle-charcoal/30">
                        <h3 className="text-xl font-bold text-white mb-2 group-hover:text-huddle-coral transition-colors line-clamp-1">
                          {post.title}
                        </h3>
                        <p className="text-sm font-medium text-gray-400 mb-5 line-clamp-2 leading-relaxed">
                          {post.briefDescription || post.description || "Building an ambitious project on Huddle."}
                        </p>

                        {/* Explainable Match Reasoning */}
                        <div className="match-reasoning">
                          <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-2">
                            Why it matches
                          </div>
                          <ul className="space-y-1.5">
                            {matchedSkills.length > 0 ? (
                              <li className="text-xs text-gray-300 flex items-center gap-2">
                                <CheckCircle2 className="w-3.5 h-3.5 text-huddle-green flex-shrink-0" />
                                <span>
                                  <strong className="text-white font-semibold">
                                    {matchedSkills.slice(0, 2).join(", ")}
                                  </strong>{" "}
                                  matches your skillset
                                </span>
                              </li>
                            ) : (
                              <li className="text-xs text-gray-300 flex items-center gap-2">
                                <CheckCircle2 className="w-3.5 h-3.5 text-huddle-green flex-shrink-0" />
                                <span>Complementary team role fit</span>
                              </li>
                            )}
                            <li className="text-xs text-gray-300 flex items-center gap-2">
                              <CheckCircle2 className="w-3.5 h-3.5 text-huddle-green flex-shrink-0" />
                              <span>Availability criteria aligns</span>
                            </li>
                          </ul>
                        </div>

                        <div className="mt-auto">
                          {/* Required Skills */}
                          <div className="mb-5">
                            <div className="text-[10px] font-bold text-gray-500 uppercase tracking-widest mb-2">
                              Required Skills
                            </div>
                            <div className="flex flex-wrap gap-1.5">
                              {desiredSkills.length > 0 ? (
                                desiredSkills.map((skill: string) => {
                                  const isMatched = matchedSkills.some(
                                    (ms) => ms.toLowerCase() === skill.toLowerCase()
                                  );
                                  return (
                                    <span
                                      key={skill}
                                      className={`skill-pill ${isMatched ? "skill-matched" : "skill-missing"}`}
                                    >
                                      {skill.trim()}
                                    </span>
                                  );
                                })
                              ) : (
                                <span className="text-xs text-gray-500">General collaboration</span>
                              )}
                            </div>
                          </div>

                          {/* Footer */}
                          <div className="flex items-center justify-between pt-5 border-t border-white/5">
                            <div className="flex items-center gap-2.5">
                              <div className="w-8 h-8 rounded-full bg-huddle-dark border border-white/10 flex items-center justify-center text-xs font-bold text-gray-300 shadow-sm">
                                {creatorInitial}
                              </div>
                              <div>
                                <div className="text-xs font-bold text-white">{creatorName}</div>
                                <div className="text-[10px] text-gray-500 font-medium">
                                  Actively Recruiting
                                </div>
                              </div>
                            </div>

                            <Link
                              href={`/post/${post.id}`}
                              className="btn-glow px-4 py-2 rounded-xl text-xs font-bold text-white shadow-md flex items-center gap-1.5"
                            >
                              View Project
                            </Link>
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* ================= MATCHED PEOPLE VIEW ================= */}
        {activeTab === "people" && (
          <div className="w-full animate-fade-in-up" style={{ animationDelay: "0.1s" }}>
            
            {myProjectsLoading ? (
              <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                <Loader2 className="w-8 h-8 text-huddle-purple animate-spin mb-3" />
                <p className="text-sm font-medium">Loading your projects...</p>
              </div>
            ) : myProjects.length === 0 ? (
              <div className="dashboard-card rounded-2xl p-12 text-center max-w-xl mx-auto my-12 border border-white/10">
                <Sparkles className="w-12 h-12 text-huddle-purple mx-auto mb-4" />
                <h3 className="text-xl font-bold text-white mb-2">No Projects Created Yet</h3>
                <p className="text-gray-400 text-xs mb-6 leading-relaxed">
                  Create a project with desired skills and availability to find matching people from the community.
                </p>
                <Link
                  href="/create-post"
                  className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-huddle-purple hover:bg-huddle-purple/90 text-white font-bold text-xs transition-colors shadow-lg"
                >
                  <Plus className="w-4 h-4" /> Post a Project
                </Link>
              </div>
            ) : (
              <div>
                {/* Project Selector Bar */}
                <div className="mb-6 p-4 rounded-2xl bg-huddle-charcoal/60 border border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4 backdrop-blur-md">
                  <div className="flex items-center gap-3">
                    <label className="text-sm font-semibold text-gray-300 whitespace-nowrap">
                      Finding matches for:
                    </label>
                    <select
                      value={selectedPostId}
                      onChange={(e) => setSelectedPostId(e.target.value)}
                      className="bg-huddle-dark border border-white/10 rounded-xl px-3 py-2 text-sm text-white focus:border-huddle-purple outline-none"
                    >
                      {myProjects.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.title}
                        </option>
                      ))}
                    </select>
                  </div>

                  {selectedProject && (
                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      <span className="text-gray-400 font-medium">Required:</span>
                      {(Array.isArray(selectedProject.desiredSkills) ? selectedProject.desiredSkills : []).map((s: string) => (
                        <span key={s} className="px-2.5 py-0.5 rounded-lg bg-huddle-dark border border-white/10 text-gray-300 font-medium">
                          {s.trim()}
                        </span>
                      ))}
                      {(Array.isArray(selectedProject.desiredAvailability) ? selectedProject.desiredAvailability : []).map((a: string) => (
                        <span key={a} className="px-2.5 py-0.5 rounded-lg bg-huddle-purple/15 border border-huddle-purple/30 text-purple-300 font-medium">
                          {a.trim()}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Match Insight Summary Bar */}
                <div className="flex items-center gap-3 mb-8 bg-white/5 border border-white/10 p-4 rounded-2xl backdrop-blur-md">
                  <div className="w-10 h-10 rounded-xl bg-huddle-dark border border-white/10 flex items-center justify-center shrink-0">
                    <Users className="w-5 h-5 text-huddle-coral" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">
                      Builders matching {selectedProject ? `"${selectedProject.title}"` : "your project"}
                    </h3>
                    <p className="text-xs font-medium text-gray-400 mt-0.5">
                      We found {peopleMatches.length} candidates with compatible skills and availability.
                    </p>
                  </div>
                </div>

                {/* People Matches Grid */}
                {peopleLoading ? (
                  <div className="flex flex-col items-center justify-center py-20 text-gray-400">
                    <Loader2 className="w-8 h-8 text-huddle-purple animate-spin mb-3" />
                    <p className="text-sm font-medium">Finding matching candidates...</p>
                  </div>
                ) : peopleMatches.length === 0 ? (
                  <div className="dashboard-card rounded-2xl p-12 text-center border border-white/5 max-w-md mx-auto my-12">
                    <Users className="w-12 h-12 text-gray-500 mx-auto mb-3" />
                    <h4 className="text-base font-bold text-white mb-1">No Matching Collaborators</h4>
                    <p className="text-xs text-gray-400 leading-relaxed mb-4">
                      No candidates currently match the specific skill requirements for this project.
                    </p>
                    <Link
                      href="/my-projects"
                      className="inline-block text-xs font-bold text-huddle-purple hover:underline"
                    >
                      Edit Project Skills &rarr;
                    </Link>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-16">
                    {peopleMatches.map((result, idx) => {
                      const person = result.user;
                      const matchPct = Math.round((result.score || 0.8) * 100);
                      const isHighMatch = matchPct >= 85;
                      const isMedMatch = matchPct >= 70;
                      const matchedSkills = result.matchedSkills || [];
                      const name = person.displayName || "Collaborator";
                      const initial = name.charAt(0).toUpperCase();
                      const firstSkill = person.skills?.[0] ? (typeof person.skills[0] === "string" ? person.skills[0] : person.skills[0].name) : "";
                      const roleTitle = firstSkill ? `${firstSkill} Specialist` : "Software Engineer";

                      // Circumference for r=38 is 2 * PI * 38 = 238.7
                      const strokeDashoffset = Math.max(0, 238.7 * (1 - matchPct / 100));

                      const glowColor = idx % 2 === 0 ? "bg-huddle-coral" : "bg-huddle-purple";
                      const ringColor = isHighMatch ? "#FF6B6B" : isMedMatch ? "#9B51E0" : "#10B981";

                      return (
                        <div key={person.id || idx} className="match-card group p-6 relative">
                          <div className={`person-bg-glow ${glowColor}`}></div>

                          <div className="flex justify-between items-start mb-5 relative z-10">
                            <div className="relative">
                              {/* Match Ring SVG */}
                              <svg className="absolute -inset-2 w-[80px] h-[80px] -rotate-90">
                                <circle cx="40" cy="40" r="38" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="2" />
                                <circle
                                  cx="40"
                                  cy="40"
                                  r="38"
                                  fill="none"
                                  stroke={ringColor}
                                  strokeWidth="2.5"
                                  strokeDasharray="238.7"
                                  strokeDashoffset={strokeDashoffset}
                                  className="transition-all duration-1000 ease-out"
                                />
                              </svg>
                              <div className="w-16 h-16 rounded-full border-4 border-huddle-charcoal bg-huddle-dark flex items-center justify-center text-xl font-bold text-white relative z-10 shadow-lg">
                                {initial}
                              </div>
                              <div className="absolute bottom-0 right-0 w-3.5 h-3.5 bg-huddle-green border-2 border-huddle-charcoal rounded-full z-20"></div>
                            </div>

                            <div className={`match-badge ${isHighMatch ? "high" : isMedMatch ? "med" : "good"} flex items-center gap-1.5`}>
                              {isHighMatch ? <Flame className="w-3.5 h-3.5" /> : <Sparkles className="w-3.5 h-3.5" />}
                              {matchPct}% Match
                            </div>
                          </div>

                          <div className="relative z-10 mb-5">
                            <h3 className="text-xl font-bold text-white mb-1 group-hover:text-huddle-coral transition-colors line-clamp-1">
                              {name}
                            </h3>
                            <p className="text-xs font-bold text-huddle-coral uppercase tracking-widest mb-3">
                              {roleTitle}
                            </p>
                            <p className="text-sm font-medium text-gray-400 line-clamp-2 leading-relaxed">
                              {person.bio || "Passionate builder looking to collaborate on impactful software."}
                            </p>
                          </div>

                          {/* Explainable Match Area */}
                          <div className="match-reasoning relative z-10">
                            <div className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-2">
                              Why they match
                            </div>
                            <ul className="space-y-1.5">
                              {matchedSkills.length > 0 ? (
                                <li className="text-xs text-gray-300 flex items-center gap-2">
                                  <CheckCircle2 className="w-3.5 h-3.5 text-huddle-green flex-shrink-0" />
                                  <span>
                                    They offer{" "}
                                    <strong className="text-white font-semibold">
                                      {matchedSkills.slice(0, 2).join(", ")}
                                    </strong>
                                  </span>
                                </li>
                              ) : (
                                <li className="text-xs text-gray-300 flex items-center gap-2">
                                  <CheckCircle2 className="w-3.5 h-3.5 text-huddle-green flex-shrink-0" />
                                  <span>Relevant domain skillset overlap</span>
                                </li>
                              )}
                              <li className="text-xs text-gray-300 flex items-center gap-2">
                                <CheckCircle2 className="w-3.5 h-3.5 text-huddle-green flex-shrink-0" />
                                <span>Availability schedules match</span>
                              </li>
                            </ul>
                          </div>

                          <div className="mt-auto relative z-10">
                            <div className="mb-5">
                              <div className="text-[10px] font-bold text-gray-500 uppercase tracking-widest mb-2">
                                Their Skillset
                              </div>
                              <div className="flex flex-wrap gap-1.5">
                                {person.skills && person.skills.length > 0 ? (
                                  person.skills.slice(0, 4).map((sk, sIdx) => {
                                    const skName = typeof sk === "string" ? sk : sk?.name || "";
                                    return (
                                      <span key={skName || sIdx} className="skill-pill skill-neutral">
                                        {skName.trim()}
                                      </span>
                                    );
                                  })
                                ) : (
                                  <span className="text-xs text-gray-500">No skills listed</span>
                                )}
                              </div>
                            </div>

                            <div className="flex gap-3 pt-5 border-t border-white/5">
                              <Link
                                href={`/people/${person.id}`}
                                className="flex-1 btn-glow py-2.5 rounded-xl text-xs font-bold text-white shadow-md flex justify-center items-center gap-1.5 text-center"
                              >
                                <MessageSquare className="w-3.5 h-3.5" /> Message
                              </Link>
                              <Link
                                href={`/people/${person.id}`}
                                className="flex-1 bg-white/5 hover:bg-white/10 border border-white/10 py-2.5 rounded-xl text-xs font-bold text-white transition-colors text-center"
                              >
                                View Profile
                              </Link>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

      </div>
    </div>
  );
}

export default function MatchedPage() {
  return (
    <Suspense fallback={<div className="p-10 text-white flex items-center justify-center"><Loader2 className="w-6 h-6 animate-spin text-huddle-coral" /></div>}>
      <MatchedContent />
    </Suspense>
  );
}
