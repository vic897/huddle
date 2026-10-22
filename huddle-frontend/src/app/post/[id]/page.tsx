"use client";

import { useEffect, useState, use } from "react";
import { postService, userService, reportProject } from "@/lib/api";
import { useRouter } from "next/navigation";
import { ChevronLeft, Share2, Users, Send, Edit2, Trash2, X, Check, Sparkles, Flag } from "lucide-react";
import Link from "next/link";
import { ReportModal } from "@/app/components/ReportModal";

export default function PostDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const { id } = use(params);
  const [post, setPost] = useState<any>(null);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  
  // Collaboration request UI state
  const [previewMessage, setPreviewMessage] = useState('');
  const [requestLoading, setRequestLoading] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [requestSuccess, setRequestSuccess] = useState(false);

  // Edit UI state
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({
    title: "",
    briefDescription: "",
    fullDescription: "",
    desiredSkills: "",
    desiredAvailability: ""
  });
  const [saveLoading, setSaveLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // Report state
  const [showReportModal, setShowReportModal] = useState(false);
  const [reportSuccessMsg, setReportSuccessMsg] = useState<string | null>(null);


  useEffect(() => {
    fetchData();
  }, [id]);

  const fetchData = async () => {
    try {
      const [postData, userData] = await Promise.all([
        postService.getPost(id),
        userService.getCurrentUser().catch(() => null)
      ]);
      setPost(postData);
      setCurrentUser(userData);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleRequest = async () => {
    if (!previewMessage.trim()) return;
    setRequestLoading(true);
    setRequestError(null);
    try {
      await postService.sendCollabRequest(id, previewMessage.trim());
      setRequestSuccess(true);
    } catch (err: any) {
      setRequestError(err.message || "Failed to send request");
    } finally {
      setRequestLoading(false);
    }
  };

  const handleEditClick = () => {
    setEditForm({
      title: post.title,
      briefDescription: post.briefDescription || "",
      fullDescription: post.fullDescription || "",
      desiredSkills: Array.isArray(post.desiredSkills) ? post.desiredSkills.join(", ") : "",
      desiredAvailability: Array.isArray(post.desiredAvailability) ? post.desiredAvailability.join(", ") : ""
    });
    setIsEditing(true);
  };

  const handleSaveEdit = async () => {
    if (saveLoading) return;
    setSaveLoading(true);
    setEditError(null);
    try {
      const payload = {
        title: editForm.title,
        briefDescription: editForm.briefDescription,
        fullDescription: editForm.fullDescription,
        desiredSkills: editForm.desiredSkills ? editForm.desiredSkills.split(",").map(s => s.trim()).filter(Boolean) : [],
        desiredAvailability: editForm.desiredAvailability ? editForm.desiredAvailability.split(",").map(s => s.trim()).filter(Boolean) : []
      };
      await postService.updatePost(id, payload);
      await fetchData();
      setIsEditing(false);
    } catch (err: any) {
      setEditError("Failed to update: " + (err.message || "Unknown error"));
    } finally {
      setSaveLoading(false);
    }
  };

  const handleDelete = async () => {
    if (deleteLoading) return;
    setDeleteLoading(true);
    setDeleteError(null);
    try {
      await postService.deletePost(id);
      setShowDeleteModal(false);
      router.push("/projects");
    } catch (err: any) {
      setDeleteError("Failed to delete: " + (err.message || "Unknown error"));
      setDeleteLoading(false);
    }
  };


  const handleReportSubmit = async (reason: string, description?: string) => {
    await reportProject(id, { reason, description });
    setReportSuccessMsg("Report submitted. Thank you for helping keep Huddle safe.");
    setTimeout(() => setReportSuccessMsg(null), 6000);
  };

  if (loading) return <div className="p-10 text-white">Loading project...</div>;
  if (!post) return <div className="p-10 text-white">Project not found</div>;

  const isOwner = currentUser && post.poster && currentUser.id === post.poster.id;
  const posterName = post.poster?.displayName || post.posterDisplayName || "Anonymous";

  return (
    <div className="max-w-4xl mx-auto">
      <div className="flex justify-between items-center mb-8">
        <Link href="/" className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors">
          <ChevronLeft className="w-4 h-4" /> Back to Browse
        </Link>
        <div className="flex items-center gap-3">
          {isOwner && !isEditing && (
            <>
              <Link
                href={`/matched?tab=people&postId=${post.id}`}
                className="flex items-center gap-2 px-4 py-2 bg-huddle-purple/20 hover:bg-huddle-purple/30 text-huddle-purple rounded-lg transition-colors border border-huddle-purple/30 text-sm font-medium"
              >
                <Sparkles className="w-4 h-4" /> Find Collaborators
              </Link>
              <button onClick={handleEditClick} className="flex items-center gap-2 px-4 py-2 bg-white/5 hover:bg-white/10 text-gray-300 rounded-lg transition-colors border border-white/10 text-sm font-medium">
                <Edit2 className="w-4 h-4" /> Edit
              </button>
              <button onClick={() => setShowDeleteModal(true)} disabled={deleteLoading} className="flex items-center gap-2 px-4 py-2 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-lg transition-colors border border-red-500/20 disabled:opacity-50 text-sm font-medium">
                <Trash2 className="w-4 h-4" /> Delete
              </button>

            </>
          )}
          {!isOwner && currentUser && (
            <button
              type="button"
              onClick={() => setShowReportModal(true)}
              className="flex items-center gap-2 px-4 py-2 bg-orange-500/10 hover:bg-orange-500/20 text-orange-400 rounded-lg transition-colors border border-orange-500/20 text-sm font-medium"
            >
              <Flag className="w-4 h-4" /> Report Project
            </button>
          )}
        </div>
      </div>

      {/* Report success banner */}
      {reportSuccessMsg && (
        <div className="mb-6 flex items-center justify-between gap-2 p-3 bg-orange-500/10 border border-orange-500/20 text-orange-300 rounded-xl text-xs">
          <div className="flex items-center gap-2">
            <Flag className="w-4 h-4 text-orange-400 flex-shrink-0" />
            <span>{reportSuccessMsg}</span>
          </div>
          <button onClick={() => setReportSuccessMsg(null)} className="text-gray-400 hover:text-white transition-colors">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      <div className="dashboard-card rounded-2xl p-8 md:p-12 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-64 bg-huddle-coral/10 blur-[100px] rounded-full pointer-events-none"></div>
        
        {isEditing ? (
          <div className="flex flex-col gap-6 mb-10 relative z-10">
            <div className="flex justify-between items-center mb-2">
              <h1 className="text-2xl font-semibold text-white">Edit Project</h1>
              <div className="flex gap-2">
                <button onClick={() => { setIsEditing(false); setEditError(null); }} className="p-2 rounded-full bg-white/5 hover:bg-white/10 text-gray-300" disabled={saveLoading}>
                  <X className="w-4 h-4" />
                </button>
                <button onClick={handleSaveEdit} className="p-2 rounded-full bg-huddle-coral/20 hover:bg-huddle-coral/30 text-huddle-coral" disabled={saveLoading}>
                  <Check className="w-4 h-4" />
                </button>
              </div>
            </div>
            {editError && (
              <div className="p-3 rounded-lg bg-red-500/10 text-red-400 text-sm border border-red-500/20">
                {editError}
              </div>
            )}
            
            <div>
              <label className="form-label">Project Title</label>
              <input type="text" value={editForm.title} onChange={e => setEditForm({...editForm, title: e.target.value})} className="form-input" />
            </div>
            <div>
              <label className="form-label">Brief Description</label>
              <textarea value={editForm.briefDescription} onChange={e => setEditForm({...editForm, briefDescription: e.target.value})} className="form-input h-24 resize-none" maxLength={280} />
            </div>
            <div>
              <label className="form-label">Full Description</label>
              <textarea value={editForm.fullDescription} onChange={e => setEditForm({...editForm, fullDescription: e.target.value})} className="form-input h-40 resize-y" />
            </div>
            <div>
              <label className="form-label">Desired Skills (comma separated)</label>
              <input type="text" value={editForm.desiredSkills} onChange={e => setEditForm({...editForm, desiredSkills: e.target.value})} className="form-input" />
            </div>
            <div>
              <label className="form-label">Desired Availability (comma separated)</label>
              <input type="text" value={editForm.desiredAvailability} onChange={e => setEditForm({...editForm, desiredAvailability: e.target.value})} className="form-input" />
            </div>
          </div>
        ) : (
          <>
            <div className="flex justify-between items-start mb-6">
              <h1 className="text-3xl md:text-5xl font-display font-bold text-white max-w-2xl">{post.title}</h1>
              <button className="p-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 border border-white/10 transition-colors">
                <Share2 className="w-5 h-5" />
              </button>
            </div>

            <div className="flex items-center gap-4 mb-10">
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-md bg-white/5 border border-white/10">
                <Users className="w-4 h-4 text-huddle-coral" />
                <span className="text-sm font-medium text-gray-200">Recruiting</span>
              </div>
              <span className="text-sm text-gray-400">Created by {posterName}</span>
            </div>

            <div className="mb-10">
              <h2 className="text-lg font-semibold text-white mb-4">Project Brief</h2>
              <p className="text-gray-300 leading-relaxed">{post.briefDescription}</p>
            </div>

            {post.fullDescription && (
              <div className="mb-10">
                <h2 className="text-lg font-semibold text-white mb-4">Full Details</h2>
                <p className="text-gray-300 leading-relaxed whitespace-pre-wrap">{post.fullDescription}</p>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
              <div>
                <h2 className="text-lg font-semibold text-white mb-4">Desired Skills</h2>
                <div className="flex flex-wrap gap-2">
                  {(Array.isArray(post.desiredSkills) && post.desiredSkills.length > 0) ? (
                    post.desiredSkills.map((skill: string) => (
                      <span key={skill} className="px-4 py-2 rounded-lg bg-huddle-charcoal border border-white/10 text-sm text-gray-200 shadow-sm">
                        {skill.trim()}
                      </span>
                    ))
                  ) : (
                    <span className="text-gray-500 text-sm">None specified</span>
                  )}
                </div>
              </div>
              <div>
                <h2 className="text-lg font-semibold text-white mb-4">Desired Availability</h2>
                <div className="flex flex-wrap gap-2">
                  {(Array.isArray(post.desiredAvailability) && post.desiredAvailability.length > 0) ? (
                    post.desiredAvailability.map((slot: string) => (
                      <span key={slot} className="px-4 py-2 rounded-lg bg-huddle-charcoal border border-white/10 text-sm text-gray-200 shadow-sm">
                        {slot.trim()}
                      </span>
                    ))
                  ) : (
                    <span className="text-gray-500 text-sm">Any</span>
                  )}
                </div>
              </div>
            </div>

            {!isOwner && (
              <div className="pt-8 border-t border-white/10 flex flex-col gap-4">
                <textarea
                  className="w-full p-2 rounded-md bg-white/5 text-white placeholder-gray-400 focus:outline-none"
                  placeholder="Add a preview message..."
                  value={previewMessage}
                  onChange={(e) => setPreviewMessage(e.target.value)}
                  rows={3}
                  disabled={requestLoading || requestSuccess}
                />
                <button
                  className="btn-primary px-8 py-3 rounded-xl text-white font-semibold flex items-center gap-2 disabled:opacity-50"
                  onClick={handleRequest}
                  disabled={requestLoading || !previewMessage.trim() || requestSuccess}
                >
                  {requestLoading ? "Sending..." : "Send Request"} <Send className="w-4 h-4" />
                </button>
                {requestSuccess && (
                  <p className="text-sm text-green-400">Request sent successfully!</p>
                )}
                {requestError && (
                  <p className="text-sm text-red-400">{requestError}</p>
                )}
              </div>
            )}
          </>
        )}
      </div>

      <ReportModal
        isOpen={showReportModal}
        onClose={() => setShowReportModal(false)}
        targetName={post?.title || "this project"}
        onSubmit={handleReportSubmit}
      />

      {/* Delete Confirmation Modal */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <div className="flex items-start gap-4 mb-5">
              <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center flex-shrink-0">
                <Trash2 className="w-5 h-5 text-red-400" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white mb-1">Delete Project?</h3>
                <p className="text-sm text-gray-400 leading-relaxed">
                  Are you sure you want to delete <span className="text-white font-semibold">"{post?.title}"</span>? This will permanently delete this project post, cancel active collaboration requests, and remove it from discovery. This action cannot be undone.
                </p>
              </div>
            </div>
            {deleteError && (
              <div className="mb-4 p-3 rounded-lg bg-red-500/10 text-red-400 text-xs border border-red-500/20">
                {deleteError}
              </div>
            )}
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => { setShowDeleteModal(false); setDeleteError(null); }}
                disabled={deleteLoading}
                className="px-4 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                disabled={deleteLoading}
                className="px-4 py-2 rounded-lg bg-red-500/80 hover:bg-red-500 text-white text-sm font-medium transition-colors disabled:opacity-50 flex items-center gap-2"
              >
                <Trash2 className="w-4 h-4" />
                {deleteLoading ? "Deleting..." : "Delete Project"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

