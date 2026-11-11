import fs from "fs";
import path from "path";
import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { pastaJourneyMap } from "@/lib/mapa-mundo";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Uma imagem de região (512x512 blocos) do JourneyMap: ?c=day&rx=0&rz=0 */
export async function GET(req: Request) {
  if (!(await auth())?.user) return new NextResponse(null, { status: 401 });
  const u = new URL(req.url).searchParams;
  const c = u.get("c") ?? "day", rx = u.get("rx") ?? "", rz = u.get("rz") ?? "";
  if (!/^(day|night|topo|-?\d+)$/.test(c) || !/^-?\d+$/.test(rx) || !/^-?\d+$/.test(rz)) return new NextResponse(null, { status: 400 });
  const dir = pastaJourneyMap();
  if (!dir) return new NextResponse(null, { status: 404 });
  const arq = path.join(dir, c, `${rx},${rz}.png`);
  try {
    const dados = await fs.promises.readFile(arq);
    return new NextResponse(new Uint8Array(dados), { headers: { "content-type": "image/png", "cache-control": "private, max-age=60" } });
  } catch {
    return new NextResponse(null, { status: 404 });
  }
}
