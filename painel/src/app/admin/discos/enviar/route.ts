import fs from "fs";
import { NextResponse } from "next/server";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";
import { PASTA_DISCOS, arquivoDoDisco, lerOgg, temArquivo } from "@/lib/discos";
import { TAMANHO_MAX_DISCO } from "@/lib/discos-arte";
import { pngDaTextura } from "@/lib/codex";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const campos = z.object({
  id: z.string().max(40),
  nome: z.string().trim().min(2, "dê um nome ao disco").max(40),
  artista: z.string().trim().max(40),
  estilo: z.string().trim().max(24),
  descricao: z.string().trim().max(160),
  preco: z.coerce.number().int().min(0).max(100_000_000),
  rankMin: z.coerce.number().int().min(0).max(6),
  limite: z.coerce.number().int().min(0).max(100000),
  arte: z.coerce.number().int().min(1).max(16),
  ordem: z.coerce.number().int().min(0).max(9999),
});

function slug(nome: string) {
  return nome.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 30).replace(/_+$/, "");
}

/** Cria ou edita um disco. O áudio vem junto no formulário (campo "arquivo"); ao editar, pode vir vazio para manter o atual. */
export async function POST(req: Request) {
  if (!(await auth())?.user) return NextResponse.json({ erro: "Não autorizado" }, { status: 401 });
  const form = await req.formData().catch(() => null);
  if (!form) return NextResponse.json({ erro: "Não consegui ler o envio. O arquivo passa de 9 MB?" }, { status: 400 });
  const dados = campos.safeParse(Object.fromEntries(["id", "nome", "artista", "estilo", "descricao", "preco", "rankMin", "limite", "arte", "ordem"].map((k) => [k, String(form.get(k) ?? "")])));
  if (!dados.success) return NextResponse.json({ erro: dados.error.issues[0]?.message ?? "dados inválidos" }, { status: 400 });
  const d = dados.data;
  const ativo = form.get("ativo") === "1";
  const texturaCrua = String(form.get("textura") ?? "").replace(/^data:image\/png;base64,/, "");
  if (texturaCrua && !pngDaTextura(texturaCrua)) return NextResponse.json({ erro: "O desenho do disco precisa ser um PNG quadrado de 16×16 ou 32×32." }, { status: 400 });

  const arq = form.get("arquivo");
  let audio: { buf: Buffer; canais: number; duracao: number } | null = null;
  if (arq instanceof File && arq.size > 0) {
    if (arq.size > TAMANHO_MAX_DISCO) return NextResponse.json({ erro: "O arquivo passa de 9 MB. Exporte com qualidade menor (96 kbps, mono)." }, { status: 400 });
    const buf = Buffer.from(await arq.arrayBuffer());
    const info = lerOgg(buf);
    if (!info) return NextResponse.json({ erro: "O arquivo precisa ser .ogg (OGG Vorbis). MP3 e outros formatos o Minecraft não toca: converta antes." }, { status: 400 });
    audio = { buf, canais: info.canais, duracao: info.duracao };
  }

  const atual = d.id ? await prisma.disco.findUnique({ where: { id: d.id } }) : null;
  if (d.id && !atual) return NextResponse.json({ erro: "Esse disco não existe mais." }, { status: 404 });
  if (!atual && !audio) return NextResponse.json({ erro: "Escolha o arquivo .ogg da música." }, { status: 400 });

  let codigo = atual?.codigo ?? slug(d.nome);
  if (!atual) {
    if (!codigo) codigo = "disco";
    const base = codigo;
    for (let n = 2; await prisma.disco.findUnique({ where: { codigo } }); n++) codigo = `${base.slice(0, 27)}_${n}`;
  }
  if (audio) {
    fs.mkdirSync(PASTA_DISCOS, { recursive: true });
    fs.writeFileSync(arquivoDoDisco(codigo), audio.buf);
  } else if (!temArquivo(codigo)) {
    return NextResponse.json({ erro: "O áudio deste disco sumiu da pasta. Envie o arquivo de novo." }, { status: 400 });
  }
  const comum = { nome: d.nome, artista: d.artista, estilo: d.estilo, descricao: d.descricao, preco: d.preco, rankMin: d.rankMin, limite: d.limite, arte: d.arte, textura: texturaCrua, ordem: d.ordem, ativo,
    ...(audio ? { duracao: audio.duracao, canais: audio.canais, tamanho: audio.buf.length } : {}) };
  if (atual) await prisma.disco.update({ where: { id: atual.id }, data: comum });
  else await prisma.disco.create({ data: { codigo, ...comum } });
  revalidatePath("/admin/discos");
  return NextResponse.json({ ok: true, aviso: audio && audio.canais === 2 ? "Salvo. A música é estéreo: ela toca no volume cheio para quem estiver perto ao começar, sem diminuir com a distância. Em mono ela diminui como os discos do jogo." : "" });
}

/** Prévia para o admin ouvir o disco no painel: /admin/discos/enviar?codigo=... */
export async function GET(req: Request) {
  if (!(await auth())?.user) return new NextResponse(null, { status: 401 });
  const codigo = new URL(req.url).searchParams.get("codigo") ?? "";
  if (!/^[a-z0-9_]{1,30}$/.test(codigo) || !temArquivo(codigo)) return new NextResponse(null, { status: 404 });
  return new NextResponse(new Uint8Array(fs.readFileSync(arquivoDoDisco(codigo))), { headers: { "content-type": "audio/ogg", "cache-control": "no-store" } });
}
