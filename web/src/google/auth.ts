import { DRIVE_APPDATA_SCOPE, googleClientId } from "./config";

type TokenResponse = {
  access_token?: string;
  error?: string;
  error_description?: string;
  expires_in?: number;
};

type TokenClient = {
  requestAccessToken(options?: { prompt?: string }): void;
};

type GoogleIdentity = {
  accounts: {
    oauth2: {
      initTokenClient(config: {
        client_id: string;
        scope: string;
        callback(response: TokenResponse): void;
        error_callback?(error: unknown): void;
      }): TokenClient;
      revoke(token: string, callback: () => void): void;
    };
  };
};

declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

let scriptPromise: Promise<void> | undefined;
let accessToken: string | undefined;

function loadGoogleIdentity(): Promise<void> {
  if (window.google) return Promise.resolve();
  if (scriptPromise) return scriptPromise;
  scriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Could not load Google Identity Services"));
    document.head.appendChild(script);
  });
  return scriptPromise;
}

export async function requestDriveAccess(): Promise<string> {
  const clientId = googleClientId();
  if (!clientId) throw new Error("VITE_GOOGLE_CLIENT_ID is not configured");
  await loadGoogleIdentity();

  return new Promise((resolve, reject) => {
    const client = window.google!.accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: DRIVE_APPDATA_SCOPE,
      callback(response) {
        if (!response.access_token) {
          reject(new Error(response.error_description ?? response.error ?? "Drive authorization failed"));
          return;
        }
        accessToken = response.access_token;
        resolve(accessToken);
      },
      error_callback: reject,
    });
    client.requestAccessToken({ prompt: "consent" });
  });
}

export function currentAccessToken(): string | undefined {
  return accessToken;
}

export async function disconnectDrive(): Promise<void> {
  if (!accessToken || !window.google) return;
  const token = accessToken;
  await new Promise<void>((resolve) => window.google!.accounts.oauth2.revoke(token, resolve));
  accessToken = undefined;
}
