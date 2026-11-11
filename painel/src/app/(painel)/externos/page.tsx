import { prisma } from "@/lib/prisma";
import { lootParaTexto } from "@/lib/dungeons";
import { carregarSugestoes, excluirChefe, excluirEstrutura } from "./actions";
import { LinhaChefe, LinhaEstrutura } from "./linhas";

export default async function ExternosPage() {
  const [chefes, estruturas] = await Promise.all([
    prisma.chefeExterno.findMany({ orderBy: [{ nivelRecomendado: "asc" }, { nome: "asc" }] }),
    prisma.estruturaExterna.findMany({ orderBy: [{ nivelRecomendado: "asc" }, { nome: "asc" }] }),
  ]);
  const vazio = chefes.length === 0 && estruturas.length === 0;

  return (
    <>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Chefes de mods</h1>
          <p className="text-pedra-500">
            Chefes e dungeons do Cataclysm, Dungeon Now Loading, IDAS e YUNG&apos;s ligados ao FRX: o chefe paga XP e loot a quem
            estiver por perto (até 48 blocos) respeitando a recarga, e conta para missões de caça (use a entidade como alvo).
            Ao entrar numa estrutura, o jogador vê o nome e o nível recomendado.
          </p>
        </div>
        <form action={carregarSugestoes}>
          <button className="shrink-0 rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">
            {vazio ? "Carregar sugestões" : "Adicionar sugestões que faltam"}
          </button>
        </form>
      </div>

      <section className="mb-10 rounded-xl border border-pedra-700 bg-pedra-900 p-3 md:p-5">
        <h2 className="mb-3 font-titulo text-xl text-ouro">Chefes</h2>
        <div className="overflow-x-auto"><div className="min-w-[860px]">
        <div className="grid grid-cols-[1.4fr_1.2fr_0.6fr_0.7fr_0.6fr_1.6fr_auto] gap-2 text-xs uppercase tracking-wider text-pedra-500">
          <span>Entidade</span><span>Nome</span><span>Nível</span><span>XP</span><span>Recarga h</span><span>Loot (item*qtd@chance)</span><span />
        </div>
        {chefes.map((c) => (
          <div key={c.id} className="flex items-center gap-2">
            <div className="flex-1">
              <LinhaChefe c={{ ...c, loot: lootParaTexto(c.loot) }} />
            </div>
            <form action={excluirChefe}><input type="hidden" name="id" value={c.id} /><button className="text-xs text-red-400 hover:underline">Excluir</button></form>
          </div>
        ))}
        <p className="mt-4 text-xs text-pedra-500">Novo chefe:</p>
        <LinhaChefe c={{ entidade: "", nome: "", origem: "", nivelRecomendado: 10, recompensaXp: 1000, recargaHoras: 24, loot: "", ativo: true }} />
        </div></div>
      </section>

      <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-3 md:p-5">
        <h2 className="mb-3 font-titulo text-xl text-ouro">Estruturas</h2>
        <div className="overflow-x-auto"><div className="min-w-[620px]">
        <div className="grid grid-cols-[1.6fr_1.4fr_0.8fr_0.6fr_auto] gap-2 text-xs uppercase tracking-wider text-pedra-500">
          <span>Estrutura</span><span>Nome</span><span>Mod</span><span>Nível</span><span />
        </div>
        {estruturas.map((e) => (
          <div key={e.id} className="flex items-center gap-2">
            <div className="flex-1"><LinhaEstrutura e={e} /></div>
            <form action={excluirEstrutura}><input type="hidden" name="id" value={e.id} /><button className="text-xs text-red-400 hover:underline">Excluir</button></form>
          </div>
        ))}
        <p className="mt-4 text-xs text-pedra-500">Nova estrutura:</p>
        <LinhaEstrutura e={{ estrutura: "", nome: "", origem: "", nivelRecomendado: 10, ativa: true }} />
        </div></div>
      </section>
    </>
  );
}
