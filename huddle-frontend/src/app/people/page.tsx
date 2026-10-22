"use client";

import { useEffect, useState, useRef, useMemo } from "react";
import { userService, UserProfile, SkillItem } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Users,
  Clock,
  Code2,
  Search,
  AlertCircle,
  Loader2,
  ChevronDown,
  X,
  Zap,
  ArrowRight,
  ArrowUpDown,
  Sparkles,
  SlidersHorizontal,
} from "lucide-react";

// Common availability slots
const AVAILABILITY_OPTIONS = [
  "weekday-mornings",
  "weekday-evenings",
  "weekdays",
  "weekends",
  "flexible",
  "full-time",
  "part-time",
];

// Helper to extract skill name string
const getSkillName = (s: any): string => (typeof s === "string" ? s : s?.name || "");

// Helper to extract skill proficiency
const getSkillProficiency = (s: any): string => (typeof s === "object" && s?.proficiency ? s.proficiency : "INTERMEDIATE");

// Theme palette presets for ambient cards
const THEME_PALETTES = [
  {
    blobBg: "bg-blue-500",
    glowBg: "bg-blue-500",
    hoverText: "group-hover:text-blue-400",
    avatarGradient: "from-blue-600 to-indigo-700",
    accentColor: "text-blue-400",
  },
  {
    blobBg: "bg-amber-500",
    glowBg: "bg-amber-500",
    hoverText: "group-hover:text-amber-400",
    avatarGradient: "from-amber-600 to-orange-700",
    accentColor: "text-amber-400",
  },
  {
    blobBg: "bg-huddle-purple",
    glowBg: "bg-huddle-purple",
    hoverText: "group-hover:text-huddle-purple",
    avatarGradient: "from-purple-600 to-indigo-800",
    accentColor: "text-huddle-purple",
  },
  {
    blobBg: "bg-cyan-500",
    glowBg: "bg-cyan-500",
    hoverText: "group-hover:text-cyan-400",
    avatarGradient: "from-cyan-600 to-teal-700",
    accentColor: "text-cyan-400",
  },
  {
    blobBg: "bg-orange-500",
    glowBg: "bg-orange-500",
    hoverText: "group-hover:text-orange-400",
    avatarGradient: "from-orange-600 to-red-700",
    accentColor: "text-orange-400",
  },
  {
    blobBg: "bg-green-500",
    glowBg: "bg-green-500",
    hoverText: "group-hover:text-green-400",
    avatarGradient: "from-emerald-600 to-green-800",
    accentColor: "text-green-400",
  },
  {
    blobBg: "bg-huddle-coral",
    glowBg: "bg-huddle-coral",
    hoverText: "group-hover:text-huddle-coral",
    avatarGradient: "from-huddle-coral to-pink-700",
    accentColor: "text-huddle-coral",
  },
];

// Dropdown Component
function FilterDropdown({
  label,
  options,
  selected,
  onToggle,
  icon,
}: {
  label: string;
  options: string[];
  selected: string[];
  onToggle: (value: string) => void;
  icon?: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const activeCount = selected.length;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={`px-4 py-2.5 text-sm font-medium flex items-center gap-2 whitespace-nowrap rounded-xl transition-all border ${
          activeCount > 0
            ? "bg-huddle-coral/15 border-huddle-coral/50 text-huddle-coral shadow-[0_0_15px_rgba(255,107,107,0.15)]"
            : "bg-white/5 border-white/10 text-gray-300 hover:bg-white/10 hover:border-white/20"
        }`}
      >
        {icon}
        <span>{label}</span>
        {activeCount > 0 && (
          <span className="bg-huddle-coral text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full leading-none">
            {activeCount}
          </span>
        )}
        <ChevronDown
          className={`w-3.5 h-3.5 text-gray-400 transition-transform duration-200 ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-2 z-50 min-w-[200px] max-h-64 overflow-y-auto bg-huddle-charcoal/95 backdrop-blur-xl border border-white/10 rounded-xl shadow-2xl p-1.5 custom-scrollbar animate-fade-in">
          {options.length === 0 ? (
            <div className="px-3 py-2.5 text-xs text-gray-500 text-center">No options available</div>
          ) : (
            options.map((opt) => {
              const checked = selected.some(
                (s) => s.toLowerCase() === opt.toLowerCase()
              );
              return (
                <button
                  key={opt}
                  type="button"
                  onClick={() => onToggle(opt)}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs font-semibold rounded-lg text-left transition-colors ${
                    checked
                      ? "bg-white/10 text-white"
                      : "text-gray-400 hover:bg-white/5 hover:text-gray-200"
                  }`}
                >
                  <span
                    className={`w-4 h-4 rounded border flex-shrink-0 flex items-center justify-center text-[10px] font-bold transition-colors ${
                      checked
                        ? "bg-huddle-coral border-huddle-coral text-white"
                        : "border-white/20 bg-black/30"
                    }`}
                  >
                    {checked && "✓"}
                  </span>
                  <span className="capitalize">{opt.replace(/-/g, " ")}</span>
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}

export default function PeoplePage() {
  const router = useRouter();

  const [people, setPeople] = useState<UserProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);
  const [selectedAvailability, setSelectedAvailability] = useState<string[]>([]);
  const [openToCollabOnly, setOpenToCollabOnly] = useState(false);
  const [sortBy, setSortBy] = useState<"recent" | "skills" | "name">("recent");

  // Dynamic skill list
  const [allSkillOptions, setAllSkillOptions] = useState<string[]>([]);

  // Auth Guard
  useEffect(() => {
    if (!isLoggedIn()) router.push("/landing");
  }, [router]);

  // Search debounce
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  // Fetch people
  useEffect(() => {
    if (!isLoggedIn()) return;
    fetchPeople();
  }, [debouncedSearch, selectedSkills, selectedAvailability]);

  const fetchPeople = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await userService.getUsers({
        q: debouncedSearch || undefined,
        skills: selectedSkills.length > 0 ? selectedSkills : undefined,
        availability: selectedAvailability.length > 0 ? selectedAvailability : undefined,
      });
      setPeople(data || []);
    } catch (err: any) {
      console.error(err);
      setError("Failed to load people. Please try again.");
      setPeople([]);
    } finally {
      setLoading(false);
    }
  };

  // Seed skill options from all users once
  useEffect(() => {
    if (!isLoggedIn()) return;
    userService
      .getUsers({})
      .then((allUsers) => {
        const skills = new Set<string>();
        allUsers.forEach((u) =>
          (u.skills || []).forEach((s) => {
            const name = getSkillName(s).trim();
            if (name) skills.add(name);
          })
        );
        setAllSkillOptions(Array.from(skills).sort());
      })
      .catch(() => {});
  }, []);

  const toggleSkill = (skill: string) =>
    setSelectedSkills((prev) =>
      prev.includes(skill) ? prev.filter((s) => s !== skill) : [...prev, skill]
    );

  const toggleAvailability = (slot: string) =>
    setSelectedAvailability((prev) =>
      prev.includes(slot) ? prev.filter((s) => s !== slot) : [...prev, slot]
    );

  const clearFilters = () => {
    setSearch("");
    setSelectedSkills([]);
    setSelectedAvailability([]);
    setOpenToCollabOnly(false);
  };

  // Filtered and sorted people list
  const filteredPeople = useMemo(() => {
    let list = [...people];

    if (openToCollabOnly) {
      list = list.filter((p) => p.openToCollaboration !== false);
    }

    if (sortBy === "name") {
      list.sort((a, b) =>
        (a.displayName || "").localeCompare(b.displayName || "")
      );
    } else if (sortBy === "skills") {
      list.sort((a, b) => (b.skills?.length || 0) - (a.skills?.length || 0));
    }

    return list;
  }, [people, openToCollabOnly, sortBy]);

  const hasFilters =
    debouncedSearch.trim() !== "" ||
    selectedSkills.length > 0 ||
    selectedAvailability.length > 0 ||
    openToCollabOnly;

  return (
    <div className="relative min-h-full pb-20">
      {/* Ambient Glowing Background Orbs */}
      <div className="ambient-orb bg-huddle-purple opacity-[0.05] w-[800px] h-[800px] -top-[10%] -right-[10%]" />
      <div
        className="ambient-orb bg-huddle-blue opacity-[0.04] w-[600px] h-[600px] bottom-[20%] -left-[10%]"
        style={{ animationDelay: "-2s" }}
      />

      <div className="max-w-[1600px] mx-auto w-full relative z-10 flex flex-col min-h-full">
        {/* Page Hero Header */}
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6 mb-8 animate-fade-in-up">
          <div className="max-w-2xl">
            <h1 className="font-display font-extrabold text-4xl md:text-5xl lg:text-6xl text-white tracking-tight mb-3">
              Find your <span className="text-gradient">people.</span>
            </h1>
            <p className="text-gray-400 text-base md:text-lg font-medium leading-relaxed">
              Discover students with the skills, interests, and availability to build something
              ambitious with you.
            </p>
          </div>
        </div>

        {/* Discovery Toolbar */}
        <div className="discovery-toolbar p-2.5 flex flex-col md:flex-row md:items-center gap-3 mb-6 animate-fade-in-up shadow-lg relative z-20">
          {/* People Search input */}
          <div className="relative flex-1 min-w-[240px]">
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
              <Search className="w-4 h-4 text-gray-400" />
            </div>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name, bio, or role..."
              className="block w-full pl-11 pr-4 py-2.5 bg-transparent border-none outline-none text-sm text-white placeholder-gray-400 font-medium"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch("")}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          <div className="hidden md:block w-px h-8 bg-white/10 flex-shrink-0" />

          {/* Filter Controls Row */}
          <div className="flex items-center gap-2 flex-wrap flex-shrink-0 py-1 overflow-visible">
            {/* Skills Filter Dropdown */}
            <FilterDropdown
              label="Skills"
              options={allSkillOptions}
              selected={selectedSkills}
              onToggle={toggleSkill}
              icon={<Code2 className="w-4 h-4 text-gray-400" />}
            />

            {/* Availability Filter Dropdown */}
            <FilterDropdown
              label="Availability"
              options={AVAILABILITY_OPTIONS}
              selected={selectedAvailability}
              onToggle={toggleAvailability}
              icon={<Clock className="w-4 h-4 text-gray-400" />}
            />

            {/* Open to Collab Quick Toggle */}
            <label
              className={`flex items-center gap-2 px-3.5 py-2.5 rounded-xl border cursor-pointer transition-all select-none ${
                openToCollabOnly
                  ? "bg-huddle-coral/15 border-huddle-coral/40 text-huddle-coral"
                  : "bg-white/5 border-white/10 text-gray-300 hover:bg-white/10"
              }`}
            >
              <input
                type="checkbox"
                checked={openToCollabOnly}
                onChange={(e) => setOpenToCollabOnly(e.target.checked)}
                className="sr-only"
              />
              <span
                className={`w-3 h-3 rounded-full transition-colors ${
                  openToCollabOnly ? "bg-huddle-green shadow-[0_0_8px_#10B981]" : "bg-gray-600"
                }`}
              />
              <span className="text-xs font-bold whitespace-nowrap">Open to Collab</span>
            </label>

            {/* Clear All Filters */}
            {hasFilters && (
              <button
                type="button"
                onClick={clearFilters}
                className="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 text-xs font-semibold text-gray-300 hover:text-white transition-all whitespace-nowrap"
              >
                <X className="w-3.5 h-3.5" />
                Clear
              </button>
            )}
          </div>
        </div>

        {/* Active Filter Chips */}
        {(selectedSkills.length > 0 || selectedAvailability.length > 0) && (
          <div className="mb-6 flex flex-wrap gap-2 items-center animate-fade-in">
            <span className="text-xs text-gray-500 font-semibold mr-1">Active filters:</span>
            {selectedSkills.map((s) => (
              <span
                key={s}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-huddle-purple/15 border border-huddle-purple/30 text-xs font-semibold text-huddle-purple"
              >
                <Code2 className="w-3 h-3" />
                {s}
                <button
                  type="button"
                  onClick={() => toggleSkill(s)}
                  className="hover:text-white transition-colors"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
            {selectedAvailability.map((a) => (
              <span
                key={a}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-white/5 border border-white/10 text-xs font-semibold text-gray-300"
              >
                <Clock className="w-3 h-3" />
                {a.replace(/-/g, " ")}
                <button
                  type="button"
                  onClick={() => toggleAvailability(a)}
                  className="hover:text-white transition-colors"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
        )}

        {/* Results Meta Info Bar */}
        <div className="flex items-center justify-between mb-6 animate-fade-in-up">
          <p className="text-sm font-medium text-gray-400 flex items-center gap-2">
            <span className="text-white font-bold text-base">{filteredPeople.length}</span>{" "}
            {filteredPeople.length === 1 ? "builder" : "builders"} found
          </p>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() =>
                setSortBy((prev) =>
                  prev === "recent" ? "skills" : prev === "skills" ? "name" : "recent"
                )
              }
              className="text-xs font-semibold text-gray-400 hover:text-white flex items-center gap-1.5 transition-colors bg-white/5 px-3 py-1.5 rounded-lg border border-white/10"
            >
              <ArrowUpDown className="w-3 h-3" />
              {sortBy === "recent"
                ? "Recently Active"
                : sortBy === "skills"
                ? "Most Skills"
                : "Alphabetical"}
            </button>
          </div>
        </div>

        {/* People Grid */}
        <section className="mb-16">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-24 text-gray-400 gap-3">
              <Loader2 className="w-8 h-8 animate-spin text-huddle-coral" />
              <p className="text-sm font-medium text-gray-400">Loading builders...</p>
            </div>
          ) : error ? (
            <div className="flex items-center justify-center gap-2 text-red-400 py-16 bg-red-500/10 border border-red-500/20 rounded-2xl">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span className="text-sm font-semibold">{error}</span>
            </div>
          ) : filteredPeople.length === 0 ? (
            <div className="flex flex-col items-center py-24 text-gray-400 gap-4 bg-white/[0.02] border border-white/5 rounded-3xl p-8 text-center">
              <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center">
                <Users className="w-8 h-8 opacity-40 text-gray-400" />
              </div>
              <div className="max-w-md">
                <h3 className="text-lg font-bold text-white mb-1">No builders found</h3>
                <p className="text-sm font-medium text-gray-400">
                  {hasFilters
                    ? "Try adjusting or clearing your search and filter criteria."
                    : "No community members discovered yet."}
                </p>
              </div>
              {hasFilters && (
                <button
                  type="button"
                  onClick={clearFilters}
                  className="px-5 py-2 rounded-xl bg-huddle-coral/15 border border-huddle-coral/30 text-xs font-bold text-huddle-coral hover:bg-huddle-coral/25 transition-all"
                >
                  Clear All Filters
                </button>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {filteredPeople.map((person, idx) => {
                const theme = THEME_PALETTES[idx % THEME_PALETTES.length];
                const name = person.displayName || "Anonymous Builder";
                const initial = name.charAt(0).toUpperCase();
                const isReady = person.openToCollaboration !== false;

                // Subtitle / Headline logic
                const firstSkill = getSkillName(person.skills?.[0]);
                let headline = "";
                if (person.college && person.course) {
                  headline = `${person.course} @ ${person.college}`;
                } else if (person.college) {
                  headline = `${person.college} ${firstSkill ? `• ${firstSkill}` : ""}`;
                } else if (firstSkill) {
                  headline = `${firstSkill} Developer`;
                } else {
                  headline = "Student Builder";
                }

                // Primary Availability
                const primaryAvail =
                  person.availability && person.availability.length > 0
                    ? person.availability[0].replace(/-/g, " ")
                    : "Flexible";

                return (
                  <div
                    key={person.id || idx}
                    onClick={() => router.push(`/people/${person.id}`)}
                    className="person-card group cursor-pointer"
                  >
                    {/* Ambient Blob */}
                    <div className={`card-ambient-blob ${theme.blobBg}`} />

                    <div className="p-6 flex flex-col flex-1 relative z-10">
                      {/* Card Top Row: Avatar & Collab Status */}
                      <div className="flex items-start justify-between mb-5">
                        <div className="avatar-container">
                          <div className={`avatar-glow ${theme.glowBg}`} />
                          {person.avatarUrl ? (
                            <img
                              src={person.avatarUrl}
                              alt={name}
                              className="avatar-img w-14 h-14 rounded-full border-2 border-white/10 object-cover shadow-lg"
                            />
                          ) : (
                            <div
                              className={`avatar-img w-14 h-14 rounded-full bg-gradient-to-br ${theme.avatarGradient} flex items-center justify-center text-white font-extrabold text-xl border-2 border-white/10 shadow-lg`}
                            >
                              {initial}
                            </div>
                          )}
                        </div>

                        {/* Collab Status Indicator */}
                        {isReady ? (
                          <div className="flex items-center gap-1.5 bg-white/5 backdrop-blur-md border border-white/10 px-2.5 py-1.5 rounded-lg shadow-sm">
                            <div className="collab-indicator w-2 h-2 bg-huddle-green rounded-full" />
                            <span className="text-[9px] font-extrabold text-gray-200 uppercase tracking-widest">
                              Ready
                            </span>
                          </div>
                        ) : (
                          <div className="flex items-center gap-1.5 bg-black/30 backdrop-blur-md border border-white/5 px-2.5 py-1.5 rounded-lg shadow-sm">
                            <div className="w-2 h-2 bg-gray-500 rounded-full" />
                            <span className="text-[9px] font-extrabold text-gray-400 uppercase tracking-widest">
                              Browsing
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Identity Details */}
                      <div className="mb-4">
                        <h3
                          className={`text-xl font-extrabold text-white mb-1 ${theme.hoverText} transition-colors line-clamp-1`}
                        >
                          {name}
                        </h3>
                        <p className="text-xs font-semibold text-gray-400 line-clamp-1 flex items-center gap-1.5">
                          {headline}
                        </p>
                      </div>

                      {/* Bio */}
                      <p className="text-sm text-gray-300 leading-relaxed font-medium line-clamp-3 mb-6">
                        {person.bio ||
                          "Passionate builder looking to collaborate on impactful software."}
                      </p>

                      {/* Bottom Section */}
                      <div className="mt-auto">
                        {/* Top Skills */}
                        <div className="mb-6">
                          <div className="flex items-center gap-1.5 mb-2.5">
                            <Zap className="w-3.5 h-3.5 text-gray-500" />
                            <span className="text-[10px] font-bold text-gray-500 uppercase tracking-widest">
                              Top Skills
                            </span>
                          </div>

                          <div className="flex flex-wrap gap-2">
                            {person.skills && person.skills.length > 0 ? (
                              <>
                                {person.skills.slice(0, 3).map((sk, sIdx) => {
                                  const sName = getSkillName(sk);
                                  const sProf = getSkillProficiency(sk);
                                  const dotClass =
                                    sProf === "ADVANCED"
                                      ? "text-blue-400 bg-blue-400"
                                      : sProf === "INTERMEDIATE"
                                      ? "text-purple-400 bg-purple-400"
                                      : "text-cyan-400 bg-cyan-400";

                                  return (
                                    <span key={sName || sIdx} className="skill-pill-prof">
                                      {sName}
                                      <span
                                        className={`prof-dot ${dotClass}`}
                                        title={sProf.toLowerCase()}
                                      />
                                    </span>
                                  );
                                })}
                                {person.skills.length > 3 && (
                                  <span className="skill-pill-prof text-gray-400 font-bold">
                                    +{person.skills.length - 3}
                                  </span>
                                )}
                              </>
                            ) : (
                              <span className="text-xs text-gray-500 italic">No skills listed</span>
                            )}
                          </div>
                        </div>

                        {/* Footer Actions */}
                        <div className="pt-4 border-t border-white/10 flex items-center justify-between">
                          <div className="text-xs font-medium text-gray-400 flex items-center gap-1.5 bg-black/30 px-2.5 py-1.5 rounded-md border border-white/5 capitalize">
                            <Clock className="w-3.5 h-3.5 text-gray-400" />
                            <span>{primaryAvail}</span>
                          </div>

                          <Link
                            href={`/people/${person.id}`}
                            onClick={(e) => e.stopPropagation()}
                            className="btn-view-profile text-xs font-bold text-gray-200 border border-white/10 bg-white/5 px-3.5 py-2 rounded-lg flex items-center gap-1.5"
                          >
                            <span>View Profile</span>
                            <ArrowRight className="w-3.5 h-3.5" />
                          </Link>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

