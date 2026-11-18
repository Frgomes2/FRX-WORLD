import { readFile } from "node:fs/promises";
import path from "node:path";
import Link from "next/link";
import { notFound } from "next/navigation";
import { prisma } from "@/lib/prisma";
import { FichaJogador } from "./ficha";

type Retrato = { nivel?: number; rank?: string; classe?: string; titulo?: string; conquistas?: number; mortes?: number; moedas?: number };

export default async function JogadorPage({ params, searchParams }: { params: Promise<{ uuid: string }>; searchParams: Promise<{ vaga?: string }> }) {
  const { uuid } = await params;
  const pedida = Number((await searchParams).vaga ?? 0);
  const j = await prisma.jogador.findUnique({ where: { uuid } });
  if (!j) notFound();
  // admin do servidor = está no ops.json do Minecraft
  const ops = await readFile(path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server", "ops.json"), "utf8")
    .then((t) => JSON.parse(t) as { uuid?: string; name?: string }[]).catch(() => []);
  const ehOp = ops.some((o) => o.uuid === j.uuid || o.name?.toLowerCase() === j.nick.toLowerCase());
  type Linha = { vaga: number; nome: string; classe: string; raca: string; nivel: number; rank: string; emUso: boolean; base: unknown; detalhe: unknown; detalheEm: Date | null };
  const personagens = (await prisma.personagem.findMany({ where: { uuid: j.uuid }, orderBy: { vaga: "asc" } })) as unknown as Linha[];
  // personagem fora de uso: a ficha vem do retrato guardado na última vez em que ele jogou
  const outro = personagens.find((p) => p.vaga === pedida && !p.emUso) ?? null;
  const retrato = (outro?.base ?? null) as unknown as Retrato | null;
  const vendo = outro && retrato ? outro : null;
  return (
    <>
    {personagens.length > 0 && (
      <section className="mb-5 rounded-xl border border-pedra-700 bg-pedra-900 p-4">
        <h2 className="mb-1 font-titulo text-lg">Personagens da conta</h2>
        <p className="mb-3 text-xs text-pedra-500">Clique num personagem para ver a ficha dele. Banco e terrenos são da conta; o resto é de cada personagem.</p>
        <div className="grid gap-3 sm:grid-cols-3">
          {personagens.map((p) => (
            <Link key={p.vaga} href={p.emUso ? `/admin/jogadores/${j.uuid}` : `/admin/jogadores/${j.uuid}?vaga=${p.vaga}`}
              className={`block rounded-lg border p-3 hover:border-ouro ${(vendo ? vendo.vaga === p.vaga : p.emUso) ? "border-ouro bg-pedra-800" : "border-pedra-700"}`}>
              <p className="flex items-center justify-between gap-2"><span className="truncate font-semibold">{p.nome || j.nick}</span>
                <span className={`text-xs ${p.emUso ? "text-ouro" : "text-pedra-500"}`}>{p.emUso ? "em uso" : `vaga ${p.vaga}`}</span></p>
              <p className="text-sm text-pedra-500">{p.classe || "sem classe"}{p.raca ? ` · ${p.raca}` : ""}</p>
              <p className="text-sm text-pedra-500">Nível {p.nivel}{p.rank ? ` · Rank ${p.rank}` : ""}</p>
              {!p.emUso && <p className="mt-1 text-xs text-pedra-500">{p.base ? "Clique para ver a ficha guardada" : "Ainda sem ficha guardada: aparece depois que ele jogar"}</p>}
            </Link>
          ))}
        </div>
      </section>
    )}
    {vendo && (
      <p className="mb-4 rounded-md border border-ouro/50 bg-pedra-900 px-3 py-2 text-sm">
        Você está vendo <b>{vendo.nome || j.nick}</b>, que não está em uso. É o retrato de {vendo.detalheEm ? vendo.detalheEm.toLocaleString("pt-BR") : "quando ele jogou pela última vez"}.
        As ações de admin só valem para o personagem em uso.
      </p>
    )}
    <FichaJogador
      key={vendo ? `vaga-${vendo.vaga}` : "em-uso"}
      congelado={vendo ? (vendo.nome || j.nick) : undefined}
      base={vendo && retrato ? {
        uuid: j.uuid, nick: j.nick, nivel: retrato.nivel ?? vendo.nivel, rank: retrato.rank ?? j.rank, classe: retrato.classe ?? "", titulo: retrato.titulo ?? "",
        conquistas: retrato.conquistas ?? 0, mortes: retrato.mortes ?? 0, moedas: retrato.moedas ?? 0, banco: j.banco, ativo: j.ativo, criadoEm: j.criadoEm.getTime(), ultimoLogin: j.ultimoLogin?.getTime() ?? null,
      } : {
        uuid: j.uuid, nick: j.nick, nivel: j.nivel, rank: j.rank, classe: j.classe, titulo: j.titulo, conquistas: j.conquistas,
        mortes: j.mortes, moedas: j.moedas, banco: j.banco, ativo: j.ativo, criadoEm: j.criadoEm.getTime(), ultimoLogin: j.ultimoLogin?.getTime() ?? null,
      }}
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      inicial={((vendo ? vendo.detalhe : j.detalhe) as any) ?? null}
      inicialEm={(vendo ? vendo.detalheEm : j.detalheEm)?.getTime() ?? null}
      inicialOnline={vendo ? false : j.online}
      inicialOp={ehOp}
    />
    </>
  );
}
