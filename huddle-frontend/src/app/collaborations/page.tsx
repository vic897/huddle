"use client";

import { useEffect, useState } from "react";
import { collaborationService, Collaboration } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Users, ArrowRight, ShieldCheck, UserCheck, AlertCircle, Loader2 } from "lucide-react";

export default function CollaborationsPage() {
  const router = useRouter();
  const [collaborations, setCollaborations] = useState<Collaboration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    fetchCollaborations();
  }, [router]);

  const fetchCollaborations = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await collaborationService.getMyCollaborations();
      setCollaborations(data || []);
    } catch (err: any) {
      setError(err?.message || "Failed to load collaborations.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto">
      <div className="flex items-center justify-between mb-10 animate-fade-in">
        <div>
          <h1 className="font-display font-semibold text-3xl md:text-4xl text-white mb-2">
            Collaborations
          </h1>
          <p className="text-gray-400 text-base">
            Active workspaces and shared projects you are participating in.
          </p>
        </div>
      </div>

      <section className="mb-16 animate-fade-in" style={{ animationDelay: "0.1s" }}>
        {loading ? (
          <div className="flex items-center justify-center py-24 text-gray-400 gap-3">
            <Loader2 className="w-5 h-5 animate-spin text-huddle-coral" />
            <span>Loading your collaboration workspaces...</span>
          </div>
        ) : error ? (
          <div className="dashboard-card p-10 rounded-2xl flex flex-col items-center justify-center text-center">
            <AlertCircle className="w-12 h-12 text-red-500 mb-4" />
            <h3 className="text-xl font-semibold text-white mb-2">Could not load collaborations</h3>
            <p className="text-gray-400 mb-6 max-w-sm">{error}</p>
            <button
              onClick={fetchCollaborations}
              className="px-4 py-2 bg-white/10 hover:bg-white/20 text-white rounded-lg transition-colors text-sm font-medium"
            >
              Try Again
            </button>
          </div>
        ) : collaborations.length === 0 ? (
          <div className="dashboard-card p-12 rounded-2xl flex flex-col items-center justify-center text-center border border-white/5">
            <div className="w-16 h-16 rounded-2xl bg-huddle-purple/10 border border-huddle-purple/20 flex items-center justify-center mb-5">
              <Users className="w-8 h-8 text-huddle-purple" />
            </div>
            <h3 className="text-xl font-semibold text-white mb-2">No active collaborations yet</h3>
            <p className="text-gray-400 mb-6 max-w-md text-sm leading-relaxed">
              When collaboration requests are accepted on your projects or by other creators, your active working workspaces will appear here.
            </p>
            <div className="flex items-center gap-3">
              <Link href="/projects">
                <button className="btn-primary px-5 py-2.5 rounded-lg text-sm font-semibold text-white shadow-lg">
                  Browse Projects
                </button>
              </Link>
              <Link href="/requests">
                <button className="px-5 py-2.5 bg-white/5 hover:bg-white/10 border border-white/10 text-gray-300 hover:text-white rounded-lg transition-colors text-sm font-medium">
                  View Requests
                </button>
              </Link>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {collaborations.map((collab) => {
              const activeMembers = (collab.members || []).filter((m) => m.status === "ACTIVE");
              const ownerMember = activeMembers.find((m) => m.role === "OWNER");
              const dateStr = new Date(collab.createdAt).toLocaleDateString(undefined, {
                year: "numeric",
                month: "short",
                day: "numeric",
              });

              return (
                <div
                  key={collab.id}
                  className="dashboard-card rounded-2xl overflow-hidden interactive-card group flex flex-col h-full relative border border-white/5 bg-huddle-charcoal/40 hover:border-white/15 transition-all duration-300"
                >
                  <div className="h-28 relative overflow-hidden bg-huddle-charcoal">
                    <div className="absolute inset-0 bg-gradient-to-br from-indigo-900/40 via-purple-900/20 to-transparent"></div>
                    <div className="absolute top-4 right-4 flex items-center gap-2">
                      <span className="px-2.5 py-1 rounded-full text-[11px] font-semibold bg-green-500/20 text-green-400 border border-green-500/30">
                        {collab.status}
                      </span>
                    </div>
                    <div className="absolute bottom-3 left-5">
                      <span className="text-[11px] text-gray-400">Created {dateStr}</span>
                    </div>
                  </div>

                  <div className="p-6 flex-grow flex flex-col">
                    <h3 className="text-lg font-bold text-white mb-2 group-hover:text-huddle-coral transition-colors line-clamp-1">
                      {collab.projectTitle}
                    </h3>

                    {collab.projectBriefDescription && (
                      <p className="text-sm text-gray-400 mb-4 line-clamp-2">
                        {collab.projectBriefDescription}
                      </p>
                    )}

                    {collab.projectDesiredSkills && collab.projectDesiredSkills.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mb-5">
                        {collab.projectDesiredSkills.slice(0, 3).map((skill) => (
                          <span
                            key={skill}
                            className="px-2 py-0.5 rounded-md bg-huddle-dark border border-white/5 text-[11px] text-gray-300"
                          >
                            {skill.trim()}
                          </span>
                        ))}
                        {collab.projectDesiredSkills.length > 3 && (
                          <span className="px-1.5 py-0.5 text-[11px] text-gray-500">
                            +{collab.projectDesiredSkills.length - 3}
                          </span>
                        )}
                      </div>
                    )}

                    <div className="mt-auto pt-4 border-t border-white/5">
                      <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-2">
                          <Users className="w-3.5 h-3.5 text-gray-400" />
                          <span className="text-xs text-gray-400">
                            {activeMembers.length} {activeMembers.length === 1 ? "member" : "members"}
                          </span>
                        </div>
                        {ownerMember && (
                          <span className="text-[11px] text-huddle-purple flex items-center gap-1 font-medium">
                            <ShieldCheck className="w-3 h-3" />
                            {ownerMember.user?.displayName || "Owner"}
                          </span>
                        )}
                      </div>

                      <div className="flex -space-x-2 overflow-hidden mb-4 py-1">
                        {activeMembers.slice(0, 5).map((m) => {
                          const initials = (m.user?.displayName || "?").charAt(0).toUpperCase();
                          return (
                            <div
                              key={m.id}
                              title={`${m.user?.displayName || "Member"} (${m.role})`}
                              className="inline-block h-7 w-7 rounded-full ring-2 ring-huddle-charcoal bg-gradient-to-br from-huddle-coral to-huddle-purple text-white text-[11px] font-bold flex items-center justify-center"
                            >
                              {initials}
                            </div>
                          );
                        })}
                        {activeMembers.length > 5 && (
                          <div className="inline-block h-7 w-7 rounded-full ring-2 ring-huddle-charcoal bg-huddle-dark text-gray-400 text-[10px] font-medium flex items-center justify-center border border-white/10">
                            +{activeMembers.length - 5}
                          </div>
                        )}
                      </div>

                      <Link href={`/collaborations/${collab.id}`} className="block">
                        <button className="w-full py-2.5 px-4 rounded-xl bg-white/5 hover:bg-white/10 group-hover:bg-huddle-coral group-hover:text-white text-gray-200 border border-white/10 group-hover:border-huddle-coral text-xs font-semibold transition-all duration-200 flex items-center justify-center gap-2">
                          <span>Open Workspace</span>
                          <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                        </button>
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}