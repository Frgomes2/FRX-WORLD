import { prisma } from "@/lib/prisma";
import { alternarMasmorra, excluirMasmorra, refazerMasmorra } from "./actions";
import { FormMasmorra } from "./linha";

const data = (d: Date | null) => (d ? d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" }) : "");
const botao = "rounded-md border border-pedra-700 px-2 py-1 text-xs hover:border-ouro";

export default async function MasmorrasPage() {
  const lista = await prisma.masmorra.findMany({ orderBy: [{ ordem: "asc" }, { id: "asc" }] });

  return (
    <>
      <h1 className="font-titulo text-3xl">Masmorras</h1>
      <p className="mb-6 text-pedra-500">
        Estruturas grandes de outros mods em pontos fixos do mapa. O servidor coloca cada uma <b>uma vez por mundo, ao ligar</b>
        (no ponto ou até ~150 blocos dele, se o terreno não servir). Mudar a posição de uma que já surgiu não move nada:
        use <b>Recolocar</b> e ela é gerada de novo no próximo início (a antiga continua no mundo). No jogo: <code>/masmorras</code>.
      </p>

      <div className="space-y-4">
        {lista.map((m) => (
          <section key={m.id} className={`rounded-xl border border-pedra-700 bg-pedra-900 p-4 ${m.ativo ? "" : "opacity-50"}`}>
            <div className="mb-3 flex flex-wrap items-center gap-3">
              <h2 className="font-titulo text-xl text-ouro">☠ {m.nome}</h2>
              {m.colocadaX !== null
                ? <span className="text-xs text-green-400">no mundo em {m.colocadaX}, {m.colocadaY}, {m.colocadaZ}{m.colocadaEstrutura ? ` (${m.colocadaEstrutura})` : ""} {data(m.colocadaEm)}</span>
                : <span className="text-xs text-pedra-500">ainda não surgiu</span>}
              {m.refazer && <span className="text-xs text-yellow-300">será recolocada no próximo início</span>}
              <div className="flex flex-wrap gap-2 sm:ml-auto">
                <form action={refazerMasmorra}><input type="hidden" name="id" value={m.id} />
                  <button className={botao}>{m.refazer ? "Cancelar recolocar" : "Recolocar"}</button></form>
                <form action={alternarMasmorra}><input type="hidden" name="id" value={m.id} />
                  <button className={botao}>{m.ativo ? "Desativar" : "Ativar"}</button></form>
                <form action={excluirMasmorra}><input type="hidden" name="id" value={m.id} />
                  <button className={`${botao} text-red-400 hover:border-red-400`}>Excluir</button></form>
              </div>
            </div>
            <FormMasmorra valores={m} />
          </section>
        ))}

        <section className="rounded-xl border border-dashed border-pedra-700 p-4">
          <h2 className="mb-3 font-titulo text-xl">Nova masmorra</h2>
          <FormMasmorra />
          <p className="mt-3 text-xs text-pedra-500">
            Exemplos de estruturas: cataclysm:ancient_factory, cataclysm:frosted_prison, cataclysm:cursed_pyramid, cataclysm:burning_arena,
            cataclysm:soul_black_smith, mowziesmobs:wrought_chamber, mowziesmobs:monastery, iceandfire:gorgon_temple,
            dungeons_arise:foundry, dungeons_arise:keep_kayra, dungeons_arise:shiraz_palace, dungeons_arise:plague_asylum.
          </p>
        </section>
      </div>
    </>
  );
}
