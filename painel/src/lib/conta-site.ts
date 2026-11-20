import "server-only";
import crypto from "crypto";
import bcrypt from "bcryptjs";
import { cookies } from "next/headers";
import { prisma } from "@/lib/prisma";

/**
 * Conta do jogador no site. Primeiro acesso (ou senha esquecida): nick + código de 6 dígitos que o jogo mostra + senha nova.
 * Depois: nick + senha. A sessão é um cookie só de servidor (httpOnly), válido por 30 dias.
 * Nada aqui devolve UUID, e-mail nem IP para o navegador.
 */
export const COOKIE = "frx_conta";
export const DIAS_SESSAO = 30;
const MAX_ERROS = 5, TRAVA_MIN = 5;

type Conta = { uuid: string; senha: string; codigo: string; codigoAte: Date | null; erros: number; travadaAte: Date | null };
type Sessao = { id: string; uuid: string; expira: Date };
type Tabela<T> = {
  findUnique: (a: { where: Record<string, unknown> }) => Promise<T | null>;
  upsert: (a: { where: Record<string, unknown>; create: Record<string, unknown>; update: Record<string, unknown> }) => Promise<T>;
  update: (a: { where: Record<string, unknown>; data: Record<string, unknown> }) => Promise<T>;
  create: (a: { data: Record<string, unknown> }) => Promise<T>;
  deleteMany: (a: { where: Record<string, unknown> }) => Promise<unknown>;
};
const db = prisma as unknown as { contaSite: Tabela<Conta>; sessaoSite: Tabela<Sessao> };

const sha = (t: string) => crypto.createHash("sha256").update(t).digest("hex");
const NICK = /^[A-Za-z0-9_]{3,16}$/;
export type Resultado = { ok: true; token: string } | { ok: false; erro: string };

/** O servidor do jogo avisa o código que acabou de mostrar ao jogador. */
export async function guardarCodigo(uuid: string, codigo: string, minutos: number) {
  const dados = { codigo: sha(codigo), codigoAte: new Date(Date.now() + minutos * 60_000), erros: 0, travadaAte: null };
  await db.contaSite.upsert({ where: { uuid }, create: { uuid, ...dados }, update: dados });
}

async function contaDoNick(nick: string) {
  if (!NICK.test(nick)) return null;
  const j = await prisma.jogador.findFirst({ where: { nick: { equals: nick, mode: "insensitive" }, ativo: true }, select: { uuid: true } });
  return j ? { uuid: j.uuid, conta: await db.contaSite.findUnique({ where: { uuid: j.uuid } }) } : null;
}

async function errou(c: Conta, apagarCodigo: boolean) {
  const erros = c.erros + 1;
  const travar = erros >= MAX_ERROS;
  await db.contaSite.update({ where: { uuid: c.uuid }, data: {
    erros: travar ? 0 : erros, travadaAte: travar ? new Date(Date.now() + TRAVA_MIN * 60_000) : c.travadaAte,
    ...(travar && apagarCodigo ? { codigo: "", codigoAte: null } : {}),
  } });
}

async function abrirSessao(uuid: string): Promise<string> {
  const token = crypto.randomBytes(32).toString("hex");
  await db.sessaoSite.deleteMany({ where: { expira: { lt: new Date() } } });
  await db.sessaoSite.create({ data: { id: sha(token), uuid, expira: new Date(Date.now() + DIAS_SESSAO * 86_400_000) } });
  return token;
}

const travada = (c: Conta | null) => !!c?.travadaAte && c.travadaAte.getTime() > Date.now();
const ESPERE = `Muitas tentativas erradas. Espere ${TRAVA_MIN} minutos e tente de novo.`;

export async function entrar(nick: string, senha: string): Promise<Resultado> {
  const r = await contaDoNick(nick);
  const errado = "Nick ou senha incorretos. Se é o seu primeiro acesso, use a opção do código do jogo.";
  if (!r?.conta || !r.conta.senha) return { ok: false, erro: errado };
  if (travada(r.conta)) return { ok: false, erro: ESPERE };
  if (!(await bcrypt.compare(senha, r.conta.senha))) { await errou(r.conta, false); return { ok: false, erro: errado }; }
  await db.contaSite.update({ where: { uuid: r.uuid }, data: { erros: 0, travadaAte: null } });
  return { ok: true, token: await abrirSessao(r.uuid) };
}

/** Primeiro acesso ou senha esquecida: confere o código do jogo e grava a senha nova. As sessões antigas da conta caem. */
export async function criarSenha(nick: string, codigo: string, senha: string): Promise<Resultado> {
  if (senha.length < 8 || senha.length > 72) return { ok: false, erro: "A senha precisa ter de 8 a 72 caracteres." };
  const r = await contaDoNick(nick);
  const errado = "Código incorreto ou vencido. Aperte F9 dentro do jogo para ver um código novo.";
  if (!r?.conta || !r.conta.codigo || !r.conta.codigoAte || r.conta.codigoAte.getTime() < Date.now()) return { ok: false, erro: errado };
  if (travada(r.conta)) return { ok: false, erro: ESPERE };
  const igual = crypto.timingSafeEqual(Buffer.from(sha(codigo.trim())), Buffer.from(r.conta.codigo));
  if (!igual) { await errou(r.conta, true); return { ok: false, erro: errado }; }
  await db.contaSite.update({ where: { uuid: r.uuid }, data: { senha: await bcrypt.hash(senha, 10), codigo: "", codigoAte: null, erros: 0, travadaAte: null } });
  await db.sessaoSite.deleteMany({ where: { uuid: r.uuid } });
  return { ok: true, token: await abrirSessao(r.uuid) };
}

/** Quem está logado neste navegador (a linha do Jogador), ou null. */
export async function contaLogada() {
  const token = (await cookies()).get(COOKIE)?.value;
  if (!token || !/^[0-9a-f]{64}$/.test(token)) return null;
  const s = await db.sessaoSite.findUnique({ where: { id: sha(token) } }).catch(() => null);
  if (!s || s.expira.getTime() < Date.now()) return null;
  const j = await prisma.jogador.findUnique({ where: { uuid: s.uuid } });
  return j && j.ativo ? j : null;
}

export async function sair() {
  const token = (await cookies()).get(COOKIE)?.value;
  if (token) await db.sessaoSite.deleteMany({ where: { id: sha(token) } }).catch(() => null);
}
