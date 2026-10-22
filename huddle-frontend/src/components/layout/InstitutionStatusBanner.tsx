"use client";

import { useEffect, useState } from "react";
import { getInstitutionStatus } from "@/lib/auth";
import { AlertCircle, Clock } from "lucide-react";

export function InstitutionStatusBanner() {
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    setStatus(getInstitutionStatus());
  }, []);

  if (!status || status === "VERIFIED") {
    return null;
  }

  if (status === "PENDING") {
    return (
      <div className="bg-amber-500/10 border-b border-amber-500/20 px-6 py-2.5 flex items-center justify-between text-amber-300 text-xs md:text-sm">
        <div className="flex items-center gap-2">
          <Clock className="w-4 h-4 text-amber-400 flex-shrink-0" />
          <span>
            <strong>Educational Institution Verification Pending:</strong> Your domain is under review by campus administrators. Browsing is enabled; posting and messaging require verified status.
          </span>
        </div>
      </div>
    );
  }

  if (status === "REJECTED") {
    return (
      <div className="bg-red-500/10 border-b border-red-500/20 px-6 py-2.5 flex items-center justify-between text-red-300 text-xs md:text-sm">
        <div className="flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0" />
          <span>
            <strong>Institution Eligibility Rejected:</strong> Your account domain was not approved for campus collaborations. Please contact your administrator.
          </span>
        </div>
      </div>
    );
  }

  return null;
}
