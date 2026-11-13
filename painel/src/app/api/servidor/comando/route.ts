import { NextResponse } from "next/server";
import { servidorMc } from "@/lib/servidor-mc";
import { naoAutorizado, tokenValido } from "@/lib/servidor";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Manda um comando para o console do Minecraft. Usado pelo agente (controle\pedido.txt: "comando <texto>").
 * Só vale para chamadas feitas do próprio PC: o que chega pelo túnel da Cloudflare é recusado, mesmo com o token.
 */
export async function POST(req: Request) {
  if (!tokenValido(req)) return naoAutorizado();
  if (req.headers.get("cf-connecting-ip") || req.headers.get("cf-ray"))
    return NextResponse.json({ erro: "só local" }, { status: 403 });
  let texto = "";
  try {
    texto = String(((await req.json()) as { comando?: unknown }).comando ?? "").trim();
  } catch {
    return NextResponse.json({ erro: "corpo inválido" }, { status: 400 });
  }
  if (!texto || texto.length > 500 || /[\r\n]/.test(texto)) return NextResponse.json({ erro: "comando inválido" }, { status: 400 });
  try {
    servidorMc.comando(texto);
  } catch (e) {
    return NextResponse.json({ ok: false, erro: e instanceof Error ? e.message : "falhou" }, { status: 409 });
  }
  return NextResponse.json({ ok: true });
}
