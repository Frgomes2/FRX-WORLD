import fs from "fs";
import path from "path";
import Link from "next/link";
import { prisma } from "@/lib/prisma";
import { contaLogada } from "@/lib/conta-site";
import { FichaJogador } from "../admin/jogadores/[uuid]/ficha";
import { BotaoSair, EntrarConta } from "./entrar";
import { PainelConta } from "./painel";
import { dadosPublicos, mapaPublico, perfilPublico } from "@/lib/publico";
import { lerConfig, xpParaProximo } from "@/lib/config-jogo";

export const dynamic = "force-dynamic";
export const metadata = { title: "Minha conta · FRX-WORLD" };

type Retrato = { nivel?: number; rank?: string; classe?: string; titulo?: string; conquistas?: number; mortes?: number; moedas?: number };
type Linha = { vaga: number; nome: string; classe: string; raca: string; nivel: number; rank: string; emUso: boolean; base: unknown; detalhe: unknown; detalheEm: Date | null };

/** Terrenos da conta, lidos de <mundo>/frx_terrenos.json. */
function terrenosDa(uuid: string) {
  try {
    const servidor = path.resolve(process.cwd(), process.env.SERVIDOR_DIR ?? "../server");
    const prop = fs.readFileSync(path.join(servidor, "server.properties"), "utf8").match(/^level-name=(.*)$/m)?.[1]?.trim() || "world";
    const raiz = JSON.parse(fs.readFileSync(path.join(servidor, prop, "frx_terrenos.json"), "utf8")) as { lotes?: { dono?: string; x0: number; z0: number; x1: number; z1: number; pago?: number }[] };
    return (raiz.lotes ?? []).filter((l) => l.dono === uuid).map((l) => ({
      x: Math.round((l.x0 + l.x1) / 2), z: Math.round((l.z0 + l.z1) / 2),
      largura: Math.abs(l.x1 - l.x0) + 1, fundo: Math.abs(l.z1 - l.z0) + 1, pago: l.pago ?? 0,
    }));
  } catch { return []; }
}

export default async function ContaPage({ searchParams }: { searchParams: Promise<{ vaga?: string }> }) {
  const j = await contaLogada();
  if (!j) {
    return (
      <main className="min-h-screen px-4 py-10">
        <p className="mx-auto mb-6 max-w-md"><Link href="/" className="text-sm text-pedra-500 hover:text-ouro">← Voltar ao site</Link></p>
        <EntrarConta />
      </main>
    );
  }
  const pedida = Number((await searchParams).vaga ?? 0);
  const personagens = (await prisma.personagem.findMany({ where: { uuid: j.uuid }, orderBy: { vaga: "asc" } }).catch(() => [])) as unknown as Linha[];
  const outro = personagens.find((p) => p.vaga === pedida && !p.emUso) ?? null;
  const retrato = (outro?.base ?? null) as unknown as Retrato | null;
  const vendo = outro && retrato ? outro : null;
  const emUso = personagens.find((p) => p.emUso) ?? null;
  const terrenos = terrenosDa(j.uuid);
  const quando = (vendo ? vendo.detalheEm : j.detalheEm) ?? null;

  const vagaVista = vendo?.vaga ?? 0;
  const vagaDele = vendo?.vaga ?? emUso?.vaga ?? 0;
  const [perfil, config, dados] = await Promise.all([
    perfilPublico(j.nick, vagaVista).catch(() => null), lerConfig().catch(() => null), dadosPublicos().catch(() => null),
  ]);
  const lugar = (lista: { conta: string; vaga: number }[] | undefined) =>
    (lista ?? []).findIndex((l) => l.conta.toLowerCase() === j.nick.toLowerCase() && (l.vaga === vagaDele || l.vaga === 0)) + 1;
  const meu = dados?.rankings.fortes[lugar(dados?.rankings.fortes) - 1];
  const posicoes = {
    fortes: lugar(dados?.rankings.fortes), ricos: lugar(dados?.rankings.ricos), conquistas: lugar(dados?.rankings.conquistas),
    semana: meu?.semana ?? 0, mudou: meu?.mudou ?? 0,
  };
  const mapa = terrenos.length ? mapaPublico() : null;

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-4 py-8">
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <Link href="/" className="text-sm text-pedra-500 hover:text-ouro">← Voltar ao site</Link>
        <p className="flex-1 text-sm text-pedra-500">Conta <b className="text-pergaminho">{j.nick}</b></p>
        <BotaoSair />
      </div>

      <PainelConta
        nick={j.nick} perfil={perfil} vagaVista={vagaVista}
        personagens={personagens.map((p) => ({ vaga: p.vaga, nome: p.nome || j.nick, classe: p.classe, raca: p.raca, nivel: p.nivel, rank: p.rank, emUso: p.emUso, temFicha: p.emUso || !!p.base }))}
        xpProximo={config ? xpParaProximo(config, perfil?.nivel ?? j.nivel) : 1}
        posicoes={posicoes} banco={j.banco} bolsa={vendo && retrato ? retrato.moedas ?? 0 : j.moedas} terrenos={terrenos} mapa={mapa}
      />

      <h2 className="mb-2 mt-8 font-titulo text-lg text-ouro">Inventário e ficha completa</h2>
      <p className="mb-4 rounded-md border border-pedra-700 bg-pedra-900 px-3 py-2 text-sm text-pedra-500">
        {vendo ? <>Você está vendo <b className="text-pergaminho">{vendo.nome || j.nick}</b>, que não está em uso. </> : null}
        Esta ficha é um retrato {quando ? `de ${quando.toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })}` : "da última vez em que você jogou"}: ela se atualiza enquanto você joga. Aqui é só consulta; nada muda no jogo por esta página.
      </p>

      <FichaJogador
        key={vendo ? `vaga-${vendo.vaga}` : "em-uso"}
        leitura
        congelado={vendo ? vendo.nome || j.nick : emUso?.nome || j.nick}
        base={vendo && retrato ? {
          uuid: j.uuid, nick: j.nick, nivel: retrato.nivel ?? vendo.nivel, rank: retrato.rank ?? j.rank, classe: retrato.classe ?? "", titulo: retrato.titulo ?? "",
          conquistas: retrato.conquistas ?? 0, mortes: retrato.mortes ?? 0, moedas: retrato.moedas ?? 0, banco: j.banco, ativo: j.ativo, criadoEm: j.criadoEm.getTime(), ultimoLogin: j.ultimoLogin?.getTime() ?? null,
        } : {
          uuid: j.uuid, nick: j.nick, nivel: j.nivel, rank: j.rank, classe: j.classe, titulo: j.titulo, conquistas: j.conquistas,
          mortes: j.mortes, moedas: j.moedas, banco: j.banco, ativo: j.ativo, criadoEm: j.criadoEm.getTime(), ultimoLogin: j.ultimoLogin?.getTime() ?? null,
        }}
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        inicial={((vendo ? vendo.detalhe : j.detalhe) as any) ?? null}
        inicialEm={quando?.getTime() ?? null}
        inicialOnline={vendo ? false : j.online}
        inicialOp={false}
      />
    </main>
  );
}
