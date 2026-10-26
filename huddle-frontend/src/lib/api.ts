import { getToken, logout } from "./auth";
import { config } from "./config";

const API_BASE = config.apiUrl;
export const getWsUrl = () => config.wsUrl;

/**
 * Fetch for public (unauthenticated) landing page endpoints.
 * Unlike apiFetch, this does NOT call logout() on 401 — the landing page
 * does not require authentication and should degrade gracefully.
 */
export async function publicFetch(endpoint: string) {
  const response = await fetch(`${API_BASE}${endpoint}`);
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
}

/** Services for the public landing page showcase sections */
export const publicLandingService = {
  /** Up to 4 users — safe ProfileResponse DTO, no email/password */
  getPeople: (): Promise<any[]> => publicFetch("/public/people"),
};

export async function apiFetch(endpoint: string, options: RequestInit = {}) {
  const token = getToken();
  
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    // If not authenticated, force logout
    logout();
    throw new Error("Unauthorized");
  }

  // Not all responses are JSON
  const contentType = response.headers.get("content-type");
  let data;
  if (contentType && contentType.indexOf("application/json") !== -1) {
    data = await response.json();
  } else {
    data = await response.text();
  }

  if (!response.ok) {
    const errorMsg = data.error || data.message || (typeof data === 'string' ? data : "API Error");
    throw new Error(errorMsg);
  }

  return data;
}

export const authService = {
  login: (data: any) => apiFetch("/auth/login", { method: "POST", body: JSON.stringify(data) }),
  signup: (data: any) => apiFetch("/auth/signup", { method: "POST", body: JSON.stringify(data) }),
  googleAuth: async (idToken: string) => {
    const res = await fetch(`${API_BASE}/auth/google`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    });
    const data = await res.json();
    if (!res.ok && res.status !== 409) {
      throw new Error(data.error || data.message || "Google sign-in failed");
    }
    return { status: res.status, data };
  },
  googleLink: async (idToken: string, password: string) => {
    const res = await fetch(`${API_BASE}/auth/google/link`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken, password }),
    });
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || data.message || "Account linking failed");
    }
    return data;
  },
  verifyEmail: (token: string) =>
    apiFetch("/auth/verify-email", { method: "POST", body: JSON.stringify({ token }) }),
  resendVerification: (email: string) =>
    apiFetch("/auth/resend-verification", { method: "POST", body: JSON.stringify({ email }) }),
  forgotPassword: (email: string) =>
    apiFetch("/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) }),
  resetPassword: (token: string, newPassword: string) =>
    apiFetch("/auth/reset-password", { method: "POST", body: JSON.stringify({ token, newPassword }) }),
};

export type SkillCategory =
  | "LANGUAGES"
  | "FRONTEND"
  | "BACKEND"
  | "MOBILE"
  | "AI_ML"
  | "CYBERSECURITY"
  | "DEVOPS"
  | "DATABASES"
  | "DESIGN"
  | "OTHER";

export type SkillProficiency = "BEGINNER" | "INTERMEDIATE" | "ADVANCED";

export interface SkillItem {
  name: string;
  category?: SkillCategory;
  proficiency?: SkillProficiency;
}

export interface EducationItem {
  institution: string;
  degree?: string;
  field?: string;
  startYear?: number;
  endYear?: number | null;
  description?: string;
}

export interface ExperienceItem {
  title: string;
  organization: string;
  startDate?: string;
  endDate?: string | null;
  description?: string;
}

export interface AchievementItem {
  title: string;
  description?: string;
  date?: string;
}

export interface UserProfile {
  id: string;
  displayName: string | null;
  bio: string | null;
  avatarUrl?: string | null;
  college?: string | null;
  course?: string | null;
  graduationYear?: number | null;
  location?: string | null;
  githubUrl?: string | null;
  linkedinUrl?: string | null;
  portfolioUrl?: string | null;
  twitterUrl?: string | null;
  skills: SkillItem[] | string[];
  availability: string[];
  openToCollaboration?: boolean;
  preferredCollaboration?: string | null;
  lookingFor?: string[];
  education?: EducationItem[];
  experience?: ExperienceItem[];
  achievements?: AchievementItem[];
  projectsCreatedCount?: number;
  collaborationsCount?: number;
  skillsCount?: number;
  // Present only on owner's own profile (/users/me)
  email?: string;
  emailVerified?: boolean;
}

export interface UpdateProfilePayload {
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
  college?: string;
  course?: string;
  graduationYear?: number;
  location?: string;
  githubUrl?: string;
  linkedinUrl?: string;
  portfolioUrl?: string;
  twitterUrl?: string;
  skills?: SkillItem[];
  availability?: string[];
  openToCollaboration?: boolean;
  preferredCollaboration?: string;
  lookingFor?: string[];
  education?: EducationItem[];
  experience?: ExperienceItem[];
  achievements?: AchievementItem[];
}

export interface UserFilterParams {
  q?: string;
  skills?: string[];
  availability?: string[];
}

export interface BlockStatusResponse {
  isBlocked: boolean;
  blocked?: boolean;
  isBlockedBy?: boolean;
}

export const userService = {
  /** Browse users — excludes self and blocked accounts. Supports keyword + skill + availability filters. */
  getUsers: (params: UserFilterParams = {}): Promise<UserProfile[]> => {
    const qs = new URLSearchParams();
    if (params.q?.trim()) qs.set("q", params.q.trim());
    if (params.skills && params.skills.length > 0) qs.set("skills", params.skills.join(","));
    if (params.availability && params.availability.length > 0) qs.set("availability", params.availability.join(","));
    const query = qs.toString();
    return apiFetch(`/users${query ? `?${query}` : ""}`);
  },
  /** @deprecated use getUsers() */
  getAllUsers: () => apiFetch("/users"),
  getCurrentUser: (): Promise<UserProfile> => apiFetch("/users/me"),
  updateProfile: (data: UpdateProfilePayload): Promise<UserProfile> =>
    apiFetch("/users/me", { method: "PUT", body: JSON.stringify(data) }),
  /** Public profile for another user (safe DTO, no email). */
  getUserById: (id: string): Promise<UserProfile> => apiFetch(`/users/${id}`),
  /** @deprecated use getUserById */
  getUser: (id: string) => apiFetch(`/users/${id}`),
  /** Block user */
  blockUser: (id: string): Promise<{ status: string; isBlocked: boolean }> =>
    apiFetch(`/users/${id}/block`, { method: "POST" }),
  /** Unblock user */
  unblockUser: (id: string): Promise<{ status: string; isBlocked: boolean }> =>
    apiFetch(`/users/${id}/block`, { method: "DELETE" }),
  /** Check block status */
  getBlockStatus: (id: string): Promise<BlockStatusResponse> =>
    apiFetch(`/users/${id}/block-status`),
};

export const reportUser = (userId: string, payload: { reason: string; description?: string }) =>
  apiFetch(`/users/${userId}/report`, { method: "POST", body: JSON.stringify(payload) });

export const reportProject = (projectId: string, payload: { reason: string; description?: string }) =>
  apiFetch(`/users/posts/${projectId}/report`, { method: "POST", body: JSON.stringify(payload) });

export interface PostFilterParams {
  q?: string;
  skills?: string[];
  availability?: string[];
}

export const postService = {
  getPosts: (params: PostFilterParams = {}): Promise<any[]> => {
    const qs = new URLSearchParams();
    if (params.q?.trim()) qs.set("q", params.q.trim());
    if (params.skills && params.skills.length > 0) qs.set("skills", params.skills.join(","));
    if (params.availability && params.availability.length > 0) qs.set("availability", params.availability.join(","));
    const query = qs.toString();
    return apiFetch(`/posts${query ? `?${query}` : ""}`);
  },
  getPost: (id: string) => apiFetch(`/posts/${id}`),
  getMatched: () => apiFetch("/posts/matched"),
  getMatchedPeople: (postId: string) => apiFetch(`/posts/${postId}/matched-users`),
  getMyPosts: () => apiFetch("/posts/mine"),
  getAcceptedPosts: () => apiFetch("/posts/accepted"),
  createPost: (data: any) => apiFetch("/posts", { method: "POST", body: JSON.stringify(data) }),
  updatePost: (id: string, data: any) => apiFetch(`/posts/${id}`, { method: "PUT", body: JSON.stringify(data) }),
  deletePost: (id: string) => apiFetch(`/posts/${id}`, { method: "DELETE" }),
  // Send collaboration request to a post
  sendCollabRequest: (postId: string, previewMessage: string) =>
    apiFetch(`/posts/${postId}/requests`, {
      method: "POST",
      body: JSON.stringify({ previewMessage }),
    }),
};

// Service for collaboration request retrieval and actions
export const requestService = {
  getMyRequests: (type: "received" | "sent" = "received") =>
    apiFetch(`/requests${type ? `?type=${type}` : ""}`),
  accept: (id: string) => apiFetch(`/requests/${id}/accept`, { method: "PUT" }),
  reject: (id: string) => apiFetch(`/requests/${id}/reject`, { method: "PUT" }),
  withdraw: (id: string) => apiFetch(`/requests/${id}/withdraw`, { method: "PUT" }),
  getUnreadMessageCount: (): Promise<number> => apiFetch(`/messages/unread-count`),
  getUnreadMessageCounts: (): Promise<UnreadCount[]> => apiFetch(`/messages/unread-counts`),
  markMessagesRead: (id: string): Promise<void> => apiFetch(`/requests/${id}/messages/read`, { method: "PUT" })
};

export interface UnreadCount {
  requestId: string;
  count: number;
}

export interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  isRead?: boolean;
  read?: boolean;
  targetUrl: string | null;
  createdAt: string;
}

export const notificationService = {
  getNotifications: (): Promise<Notification[]> => apiFetch("/notifications"),
  getUnreadCount: (): Promise<number> => apiFetch("/notifications/unread-count"),
  markRead: (id: string): Promise<void> => apiFetch(`/notifications/${id}/read`, { method: "PUT" }),
  markAllRead: (): Promise<void> => apiFetch("/notifications/read-all", { method: "PUT" }),
};

export interface CollaborationMember {
  id: string;
  user: UserProfile;
  role: "OWNER" | "MEMBER";
  status: "ACTIVE" | "LEFT" | "REMOVED";
  joinedAt: string;
}

export interface Collaboration {
  id: string;
  projectId: string;
  projectTitle: string;
  projectBriefDescription?: string;
  projectDesiredSkills?: string[];
  status: "ACTIVE" | "ARCHIVED";
  createdAt: string;
  members: CollaborationMember[];
  conversationRequestId?: string | null;
}

export const collaborationService = {
  getMyCollaborations: (): Promise<Collaboration[]> => apiFetch("/collaborations/my"),
  getCollaboration: (id: string): Promise<Collaboration> => apiFetch(`/collaborations/${id}`),
  getCollaborationByPost: (postId: string): Promise<Collaboration> => apiFetch(`/posts/${postId}/collaboration`),
  getMessages: (collaborationId: string): Promise<any[]> => apiFetch(`/collaborations/${collaborationId}/messages`),
  markMessagesRead: (collaborationId: string): Promise<void> =>
    apiFetch(`/collaborations/${collaborationId}/messages/read`, { method: "PUT" }),
  leaveCollaboration: (id: string): Promise<void> =>
    apiFetch(`/collaborations/${id}/leave`, { method: "POST" }),
  removeMember: (collaborationId: string, userId: string): Promise<void> =>
    apiFetch(`/collaborations/${collaborationId}/members/${userId}`, { method: "DELETE" }),
};

export interface AdminReport {
  id: string;
  reporterDisplayName: string;
  reportedDisplayName?: string | null;
  reason: string;
  description?: string | null;
  projectId?: string | null;
  status: "PENDING" | "RESOLVED" | "DISMISSED";
  createdAt: string;
}

export const adminService = {
  getReports: (): Promise<AdminReport[]> => apiFetch("/admin/reports"),
  resolveReport: (id: string): Promise<AdminReport> =>
    apiFetch(`/admin/reports/${id}/resolve`, { method: "PUT" }),
  dismissReport: (id: string): Promise<AdminReport> =>
    apiFetch(`/admin/reports/${id}/dismiss`, { method: "PUT" }),
};

export interface FileItem {
  id: string;
  name: string;
  fileType: string | null;
  sizeBytes: number | null;
  isFolder: boolean;
  parentFolderId: string | null;
  isInTrash: boolean;
  uploadedBy: string;
  uploadedByDisplayName: string;
  createdAt: string;
  updatedAt: string;
}

export interface FileListResponse {
  files: FileItem[];
  currentFolderId: string | null;
  currentFolderName: string | null;
}

export interface FileUploadResponse {
  id: string;
  name: string;
  fileType: string | null;
  sizeBytes: number;
  storagePath: string;
  uploadedBy: string;
  uploadedByDisplayName: string;
  createdAt: string;
}

export interface StorageUsageResponse {
  usedBytes: number;
  totalBytes: number;
  usagePercentage: number;
  fileCount: number;
}

export interface FileVersionResponse {
  id: string;
  versionNumber: number;
  sizeBytes: number;
  uploadedBy: string;
  uploadedByDisplayName: string;
  createdAt: string;
}

export interface VersionListResponse {
  fileId: string;
  fileName: string;
  versions: FileVersionResponse[];
}

export const fileService = {
  listFiles: (collaborationId: string, folderId?: string): Promise<FileListResponse> => {
    const params = new URLSearchParams();
    if (folderId) params.set("folderId", folderId);
    const query = params.toString();
    return apiFetch(`/collaborations/${collaborationId}/files${query ? `?${query}` : ""}`);
  },
  listTrash: (collaborationId: string): Promise<FileListResponse> =>
    apiFetch(`/collaborations/${collaborationId}/files/trash`),
  uploadFile: (collaborationId: string, file: File, folderId?: string): Promise<FileUploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    if (folderId) formData.append("folderId", folderId);

    const token = getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    return fetch(`${API_BASE}/collaborations/${collaborationId}/files/upload`, {
      method: "POST",
      headers,
      body: formData,
    }).then(async (response) => {
      if (response.status === 401) {
        logout();
        throw new Error("Unauthorized");
      }
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || data.message || "Upload failed");
      }
      return data;
    });
  },
  createFolder: (collaborationId: string, name: string, parentFolderId?: string): Promise<FileItem> => {
    const params = new URLSearchParams();
    params.set("name", name);
    if (parentFolderId) params.set("parentFolderId", parentFolderId);
    const query = params.toString();
    return apiFetch(`/collaborations/${collaborationId}/files/folder?${query}`, { method: "POST" });
  },
  renameFile: (collaborationId: string, fileId: string, name: string): Promise<FileItem> => {
    const params = new URLSearchParams();
    params.set("name", name);
    const query = params.toString();
    return apiFetch(`/collaborations/${collaborationId}/files/${fileId}/rename?${query}`, { method: "PUT" });
  },
  moveFile: (collaborationId: string, fileId: string, targetFolderId?: string): Promise<FileItem> => {
    const params = new URLSearchParams();
    if (targetFolderId) params.set("targetFolderId", targetFolderId);
    const query = params.toString();
    return apiFetch(`/collaborations/${collaborationId}/files/${fileId}/move?${query}`, { method: "PUT" });
  },
  moveToTrash: (collaborationId: string, fileId: string): Promise<{ message: string }> =>
    apiFetch(`/collaborations/${collaborationId}/files/${fileId}/trash`, { method: "PUT" }),
  restoreFromTrash: (collaborationId: string, fileId: string): Promise<FileItem> =>
    apiFetch(`/collaborations/${collaborationId}/files/${fileId}/restore`, { method: "PUT" }),
  permanentlyDelete: (collaborationId: string, fileId: string): Promise<{ message: string }> =>
    apiFetch(`/collaborations/${collaborationId}/files/${fileId}`, { method: "DELETE" }),
  downloadFile: (collaborationId: string, fileId: string): void => {
    const token = getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    fetch(`${API_BASE}/collaborations/${collaborationId}/files/${fileId}/download`, {
      headers,
    }).then((response) => {
      if (response.status === 401) {
        logout();
        return;
      }
      if (!response.ok) {
        throw new Error("Download failed");
      }
      return response.blob();
    }).then((blob) => {
      if (!blob) {
        throw new Error("Download failed - no data received");
      }
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = blob.type || "download";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    }).catch((err) => {
      console.error("Download error:", err);
    });
  },
  replaceFile: (collaborationId: string, fileId: string, file: File): Promise<FileItem> => {
    const formData = new FormData();
    formData.append("file", file);

    const token = getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    return fetch(`${API_BASE}/collaborations/${collaborationId}/files/${fileId}/replace`, {
      method: "POST",
      headers,
      body: formData,
    }).then(async (response) => {
      if (response.status === 401) {
        logout();
        throw new Error("Unauthorized");
      }
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || data.message || "Replace failed");
      }
      return data;
    });
  },
  listVersions: (collaborationId: string, fileId: string): Promise<VersionListResponse> =>
    apiFetch(`/collaborations/${collaborationId}/files/${fileId}/versions`),
  downloadVersion: (collaborationId: string, fileId: string, versionNumber: number): void => {
    const token = getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    fetch(`${API_BASE}/collaborations/${collaborationId}/files/${fileId}/versions/${versionNumber}/download`, {
      headers,
    }).then((response) => {
      if (response.status === 401) {
        logout();
        return;
      }
      if (!response.ok) {
        throw new Error("Download failed");
      }
      return response.blob();
    }).then((blob) => {
      if (!blob) {
        throw new Error("Download failed - no data received");
      }
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = blob.type || "download";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    }).catch((err) => {
      console.error("Download error:", err);
    });
  },
  restoreVersion: (collaborationId: string, fileId: string, versionNumber: number): Promise<FileItem> =>
    apiFetch(`/collaborations/${collaborationId}/files/${fileId}/versions/${versionNumber}/restore`, { method: "POST" }),
  getStorageUsage: (collaborationId: string): Promise<StorageUsageResponse> =>
    apiFetch(`/collaborations/${collaborationId}/storage`),
};
