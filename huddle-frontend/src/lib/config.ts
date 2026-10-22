/**
 * Centralized application configuration.
 * All frontend environment variables should be read and exported here.
 */

export const config = {
  apiUrl: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api",
  wsUrl: process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws",
  googleClientId: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "",
} as const;
