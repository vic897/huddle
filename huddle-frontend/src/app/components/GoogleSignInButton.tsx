"use client";

import { useEffect, useRef } from "react";
import { config } from "@/lib/config";

declare global {
  interface Window {
    google?: any;
  }
}

interface GoogleSignInButtonProps {
  onSuccess: (credential: string) => void;
  onError?: (err: any) => void;
  text?: "signin_with" | "signup_with" | "continue_with";
}

export default function GoogleSignInButton({ onSuccess, onError, text = "continue_with" }: GoogleSignInButtonProps) {
  const buttonRef = useRef<HTMLDivElement>(null);
  const clientId = config.googleClientId;

  useEffect(() => {
    if (!clientId) {
      return;
    }

    const loadScript = () => {
      if (document.getElementById("google-gsi-client")) {
        renderButton();
        return;
      }
      const script = document.createElement("script");
      script.id = "google-gsi-client";
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      script.defer = true;
      script.onload = () => renderButton();
      document.body.appendChild(script);
    };

    const renderButton = () => {
      if (window.google?.accounts?.id && buttonRef.current) {
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response: any) => {
            if (response.credential) {
              onSuccess(response.credential);
            } else if (onError) {
              onError(response);
            }
          },
        });
        buttonRef.current.innerHTML = "";
        window.google.accounts.id.renderButton(buttonRef.current, {
          theme: "outline",
          size: "large",
          width: "100%",
          text: text,
          shape: "pill",
        });
      }
    };

    loadScript();
  }, [clientId, onSuccess, onError, text]);

  if (!clientId) {
    return (
      <div className="p-3 text-xs text-center text-amber-400/80 bg-amber-500/10 rounded-lg border border-amber-500/20">
        Google Sign-In is unavailable. Set NEXT_PUBLIC_GOOGLE_CLIENT_ID in your frontend environment.
      </div>
    );
  }

  return (
    <div className="w-full flex justify-center my-2">
      <div ref={buttonRef} className="w-full flex justify-center" />
    </div>
  );
}
