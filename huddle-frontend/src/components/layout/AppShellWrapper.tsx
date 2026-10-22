"use client";

import { usePathname } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";
import React from "react";

const PUBLIC_AUTH_PATHS = [
  "/login",
  "/signup",
  "/landing",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
];

export function AppShellWrapper({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuthPage = PUBLIC_AUTH_PATHS.includes(pathname);

  if (isAuthPage) {
    return <>{children}</>;
  }

  return <AppShell>{children}</AppShell>;
}
