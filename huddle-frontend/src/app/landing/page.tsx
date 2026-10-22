"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  ArrowRight,
  Search,
  Lightbulb,
  Puzzle,
  Code2,
  Brush,
  Rocket,
  TrendingUp,
  ChevronDown,
  Database,
  Sparkles,
  FolderGit2,
  Users,
} from "lucide-react";
import { publicLandingService } from "../../lib/api";

// ── Types ────────────────────────────────────────────────────────────────────

interface Person {
  id: string;
  displayName: string;
  bio: string | null;
  skills: string[];
  avatarUrl?: string;
}

// ── Helper: HuddleLogoIcon ────────────────────────────────────────────────────

function HuddleLogoIcon({ className = "" }: { className?: string }) {
  return (
    <span className={`huddle-logo-icon ${className}`}>
      <span className="huddle-panel-left" />
      <span className="huddle-panel-right" />
      <span className="huddle-crossbar" />
    </span>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function LandingNav() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handler = () => setScrolled(window.scrollY > 40);
    window.addEventListener("scroll", handler, { passive: true });
    return () => window.removeEventListener("scroll", handler);
  }, []);

  return (
    <nav
      className={`fixed w-full z-50 top-0 pt-6 pb-4 px-6 md:px-12 transition-all duration-300 ${
        scrolled
          ? "bg-huddle-black/90 backdrop-blur-xl border-b border-white/5 shadow-2xl shadow-black/10"
          : ""
      }`}
    >
      <div className="max-w-7xl mx-auto flex justify-between items-center">
        {/* Logo */}
        <Link href="/landing" className="flex items-center gap-3 group">
          <HuddleLogoIcon />
          <span className="font-display font-extrabold text-2xl md:text-3xl tracking-tight text-white group-hover:text-huddle-coral transition-colors">
            Huddle
          </span>
        </Link>

        {/* Actions */}
        <div className="flex items-center gap-6">
          <Link
            href="/login"
            className="text-sm font-medium text-gray-300 hover:text-white transition-colors hidden md:block"
          >
            Log in
          </Link>
          <Link
            href="/signup"
            className="btn-gradient px-7 py-3 rounded-full text-sm font-semibold text-white shadow-lg shadow-huddle-purple/10 hover:shadow-huddle-purple/30 transition-all"
          >
            Find Your Huddle
          </Link>
        </div>
      </div>
    </nav>
  );
}

function LandingHero() {
  return (
    <section className="relative min-h-screen flex items-center pt-32 pb-16 px-6 md:px-12 overflow-hidden bg-huddle-black">
      {/* Cinematic background */}
      <div
        className="absolute inset-0"
        style={{
          background:
            "radial-gradient(circle at 30% 30%, rgba(255,107,107,0.1), transparent 70%), radial-gradient(circle at 70% 70%, rgba(155,81,224,0.1), transparent 70%)",
          zIndex: -2,
        }}
      />
      <div
        className="absolute inset-0"
        style={{
          background:
            "linear-gradient(to bottom, rgba(10,10,12,0.4) 0%, rgba(10,10,12,0.8) 50%, rgba(10,10,12,1) 100%)",
          zIndex: -1,
        }}
      />

      {/* Ambient glows */}
      <div
        className="ambient-glow"
        style={{ background: "#FF6B6B", top: "-10%", left: "-10%", opacity: 0.15, animation: "glowPulse 4s ease-in-out infinite" }}
      />
      <div
        className="ambient-glow"
        style={{ background: "#9B51E0", bottom: "-20%", right: "-10%", opacity: 0.15, animation: "glowPulse 4s ease-in-out infinite" }}
      />
      <div
        className="ambient-glow"
        style={{ background: "#4A90E2", top: "40%", left: "50%", transform: "translateX(-50%)", opacity: 0.08, animation: "glowPulse 4s ease-in-out infinite" }}
      />

      {/* Floating labels */}
      <div className="absolute inset-0 pointer-events-none hidden lg:block z-0">
        <div className="absolute top-[28%] right-[12%] glass-panel floating-label animate-float" style={{ animationDelay: "0.5s" }}>
          <span className="w-2 h-2 rounded-full bg-huddle-coral inline-block flex-shrink-0" /> 3D Artist needed
        </div>
        <div className="absolute top-[48%] right-[22%] glass-panel floating-label animate-float" style={{ animationDelay: "2.5s" }}>
          <span className="w-2 h-2 rounded-full bg-huddle-blue inline-block flex-shrink-0" /> AI Engineer
        </div>
        <div className="absolute bottom-[35%] right-[8%] glass-panel floating-label animate-float" style={{ animationDelay: "1.2s" }}>
          <span className="w-2 h-2 rounded-full bg-huddle-purple inline-block flex-shrink-0" /> Product Visionary
        </div>
        <div className="absolute top-[35%] left-[10%] glass-panel floating-label animate-float" style={{ animationDelay: "3s" }}>
          <span className="w-2 h-2 rounded-full bg-green-400 inline-block flex-shrink-0" /> Rust Dev
        </div>
        <div className="absolute bottom-[25%] left-[20%] glass-panel floating-label animate-float" style={{ animationDelay: "4s" }}>
          <span className="w-2 h-2 rounded-full bg-pink-400 inline-block flex-shrink-0" /> UX Researcher
        </div>

        {/* Connection paths */}
        <svg className="absolute top-0 left-0 w-full h-full" xmlns="http://www.w3.org/2000/svg">
          <path d="M 500 400 Q 750 250 1100 500" fill="transparent" className="connection-path" />
          <path d="M 1100 500 Q 1200 700 1000 800" fill="transparent" className="connection-path" />
          <path d="M 300 600 Q 450 750 600 700" fill="transparent" className="connection-path" />
        </svg>
      </div>

      {/* Content */}
      <div className="max-w-7xl mx-auto w-full relative z-10 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        <div className="lg:col-span-8 xl:col-span-7 flex flex-col justify-center items-start pt-10">

          {/* Badge */}
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-white/10 bg-white/5 backdrop-blur-md mb-10 shadow-inner animate-fade-in-up">
            <span className="flex h-2.5 w-2.5 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-huddle-coral opacity-75" />
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-huddle-coral" />
            </span>
            <span className="text-xs md:text-sm font-semibold tracking-wide text-gray-200 uppercase">
              Where ambitious ideas meet their co-creators
            </span>
          </div>

          {/* H1 */}
          <h1 className="font-display font-extrabold text-5xl md:text-7xl lg:text-8xl leading-[1.05] tracking-tight mb-8 animate-fade-in-up">
            Your next <br />
            groundbreaking <br />
            project starts <br />
            <span className="text-gradient font-black italic pr-1">right here.</span>
          </h1>

          {/* H2 */}
          <h2 className="text-xl md:text-2xl lg:text-3xl font-semibold text-gray-100 mb-8 max-w-2xl animate-fade-in-up leading-relaxed">
            Have an idea that changes things? Find the perfect team on Huddle.
          </h2>

          {/* Body */}
          <p className="text-base md:text-lg lg:text-xl text-gray-400 mb-12 max-w-xl leading-relaxed animate-fade-in-up">
            Discover ambitious projects, meet incredible people with complementary skills, and form a Huddle to build the future together.
          </p>

          {/* CTAs */}
          <div className="flex flex-col sm:flex-row gap-5 w-full sm:w-auto mb-16 animate-fade-in-up">
            <Link
              href="/signup"
              className="btn-gradient px-10 py-5 rounded-full text-base lg:text-lg font-bold text-white shadow-2xl shadow-huddle-pink/15 hover:shadow-huddle-pink/35 transition-all flex items-center justify-center gap-2.5 group scale-105"
            >
              Find Your Huddle
              <ArrowRight className="w-5 h-5 lg:w-6 lg:h-6 group-hover:translate-x-1.5 transition-transform" />
            </Link>
            <Link
              href="/projects"
              className="btn-gradient-secondary px-10 py-5 rounded-full text-base lg:text-lg font-semibold text-white shadow-inner flex items-center justify-center gap-2 group"
            >
              Explore Projects
              <Search className="w-5 h-5 lg:w-6 lg:h-6 transition-opacity opacity-70 group-hover:opacity-100" />
            </Link>
          </div>

          {/* Social proof */}
          <div className="flex items-center gap-5 animate-fade-in-up">
            <div className="flex -space-x-3.5">
              {[
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80",
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=100&q=80",
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=100&q=80",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=100&q=80",
              ].map((src, i) => (
                <img
                  key={i}
                  src={src}
                  alt="Avatar"
                  className="w-11 h-11 rounded-full border-2 border-huddle-black object-cover shadow-md"
                />
              ))}
              <div className="w-11 h-11 rounded-full border-2 border-huddle-black bg-huddle-dark flex items-center justify-center text-xs font-semibold text-white shadow-md">
                +10k
              </div>
            </div>
            <div className="text-sm md:text-base">
              <div className="font-bold text-white">Join 10,000+ top builders</div>
              <div className="text-gray-400 font-medium">accelerating their visions.</div>
            </div>
          </div>
        </div>
      </div>

      {/* Scroll indicator */}
      <div className="absolute bottom-10 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2 opacity-50 hover:opacity-100 transition-all group animate-pulse-slow">
        <span className="text-[11px] font-bold tracking-[0.2em] uppercase text-gray-300">Explore Huddle</span>
        <ChevronDown className="w-5 h-5 text-gray-300 transition-transform group-hover:translate-y-1" />
      </div>
    </section>
  );
}

function LandingValue() {
  return (
    <section className="py-24 md:py-32 relative px-6 md:px-12 bg-huddle-dark border-t border-white/5">
      <div className="max-w-7xl mx-auto">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-20 items-center">

          {/* Left text */}
          <div>
            <h3 className="font-display font-extrabold text-3xl md:text-5xl leading-tight mb-8">
              Great ideas are rarely <br />
              <span className="text-gray-500">built alone.</span>
            </h3>
            <p className="text-lg md:text-xl text-gray-300 mb-10 max-w-lg leading-relaxed font-medium">
              You can have the groundbreaking idea, the raw ambition, and the deep skills. Finding the perfect people to collaborate and build with is always the bottleneck.
            </p>

            <div className="space-y-8">
              <div className="flex items-start gap-5">
                <div className="w-12 h-12 rounded-full bg-huddle-black border border-white/8 flex items-center justify-center shrink-0 shadow-lg">
                  <Lightbulb className="w-6 h-6 text-huddle-coral" />
                </div>
                <div>
                  <h4 className="font-bold text-white text-xl">The Spark of a New Idea</h4>
                  <p className="text-gray-400 text-base mt-2 leading-relaxed">
                    It starts with a spark. But flawless execution demands a multi-talented team.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-5">
                <div className="w-12 h-12 rounded-full bg-huddle-black border border-white/8 flex items-center justify-center shrink-0 shadow-lg">
                  <Puzzle className="w-6 h-6 text-huddle-pink" />
                </div>
                <div>
                  <h4 className="font-bold text-white text-xl">The Critical Missing Skillsets</h4>
                  <p className="text-gray-400 text-base mt-2 leading-relaxed">
                    You might master the code, but you need the design intuition. You are a designer but need engineering expertise.
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Right visual */}
          <div className="relative h-[450px] flex items-center justify-center scale-105">
            {/* Centre pulsing hub */}
            <div
              className="absolute z-20 w-32 h-32 rounded-full flex items-center justify-center"
              style={{
                background: "linear-gradient(135deg, #FF6B6B, #FF4785, #9B51E0)",
                boxShadow: "0 0 80px rgba(255,71,133,0.4)",
                animation: "glowPulse 3s ease-in-out infinite",
              }}
            >
              <HuddleLogoIcon className="scale-[1.5]" />
            </div>

            {/* Orbiting skill nodes */}
            <div className="absolute w-full h-full" style={{ animation: "spin 100s linear infinite" }}>
              <div className="absolute top-[8%] left-[18%] glass-card p-4 rounded-2xl flex items-center gap-3.5" style={{ transform: "rotate(-15deg)" }}>
                <div className="w-10 h-10 rounded-full bg-blue-500/20 border border-blue-500/10 flex items-center justify-center shadow-inner">
                  <Code2 className="w-5 h-5 text-blue-300" />
                </div>
                <span className="text-base font-semibold text-gray-100">Lead Engineer</span>
              </div>

              <div className="absolute bottom-[18%] right-[12%] glass-card p-4 rounded-2xl flex items-center gap-3.5" style={{ transform: "rotate(10deg)" }}>
                <div className="w-10 h-10 rounded-full bg-pink-500/20 border border-pink-500/10 flex items-center justify-center shadow-inner">
                  <Brush className="w-5 h-5 text-pink-300" />
                </div>
                <span className="text-base font-semibold text-gray-100">Product Designer</span>
              </div>

              <div className="absolute top-[28%] right-[8%] glass-card p-4 rounded-2xl flex items-center gap-3.5" style={{ transform: "rotate(15deg)" }}>
                <div className="w-10 h-10 rounded-full bg-purple-500/20 border border-purple-500/10 flex items-center justify-center shadow-inner">
                  <Rocket className="w-5 h-5 text-purple-300" />
                </div>
                <span className="text-base font-semibold text-gray-100">Co-Founder</span>
              </div>

              <div className="absolute bottom-[10%] left-[25%] glass-card p-4 rounded-2xl flex items-center gap-3.5" style={{ transform: "rotate(-5deg)" }}>
                <div className="w-10 h-10 rounded-full bg-green-500/20 border border-green-500/10 flex items-center justify-center shadow-inner">
                  <TrendingUp className="w-5 h-5 text-green-300" />
                </div>
                <span className="text-base font-semibold text-gray-100">Growth Strategist</span>
              </div>
            </div>

            {/* Orbital rings */}
            <svg className="absolute inset-0 w-full h-full z-0" style={{ filter: "drop-shadow(0 0 10px rgba(255,255,255,0.15))" }}>
              <circle cx="50%" cy="50%" r="140" fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="1.5" strokeDasharray="6 6" />
              <circle cx="50%" cy="50%" r="200" fill="none" stroke="rgba(255,255,255,0.04)" strokeWidth="1" />
            </svg>
          </div>

        </div>
      </div>
    </section>
  );
}

function LandingProcess() {
  const steps = [
    {
      num: "01",
      label: "Discover",
      desc: "Explore highly ambitious, verified projects and revolutionary ideas worth building.",
      hoverColor: "group-hover:text-huddle-coral",
      borderHover: "group-hover:border-huddle-coral/30",
      innerBorder: "border-huddle-coral/20",
      glow: "bg-huddle-coral/5",
    },
    {
      num: "02",
      label: "Connect",
      desc: "Utilize intelligent matching to find elite talent whose skills perfectly complement what you need.",
      hoverColor: "group-hover:text-huddle-pink",
      borderHover: "group-hover:border-huddle-pink/30",
      innerBorder: "border-huddle-pink/20",
      glow: "bg-huddle-pink/5",
    },
    {
      num: "03",
      label: "Build",
      desc: "Form your dedicated Huddle and launch your vision into reality, together.",
      hoverColor: "group-hover:text-huddle-purple",
      borderHover: "group-hover:border-huddle-purple/30",
      innerBorder: "border-huddle-purple/20",
      glow: "bg-huddle-purple/5",
    },
  ];

  return (
    <section className="py-24 relative px-6 md:px-12 bg-huddle-dark border-y border-white/5 overflow-hidden">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: "radial-gradient(#FAFAFA 1px, transparent 1px)",
          backgroundSize: "60px 60px",
          opacity: 0.03,
        }}
      />

      <div className="max-w-7xl mx-auto relative z-10">
        <div className="text-center mb-20 animate-fade-in-up">
          <h3 className="font-display font-extrabold text-3xl md:text-5xl mb-6">
            From initial spark to forming your Huddle.
          </h3>
          <p className="text-gray-300 text-lg md:text-xl font-medium max-w-2xl mx-auto leading-relaxed">
            A seamless, data-driven path to discover, connect, and co-create the extraordinary.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-10 relative">
          {/* Connecting line */}
          <div className="hidden md:block absolute top-16 left-[12%] right-[12%] h-1 bg-gradient-to-r from-transparent via-white/15 to-transparent z-0 shadow-inner" />

          {steps.map((step, i) => (
            <div
              key={step.num}
              className={`relative z-10 flex flex-col items-center text-center animate-fade-in-up group ${i > 0 ? "pt-10 md:pt-0" : ""}`}
            >
              <div
                className={`w-32 h-32 rounded-full bg-huddle-black border-2 border-white/10 flex items-center justify-center mb-8 relative shadow-xl transition-all group-hover:scale-105 ${step.borderHover}`}
              >
                <div className={`absolute inset-2.5 rounded-full border ${step.innerBorder}`} />
                <div className={`absolute inset-0 rounded-full opacity-0 group-hover:opacity-100 transition-opacity ${step.glow} blur-md`} />
                <span className="text-4xl font-display font-black text-transparent bg-clip-text bg-gradient-to-br from-white to-gray-400">
                  {step.num}
                </span>
              </div>
              <h4 className={`text-2xl font-extrabold text-white mb-4 transition-colors ${step.hoverColor}`}>
                {step.label}
              </h4>
              <p className="text-gray-400 text-base font-medium leading-relaxed max-w-[280px]">
                {step.desc}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function PersonCardSkeleton() {
  return (
    <div className="glass-card p-8 rounded-3xl w-full max-w-sm">
      <div className="h-4 bg-white/5 rounded animate-pulse mb-8 w-2/3 mx-auto" />
      <div className="flex flex-col gap-4">
        {[0, 1, 2].map((j) => (
          <div key={j}>
            <div className="flex items-center gap-5 bg-white/5 p-4 rounded-2xl">
              <div className="w-12 h-12 rounded-full bg-white/5 animate-pulse shrink-0" />
              <div className="flex-1">
                <div className="h-4 bg-white/5 rounded animate-pulse w-3/4 mb-2" />
                <div className="h-3 bg-white/5 rounded animate-pulse w-1/2" />
              </div>
            </div>
            {j < 2 && (
              <div className="flex justify-center -my-3.5 relative z-10">
                <div className="w-7 h-7 rounded-full bg-huddle-black border border-white/10 flex items-center justify-center text-xs text-gray-500 font-bold">+</div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function LandingMatch() {
  const [people, setPeople] = useState<Person[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    publicLandingService
      .getPeople()
      .then((data) => {
        setPeople(data.slice(0, 4));
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  // Split people into two combo cards
  const group1 = people.slice(0, 2);
  const group2 = people.slice(2, 4);

  const ROLE_LABELS = ["Visionary Founder", "Chief Engineer", "Growth Lead", "Product Designer"];
  const ROLE_SUBTITLES = ["Product Strategy & Ops", "System Architecture", "User Acquisition & GTM", "UI/UX & Brand"];
  const ROLE_ICON_STYLES = [
    { bg: "bg-blue-500/20 border-blue-500/10", icon: "text-blue-300" },
    { bg: "bg-purple-500/20 border-purple-500/10", icon: "text-purple-300" },
    { bg: "bg-green-500/20 border-green-500/10", icon: "text-green-300" },
    { bg: "bg-pink-500/20 border-pink-500/10", icon: "text-pink-300" },
  ];
  const ROLE_ICONS = [Lightbulb, Database, TrendingUp, Brush];

  function PersonComboCard({
    persons,
    offset,
    label,
    featured = false,
  }: {
    persons: Person[];
    offset: number;
    label: string;
    featured?: boolean;
  }) {
    const items = persons.length > 0 ? persons : [{} as Person, {} as Person];

    return (
      <div
        className={`glass-card p-8 rounded-3xl w-full max-w-sm relative group animate-fade-in-up ${
          featured
            ? "md:scale-110 border-huddle-pink/40 shadow-[0_30px_70px_-15px_rgba(255,71,133,0.3)]"
            : ""
        }`}
      >
        {featured && (
          <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-gradient-to-r from-huddle-coral to-huddle-pink text-white text-[11px] font-extrabold uppercase tracking-widest px-4 py-1.5 rounded-full shadow-lg">
            Highly In-Demand
          </div>
        )}
        <div className="text-xs md:text-sm font-extrabold text-gray-400 mb-8 uppercase tracking-widest text-center group-hover:text-white transition-colors pt-2">
          {label}
        </div>

        <div className="flex flex-col gap-4">
          {items.map((person, idx) => {
            const globalIdx = offset + idx;
            const RoleIcon = ROLE_ICONS[globalIdx % ROLE_ICONS.length];
            const roleStyle = ROLE_ICON_STYLES[globalIdx % ROLE_ICON_STYLES.length];
            const skillSub = person.skills?.length > 0
              ? person.skills.slice(0, 2).map((s: any) => (typeof s === "string" ? s : s?.name || "")).filter(Boolean).join(" & ")
              : ROLE_SUBTITLES[globalIdx % ROLE_SUBTITLES.length];

            return (
              <div key={idx}>
                <div className="flex items-center gap-5 bg-white/5 p-4 rounded-2xl border border-white/5 shadow-inner">
                  <div className={`w-12 h-12 rounded-full ${roleStyle.bg} border flex items-center justify-center shrink-0 shadow-lg`}>
                    <RoleIcon className={`w-6 h-6 ${roleStyle.icon}`} />
                  </div>
                  <div>
                    <div className="text-base font-bold text-white">
                      {person.displayName || ROLE_LABELS[globalIdx % ROLE_LABELS.length]}
                    </div>
                    <div className="text-xs text-gray-500 font-medium mt-1 truncate max-w-[160px]">
                      {skillSub}
                    </div>
                  </div>
                </div>
                {idx < items.length - 1 && (
                  <div className="flex justify-center -my-3.5 relative z-10">
                    <div className="w-7 h-7 rounded-full bg-huddle-black border border-white/10 flex items-center justify-center text-xs text-gray-500 font-bold shadow-md">
                      +
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <section className="py-24 md:py-32 relative px-6 md:px-12 bg-huddle-dark overflow-hidden border-t border-white/5">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: "radial-gradient(#FAFAFA 1px, transparent 1px)",
          backgroundSize: "40px 40px",
          opacity: 0.05,
        }}
      />

      <div className="max-w-7xl mx-auto relative z-10">
        <div className="text-center mb-16 animate-fade-in-up">
          <h3 className="font-display font-extrabold text-3xl md:text-5xl lg:text-6xl mb-6">
            Your missing co-creator <br />
            is likely{" "}
            <span className="text-gradient font-black italic pr-1">already here.</span>
          </h3>
          <p className="text-gray-300 text-lg md:text-xl font-medium max-w-2xl mx-auto leading-relaxed">
            Stop operating in silos. Leverage our ecosystem to find the exact high-impact skills that complete your team's DNA.
          </p>
        </div>

        <div className="flex flex-col md:flex-row gap-8 justify-center items-center">
          {loading ? (
            <>
              <PersonCardSkeleton />
              <PersonCardSkeleton />
            </>
          ) : (
            <>
              <PersonComboCard
                persons={group1}
                offset={0}
                label="The High-Growth Startup Huddle"
              />
              <PersonComboCard
                persons={group2}
                offset={2}
                label="The Elite Game Studio Huddle"
                featured
              />
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function LandingFlow() {
  return (
    <section className="py-24 md:py-32 relative px-6 md:px-12 bg-huddle-black overflow-hidden">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: "radial-gradient(#FAFAFA 1px, transparent 1px)",
          backgroundSize: "50px 50px",
          opacity: 0.02,
        }}
      />

      <div className="max-w-6xl mx-auto text-center relative z-10">
        <h3 className="font-display font-extrabold text-3xl md:text-5xl lg:text-6xl mb-16 lg:mb-20 animate-fade-in-up">
          Bring the elite <br />
          together.{" "}
          <span className="text-gradient font-black italic pr-1">Seamlessly.</span>
        </h3>

        <div className="relative max-w-4xl mx-auto py-16 animate-fade-in-up">
          {/* Vertical track */}
          <div className="absolute top-0 bottom-0 left-1/2 w-0.5 bg-gradient-to-b from-transparent via-white/15 to-transparent -translate-x-1/2 group">
            <div className="absolute inset-0 opacity-10 bg-white/5 animate-pulse-slow" />
          </div>

          <div className="relative flex flex-col gap-20 items-center">
            {/* Step 1 */}
            <div className="flex flex-col items-center gap-4 bg-huddle-black relative z-10 p-5 rounded-full shadow-lg group">
              <div className="w-20 h-20 rounded-full glass-panel flex items-center justify-center text-white border-2 border-white/15 shadow-inner transition-transform duration-500 group-hover:scale-105">
                <Sparkles className="w-8 h-8 text-huddle-coral" />
              </div>
              <span className="text-base font-bold text-gray-200 group-hover:text-white">The Spark of an Idea</span>
            </div>

            {/* Step 2 */}
            <div className="flex flex-col items-center gap-4 bg-huddle-black relative z-10 p-5 rounded-full shadow-lg group">
              <div className="w-20 h-20 rounded-full glass-panel flex items-center justify-center text-white border-2 border-white/15 shadow-inner transition-transform duration-500 group-hover:scale-105">
                <FolderGit2 className="w-8 h-8 text-huddle-purple" />
              </div>
              <span className="text-base font-bold text-gray-200 group-hover:text-white">Validated Project Listed</span>
            </div>

            {/* Step 3: Matching */}
            <div className="flex items-center justify-center w-full relative z-10 bg-huddle-black py-6">
              <div className="absolute w-full h-[1.5px] bg-gradient-to-r from-transparent via-white/10 to-transparent" />
              <div className="flex gap-5 items-center justify-center relative">
                <div className="absolute -left-16 -top-10 animate-float" style={{ animationDelay: "0.5s" }}>
                  <img
                    src="https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=60&q=80"
                    className="w-12 h-12 rounded-full border-2 border-huddle-black opacity-60 shadow-lg scale-90"
                    alt=""
                  />
                </div>
                <div className="absolute -right-16 -bottom-10 animate-float" style={{ animationDelay: "2s" }}>
                  <img
                    src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=60&q=80"
                    className="w-12 h-12 rounded-full border-2 border-huddle-black opacity-60 shadow-lg scale-90"
                    alt=""
                  />
                </div>
                <div className="w-24 h-24 rounded-full bg-white/5 border-2 border-white/20 flex items-center justify-center text-white backdrop-blur-lg shadow-inner animate-pulse-slow">
                  <Users className="w-10 h-10 text-gray-200" />
                </div>
              </div>
            </div>
            <span className="text-base font-bold text-gray-200 bg-huddle-black px-6 relative z-10 -mt-14 transition-colors hover:text-white">
              Collaboration &amp; Matching Requests
            </span>

            {/* Step 4: Huddle formed */}
            <div className="flex flex-col items-center gap-5 bg-huddle-black relative z-10 p-6 mt-12 rounded-3xl animate-fade-in-up">
              <div className="p-8 rounded-[40px] bg-gradient-to-b from-white/10 to-transparent border-2 border-white/20 shadow-[0_30px_90px_rgba(255,107,107,0.2)] flex flex-col items-center gap-5 transition-transform duration-500 hover:scale-105">
                <div className="flex -space-x-5">
                  {[
                    "https://images.unsplash.com/photo-1599566150163-29194dcaad36?auto=format&fit=crop&w=100&q=80",
                    "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=100&q=80",
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80",
                  ].map((src, i) => (
                    <img
                      key={i}
                      className={`w-16 h-16 rounded-full border-4 border-huddle-black object-cover shadow-lg z-${30 - i * 10}`}
                      src={src}
                      alt=""
                    />
                  ))}
                </div>
                <div className="flex items-center gap-3">
                  <HuddleLogoIcon />
                  <span className="font-display font-black text-2xl tracking-tight text-transparent bg-clip-text bg-gradient-to-r from-huddle-coral to-huddle-purple">
                    The Huddle Formed
                  </span>
                </div>
                <p className="text-sm font-medium text-gray-300 max-w-[220px]">
                  3 high-impact creators co-creating the next revolution.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function LandingFinalCTA() {
  return (
    <section className="py-32 md:py-40 relative px-6 md:px-12 overflow-hidden flex items-center justify-center min-h-[85vh] bg-huddle-black">
      {/* Deep cinematic background */}
      <div className="absolute inset-0 z-0">
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: "radial-gradient(#FAFAFA 1px, transparent 1px)",
            backgroundSize: "80px 80px",
            opacity: 0.02,
          }}
        />
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[1200px] h-[600px] rounded-full"
          style={{ background: "#9B51E0", opacity: 0.2, filter: "blur(180px)", animation: "glowPulse 3s ease-in-out infinite" }}
        />
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[300px] rounded-full"
          style={{ background: "#FF6B6B", opacity: 0.25, filter: "blur(120px)", animation: "glowPulse 3s ease-in-out infinite", animationDelay: "2s" }}
        />
      </div>

      <div className="max-w-5xl mx-auto relative z-10 text-center flex flex-col items-center animate-fade-in-up">
        <div className="mb-16 animate-pulse-slow" style={{ transform: "scale(2.2)", transformOrigin: "center" }}>
          <HuddleLogoIcon />
        </div>

        <h2 className="font-display font-black text-6xl md:text-8xl lg:text-9xl leading-[1.0] tracking-tight mb-10">
          Your next revolution <br />
          is waiting for <br />
          <span className="text-gradient pr-3 italic font-black">its people.</span>
        </h2>

        <p className="text-xl md:text-2xl font-semibold text-gray-300 mb-16 max-w-2xl mx-auto leading-relaxed">
          Have an idea that changes things? Find the critical team members who can help you build and launch it. Stop waiting, start Huddling.
        </p>

        <div className="flex flex-col sm:flex-row gap-6 w-full sm:w-auto">
          <Link
            href="/signup"
            className="btn-gradient px-12 py-6 rounded-full text-lg md:text-xl font-extrabold text-white shadow-2xl shadow-huddle-purple/15 hover:shadow-huddle-purple/40 transition-all flex items-center justify-center gap-3 group scale-105"
          >
            Find Your Huddle
            <ArrowRight className="w-6 h-6 lg:w-7 lg:h-7 group-hover:translate-x-2 transition-transform" />
          </Link>
          <Link
            href="/projects"
            className="btn-gradient-secondary px-12 py-6 rounded-full text-lg md:text-xl font-semibold text-white shadow-inner flex items-center justify-center gap-2.5 group"
          >
            Explore Projects
            <Search className="w-6 h-6 lg:w-7 lg:h-7 transition-opacity opacity-70 group-hover:opacity-100" />
          </Link>
        </div>
      </div>
    </section>
  );
}

function LandingFooter() {
  const product = [
    { label: "Discover Projects", href: "/projects" },
    { label: "Find People", href: "/people" },
    { label: "Intelligent Matching", href: "/projects" }, // TODO: replace with /matching when available
    { label: "How Huddle Works", href: "/landing" }, // TODO: replace with /how-it-works when available
  ];
  const company = [
    { label: "About Us", href: "/landing" }, // TODO: /about
    { label: "Careers", href: "/landing" }, // TODO: /careers
    { label: "Press & Media", href: "/landing" }, // TODO: /press
    { label: "Contact", href: "/landing" }, // TODO: /contact
  ];
  const resources = [
    { label: "Help Center", href: "/landing" }, // TODO: /help
    { label: "Community", href: "/landing" }, // TODO: /community
    { label: "Terms of Service", href: "/landing" }, // TODO: /terms
    { label: "Privacy Policy", href: "/landing" }, // TODO: /privacy
  ];

  const linkCols = [
    { heading: "Product", links: product, hoverColors: ["hover:text-huddle-coral", "hover:text-huddle-pink", "hover:text-huddle-purple", "hover:text-huddle-blue"] },
    { heading: "Company", links: company, hoverColors: ["hover:text-huddle-coral", "hover:text-huddle-pink", "hover:text-huddle-purple", "hover:text-huddle-blue"] },
    { heading: "Resources", links: resources, hoverColors: ["hover:text-huddle-coral", "hover:text-huddle-pink", "hover:text-huddle-purple", "hover:text-huddle-blue"] },
  ];

  return (
    <footer className="bg-huddle-black border-t border-white/5 pt-24 pb-10 px-6 md:px-12 relative z-10">
      <div className="max-w-7xl mx-auto">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-10 mb-20">
          {/* Brand */}
          <div className="col-span-2 md:col-span-2">
            <Link href="/landing" className="flex items-center gap-3.5 mb-8 group">
              <HuddleLogoIcon />
              <span className="font-display font-extrabold text-2xl text-white group-hover:text-huddle-coral transition-colors">
                Huddle
              </span>
            </Link>
            <p className="text-gray-400 text-base font-medium max-w-sm leading-relaxed mb-6">
              The definitive platform where highly ambitious people discover revolutionary projects, find complementary elite skills, and form high-performance Huddles to build the future.
            </p>
            <div className="flex gap-4 text-gray-500">
              {/* Twitter / X */}
              <a href="#" className="hover:text-white transition-colors" aria-label="Twitter">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-4.714-6.231-5.401 6.231H2.747l7.73-8.835L1.254 2.25H8.08l4.259 5.629L18.244 2.25zm-1.161 17.52h1.833L7.084 4.126H5.117L17.083 19.77z"/>
                </svg>
              </a>
              {/* GitHub */}
              <a href="#" className="hover:text-white transition-colors" aria-label="GitHub">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
                </svg>
              </a>
              {/* LinkedIn */}
              <a href="#" className="hover:text-white transition-colors" aria-label="LinkedIn">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/>
                </svg>
              </a>
            </div>

          </div>

          {/* Link columns */}
          {linkCols.map((col) => (
            <div key={col.heading} className="pt-2">
              <h4 className="text-white font-bold mb-6 text-base uppercase tracking-wider">{col.heading}</h4>
              <ul className="space-y-4 text-base text-gray-400 font-medium">
                {col.links.map((link, i) => (
                  <li key={link.label}>
                    <Link href={link.href} className={`${col.hoverColors[i]} transition-colors`}>
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="flex flex-col md:flex-row justify-between items-center pt-10 border-t border-white/5 text-sm text-gray-600 font-medium gap-5 text-center md:text-left">
          <p>© 2026 Huddle Inc. Accelerating ambition, globally. All rights reserved.</p>
          <p>Designed with ❤️ for high-performance teams.</p>
        </div>
      </div>
    </footer>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function LandingPage() {
  return (
    <div className="bg-huddle-black min-h-screen text-white overflow-x-hidden">
      <LandingNav />
      <LandingHero />
      <LandingValue />
      <LandingProcess />
      <LandingMatch />
      <LandingFlow />
      <LandingFinalCTA />
      <LandingFooter />
    </div>
  );
}
