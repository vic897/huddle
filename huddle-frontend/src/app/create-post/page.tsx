"use client";

import { useState } from "react";
import { postService } from "@/lib/api";
import { useRouter } from "next/navigation";
import { ChevronLeft, Plus } from "lucide-react";
import Link from "next/link";

export default function CreatePostPage() {
  const router = useRouter();
  const [formData, setFormData] = useState({
    title: "",
    briefDescription: "",
    fullDescription: "",
    desiredSkills: "",
    desiredAvailability: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const payload = {
        title: formData.title,
        briefDescription: formData.briefDescription,
        fullDescription: formData.fullDescription,
        desiredSkills: formData.desiredSkills
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
        desiredAvailability: formData.desiredAvailability
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
      };
      await postService.createPost(payload);
      router.push("/");
    } catch (err: any) {
      setError(err.message || "Failed to create post");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      <Link href="/" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white mb-8 transition-colors">
        <ChevronLeft className="w-4 h-4" /> Back
      </Link>
      <div className="dashboard-card rounded-2xl p-8">
        <h1 className="text-2xl font-display font-semibold text-white mb-6">Post a Project</h1>
        {error && (
          <div className="mb-6 p-4 rounded-lg bg-red-500/10 text-red-400 text-sm border border-red-500/20">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <div>
            <label className="form-label">Project Title</label>
            <input
              type="text"
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              className="form-input"
              placeholder="E.g. Echo AI Study Companion"
              required
            />
          </div>
          <div>
            <label className="form-label">Brief Description</label>
            <textarea
              value={formData.briefDescription}
              onChange={(e) => setFormData({ ...formData, briefDescription: e.target.value })}
              className="form-input h-24 resize-none"
              placeholder="A one or two sentence pitch for the project..."
              maxLength={280}
              required
            />
          </div>
          <div>
            <label className="form-label">Full Description (Hidden until accepted)</label>
            <textarea
              value={formData.fullDescription}
              onChange={(e) => setFormData({ ...formData, fullDescription: e.target.value })}
              className="form-input h-40 resize-y"
              placeholder="Detailed explanation, architecture, roadmap..."
            />
          </div>
          <div>
            <label className="form-label">Desired Skills (comma separated)</label>
            <input
              type="text"
              value={formData.desiredSkills}
              onChange={(e) => setFormData({ ...formData, desiredSkills: e.target.value })}
              className="form-input"
              placeholder="E.g. React, UI/UX, Python"
            />
          </div>
          <div>
            <label className="form-label">Desired Availability (comma separated)</label>
            <input
              type="text"
              value={formData.desiredAvailability}
              onChange={(e) => setFormData({ ...formData, desiredAvailability: e.target.value })}
              className="form-input"
              placeholder="E.g. weekday-evenings, weekends"
            />
          </div>
          <div className="pt-6 border-t border-white/10 flex justify-end gap-4">
            <Link
              href="/"
              className="px-6 py-3 rounded-xl text-gray-300 hover:text-white hover:bg-white/5 font-semibold transition-colors"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={loading}
              className="btn-primary px-6 py-3 rounded-xl text-white font-semibold flex items-center gap-2 disabled:opacity-50"
            >
              {loading ? "Creating..." : "Post Project"}
              <Plus className="w-4 h-4" />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
