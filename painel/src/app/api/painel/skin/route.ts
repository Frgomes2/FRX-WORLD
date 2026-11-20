import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { contaLogada } from "@/lib/conta-site";
import { textura } from "@/lib/jar-icones";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Cache = Map<string, { em: number; png: Uint8Array; modelo: string }>;
const g = globalThis as unknown as { __frxSkins?: Cache };
const cache: Cache = (g.__frxSkins ??= new Map());

/**
 * Skin oficial do jogador (servidor de sessões da Mojang → textures.minecraft.net), servida pelo próprio painel.
 * Cabeçalho x-skin-modelo: "slim" (braços finos) ou "classic". Sem internet ou sem skin: a do Steve do jogo.
 */
export async function GET(req: Request) {
  const uuid = (new URL(req.url).searchParams.get("uuid") ?? "").replace(/-/g, "").toLowerCase();
  if (!(await auth())?.user) {
    const eu = await contaLogada();
    if (!eu || eu.uuid.replace(/-/g, "").toLowerCase() !== uuid) return new NextResponse(null, { status: 401 });
  }
  if (!/^[0-9a-f]{32}$/.test(uuid)) return new NextResponse(null, { status: 400 });

  const novo = new URL(req.url).searchParams.get("novo") === "1";
  const c = cache.get(uuid);
  if (c && !novo && Date.now() - c.em < 60 * 60_000) {
    return resposta(c.png, c.modelo, "cache");
  }

  try {
    const perfil = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${uuid}`, { signal: AbortSignal.timeout(6000) });
    if (perfil.ok) {
      const j = (await perfil.json()) as { properties?: { name: string; value: string }[] };
      const prop = j.properties?.find((p) => p.name === "textures");
      if (prop) {
        const t = JSON.parse(Buffer.from(prop.value, "base64").toString("utf8")) as { textures?: { SKIN?: { url: string; metadata?: { model?: string } } } };
        const url = t.textures?.SKIN?.url?.replace(/^http:/, "https:");
        if (url) {
          const img = await fetch(url, { signal: AbortSignal.timeout(6000) });
          if (img.ok) {
            const png = new Uint8Array(await img.arrayBuffer());
            const modelo = t.textures?.SKIN?.metadata?.model === "slim" ? "slim" : "classic";
            cache.set(uuid, { em: Date.now(), png, modelo });
            return resposta(png, modelo, "mojang");
          }
        }
      }
    }
  } catch { /* sem internet: cai no Steve */ }

  const steve = textura("minecraft/textures/entity/player/wide/steve.png");
  if (!steve) return new NextResponse(null, { status: 404 });
  return resposta(new Uint8Array(steve), "classic", "steve");
}

function resposta(png: Uint8Array, modelo: string, origem: string) {
  return new NextResponse(png as unknown as BodyInit, {
    headers: { "content-type": "image/png", "x-skin-modelo": modelo, "x-skin-origem": origem, "cache-control": "no-store" },
  });
}
