import type { DriveFile } from "./drive";

export interface DiagnosticDrive {
  create(name: string, bytes: Uint8Array, mimeType?: string): Promise<DriveFile>;
  read(fileId: string): Promise<Uint8Array>;
  remove(fileId: string): Promise<void>;
}

export type DriveDiagnosticResult = {
  bytes: number;
  sha256: string;
  created: true;
  readBack: true;
  checksumMatched: true;
  deleted: true;
};

export async function runDriveDiagnostic(drive: DiagnosticDrive): Promise<DriveDiagnosticResult> {
  const nonce = crypto.randomUUID();
  const payload = new TextEncoder().encode(JSON.stringify({
    schema: "nobelle-drive-diagnostic-v1",
    nonce,
    createdUtc: new Date().toISOString(),
    personalData: false,
  }));
  const expectedHash = await sha256(payload);
  let file: DriveFile | undefined;
  let operationError: unknown;

  try {
    file = await drive.create(`ev_${nonce}.json`, payload);
    const downloaded = await drive.read(file.id);
    const actualHash = await sha256(downloaded);
    if (actualHash !== expectedHash) throw new Error("Downloaded diagnostic checksum did not match the uploaded payload");
  } catch (error) {
    operationError = error;
  }

  if (file) {
    try {
      await drive.remove(file.id);
    } catch (cleanupError) {
      const cleanupMessage = cleanupError instanceof Error ? cleanupError.message : "unknown cleanup error";
      if (operationError) {
        const operationMessage = operationError instanceof Error ? operationError.message : "diagnostic failed";
        throw new Error(`${operationMessage}; temporary Drive file cleanup also failed: ${cleanupMessage}`);
      }
      throw new Error(`Diagnostic data verified, but temporary Drive file cleanup failed: ${cleanupMessage}`);
    }
  }

  if (operationError) throw operationError;

  return {
    bytes: payload.byteLength,
    sha256: expectedHash,
    created: true,
    readBack: true,
    checksumMatched: true,
    deleted: true,
  };
}

async function sha256(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes.slice().buffer as ArrayBuffer);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
