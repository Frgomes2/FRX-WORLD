import { prisma } from "@/lib/prisma";
import { lootParaTexto } from "@/lib/dungeons";
import { lerConfigEventos } from "@/lib/eventos";
import { carregarSugestoesEventos, excluirEvento } from "./actions";
import { FormConfig, FormEvento } from "./linhas";

export default async function EventosPage() {
  const [config, eventos] = await Promise.all([
    lerConfigEventos(),
    prisma.eventoEstrada.findMany({ orderBy: [{ nivelMin: "asc" }, { nome: "asc" }] }),
  ]);
  const pesoTotal = eventos.filter((e) => e.ativo).reduce((s, e) => s + e.peso, 0);

  return (
    <>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Eventos de estrada</h1>
          <p className="text-pedra-500">
            Enquanto o jogador viaja, o servidor sorteia eventos pelo nível dele: emboscadas, chefes errantes, mercadores raros e tesouros.
            Quem derrotar todos os inimigos (ou chegar ao tesouro) ganha o XP e o loot.
          </p>
        </div>
        <form action={carregarSugestoesEventos}>
          <button className="shrink-0 rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">
            {eventos.length === 0 ? "Carregar sugestões" : "Adicionar sugestões que faltam"}
          </button>
        </form>
      </div>

      <section className="mb-8 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
        <h2 className="mb-3 font-titulo text-xl text-ouro">Frequência</h2>
        <FormConfig c={config} />
        <p className="mt-3 text-xs text-pedra-500">
          Com estes valores, quem está viajando tem em média 1 evento a cada{" "}
          {config.chance > 0 ? Math.round(Math.max(config.checagemSegundos / (config.chance / 100), config.recargaMinutos * 60 + config.checagemSegundos) / 60) : "∞"} min.
        </p>
      </section>

      <section className="rounded-xl border border-pedra-700 bg-pedra-900 p-5">
        <h2 className="mb-3 font-titulo text-xl text-ouro">Eventos</h2>
        <div className="flex flex-col gap-5">
          {eventos.map((e) => (
            <div key={e.id} className="rounded-lg border border-pedra-800 p-3">
              <div className="mb-2 flex justify-between text-xs text-pedra-500">
                <span>{e.ativo && pesoTotal > 0 ? `${Math.round((e.peso / pesoTotal) * 100)}% dos sorteios (entre os do nível)` : "desativado"}</span>
                <form action={excluirEvento}><input type="hidden" name="id" value={e.id} /><button className="text-red-400 hover:underline">Excluir</button></form>
              </div>
              <FormEvento e={{ ...e, loot: lootParaTexto(e.loot) }} />
            </div>
          ))}
          <div className="rounded-lg border border-dashed border-pedra-700 p-3">
            <p className="mb-2 text-xs text-pedra-500">Novo evento</p>
            <FormEvento e={{ nome: "", tipo: "EMBOSCADA", mensagem: "", peso: 10, nivelMin: 1, nivelMax: 1000, mobTipo: "minecraft:zombie", mobQtd: 4, vida: 0, dano: 0, preco: 0, recompensaXp: 200, loot: "", ativo: true }} />
          </div>
        </div>
      </section>
    </>
  );
}
