import fs from "fs";
import path from "path";
import { NextResponse } from "next/server";
import { pastaJourneyMap } from "@/lib/mapa-mundo";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Imagem de uma região da superfície (512x512 blocos) para o mapa do site público: ?rx=0&rz=0 */
export async function GET(req: Request) {
  const u = new URL(req.url).searchParams;
  const rx = u.get("rx") ?? "", rz = u.get("rz") ?? "";
  if (!/^-?\d{1,4}$/.test(rx) || !/^-?\d{1,4}$/.test(rz)) return new NextResponse(null, { status: 400 });
  const dir = pastaJourneyMap();
  if (!dir) return new NextResponse(null, { status: 404 });
  try {
    const dados = await fs.promises.readFile(path.join(dir, "day", `${rx},${rz}.png`));
    return new NextResponse(new Uint8Array(dados), { headers: { "content-type": "image/png", "cache-control": "public, max-age=600" } });
  } catch {
    return new NextResponse(null, { status: 404 });
  }
}
