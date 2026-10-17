import { afterEach, describe, expect, it, vi } from "vitest";
import { AppDataDriveClient } from "./drive";

afterEach(() => vi.unstubAllGlobals());

describe("AppDataDriveClient", () => {
  it("lists only appDataFolder with bounded fields", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ files: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await new AppDataDriveClient("token").list("cursor", 5000);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const parsed = new URL(url);
    expect(parsed.searchParams.get("spaces")).toBe("appDataFolder");
    expect(parsed.searchParams.get("pageSize")).toBe("1000");
    expect(parsed.searchParams.get("pageToken")).toBe("cursor");
    expect(parsed.searchParams.get("q")).toBe("trashed = false");
    expect(init.headers).toEqual({ Authorization: "Bearer token" });
  });

  it("creates files with appDataFolder as the only parent", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "id-1", name: "opaque.json" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await new AppDataDriveClient("token").create("opaque.json", new TextEncoder().encode("{}"));

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("uploadType=multipart");
    expect(init.method).toBe("POST");
    const body = await (init.body as Blob).text();
    expect(body).toContain('"parents":["appDataFolder"]');
    expect(body).not.toContain("root");
  });

  it("never puts tokens in URLs", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ files: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await new AppDataDriveClient("secret-token").list();

    expect(fetchMock.mock.calls[0][0]).not.toContain("secret-token");
  });
});
