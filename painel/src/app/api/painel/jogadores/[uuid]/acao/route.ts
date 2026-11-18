import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { servidorMc } from "@/lib/servidor-mc";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Pedido = { acao?: string; valor?: string | number; alvo?: string };

const INT = /^-?\d{1,9}$/;
const ID = /^[a-z0-9_:.\-]{1,80}$/;

/** Ações de admin na ficha do jogador: viram comandos no console do servidor. */
export async function POST(req: Request, { params }: { params: Promise<{ uuid: string }> }) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "não autorizado" }, { status: 401 });
  const { uuid } = await params;
  const j = await prisma.jogador.findUnique({ where: { uuid } });
  if (!j) return NextResponse.json({ erro: "jogador não encontrado" }, { status: 404 });
  if (!/^\w{3,16}$/.test(j.nick)) return NextResponse.json({ erro: "nick inválido" }, { status: 400 });
  const { acao, valor, alvo } = ((await req.json().catch(() => ({}))) ?? {}) as Pedido;
  const v = String(valor ?? "").trim();
  const a = String(alvo ?? "").trim();
  const n = j.nick;
  const precisaInt = () => { if (!INT.test(v)) throw new Error("Número inválido."); return v; };
  const precisaId = (x: string) => { if (!ID.test(x)) throw new Error("Identificador inválido."); return x; };

  let cmd: string;
  try {
    switch (acao) {
      case "ficha": cmd = `frx admin ${n} ficha`; break;
      case "nivel": cmd = `frx nivel ${n} ${precisaInt()}`; break;
      case "xp": cmd = `frx xp ${n} ${precisaInt()}`; break;
      case "moedas": cmd = `frx moedas ${n} ${precisaInt()}`; break;
      case "tirarmoedas": cmd = `frx admin ${n} tirarmoedas ${precisaInt()}`; break;
      case "banco": cmd = `frx admin ${n} banco ${precisaInt()}`; break;
      // funciona com o jogador offline: o mod guarda o valor e aplica no próximo login
      case "bancoDefinir": if (!/^\d{1,9}$/.test(v)) throw new Error("Número inválido."); cmd = `frx bancooffline ${j.uuid} ${v}`; break;
      // bolsa exata: as moedas da bolsa são itens, então só com o jogador no jogo
      case "bolsaDefinir":
        if (!/^\d{1,9}$/.test(v)) throw new Error("Número inválido.");
        if (!j.online) throw new Error(`${n} precisa estar no jogo para a bolsa ser alterada. O banco pode ser alterado com ele fora.`);
        cmd = `frx admin ${n} bolsa ${v}`; break;
      case "classe": cmd = `frx classe ${n} ${precisaId(v.toLowerCase())}`; break;
      case "talentosResetar": cmd = `frx talentos resetar ${n}`; break;
      case "talento": cmd = `frx admin ${n} talento ${precisaId(a)} ${precisaInt()}`; break;
      case "reputacao": cmd = `frx admin ${n} reputacao ${precisaId(a)} ${precisaInt()}`; break;
      case "habAprender": cmd = `frx admin ${n} habilidade aprender ${precisaId(a)}`; break;
      case "habEsquecer": cmd = `frx admin ${n} habilidade esquecer ${precisaId(a)}`; break;
      case "habNivel": cmd = `frx admin ${n} habilidade nivel ${precisaId(a)} ${precisaInt()}`; break;
      case "habTodas": cmd = `frx admin ${n} habilidade todas`; break;
      case "curar": cmd = `frx admin ${n} curar`; break;
      case "dar": cmd = `give ${n} ${precisaId(a)} ${INT.test(v) ? v : "1"}`; break;
      case "modo": cmd = `gamemode ${precisaId(v)} ${n}`; break;
      case "op": cmd = `op ${n}`; break;
      case "deop": cmd = `deop ${n}`; break;
      default: throw new Error("Ação desconhecida.");
    }
    if (!["survival", "creative", "adventure", "spectator"].includes(v) && acao === "modo") throw new Error("Modo inválido.");
    servidorMc.comando(cmd);
    if (acao === "bancoDefinir") await prisma.jogador.update({ where: { uuid }, data: { banco: Number(v) } });
    if (acao === "bolsaDefinir") await prisma.jogador.update({ where: { uuid }, data: { moedas: Number(v) } });
    if (acao === "dar" || acao === "modo") setTimeout(() => { try { servidorMc.comando(`frx admin ${n} ficha`); } catch { /* */ } }, 800);
  } catch (e) {
    const msg = e instanceof Error ? e.message : "Erro";
    return NextResponse.json({ erro: msg.includes("não está rodando") ? "O servidor precisa estar ligado pelo painel para receber comandos." : msg }, { status: 400 });
  }
  return NextResponse.json({ ok: true, comando: cmd });
}
