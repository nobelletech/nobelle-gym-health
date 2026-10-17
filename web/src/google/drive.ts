export type DriveFile = {
  id: string;
  name: string;
  createdTime?: string;
  modifiedTime?: string;
};

export type DrivePage = {
  files: DriveFile[];
  nextPageToken?: string;
};

const API = "https://www.googleapis.com/drive/v3";
const UPLOAD_API = "https://www.googleapis.com/upload/drive/v3";

export class AppDataDriveClient {
  constructor(private readonly accessToken: string) {}

  async list(pageToken?: string, pageSize = 100): Promise<DrivePage> {
    const query = new URLSearchParams({
      spaces: "appDataFolder",
      pageSize: String(Math.min(pageSize, 1000)),
      fields: "nextPageToken,files(id,name,createdTime,modifiedTime)",
      q: "trashed = false",
    });
    if (pageToken) query.set("pageToken", pageToken);
    return this.request<DrivePage>(`${API}/files?${query}`);
  }

  async read(fileId: string): Promise<Uint8Array> {
    const response = await fetch(`${API}/files/${encodeURIComponent(fileId)}?alt=media`, {
      headers: this.headers(),
    });
    await ensureOk(response);
    return new Uint8Array(await response.arrayBuffer());
  }

  async create(name: string, bytes: Uint8Array, mimeType = "application/json"): Promise<DriveFile> {
    const boundary = `nobelle_${crypto.randomUUID()}`;
    const metadata = JSON.stringify({ name, parents: ["appDataFolder"], mimeType });
    const body = multipartBody(boundary, metadata, bytes, mimeType);
    const response = await fetch(`${UPLOAD_API}/files?uploadType=multipart&fields=id,name,createdTime,modifiedTime`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.accessToken}`,
        "Content-Type": `multipart/related; boundary=${boundary}`,
      },
      body,
    });
    await ensureOk(response);
    return response.json() as Promise<DriveFile>;
  }

  async remove(fileId: string): Promise<void> {
    const response = await fetch(`${API}/files/${encodeURIComponent(fileId)}`, {
      method: "DELETE",
      headers: this.headers(),
    });
    await ensureOk(response);
  }

  private headers(): HeadersInit {
    return { Authorization: `Bearer ${this.accessToken}` };
  }

  private async request<T>(url: string): Promise<T> {
    const response = await fetch(url, { headers: this.headers() });
    await ensureOk(response);
    return response.json() as Promise<T>;
  }
}

function multipartBody(boundary: string, metadata: string, bytes: Uint8Array, mimeType: string): Blob {
  return new Blob([
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n`,
    `--${boundary}\r\nContent-Type: ${mimeType}\r\n\r\n`,
    bytes.slice().buffer as ArrayBuffer,
    `\r\n--${boundary}--`,
  ]);
}

async function ensureOk(response: Response): Promise<void> {
  if (response.ok) return;
  const detail = await response.text();
  throw new Error(`Google Drive request failed (${response.status}): ${detail || response.statusText}`);
}
