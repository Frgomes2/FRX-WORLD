import { z } from "zod";
import { prisma } from "@/lib/prisma";
import { itemLoot, lootParaTexto } from "@/lib/dungeons";
import { carregarSugestoes } from "./actions";
import { ListaExternos } from "./lista";

export default async function ExternosPage() {
  const [chefes, estruturas] = await Promise.all([
    prisma.chefeExterno.findMany({ orderBy: [{ nivelRecomendado: "asc" }, { nome: "asc" }] }),
    prisma.estruturaExterna.findMany({ orderBy: [{ nivelRecomendado: "asc" }, { nome: "asc" }] }),
  ]);
  const vazio = chefes.length === 0 && estruturas.length === 0;
  const ativos = chefes.filter((c) => c.ativo).length;

  return (
    <>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div className="max-w-3xl">
          <h1 className="font-titulo text-3xl">Chefes de mods</h1>
          <p className="text-pedra-500">
            Chefes e estruturas dos outros mods ligados ao FRX. Quem estiver a até 48 blocos quando o chefe morre ganha a XP e o loot
            (respeitando a recarga), e o abate conta para missões de caça. Ao entrar numa estrutura, o jogador vê o nome e o nível recomendado.
          </p>
        </div>
        <form action={carregarSugestoes}>
          <button className="shrink-0 rounded-md border border-pedra-700 px-4 py-2 text-sm hover:border-ouro hover:text-ouro">
            {vazio ? "Carregar sugestões" : "Adicionar sugestões que faltam"}
          </button>
        </form>
      </div>

      <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[["Chefes", chefes.length], ["Ativos", ativos], ["Estruturas", estruturas.length],
          ["Nível mais alto", chefes.reduce((m, c) => Math.max(m, c.nivelRecomendado), 0)]].map(([t, v]) => (
          <div key={t} className="rounded-xl border border-pedra-700 bg-pedra-900 p-3">
            <p className="text-xs uppercase tracking-wider text-pedra-500">{t}</p>
            <p className="font-titulo text-2xl text-ouro">{v}</p>
          </div>
        ))}
      </div>

      <ListaExternos
        chefes={chefes.map((c) => {
          const itens = z.array(itemLoot).safeParse(c.loot);
          return { id: c.id, entidade: c.entidade, nome: c.nome, origem: c.origem, nivelRecomendado: c.nivelRecomendado, recompensaXp: c.recompensaXp,
            recargaHoras: c.recargaHoras, loot: lootParaTexto(c.loot), itens: itens.success ? itens.data : [], ativo: c.ativo };
        })}
        estruturas={estruturas.map((e) => ({ id: e.id, estrutura: e.estrutura, nome: e.nome, origem: e.origem, nivelRecomendado: e.nivelRecomendado, ativa: e.ativa }))}
      />
    </>
  );
}
