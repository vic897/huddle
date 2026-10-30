"use client";

import { useEffect, useState, use, useRef } from "react";
import { collaborationService, fileService, FileItem, StorageUsageResponse, VersionListResponse, FileVersionResponse } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Folder,
  FileText,
  Upload,
  Download,
  Trash2,
  RefreshCw,
  HardDrive,
  AlertCircle,
  Loader2,
  ChevronRight,
  Home,
  Plus,
  MoreVertical,
  Edit2,
  Move,
  RotateCcw,
} from "lucide-react";

export default function FilesPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const router = useRouter();
  const { id } = use(params);

  const [collaboration, setCollaboration] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [errorCode, setErrorCode] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [files, setFiles] = useState<FileItem[]>([]);
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [currentFolderName, setCurrentFolderName] = useState<string | null>(null);
  const [folderPath, setFolderPath] = useState<{ id: string | null; name: string }[]>([
    { id: null, name: "Files" },
  ]);

  const [storageUsage, setStorageUsage] = useState<StorageUsageResponse | null>(null);
  const [viewingTrash, setViewingTrash] = useState(false);

  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const [showCreateFolderModal, setShowCreateFolderModal] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");
  const [creatingFolder, setCreatingFolder] = useState(false);

  const [showRenameModal, setShowRenameModal] = useState(false);
  const [renameTarget, setRenameTarget] = useState<FileItem | null>(null);
  const [newName, setNewName] = useState("");
  const [renaming, setRenaming] = useState(false);

  const [showMoveModal, setShowMoveModal] = useState(false);
  const [moveTarget, setMoveTarget] = useState<FileItem | null>(null);
  const [targetFolderId, setTargetFolderId] = useState<string | null>(null);
  const [moving, setMoving] = useState(false);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<FileItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [showVersionsModal, setShowVersionsModal] = useState(false);
  const [versionsTarget, setVersionsTarget] = useState<FileItem | null>(null);
  const [versions, setVersions] = useState<FileVersionResponse[]>([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [restoringVersion, setRestoringVersion] = useState(false);

  const [showReplaceModal, setShowReplaceModal] = useState(false);
  const [replaceTarget, setReplaceTarget] = useState<FileItem | null>(null);
  const [replacing, setReplacing] = useState(false);
  const replaceFileRef = useRef<HTMLInputElement>(null);

  const [actionMessage, setActionMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.push("/landing");
      return;
    }
    loadCollaboration();
  }, [id, router]);

  useEffect(() => {
    if (collaboration && !errorCode) {
      loadFiles();
      loadStorageUsage();
    }
  }, [collaboration, currentFolderId, viewingTrash, errorCode]);

  const loadCollaboration = async () => {
    setLoading(true);
    try {
      const data = await collaborationService.getCollaboration(id);
      setCollaboration(data);
    } catch (err: any) {
      const msg = err?.message || "";
      if (msg.includes("403") || msg.toLowerCase().includes("forbidden")) {
        setErrorCode(403);
      } else if (msg.includes("404") || msg.toLowerCase().includes("not found")) {
        setErrorCode(404);
      } else {
        setErrorMessage(msg || "Failed to load collaboration.");
      }
    } finally {
      setLoading(false);
    }
  };

  const loadFiles = async () => {
    try {
      const response = viewingTrash
        ? await fileService.listTrash(id)
        : await fileService.listFiles(id, currentFolderId || undefined);
      setFiles(response.files);
      if (!viewingTrash) {
        setCurrentFolderId(response.currentFolderId);
        setCurrentFolderName(response.currentFolderName);
      }
    } catch (err: any) {
      console.error("Failed to load files:", err);
      setActionMessage({ type: "error", text: err?.message || "Failed to load files" });
    }
  };

  const loadStorageUsage = async () => {
    try {
      const usage = await fileService.getStorageUsage(id);
      setStorageUsage(usage);
    } catch (err: any) {
      console.error("Failed to load storage usage:", err);
    }
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 250 * 1024 * 1024) {
      setActionMessage({ type: "error", text: "File size exceeds 250 MB limit" });
      return;
    }

    setUploading(true);
    setUploadProgress(0);

    try {
      await fileService.uploadFile(id, file, currentFolderId || undefined);
      setActionMessage({ type: "success", text: "File uploaded successfully" });
      loadFiles();
      loadStorageUsage();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Upload failed" });
    } finally {
      setUploading(false);
      setUploadProgress(0);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const handleCreateFolder = async () => {
    if (!newFolderName.trim()) return;

    setCreatingFolder(true);
    try {
      await fileService.createFolder(id, newFolderName.trim(), currentFolderId || undefined);
      setActionMessage({ type: "success", text: "Folder created successfully" });
      setShowCreateFolderModal(false);
      setNewFolderName("");
      loadFiles();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to create folder" });
    } finally {
      setCreatingFolder(false);
    }
  };

  const handleRename = async () => {
    if (!renameTarget || !newName.trim()) return;

    setRenaming(true);
    try {
      await fileService.renameFile(id, renameTarget.id, newName.trim());
      setActionMessage({ type: "success", text: "Renamed successfully" });
      setShowRenameModal(false);
      setRenameTarget(null);
      setNewName("");
      loadFiles();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to rename" });
    } finally {
      setRenaming(false);
    }
  };

  const handleMove = async () => {
    if (!moveTarget) return;

    setMoving(true);
    try {
      await fileService.moveFile(id, moveTarget.id, targetFolderId || undefined);
      setActionMessage({ type: "success", text: "Moved successfully" });
      setShowMoveModal(false);
      setMoveTarget(null);
      setTargetFolderId(null);
      loadFiles();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to move" });
    } finally {
      setMoving(false);
    }
  };

  const handleMoveToTrash = async (file: FileItem) => {
    try {
      await fileService.moveToTrash(id, file.id);
      setActionMessage({ type: "success", text: "Moved to trash" });
      loadFiles();
      loadStorageUsage();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to move to trash" });
    }
  };

  const handleRestore = async (file: FileItem) => {
    try {
      await fileService.restoreFromTrash(id, file.id);
      setActionMessage({ type: "success", text: "Restored successfully" });
      loadFiles();
      loadStorageUsage();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to restore" });
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await fileService.permanentlyDelete(id, deleteTarget.id);
      setActionMessage({ type: "success", text: "Deleted successfully" });
      setShowDeleteModal(false);
      setDeleteTarget(null);
      loadFiles();
      loadStorageUsage();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to delete" });
    } finally {
      setDeleting(false);
    }
  };

  const handleFolderClick = (folder: FileItem) => {
    setFolderPath([...folderPath, { id: folder.id, name: folder.name }]);
    setCurrentFolderId(folder.id);
  };

  const handleBreadcrumbClick = (index: number) => {
    const newPath = folderPath.slice(0, index + 1);
    setFolderPath(newPath);
    setCurrentFolderId(newPath[newPath.length - 1].id);
  };

  const handleOpenVersions = async (file: FileItem) => {
    setVersionsTarget(file);
    setShowVersionsModal(true);
    setLoadingVersions(true);
    try {
      const response: VersionListResponse = await fileService.listVersions(id, file.id);
      setVersions(response.versions);
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to load versions" });
    } finally {
      setLoadingVersions(false);
    }
  };

  const handleDownloadVersion = (versionNumber: number) => {
    if (!versionsTarget) return;
    fileService.downloadVersion(id, versionsTarget.id, versionNumber);
  };

  const handleRestoreVersion = async (versionNumber: number) => {
    if (!versionsTarget) return;
    setRestoringVersion(true);
    try {
      await fileService.restoreVersion(id, versionsTarget.id, versionNumber);
      setActionMessage({ type: "success", text: "Version restored successfully" });
      setShowVersionsModal(false);
      setVersionsTarget(null);
      loadFiles();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to restore version" });
    } finally {
      setRestoringVersion(false);
    }
  };

  const handleOpenReplace = (file: FileItem) => {
    setReplaceTarget(file);
    setShowReplaceModal(true);
  };

  const handleReplaceFile = async (file: File) => {
    if (!replaceTarget) return;
    setReplacing(true);
    try {
      await fileService.replaceFile(id, replaceTarget.id, file);
      setActionMessage({ type: "success", text: "File replaced successfully" });
      setShowReplaceModal(false);
      setReplaceTarget(null);
      loadFiles();
      loadStorageUsage();
    } catch (err: any) {
      setActionMessage({ type: "error", text: err?.message || "Failed to replace file" });
    } finally {
      setReplacing(false);
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + " " + sizes[i];
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  if (loading) {
    return (
      <div className="max-w-5xl mx-auto py-24 flex items-center justify-center gap-3 text-gray-400">
        <Loader2 className="w-5 h-5 animate-spin text-huddle-coral" />
        <span>Loading files...</span>
      </div>
    );
  }

  if (errorCode === 403) {
    return (
      <div className="max-w-md mx-auto py-24 text-center">
        <div className="w-16 h-16 rounded-2xl bg-red-500/10 border border-red-500/20 flex items-center justify-center mx-auto mb-5">
          <AlertCircle className="w-8 h-8 text-red-400" />
        </div>
        <h2 className="text-xl font-bold text-white mb-2">Access Denied</h2>
        <p className="text-gray-400 text-sm mb-6">
          You are not an active member of this collaboration.
        </p>
        <Link href="/collaborations">
          <button className="px-5 py-2.5 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors">
            ← Back to Collaborations
          </button>
        </Link>
      </div>
    );
  }

  if (errorCode === 404 || !collaboration) {
    return (
      <div className="max-w-md mx-auto py-24 text-center">
        <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-white mb-2">Collaboration Not Found</h2>
        <Link href="/collaborations">
          <button className="px-5 py-2.5 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors mt-4">
            ← Back to Collaborations
          </button>
        </Link>
      </div>
    );
  }

  const isOwner = collaboration.members?.some(
    (m: any) => m.user?.id === collaboration.members?.find((cm: any) => cm.user?.id === "current")?.user?.id && m.role === "OWNER"
  );

  const isArchived = collaboration.status === "ARCHIVED";

  return (
    <div className="max-w-6xl mx-auto pb-16">
      {/* Archive Banner */}
      {isArchived && (
        <div className="mb-6 flex items-center gap-3 px-4 py-3 rounded-xl bg-amber-500/10 border border-amber-500/20">
          <AlertCircle className="w-5 h-5 text-amber-400 flex-shrink-0" />
          <div className="flex-1">
            <p className="text-sm text-amber-200 font-medium">This project is archived</p>
            <p className="text-xs text-amber-300/70">Files are read-only. You can download files but cannot upload, rename, move, or delete them.</p>
          </div>
        </div>
      )}

      {/* Action Banner */}
      {actionMessage && (
        <div
          className={`mb-4 flex items-center gap-2.5 px-4 py-3 rounded-xl border text-sm ${
            actionMessage.type === "success"
              ? "bg-green-500/10 border-green-500/20 text-green-400"
              : "bg-red-500/10 border-red-500/20 text-red-400"
          }`}
        >
          {actionMessage.type === "success" ? (
            <RefreshCw className="w-4 h-4 flex-shrink-0" />
          ) : (
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
          )}
          <span>{actionMessage.text}</span>
        </div>
      )}

      {/* Navigation Header */}
      <div className="mb-6 flex items-center justify-between">
        <Link
          href={`/collaborations/${id}`}
          className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Workspace</span>
        </Link>

        <div className="flex items-center gap-3">
          {!isArchived && (
            <button
              onClick={() => setViewingTrash(!viewingTrash)}
              className={`text-xs px-3 py-1.5 rounded-lg border transition-colors flex items-center gap-1.5 ${
                viewingTrash
                  ? "bg-red-500/20 text-red-400 border-red-500/30"
                  : "bg-white/5 text-gray-400 border-white/10 hover:bg-white/10"
              }`}
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>{viewingTrash ? "View Files" : "View Trash"}</span>
            </button>
          )}
        </div>
      </div>

      {/* Storage Usage Bar */}
      {storageUsage && (
        <div className="dashboard-card rounded-2xl p-4 border border-white/5 bg-huddle-charcoal/40 mb-6">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2 text-sm text-gray-300">
              <HardDrive className="w-4 h-4 text-huddle-coral" />
              <span>Storage Usage</span>
            </div>
            <span className="text-xs text-gray-400">
              {formatBytes(storageUsage.usedBytes)} / {formatBytes(storageUsage.totalBytes)} ({storageUsage.fileCount} files)
            </span>
          </div>
          <div className="w-full h-2 bg-white/10 rounded-full overflow-hidden">
            <div
              className={`h-full transition-all ${
                storageUsage.usagePercentage >= 95
                  ? "bg-red-500"
                  : storageUsage.usagePercentage >= 80
                  ? "bg-yellow-500"
                  : "bg-huddle-coral"
              }`}
              style={{ width: `${Math.min(storageUsage.usagePercentage, 100)}%` }}
            />
          </div>
          {storageUsage.usagePercentage >= 80 && (
            <p className="text-xs text-yellow-400 mt-2">
              {storageUsage.usagePercentage >= 95
                ? "Storage nearly full. Delete files to free space."
                : "Storage running low. Consider cleaning up."}
            </p>
          )}
        </div>
      )}

      {/* Files Card */}
      <div className="dashboard-card rounded-2xl border border-white/5 bg-huddle-charcoal/40 overflow-hidden">
        {/* Toolbar */}
        <div className="p-4 border-b border-white/5 flex items-center justify-between bg-huddle-charcoal/60">
          <div className="flex items-center gap-2">
            {/* Breadcrumbs */}
            {!viewingTrash && (
              <div className="flex items-center gap-1 text-sm">
                {folderPath.map((crumb, index) => (
                  <div key={crumb.id || "root"} className="flex items-center gap-1">
                    {index > 0 && <ChevronRight className="w-3 h-3 text-gray-500" />}
                    <button
                      onClick={() => handleBreadcrumbClick(index)}
                      className={`hover:text-huddle-coral transition-colors ${
                        index === folderPath.length - 1 ? "text-white font-medium" : "text-gray-400"
                      }`}
                    >
                      {crumb.name}
                    </button>
                  </div>
                ))}
              </div>
            )}
            {viewingTrash && (
              <div className="flex items-center gap-2 text-sm text-red-400">
                <Trash2 className="w-4 h-4" />
                <span className="font-medium">Trash</span>
              </div>
            )}
          </div>

          {!viewingTrash && !isArchived && (
            <div className="flex items-center gap-2">
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleUpload}
                className="hidden"
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-huddle-coral/20 hover:bg-huddle-coral/30 text-huddle-coral text-xs font-medium transition-colors disabled:opacity-50"
              >
                {uploading ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Upload className="w-3.5 h-3.5" />
                )}
                <span>Upload</span>
              </button>
              <button
                onClick={() => setShowCreateFolderModal(true)}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/10 hover:bg-white/15 text-white text-xs font-medium transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>New Folder</span>
              </button>
            </div>
          )}
        </div>

        {/* Files List */}
        <div className="divide-y divide-white/5">
          {files.length === 0 ? (
            <div className="p-12 text-center text-gray-500">
              <Folder className="w-12 h-12 mx-auto mb-3 opacity-30" />
              <p className="text-sm">
                {viewingTrash ? "Trash is empty" : "No files or folders yet"}
              </p>
            </div>
          ) : (
            files.map((file) => (
              <div
                key={file.id}
                className="p-4 flex items-center justify-between hover:bg-white/[0.02] transition-colors group"
              >
                <div className="flex items-center gap-3 flex-1 min-w-0">
                  <div className="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center flex-shrink-0">
                    {file.isFolder ? (
                      <Folder className="w-5 h-5 text-huddle-purple" />
                    ) : (
                      <FileText className="w-5 h-5 text-gray-400" />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-white font-medium truncate">{file.name}</p>
                    <p className="text-xs text-gray-500">
                      {file.isFolder ? "Folder" : formatBytes(file.sizeBytes || 0)} • {formatDate(file.createdAt)}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                  {!viewingTrash ? (
                    <>
                      {file.isFolder && (
                        <button
                          onClick={() => handleFolderClick(file)}
                          className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                          title="Open folder"
                        >
                          <Folder className="w-4 h-4" />
                        </button>
                      )}
                      {!file.isFolder && (
                        <button
                          onClick={() => fileService.downloadFile(id, file.id)}
                          className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                          title="Download"
                        >
                          <Download className="w-4 h-4" />
                        </button>
                      )}
                      {!isArchived && (
                        <>
                          <button
                            onClick={() => {
                              setRenameTarget(file);
                              setNewName(file.name);
                              setShowRenameModal(true);
                            }}
                            className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                            title="Rename"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => {
                              setMoveTarget(file);
                              setTargetFolderId(null);
                              setShowMoveModal(true);
                            }}
                            className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                            title="Move"
                          >
                            <Move className="w-4 h-4" />
                          </button>
                          {!viewingTrash && !file.isFolder && (
                            <>
                              <button
                                onClick={() => handleOpenReplace(file)}
                                className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                                title="Replace"
                              >
                                <RotateCcw className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => handleOpenVersions(file)}
                                className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                                title="Version History"
                              >
                                <RefreshCw className="w-4 h-4" />
                              </button>
                            </>
                          )}
                          <button
                            onClick={() => handleMoveToTrash(file)}
                            className="p-2 rounded-lg text-gray-400 hover:text-red-400 hover:bg-red-400/10 transition-colors"
                            title="Move to trash"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </>
                      )}
                    </>
                  ) : (
                    <>
                      <button
                        onClick={() => handleRestore(file)}
                        className="p-2 rounded-lg text-gray-400 hover:text-green-400 hover:bg-green-400/10 transition-colors"
                        title="Restore"
                      >
                        <RotateCcw className="w-4 h-4" />
                      </button>
                      {isOwner && (
                        <button
                          onClick={() => {
                            setDeleteTarget(file);
                            setShowDeleteModal(true);
                          }}
                          className="p-2 rounded-lg text-gray-400 hover:text-red-400 hover:bg-red-400/10 transition-colors"
                          title="Permanently delete"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Create Folder Modal */}
      {showCreateFolderModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <h3 className="text-lg font-bold text-white mb-4">Create New Folder</h3>
            <input
              type="text"
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              placeholder="Folder name"
              className="w-full bg-huddle-dark border border-white/10 rounded-lg px-4 py-2.5 text-white text-sm outline-none focus:border-huddle-coral transition-colors mb-4"
              autoFocus
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => {
                  setShowCreateFolderModal(false);
                  setNewFolderName("");
                }}
                className="px-4 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateFolder}
                disabled={creatingFolder || !newFolderName.trim()}
                className="px-4 py-2 rounded-lg bg-huddle-coral hover:bg-huddle-pink text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                {creatingFolder ? <Loader2 className="w-4 h-4 animate-spin" /> : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Rename Modal */}
      {showRenameModal && renameTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <h3 className="text-lg font-bold text-white mb-4">Rename</h3>
            <input
              type="text"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="New name"
              className="w-full bg-huddle-dark border border-white/10 rounded-lg px-4 py-2.5 text-white text-sm outline-none focus:border-huddle-coral transition-colors mb-4"
              autoFocus
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => {
                  setShowRenameModal(false);
                  setRenameTarget(null);
                  setNewName("");
                }}
                className="px-4 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleRename}
                disabled={renaming || !newName.trim()}
                className="px-4 py-2 rounded-lg bg-huddle-coral hover:bg-huddle-pink text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                {renaming ? <Loader2 className="w-4 h-4 animate-spin" /> : "Rename"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Move Modal */}
      {showMoveModal && moveTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <h3 className="text-lg font-bold text-white mb-4">Move to Folder</h3>
            <div className="mb-4">
              <label className="block text-sm text-gray-400 mb-2">Select destination folder:</label>
              <select
                value={targetFolderId || ""}
                onChange={(e) => setTargetFolderId(e.target.value || null)}
                className="w-full bg-huddle-dark border border-white/10 rounded-lg px-4 py-2.5 text-white text-sm outline-none focus:border-huddle-coral transition-colors"
              >
                <option value="">Root (no folder)</option>
                {files.filter(f => f.isFolder && f.id !== moveTarget.id).map(folder => (
                  <option key={folder.id} value={folder.id}>{folder.name}</option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => {
                  setShowMoveModal(false);
                  setMoveTarget(null);
                  setTargetFolderId(null);
                }}
                className="px-4 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleMove}
                disabled={moving}
                className="px-4 py-2 rounded-lg bg-huddle-coral hover:bg-huddle-pink text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                {moving ? <Loader2 className="w-4 h-4 animate-spin" /> : "Move"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteModal && deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <div className="flex items-start gap-4 mb-4">
              <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center flex-shrink-0">
                <Trash2 className="w-5 h-5 text-red-400" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-white mb-1">Permanently Delete?</h3>
                <p className="text-sm text-gray-400">
                  This action cannot be undone. "{deleteTarget.name}" will be permanently deleted.
                </p>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => {
                  setShowDeleteModal(false);
                  setDeleteTarget(null);
                }}
                className="px-4 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="px-4 py-2 rounded-lg bg-red-500 hover:bg-red-600 text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                {deleting ? <Loader2 className="w-4 h-4 animate-spin" /> : "Delete Permanently"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Version History Modal */}
      {showVersionsModal && versionsTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-2xl w-full shadow-2xl max-h-[80vh] flex flex-col">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-white">Version History - {versionsTarget.name}</h3>
              <button
                onClick={() => {
                  setShowVersionsModal(false);
                  setVersionsTarget(null);
                  setVersions([]);
                }}
                className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
              >
                <MoreVertical className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto mb-4">
              {loadingVersions ? (
                <div className="flex items-center justify-center py-8 text-gray-400">
                  <Loader2 className="w-5 h-5 animate-spin mr-2" />
                  <span>Loading versions...</span>
                </div>
              ) : versions.length === 0 ? (
                <div className="text-center py-8 text-gray-400">
                  <p>No versions available</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {versions.map((version) => (
                    <div
                      key={version.id}
                      className="flex items-center justify-between p-3 rounded-lg bg-huddle-dark border border-white/10 hover:border-white/20 transition-colors"
                    >
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm font-medium text-white">Version {version.versionNumber}</span>
                          <span className="text-xs text-gray-400">•</span>
                          <span className="text-xs text-gray-400">{formatBytes(version.sizeBytes)}</span>
                        </div>
                        <div className="flex items-center gap-2 text-xs text-gray-400">
                          <span>{version.uploadedByDisplayName}</span>
                          <span>•</span>
                          <span>{new Date(version.createdAt).toLocaleString()}</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => handleDownloadVersion(version.versionNumber)}
                          className="p-2 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                          title="Download"
                        >
                          <Download className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleRestoreVersion(version.versionNumber)}
                          disabled={restoringVersion}
                          className="p-2 rounded-lg text-gray-400 hover:text-huddle-coral hover:bg-huddle-coral/10 transition-colors disabled:opacity-50"
                          title="Restore this version"
                        >
                          <RotateCcw className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Replace File Modal */}
      {showReplaceModal && replaceTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-huddle-charcoal border border-white/10 rounded-2xl p-6 max-w-md w-full shadow-2xl">
            <h3 className="text-lg font-bold text-white mb-4">Replace File</h3>
            <p className="text-sm text-gray-400 mb-4">
              Replace "{replaceTarget.name}" with a new file. The current version will be saved to version history.
            </p>
            <input
              type="file"
              ref={replaceFileRef}
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  handleReplaceFile(e.target.files[0]);
                }
              }}
              className="hidden"
            />
            <button
              onClick={() => replaceFileRef.current?.click()}
              disabled={replacing}
              className="w-full px-4 py-3 rounded-lg bg-huddle-coral hover:bg-huddle-pink text-white text-sm font-medium transition-colors disabled:opacity-50 mb-3"
            >
              {replacing ? <Loader2 className="w-4 h-4 animate-spin mx-auto" /> : "Choose New File"}
            </button>
            <button
              onClick={() => {
                setShowReplaceModal(false);
                setReplaceTarget(null);
              }}
              disabled={replacing}
              className="w-full px-4 py-2 rounded-lg bg-white/10 hover:bg-white/15 text-white text-sm font-medium transition-colors disabled:opacity-50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
