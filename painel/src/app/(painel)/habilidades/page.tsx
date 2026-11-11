import { prisma } from "@/lib/prisma";
import { CLASSES, TIPOS } from "@/lib/habilidades";
import { carregarSugestoesHabilidades, excluirHabilidade } from "./actions";
import { FormHabilidade } from "./form";

export default async function HabilidadesPage() {
  const todas = await prisma.habilidade.findMany({ orderBy: [{ ultimate: "asc" }, { nivelReq: "asc" }, { nome: "asc" }] });
  const porClasse = await prisma.jogador.groupBy({ by: ["classe"], _count: true });
  const qtd = (c: string) => porClasse.find((p) => p.classe === c)?._count ?? 0;

  return (
    <>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div>
          <h1 className="font-titulo text-3xl">Habilidades</h1>
          <p className="text-pedra-500">
            Cada jogador escolhe uma classe (tecla K) e usa até 4 habilidades + 1 ultimate nas teclas R, G, V, C e X.
            As de &quot;Nível&quot; liberam sozinhas; as de &quot;Tomo Arcano&quot; vêm do item tomo (coloque <code>frxworld:tomo_arcano</code> no loot).
            Cada habilidade sobe de nível com o uso (até 10): mais dano e recarga menor.
          </p>
        </div>
        <form action={carregarSugestoesHabilidades}>
          <button className="shrink-0 rounded-md border border-ouro px-4 py-2 text-ouro hover:bg-ouro hover:text-pedra-950">
            {todas.length === 0 ? "Carregar sugestões" : "Adicionar sugestões que faltam"}
          </button>
        </form>
      </div>

      <details className="mb-6 rounded-xl border border-pedra-700 bg-pedra-900 p-4 text-sm">
        <summary className="cursor-pointer text-ouro">Como cada tipo usa os campos</summary>
        <ul className="mt-2 list-disc pl-5 text-pedra-500">
          {Object.entries(TIPOS).map(([k, v]) => <li key={k}><b className="text-ouro">{k}</b>: {v}</li>)}
        </ul>
      </details>

      {Object.entries(CLASSES).map(([classe, info]) => {
        const lista = todas.filter((h) => h.classe === classe);
        return (
          <section key={classe} className="mb-8 rounded-xl border border-pedra-700 bg-pedra-900 p-5">
            <div className="mb-3 flex items-baseline justify-between">
              <h2 className="font-titulo text-xl text-ouro">{info.nome} <span className="text-sm text-pedra-500">· {info.recurso}</span></h2>
              <span className="text-xs text-pedra-500">{info.estilo} · {qtd(classe)} jogador(es)</span>
            </div>
            <div className="flex flex-col gap-4">
              {lista.map((h) => (
                <div key={h.id} className="rounded-lg border border-pedra-800 p-3">
                  <div className="mb-2 flex justify-between text-xs">
                    <span className={h.ultimate ? "font-semibold text-sangue" : "text-pedra-500"}>{h.ultimate ? "ULTIMATE" : h.tipo}</span>
                    <form action={excluirHabilidade}><input type="hidden" name="id" value={h.id} /><button className="text-red-400 hover:underline">Excluir</button></form>
                  </div>
                  <FormHabilidade h={h} />
                </div>
              ))}
              {lista.length === 0 && <p className="text-sm text-pedra-500">Nenhuma habilidade ainda.</p>}
            </div>
          </section>
        );
      })}

      <section className="rounded-xl border border-dashed border-pedra-700 p-5">
        <h2 className="mb-3 font-titulo text-xl">Nova habilidade</h2>
        <FormHabilidade h={{ codigo: "", nome: "", descricao: "", classe: "MAGO", tipo: "SKILLSHOT", elemento: "FOGO", icone: "minecraft:blaze_powder", custo: 20, cooldown: 5, dano: 8, alcance: 20, raio: 0, duracao: 0, efeito: "", potencia: 0, nivelReq: 1, ultimate: false, aprendizado: "NIVEL", ativa: true }} />
      </section>
    </>
  );
}
