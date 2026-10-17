export const DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata";
export const LOCAL_WEB_ORIGIN = "http://localhost:5173";

export function googleClientId(): string {
  return import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? "";
}
