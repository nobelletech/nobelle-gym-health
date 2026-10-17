import { describe, expect, it, vi } from "vitest";
import type { DiagnosticDrive } from "./diagnostic";
import { runDriveDiagnostic } from "./diagnostic";

function fakeDrive(overrides: Partial<DiagnosticDrive> = {}): DiagnosticDrive & { stored?: Uint8Array } {
  const drive: DiagnosticDrive & { stored?: Uint8Array } = {
    async create(name, bytes) {
      drive.stored = bytes;
      return { id: "temporary-id", name };
    },
    async read() {
      return drive.stored!;
    },
    async remove() {},
    ...overrides,
  };
  return drive;
}

describe("runDriveDiagnostic", () => {
  it("creates, reads, checksum verifies, and deletes non-personal bytes", async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    const drive = fakeDrive({ remove });

    const result = await runDriveDiagnostic(drive);

    expect(result.created).toBe(true);
    expect(result.readBack).toBe(true);
    expect(result.checksumMatched).toBe(true);
    expect(result.deleted).toBe(true);
    expect(result.sha256).toMatch(/^[0-9a-f]{64}$/);
    expect(new TextDecoder().decode(drive.stored)).toContain('"personalData":false');
    expect(remove).toHaveBeenCalledWith("temporary-id");
  });

  it("deletes the temporary file when checksum verification fails", async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    const drive = fakeDrive({
      async read() { return new TextEncoder().encode("corrupted"); },
      remove,
    });

    await expect(runDriveDiagnostic(drive)).rejects.toThrow("checksum");
    expect(remove).toHaveBeenCalledWith("temporary-id");
  });

  it("reports cleanup failures rather than claiming deletion", async () => {
    const drive = fakeDrive({
      async remove() { throw new Error("delete denied"); },
    });

    await expect(runDriveDiagnostic(drive)).rejects.toThrow("cleanup failed: delete denied");
  });
});
