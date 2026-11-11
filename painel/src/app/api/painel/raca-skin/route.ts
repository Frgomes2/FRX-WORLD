import fs from "fs";
import path from "path";
import { NextResponse } from "next/server";
import { auth } from "@/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Skin padrão de uma raça (as que vêm no core mod): ?raca=anao&k=0 */
export async function GET(req: Request) {
  if (!(await auth())?.user) return new NextResponse(null, { status: 401 });
  const u = new URL(req.url).searchParams;
  const raca = u.get("raca") ?? "", k = u.get("k") ?? "0";
  if (!/^[a-z]+$/.test(raca) || !/^[0-4]$/.test(k)) return new NextResponse(null, { status: 400 });
  const servidor = path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
  const arq = path.resolve(servidor, "..", "core-mod", "src", "main", "resources", "assets", "frxworld", "textures", "entity", "raca", `${raca}_${k}.png`);
  try {
    return new NextResponse(new Uint8Array(await fs.promises.readFile(arq)), { headers: { "content-type": "image/png", "cache-control": "private, max-age=3600" } });
  } catch {
    return new NextResponse(null, { status: 404 });
  }
}
