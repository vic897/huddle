"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  HelpCircle,
  Search,
  Compass,
  FolderGit2,
  Users,
  MessageSquare,
  ShieldCheck,
  ArrowUpRight,
  ChevronDown,
  Settings,
  Mail,
  Network,
  CheckCircle2,
} from "lucide-react";

interface TopicCard {
  id: string;
  title: string;
  description: string;
  href: string;
  icon: typeof FolderGit2;
  iconBoxClass: string;
  hoverTitleClass: string;
}

interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

const TOPIC_CARDS: TopicCard[] = [
  {
    id: "projects",
    title: "Discovering Projects",
    description: "Head over to Projects or Matched to search by keyword, skills, or your preferred availability.",
    href: "/projects",
    icon: FolderGit2,
    iconBoxClass: "bg-huddle-coral/10 border-huddle-coral/30 text-huddle-coral",
    hoverTitleClass: "group-hover:text-huddle-coral",
  },
  {
    id: "people",
    title: "Connecting with People",
    description: "Browse candidate profiles on the People directory. Filter by technical skills, view GitHub links, and find co-creators.",
    href: "/people",
    icon: Users,
    iconBoxClass: "bg-huddle-purple/10 border-huddle-purple/30 text-huddle-purple",
    hoverTitleClass: "group-hover:text-huddle-purple",
  },
  {
    id: "workspaces",
    title: "Collaboration Workspaces",
    description: "Once a collaboration request is accepted, a dedicated workspace is unlocked with team group chat and member rosters.",
    href: "/collaborations",
    icon: Network,
    iconBoxClass: "bg-blue-500/10 border-blue-500/30 text-blue-400",
    hoverTitleClass: "group-hover:text-blue-400",
  },
  {
    id: "safety",
    title: "Safety & Moderation",
    description: "Huddle supports bidirectional blocking and report submissions on profiles and projects to keep collaboration spaces safe.",
    href: "/profile",
    icon: ShieldCheck,
    iconBoxClass: "bg-green-500/10 border-green-500/30 text-green-400",
    hoverTitleClass: "group-hover:text-green-400",
  },
];

const FAQ_ITEMS: FaqItem[] = [
  {
    id: "faq-1",
    question: "How do I send a collaboration request?",
    answer: "Navigate to any project card or profile in Browse or Matched, click \"View Project\" or profile details, and click the collaboration request button. You can include a short introductory message with your request.",
  },
  {
    id: "faq-2",
    question: "What happens after a request is accepted?",
    answer: "Once accepted, the project automatically appears in your \"Collaborations\" workspace, and a secure direct or group chat thread is unlocked under your \"Messages\" tab so you can start coordinating immediately.",
  },
  {
    id: "faq-3",
    question: "How does Huddle matching work?",
    answer: "Huddle evaluates your registered technical skills, proficiency levels, and availability against project requirements to compute an explainable compatibility percentage score.",
  },
  {
    id: "faq-4",
    question: "How do I update my profile or skills?",
    answer: "Click on \"Profile\" in your sidebar navigation, then click \"Edit Profile\" to update your bio, social links, academic details, and skill categories.",
  },
];

const GETTING_STARTED_STEPS = [
  {
    step: "01",
    title: "Complete Profile",
    description: "Add your skills, tech stack, and bio.",
    href: "/profile",
  },
  {
    step: "02",
    title: "Discover Projects",
    description: "Explore active student project ideas.",
    href: "/projects",
  },
  {
    step: "03",
    title: "Find Collaborators",
    description: "Match based on complementary skills.",
    href: "/people",
  },
  {
    step: "04",
    title: "Send Request",
    description: "Apply to join or team up on an idea.",
    href: "/projects",
  },
  {
    step: "05",
    title: "Collaborate",
    description: "Unlock team chat and workspaces.",
    href: "/collaborations",
  },
];

export default function HelpPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [openFaqIndex, setOpenFaqIndex] = useState<number | null>(null);
  const [copiedSupport, setCopiedSupport] = useState(false);

  const toggleFaq = (index: number) => {
    setOpenFaqIndex(openFaqIndex === index ? null : index);
  };

  const handleSupportClick = () => {
    window.location.href = "mailto:support@huddle.campus?subject=Huddle%20Support%20Request";
    setCopiedSupport(true);
    setTimeout(() => setCopiedSupport(false), 3000);
  };

  const normalizedQuery = searchQuery.toLowerCase().trim();

  const filteredTopics = TOPIC_CARDS.filter(
    (topic) =>
      topic.title.toLowerCase().includes(normalizedQuery) ||
      topic.description.toLowerCase().includes(normalizedQuery)
  );

  const filteredFaqs = FAQ_ITEMS.filter(
    (faq) =>
      faq.question.toLowerCase().includes(normalizedQuery) ||
      faq.answer.toLowerCase().includes(normalizedQuery)
  );

  return (
    <div className="max-w-4xl mx-auto w-full relative pb-12">
      {/* Ambient Glow Orbs */}
      <div className="absolute top-[5%] right-[10%] w-[700px] h-[700px] bg-huddle-purple opacity-[0.04] blur-[140px] rounded-full pointer-events-none" />
      <div className="absolute bottom-[20%] left-[10%] w-[500px] h-[500px] bg-huddle-coral opacity-[0.03] blur-[120px] rounded-full pointer-events-none" />

      {/* Back to Dashboard / Breadcrumb */}
      <div className="mb-6 relative z-10">
        <Link
          href="/"
          className="inline-flex items-center gap-2 text-xs font-semibold text-gray-400 hover:text-white transition-colors bg-white/5 px-3 py-1.5 rounded-lg border border-white/5 hover:bg-white/10"
        >
          <ArrowLeft className="w-3.5 h-3.5" /> Back to Dashboard
        </Link>
      </div>

      {/* Page Header & Search */}
      <div className="text-center max-w-2xl mx-auto mb-12 relative z-10">
        <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-huddle-coral/10 border border-huddle-coral/30 text-huddle-coral mb-4 shadow-lg">
          <HelpCircle className="w-6 h-6" />
        </div>
        <h1 className="font-display font-extrabold text-3xl md:text-4xl text-white tracking-tight mb-3">
          How can we help you build?
        </h1>
        <p className="text-sm md:text-base text-gray-400 font-medium mb-8">
          Everything you need to know about discovering projects, collaborating, and navigating Huddle.
        </p>

        {/* Help Search Input */}
        <div className="relative max-w-xl mx-auto">
          <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
            <Search className="w-4 h-4 text-gray-400" />
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="block w-full pl-11 pr-4 py-3.5 bg-huddle-charcoal/80 border border-white/10 rounded-2xl text-sm text-white placeholder-gray-500 focus:outline-none focus:border-huddle-coral/50 focus:ring-2 focus:ring-huddle-coral/20 transition-all shadow-xl backdrop-blur-xl font-medium"
            placeholder="Search help articles, features, and guides..."
          />
        </div>
      </div>

      {/* Getting Started Flow */}
      <div className="p-6 md:p-8 rounded-2xl mb-12 border border-white/10 shadow-xl relative overflow-hidden bg-huddle-charcoal/40 backdrop-blur-xl z-10">
        <div className="absolute top-0 right-0 w-40 h-40 bg-huddle-purple/10 rounded-bl-full blur-3xl pointer-events-none" />
        <h2 className="font-display font-bold text-lg text-white mb-6 flex items-center gap-2">
          <Compass className="w-5 h-5 text-huddle-coral" /> Getting Started with Huddle
        </h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          {GETTING_STARTED_STEPS.map((stepItem, idx) => (
            <Link
              key={stepItem.step}
              href={stepItem.href}
              className={`bg-white/5 hover:bg-white/10 p-4 rounded-xl border border-white/5 hover:border-white/15 relative flex flex-col justify-between transition-all duration-200 group ${
                idx === 4 ? "sm:col-span-2 lg:col-span-1" : ""
              }`}
            >
              <div className="text-xs font-extrabold text-huddle-coral mb-2">
                {stepItem.step}
              </div>
              <div>
                <h3 className="text-xs font-bold text-white mb-1 group-hover:text-huddle-coral transition-colors flex items-center justify-between">
                  {stepItem.title}
                  <ArrowUpRight className="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity" />
                </h3>
                <p className="text-[11px] text-gray-400 leading-relaxed font-medium">
                  {stepItem.description}
                </p>
              </div>
            </Link>
          ))}
        </div>
      </div>

      {/* Help Categories / Explore Topics Grid */}
      <div className="mb-12 relative z-10">
        <h2 className="font-display font-bold text-xl text-white mb-6">
          Explore Topics
        </h2>

        {filteredTopics.length === 0 ? (
          <div className="p-8 text-center text-gray-400 text-sm bg-white/[0.02] border border-white/5 rounded-2xl">
            No help topics matching &ldquo;{searchQuery}&rdquo;.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {filteredTopics.map((topic) => {
              const IconComponent = topic.icon;
              return (
                <Link
                  key={topic.id}
                  href={topic.href}
                  className="p-6 flex items-start gap-4 rounded-2xl border border-white/[0.06] bg-[rgba(21,21,28,0.5)] hover:bg-[rgba(25,25,34,0.8)] hover:border-huddle-coral/30 hover:-translate-y-1 hover:shadow-[0_12px_30px_-10px_rgba(255,71,133,0.1)] transition-all duration-300 group cursor-pointer backdrop-blur-xl"
                >
                  <div
                    className={`w-12 h-12 rounded-xl border flex items-center justify-center flex-shrink-0 group-hover:scale-110 transition-transform ${topic.iconBoxClass}`}
                  >
                    <IconComponent className="w-6 h-6" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h3
                      className={`text-base font-bold text-white mb-1.5 transition-colors flex items-center justify-between ${topic.hoverTitleClass}`}
                    >
                      <span>{topic.title}</span>
                      <ArrowUpRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0 ml-1" />
                    </h3>
                    <p className="text-sm text-gray-400 leading-relaxed font-medium">
                      {topic.description}
                    </p>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>

      {/* Frequently Asked Questions (Accordion) */}
      <div className="mb-12 relative z-10">
        <h2 className="font-display font-bold text-xl text-white mb-6">
          Frequently Asked Questions
        </h2>

        {filteredFaqs.length === 0 ? (
          <div className="p-8 text-center text-gray-400 text-sm bg-white/[0.02] border border-white/5 rounded-2xl">
            No FAQ articles matching &ldquo;{searchQuery}&rdquo;.
          </div>
        ) : (
          <div className="space-y-3">
            {filteredFaqs.map((faq, idx) => {
              const isOpen = openFaqIndex === idx;
              return (
                <div
                  key={faq.id}
                  onClick={() => toggleFaq(idx)}
                  className="p-5 rounded-xl border border-white/[0.06] bg-[rgba(21,21,28,0.4)] hover:border-white/15 transition-all cursor-pointer backdrop-blur-md"
                >
                  <div className="flex items-center justify-between font-bold text-sm text-white">
                    <span>{faq.question}</span>
                    <ChevronDown
                      className={`w-4 h-4 text-gray-400 transition-transform duration-300 flex-shrink-0 ml-2 ${
                        isOpen ? "rotate-180 text-white" : ""
                      }`}
                    />
                  </div>
                  {isOpen && (
                    <div className="text-xs text-gray-400 leading-relaxed pt-3 animate-fade-in font-medium border-t border-white/5 mt-3">
                      {faq.answer}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Support / Assistance Section */}
      <div className="p-8 rounded-2xl border border-white/10 text-center relative overflow-hidden mb-12 bg-huddle-charcoal/40 backdrop-blur-xl z-10">
        <div className="absolute inset-0 bg-gradient-to-r from-huddle-coral/5 via-transparent to-huddle-purple/5 pointer-events-none" />

        <h3 className="font-display font-bold text-lg text-white mb-2 relative z-10">
          Still need further assistance?
        </h3>
        <p className="text-sm text-gray-400 max-w-lg mx-auto mb-6 leading-relaxed font-medium relative z-10">
          If you encounter any account issues or have questions regarding campus guidelines, reach out directly to your campus administrator or moderation team.
        </p>

        <div className="flex flex-wrap items-center justify-center gap-4 relative z-10">
          <button
            onClick={handleSupportClick}
            className="px-6 py-2.5 rounded-xl text-xs font-bold text-white shadow-lg bg-gradient-to-r from-huddle-coral to-huddle-pink hover:opacity-90 transition-all transform hover:-translate-y-0.5"
          >
            {copiedSupport ? (
              <span className="flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-white" /> Opening Email Client...
              </span>
            ) : (
              "Contact Support Team"
            )}
          </button>
          <Link
            href="/profile"
            className="px-6 py-2.5 rounded-xl text-xs font-bold text-white shadow-sm flex items-center gap-2 bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 transition-all transform hover:-translate-y-0.5"
          >
            <Settings className="w-4 h-4 text-gray-400" /> Manage Profile Settings
          </Link>
        </div>
      </div>
    </div>
  );
}
