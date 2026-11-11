import { NextResponse, type NextRequest } from "next/server";

/**
 * As rotas /api/servidor/* são só para o mod e para os atalhos, que chamam http://localhost:3000.
 * Quem chega pelo Cloudflare Tunnel (craft.frgomes.com.br) traz o cabeçalho cf-connecting-ip:
 * essas chamadas são recusadas, mesmo com o token certo.
 */
export function proxy(req: NextRequest) {
  if (req.headers.get("cf-connecting-ip") || req.headers.get("cf-ray")) {
    return NextResponse.json({ erro: "Rota disponível só no servidor local." }, { status: 403 });
  }
  return NextResponse.next();
}

export const config = { matcher: "/api/servidor/:path*" };
