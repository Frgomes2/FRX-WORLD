import { prisma } from "@/lib/prisma";
import { pacoteRecursos } from "@/lib/codex";
import { TelaCodex } from "./tela";

export const dynamic = "force-dynamic";

export default async function CodexPage() {
  const armas = await prisma.armaCodex.findMany({ orderBy: [{ rank: "asc" }, { nome: "asc" }] });
  const pacote = await pacoteRecursos().catch(() => null);
  return (
    <>
      <div className="mb-5">
        <h1 className="font-titulo text-3xl">Códex de Armas</h1>
        <p className="text-pedra-500">
          Armas únicas do servidor. Não caem de monstro nem de baú: só saem do ferreiro ou como recompensa de missão.
          Você define o rank, quantas existem no mundo, a aparência e se a arma evolui com o dono.
        </p>
      </div>
      <TelaCodex
        armas={armas.map((a) => ({
          id: a.id, codigo: a.codigo, nome: a.nome, descricao: a.descricao, itemBase: a.itemBase, rank: a.rank, raridade: a.raridade, dano: a.dano,
          limite: a.limite, ferreiro: a.ferreiro, preco: a.preco, nivelMin: a.nivelMin, evolui: a.evolui, danoPorNivel: a.danoPorNivel,
          umaPorJogador: a.umaPorJogador, inquebravel: (a as typeof a & { inquebravel?: boolean }).inquebravel ?? false, nivelUso: (a as typeof a & { nivelUso?: boolean }).nivelUso ?? false, encantos: a.encantos, textura: a.textura, emitidas: a.emitidas, ativa: a.ativa,
          donos: Object.values((a.donos ?? {}) as Record<string, string>),
        }))}
        armasNoPacote={pacote?.armas ?? 0}
      />
    </>
  );
}
