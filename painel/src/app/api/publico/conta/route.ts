import { NextResponse } from "next/server";
import { z } from "zod";
import { COOKIE, DIAS_SESSAO, criarSenha, entrar, sair } from "@/lib/conta-site";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const schema = z.discriminatedUnion("acao", [
  z.object({ acao: z.literal("entrar"), nick: z.string().max(16), senha: z.string().max(72) }),
  z.object({ acao: z.literal("criar"), nick: z.string().max(16), codigo: z.string().max(12), senha: z.string().max(72) }),
  z.object({ acao: z.literal("sair") }),
]);

/** Entrar, criar a senha com o código do jogo, ou sair. A sessão vai num cookie que o JavaScript da página não lê. */
export async function POST(req: Request) {
  const d = schema.safeParse(await req.json().catch(() => null));
  if (!d.success) return NextResponse.json({ erro: "Preencha os campos." }, { status: 400 });
  if (d.data.acao === "sair") {
    await sair();
    const r = NextResponse.json({ ok: true });
    r.cookies.delete(COOKIE);
    return r;
  }
  const res = d.data.acao === "entrar" ? await entrar(d.data.nick.trim(), d.data.senha) : await criarSenha(d.data.nick.trim(), d.data.codigo, d.data.senha);
  if (!res.ok) return NextResponse.json({ erro: res.erro }, { status: 401 });
  const r = NextResponse.json({ ok: true });
  const https = req.headers.get("x-forwarded-proto") === "https" || !!req.headers.get("cf-ray");
  r.cookies.set(COOKIE, res.token, { httpOnly: true, sameSite: "lax", secure: https, path: "/", maxAge: DIAS_SESSAO * 86_400 });
  return r;
}
