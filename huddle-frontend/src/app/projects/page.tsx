"use client";

import { useEffect, useState, useRef, useMemo } from "react";
import { postService, userService, UserProfile } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Search,
  Sparkles,
  SlidersHorizontal,
  Bot,
  MapPin,
  ShieldCheck,
  Store,
  Terminal,
  CalendarClock,
  Users,
  Code2,
  Database,
  PenTool,
  Layers,
  ChevronDown,
  X,
  Loader2,
  AlertCircle,
  ExternalLink,
} from "lucide-react";

// Project Category Filter definitions
const PROJECT_CATEGORIES = [
  "All Projects",
  "Actively Recruiting",
  "AI & Machine Learning",
  "Web3",
  "Campus Local",
];

// People Category Filter definitions
const PEOPLE_ROLES = [
  "All Roles",
  "Frontend Developers",
  "Backend Developers",
  "UI/UX Designers",
  "Data Scientists",
];

const COMMON_SKILLS = [
  "React",
  "Next.js",
  "TypeScript",
  "Node.js",
  "Python",
  "Java",
  "Spring Boot",
  "Rust",
  "UI/UX",
  "Figma",
  "PostgreSQL",
  "MongoDB",
  "AI/ML",
  "PyTorch",
];

const AVAILABILITY_OPTIONS = [
  "weekday-mornings",
  "weekday-afternoons",
  "weekday-evenings",
  "weekends",
  "flexible",
  "full-time",
  "part-time",
];

export default function BrowsePage() {
  const router = useRouter();

  // Mode: "projects" | "people"
  const [activeTab, setActiveTab] = useState<"projects" | "people">("projects");
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState("All Projects");
  const [activeRoleCategory, setActiveRoleCategory] = useState("All Roles");

  // Advanced Filters
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);
  const [selectedAvailability, setSelectedAvailability] = useState<string[]>([]);
  const [showFilterDropdown, setShowFilterDropdown] = useState(false);
  const filterDropdownRef = useRef<HTMLDivElement>(null);

  // Data states
  const [projects, setProjects] = useState<any[]>([]);
  const [people, setPeople] = useState<UserProfile[]>([]);
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(null);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingPeople, setLoadingPeople] = useState(false);
  const [visibleCount, setVisibleCount] = useState(6);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }

    userService.getCurrentUser()
      .then((user) => setCurrentUser(user))
      .catch(() => {});

    fetchProjects();
  }, [router]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (filterDropdownRef.current && !filterDropdownRef.current.contains(e.target as Node)) {
        setShowFilterDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Fetch projects when filters or query changes
  useEffect(() => {
    fetchProjects();
  }, [selectedSkills, selectedAvailability]);

  // Fetch people when activeTab becomes 'people'
  useEffect(() => {
    if (activeTab === "people" && people.length === 0) {
      fetchPeople();
    }
  }, [activeTab]);

  const fetchProjects = async () => {
    setLoadingProjects(true);
    try {
      const params: any = {};
      if (selectedSkills.length > 0) params.skills = selectedSkills;
      if (selectedAvailability.length > 0) params.availability = selectedAvailability;
      if (searchQuery.trim()) params.q = searchQuery.trim();

      const data = await postService.getPosts(params);
      setProjects(data || []);
    } catch (err) {
      console.error("Failed to load projects", err);
    } finally {
      setLoadingProjects(false);
    }
  };

  const fetchPeople = async () => {
    setLoadingPeople(true);
    try {
      const params: any = {};
      if (selectedSkills.length > 0) params.skills = selectedSkills;
      if (selectedAvailability.length > 0) params.availability = selectedAvailability;
      if (searchQuery.trim()) params.q = searchQuery.trim();

      const data = await userService.getUsers(params);
      setPeople(data || []);
    } catch (err) {
      console.error("Failed to load people", err);
    } finally {
      setLoadingPeople(false);
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (activeTab === "projects") {
      fetchProjects();
    } else {
      fetchPeople();
    }
  };

  const toggleSkill = (skill: string) => {
    const s = skill.toLowerCase();
    setSelectedSkills((prev) =>
      prev.includes(s) ? prev.filter((item) => item !== s) : [...prev, s]
    );
  };

  const toggleAvailability = (avail: string) => {
    const a = avail.toLowerCase();
    setSelectedAvailability((prev) =>
      prev.includes(a) ? prev.filter((item) => item !== a) : [...prev, a]
    );
  };

  const clearAllFilters = () => {
    setSelectedSkills([]);
    setSelectedAvailability([]);
    setSearchQuery("");
    setActiveCategory("All Projects");
    setActiveRoleCategory("All Roles");
  };

  // Filter projects by activeCategory
  const filteredProjects = useMemo(() => {
    let list = [...projects];

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (p) =>
          p.title?.toLowerCase().includes(q) ||
          p.description?.toLowerCase().includes(q) ||
          p.desiredSkills?.some((s: string) => s.toLowerCase().includes(q))
      );
    }

    if (activeCategory === "Actively Recruiting") {
      list = list.filter((p) => p.status === "OPEN" || !p.status);
    } else if (activeCategory === "AI & Machine Learning") {
      list = list.filter((p) =>
        p.desiredSkills?.some((s: string) =>
          ["ai", "ml", "python", "pytorch", "tensorflow", "llm", "data"].some((kw) =>
            s.toLowerCase().includes(kw)
          )
        ) || p.title?.toLowerCase().includes("ai") || p.description?.toLowerCase().includes("ai")
      );
    } else if (activeCategory === "Web3") {
      list = list.filter((p) =>
        p.desiredSkills?.some((s: string) =>
          ["web3", "solidity", "crypto", "blockchain", "rust"].some((kw) =>
            s.toLowerCase().includes(kw)
          )
        ) || p.title?.toLowerCase().includes("crypto") || p.description?.toLowerCase().includes("web3")
      );
    } else if (activeCategory === "Campus Local") {
      list = list.filter((p) =>
        p.title?.toLowerCase().includes("campus") ||
        p.description?.toLowerCase().includes("campus") ||
        p.description?.toLowerCase().includes("student")
      );
    }

    return list;
  }, [projects, activeCategory, searchQuery]);

  const getSkillName = (s: any): string => (typeof s === "string" ? s : s?.name || "");

  // Filter people by activeRoleCategory
  const filteredPeople = useMemo(() => {
    let list = [...people];

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (person) =>
          person.displayName?.toLowerCase().includes(q) ||
          person.bio?.toLowerCase().includes(q) ||
          person.skills?.some((s) => getSkillName(s).toLowerCase().includes(q))
      );
    }

    if (activeRoleCategory === "Frontend Developers") {
      list = list.filter((p) =>
        p.skills?.some((s) =>
          ["react", "next.js", "frontend", "vue", "angular", "css", "tailwind", "html", "javascript", "typescript"].some((kw) =>
            getSkillName(s).toLowerCase().includes(kw)
          )
        )
      );
    } else if (activeRoleCategory === "Backend Developers") {
      list = list.filter((p) =>
        p.skills?.some((s) =>
          ["node", "java", "spring", "python", "backend", "sql", "postgresql", "mongodb", "rust", "go", "api"].some((kw) =>
            getSkillName(s).toLowerCase().includes(kw)
          )
        )
      );
    } else if (activeRoleCategory === "UI/UX Designers") {
      list = list.filter((p) =>
        p.skills?.some((s) =>
          ["ui", "ux", "figma", "design", "prototyping", "research"].some((kw) =>
            getSkillName(s).toLowerCase().includes(kw)
          )
        )
      );
    } else if (activeRoleCategory === "Data Scientists") {
      list = list.filter((p) =>
        p.skills?.some((s) =>
          ["data", "ml", "ai", "python", "pandas", "pytorch", "tensorflow", "nlp"].some((kw) =>
            getSkillName(s).toLowerCase().includes(kw)
          )
        )
      );
    }

    return list;
  }, [people, activeRoleCategory, searchQuery]);

  // Icons and card decorations for project cards
  const getProjectCardStyle = (index: number) => {
    const styles = [
      {
        bg: "from-blue-900/60 to-purple-900/60",
        icon: Bot,
        iconColor: "text-blue-400",
        hoverText: "group-hover:text-blue-400",
      },
      {
        bg: "from-emerald-900/60 to-teal-900/60",
        icon: MapPin,
        iconColor: "text-emerald-400",
        hoverText: "group-hover:text-emerald-400",
      },
      {
        bg: "from-orange-900/60 to-red-900/60",
        icon: ShieldCheck,
        iconColor: "text-orange-400",
        hoverText: "group-hover:text-orange-400",
      },
      {
        bg: "from-pink-900/60 to-purple-900/60",
        icon: Store,
        iconColor: "text-pink-400",
        hoverText: "group-hover:text-pink-400",
      },
      {
        bg: "from-indigo-900/60 to-cyan-900/60",
        icon: Terminal,
        iconColor: "text-indigo-400",
        hoverText: "group-hover:text-indigo-400",
      },
      {
        bg: "from-yellow-900/60 to-amber-900/60",
        icon: CalendarClock,
        iconColor: "text-amber-400",
        hoverText: "group-hover:text-amber-400",
      },
    ];
    return styles[index % styles.length];
  };

  const primaryUserSkill = getSkillName(currentUser?.skills?.[0]) || "Frontend";

  // Split into Recommended and Primary Skill sections if on All Projects
  const recommendedSlice = filteredProjects.slice(0, 3);
  const secondarySlice = filteredProjects.slice(3, visibleCount);

  return (
    <div className="relative min-h-full pb-20">
      {/* Ambient Glowing Orbs */}
      <div className="ambient-orb bg-huddle-purple opacity-[0.06] w-[800px] h-[800px] -top-[20%] -right-[10%]"></div>
      <div className="ambient-orb bg-huddle-coral opacity-[0.04] w-[600px] h-[600px] bottom-[10%] -left-[10%]"></div>

      <div className="max-w-7xl mx-auto w-full relative z-10 flex flex-col min-h-full">
        
        {/* Header */}
        <header className="flex flex-col items-center justify-center mb-12 animate-fade-in-up mt-4 md:mt-8 text-center">
          <h1 className="font-display font-extrabold text-4xl md:text-5xl lg:text-6xl text-white tracking-tight mb-4">
            Discover something <br className="md:hidden" />
            <span className="text-gradient italic pr-2">worth building.</span>
          </h1>
          <p className="text-gray-400 text-base md:text-lg font-medium max-w-2xl mx-auto mb-10">
            Explore ambitious projects, find groundbreaking ideas, and connect with brilliant people who want to build the future.
          </p>

          {/* Prominent Search Bar */}
          <form onSubmit={handleSearchSubmit} className="w-full max-w-3xl mx-auto mb-8 relative">
            <div className="search-hero-input bg-huddle-charcoal/60 backdrop-blur-md rounded-2xl flex items-center px-4 py-3 md:py-4">
              <Search className="w-6 h-6 text-gray-400 ml-2 flex-shrink-0" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={
                  activeTab === "projects"
                    ? "Search projects, skills, or ideas..."
                    : "Search people, roles, or skills..."
                }
                className="w-full bg-transparent border-none outline-none text-white text-base md:text-lg px-4 placeholder-gray-500"
              />
              <button
                type="submit"
                className="bg-white/10 hover:bg-white/20 text-white rounded-xl px-5 py-2.5 text-sm font-semibold transition-colors flex items-center gap-2 flex-shrink-0"
              >
                Search
              </button>
            </div>
          </form>

          {/* Projects / People Toggle */}
          <div className="segmented-control">
            <button
              onClick={() => setActiveTab("projects")}
              className={`segment-btn ${activeTab === "projects" ? "active" : ""}`}
            >
              Projects
            </button>
            <button
              onClick={() => setActiveTab("people")}
              className={`segment-btn ${activeTab === "people" ? "active" : ""}`}
            >
              People
            </button>
          </div>
        </header>

        {/* ================= PROJECTS VIEW ================= */}
        {activeTab === "projects" && (
          <div className="w-full animate-fade-in-up" style={{ animationDelay: "0.1s" }}>
            
            {/* Filters Row */}
            <div className="flex items-center justify-between mb-8 pb-4 border-b border-white/5 relative">
              <div className="flex items-center gap-3 overflow-x-auto hide-scrollbar flex-1 pr-4">
                {PROJECT_CATEGORIES.map((cat) => (
                  <button
                    key={cat}
                    onClick={() => setActiveCategory(cat)}
                    className={`filter-chip px-4 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-all ${
                      activeCategory === cat ? "active font-semibold" : "text-gray-400"
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>

              {/* Advanced Filters Trigger */}
              <div className="relative" ref={filterDropdownRef}>
                <button
                  onClick={() => setShowFilterDropdown(!showFilterDropdown)}
                  className={`flex items-center gap-2 text-sm font-medium transition-colors px-3.5 py-1.5 rounded-lg border flex-shrink-0 ${
                    selectedSkills.length > 0 || selectedAvailability.length > 0
                      ? "bg-huddle-coral/15 border-huddle-coral/40 text-huddle-coral"
                      : "bg-white/5 border-white/5 text-gray-400 hover:text-white"
                  }`}
                >
                  <SlidersHorizontal className="w-4 h-4" />
                  Filters
                  {(selectedSkills.length > 0 || selectedAvailability.length > 0) && (
                    <span className="bg-huddle-coral text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full">
                      {selectedSkills.length + selectedAvailability.length}
                    </span>
                  )}
                </button>

                {/* Filters Dropdown Panel */}
                {showFilterDropdown && (
                  <div className="absolute right-0 top-full mt-2 w-80 md:w-96 bg-huddle-charcoal border border-white/10 rounded-2xl shadow-2xl p-5 z-50 backdrop-blur-xl">
                    <div className="flex items-center justify-between mb-4">
                      <h4 className="text-sm font-bold text-white">Filter by Skills & Availability</h4>
                      {(selectedSkills.length > 0 || selectedAvailability.length > 0) && (
                        <button
                          onClick={clearAllFilters}
                          className="text-xs text-huddle-coral hover:underline"
                        >
                          Reset all
                        </button>
                      )}
                    </div>

                    <div className="mb-4">
                      <div className="text-xs font-semibold text-gray-400 mb-2 uppercase tracking-wider">
                        Skills
                      </div>
                      <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto hide-scrollbar">
                        {COMMON_SKILLS.map((skill) => {
                          const active = selectedSkills.includes(skill.toLowerCase());
                          return (
                            <button
                              key={skill}
                              type="button"
                              onClick={() => toggleSkill(skill)}
                              className={`px-2.5 py-1 rounded-lg text-xs font-medium border transition-all ${
                                active
                                  ? "bg-huddle-coral/20 border-huddle-coral text-huddle-coral"
                                  : "bg-huddle-dark border-white/5 text-gray-400 hover:text-white hover:border-white/20"
                              }`}
                            >
                              {skill}
                            </button>
                          );
                        })}
                      </div>
                    </div>

                    <div>
                      <div className="text-xs font-semibold text-gray-400 mb-2 uppercase tracking-wider">
                        Availability
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {AVAILABILITY_OPTIONS.map((avail) => {
                          const active = selectedAvailability.includes(avail.toLowerCase());
                          return (
                            <button
                              key={avail}
                              type="button"
                              onClick={() => toggleAvailability(avail)}
                              className={`px-2.5 py-1 rounded-lg text-xs font-medium border transition-all ${
                                active
                                  ? "bg-huddle-purple/20 border-huddle-purple text-huddle-purple"
                                  : "bg-huddle-dark border-white/5 text-gray-400 hover:text-white hover:border-white/20"
                              }`}
                            >
                              {avail.replace("-", " ")}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {loadingProjects ? (
              <div className="flex items-center justify-center py-20">
                <Loader2 className="w-8 h-8 text-huddle-coral animate-spin" />
              </div>
            ) : filteredProjects.length === 0 ? (
              <div className="dashboard-card p-12 text-center max-w-md mx-auto my-12">
                <AlertCircle className="w-12 h-12 text-gray-500 mx-auto mb-3" />
                <h3 className="text-lg font-bold text-white mb-1">No Projects Found</h3>
                <p className="text-xs text-gray-400 mb-6">
                  Try adjusting your filters or search terms to discover more projects.
                </p>
                <button
                  onClick={clearAllFilters}
                  className="px-5 py-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-xs font-semibold text-white transition-colors"
                >
                  Clear Filters
                </button>
              </div>
            ) : (
              <>
                {/* Section: Recommended for you */}
                <section className="mb-12">
                  <div className="flex items-center justify-between mb-6">
                    <h2 className="text-xl font-bold text-white flex items-center gap-2">
                      <Sparkles className="w-5 h-5 text-huddle-coral" /> Recommended for you
                    </h2>
                  </div>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {recommendedSlice.map((project, idx) => {
                      const style = getProjectCardStyle(idx);
                      const IconComp = style.icon;
                      const creatorName = project.author?.displayName || project.creator?.displayName || "Alex M.";
                      const creatorInitial = creatorName.charAt(0).toUpperCase();

                      return (
                        <Link
                          key={project.id || idx}
                          href={`/post/${project.id}`}
                          className="browse-card group flex flex-col h-full cursor-pointer"
                        >
                          <div className="h-40 relative overflow-hidden bg-huddle-charcoal">
                            <div className={`absolute inset-0 bg-gradient-to-br ${style.bg} z-0`}></div>
                            <IconComp className={`absolute inset-0 m-auto w-12 h-12 ${style.iconColor} opacity-50 z-10 group-hover:scale-110 transition-transform duration-500`} />
                            <div className="absolute top-3 right-3 z-20">
                              <span className="px-2 py-1 rounded bg-black/60 backdrop-blur-md border border-white/10 text-[10px] font-bold text-huddle-coral uppercase tracking-wider flex items-center gap-1">
                                <span className="w-1.5 h-1.5 rounded-full bg-huddle-coral animate-pulse"></span> Open
                              </span>
                            </div>
                          </div>
                          <div className="p-5 flex flex-col flex-1">
                            <h3 className={`text-lg font-bold text-white mb-2 ${style.hoverText} transition-colors`}>
                              {project.title}
                            </h3>
                            <p className="text-sm font-medium text-gray-400 mb-4 line-clamp-2">
                              {project.description || "Building an exciting collaborative idea on Huddle."}
                            </p>
                            
                            <div className="mt-auto">
                              <div className="flex flex-wrap gap-2 mb-4">
                                {project.desiredSkills?.slice(0, 3).map((skill: string) => (
                                  <span
                                    key={skill}
                                    className="px-2 py-1 rounded bg-white/5 border border-white/10 text-[10px] font-medium text-gray-300"
                                  >
                                    {skill}
                                  </span>
                                ))}
                              </div>
                              <div className="flex items-center justify-between pt-4 border-t border-white/5">
                                <div className="flex items-center gap-2">
                                  <div className="w-6 h-6 rounded-full bg-huddle-dark border border-white/10 flex items-center justify-center text-[10px] font-bold text-gray-300">
                                    {creatorInitial}
                                  </div>
                                  <span className="text-xs font-medium text-gray-300">{creatorName}</span>
                                </div>
                                <div className="flex items-center gap-1.5 text-xs font-semibold text-gray-400">
                                  <Users className="w-3.5 h-3.5" /> 1/4
                                </div>
                              </div>
                            </div>
                          </div>
                        </Link>
                      );
                    })}
                  </div>
                </section>

                {/* Section: Secondary / Skill Based */}
                {secondarySlice.length > 0 && (
                  <section className="mb-12">
                    <div className="flex items-center justify-between mb-6">
                      <h2 className="text-xl font-bold text-white flex items-center gap-2">
                        Because you know <span className="text-huddle-pink">{primaryUserSkill}</span>
                      </h2>
                    </div>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                      {secondarySlice.map((project, idx) => {
                        const style = getProjectCardStyle(idx + 3);
                        const IconComp = style.icon;
                        const creatorName = project.author?.displayName || project.creator?.displayName || "Samira J.";
                        const creatorInitial = creatorName.charAt(0).toUpperCase();

                        return (
                          <Link
                            key={project.id || idx + 3}
                            href={`/post/${project.id}`}
                            className="browse-card group flex flex-col h-full cursor-pointer"
                          >
                            <div className="h-40 relative overflow-hidden bg-huddle-charcoal">
                              <div className={`absolute inset-0 bg-gradient-to-br ${style.bg} z-0`}></div>
                              <IconComp className={`absolute inset-0 m-auto w-12 h-12 ${style.iconColor} opacity-50 z-10 group-hover:scale-110 transition-transform duration-500`} />
                              <div className="absolute top-3 right-3 z-20">
                                <span className="px-2 py-1 rounded bg-black/60 backdrop-blur-md border border-white/10 text-[10px] font-bold text-huddle-coral uppercase tracking-wider flex items-center gap-1">
                                  <span className="w-1.5 h-1.5 rounded-full bg-huddle-coral animate-pulse"></span> Open
                                </span>
                              </div>
                            </div>
                            <div className="p-5 flex flex-col flex-1">
                              <h3 className={`text-lg font-bold text-white mb-2 ${style.hoverText} transition-colors`}>
                                {project.title}
                              </h3>
                              <p className="text-sm font-medium text-gray-400 mb-4 line-clamp-2">
                                {project.description || "Building an exciting collaborative idea on Huddle."}
                              </p>
                              
                              <div className="mt-auto">
                                <div className="flex flex-wrap gap-2 mb-4">
                                  <span className="px-2 py-1 rounded bg-huddle-pink/10 border border-huddle-pink/30 text-[10px] font-bold text-huddle-pink">
                                    {primaryUserSkill} Needed
                                  </span>
                                  {project.desiredSkills?.slice(0, 2).map((skill: string) => (
                                    <span
                                      key={skill}
                                      className="px-2 py-1 rounded bg-white/5 border border-white/10 text-[10px] font-medium text-gray-300"
                                    >
                                      {skill}
                                    </span>
                                  ))}
                                </div>
                                <div className="flex items-center justify-between pt-4 border-t border-white/5">
                                  <div className="flex items-center gap-2">
                                    <div className="w-6 h-6 rounded-full bg-huddle-dark border border-white/10 flex items-center justify-center text-[10px] font-bold text-gray-300">
                                      {creatorInitial}
                                    </div>
                                    <span className="text-xs font-medium text-gray-300">{creatorName}</span>
                                  </div>
                                  <div className="flex items-center gap-1.5 text-xs font-semibold text-gray-400">
                                    <Users className="w-3.5 h-3.5" /> 2/4
                                  </div>
                                </div>
                              </div>
                            </div>
                          </Link>
                        );
                      })}
                    </div>
                  </section>
                )}

                {/* Load More Button */}
                {visibleCount < filteredProjects.length && (
                  <div className="flex justify-center mt-8 mb-16">
                    <button
                      onClick={() => setVisibleCount((prev) => prev + 6)}
                      className="px-6 py-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-sm font-semibold text-white transition-colors"
                    >
                      Load More Projects
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* ================= PEOPLE VIEW (ONLY WHEN PEOPLE TOGGLED) ================= */}
        {activeTab === "people" && (
          <div className="w-full animate-fade-in-up" style={{ animationDelay: "0.1s" }}>
            
            {/* Filters Row */}
            <div className="flex items-center justify-between mb-8 pb-4 border-b border-white/5">
              <div className="flex items-center gap-3 overflow-x-auto hide-scrollbar flex-1 pr-4">
                {PEOPLE_ROLES.map((role) => (
                  <button
                    key={role}
                    onClick={() => setActiveRoleCategory(role)}
                    className={`filter-chip px-4 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-all ${
                      activeRoleCategory === role ? "active font-semibold" : "text-gray-400"
                    }`}
                  >
                    {role}
                  </button>
                ))}
              </div>
            </div>

            {loadingPeople ? (
              <div className="flex items-center justify-center py-20">
                <Loader2 className="w-8 h-8 text-huddle-purple animate-spin" />
              </div>
            ) : filteredPeople.length === 0 ? (
              <div className="dashboard-card p-12 text-center max-w-md mx-auto my-12">
                <Users className="w-12 h-12 text-gray-500 mx-auto mb-3" />
                <h3 className="text-lg font-bold text-white mb-1">No Collaborators Found</h3>
                <p className="text-xs text-gray-400 mb-6">
                  Try clearing your search query or selecting a different role.
                </p>
                <button
                  onClick={clearAllFilters}
                  className="px-5 py-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-xs font-semibold text-white transition-colors"
                >
                  Clear Filters
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-16">
                {filteredPeople.map((person, pIdx) => {
                  const name = person.displayName || "Collaborator";
                  const initial = name.charAt(0).toUpperCase();
                  const firstSkill = getSkillName(person.skills?.[0]);
                  const role = firstSkill ? `${firstSkill} Specialist` : "Developer";
                  const matchColors = [
                    { border: "group-hover:border-huddle-blue", text: "group-hover:text-huddle-blue", badgeText: "text-huddle-blue", pct: "95%" },
                    { border: "group-hover:border-huddle-pink", text: "group-hover:text-huddle-pink", badgeText: "text-huddle-pink", pct: "88%" },
                    { border: "group-hover:border-huddle-amber", text: "group-hover:text-huddle-amber", badgeText: "text-huddle-amber", pct: "82%" },
                    { border: "group-hover:border-huddle-purple", text: "group-hover:text-huddle-purple", badgeText: "text-huddle-purple", pct: "75%" },
                  ];
                  const mStyle = matchColors[pIdx % matchColors.length];

                  return (
                    <div
                      key={person.id || pIdx}
                      className="browse-card p-6 flex flex-col items-center text-center group cursor-pointer"
                    >
                      <div className="relative mb-4">
                        <div className={`w-20 h-20 rounded-full border-2 border-white/10 bg-huddle-dark flex items-center justify-center text-2xl font-bold text-white ${mStyle.border} transition-colors`}>
                          {initial}
                        </div>
                        <div className={`absolute -top-2 -right-2 bg-huddle-dark border border-white/10 ${mStyle.badgeText} text-[9px] font-bold px-2 py-0.5 rounded-full shadow-sm`}>
                          {mStyle.pct} Match
                        </div>
                      </div>
                      <h3 className={`text-lg font-bold text-white mb-1 ${mStyle.text} transition-colors`}>
                        {name}
                      </h3>
                      <div className="text-xs font-semibold text-gray-400 mb-3">{role}</div>
                      <p className="text-xs text-gray-500 mb-5 line-clamp-3">
                        {person.bio || "Passionate builder looking to collaborate on impactful software."}
                      </p>
                      
                      <div className="flex flex-wrap justify-center gap-1.5 mb-6 mt-auto">
                        {person.skills?.slice(0, 3).map((sk, sIdx) => {
                          const sName = getSkillName(sk);
                          return (
                            <span
                              key={sName || sIdx}
                              className="px-2 py-1 rounded bg-white/5 border border-white/10 text-[10px] font-medium text-gray-300"
                            >
                              {sName}
                            </span>
                          );
                        })}
                      </div>
                      
                      <Link
                        href={`/people/${person.id}`}
                        className="w-full py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-white transition-colors block text-center"
                      >
                        View Profile
                      </Link>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

      </div>
    </div>
  );
}
