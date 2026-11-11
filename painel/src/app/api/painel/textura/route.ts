import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { textura } from "@/lib/jar-icones";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** ?p=minecraft/textures/models/armor/iron_layer_1.png (só armaduras e skins). */
export async function GET(req: Request) {
  if (!(await auth())?.user) return new NextResponse(null, { status: 401 });
  const png = textura(new URL(req.url).searchParams.get("p") ?? "");
  if (!png) return new NextResponse(null, { status: 404 });
  return new NextResponse(new Uint8Array(png), { headers: { "content-type": "image/png", "cache-control": "private, max-age=86400" } });
}
