import Link from "next/link";
import { prisma } from "@/lib/prisma";
import { alternarAcesso } from "./actions";
import { CLASSES } from "@/lib/habilidades";
import { personagensGuardados, porPersonagem } from "@/lib/personagens";

const data = (d: Date | null) => (d ? d.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo" }) : "—");

export default async function JogadoresPage() {
  const jogadores = await prisma.jogador.findMany({ orderBy: [{ nivel: "desc" }, { criadoEm: "asc" }] });
  const fichas = porPersonagem(jogadores, await personagensGuardados());
  // uma linha por personagem, agrupadas por conta; a conta com o personagem mais forte vem primeiro
  const grupos = jogadores.map((j) => ({ j, dele: fichas.filter((f) => f.uuid === j.uuid) }))
    .sort((a, b) => Math.max(...b.dele.map((f) => f.nivel)) - Math.max(...a.dele.map((f) => f.nivel)));

  return (
    <>
      <h1 className="font-titulo text-3xl">Jogadores</h1>
      <p className="mb-6 text-pedra-500">Uma linha por personagem; cada conta pode ter até 3. Clique no nome para ver a ficha completa (inventário, habilidades, conquistas e ferramentas de admin). O personagem fora de uso mostra os números da última vez em que foi jogado. Acesso, último login e Revogar valem para a conta inteira.</p>

      <div className="overflow-x-auto rounded-xl border border-pedra-700">
        <table className="w-full text-sm">
          <thead className="bg-pedra-800 text-left text-pedra-500">
            <tr>
              <th className="px-4 py-3">Personagem</th>
              <th className="px-4 py-3">Nível</th>
              <th className="px-4 py-3">Rank</th>
              <th className="px-4 py-3">Classe</th>
              <th className="px-4 py-3">Conquistas</th>
              <th className="px-4 py-3">Mortes</th>
              <th className="px-4 py-3">Acesso</th>
              <th className="px-4 py-3">Último login</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {jogadores.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-6 text-center text-pedra-500">Ninguém entrou ainda.</td></tr>
            )}
            {grupos.map(({ j, dele }) => dele.map((f, i) => (
              <tr key={`${j.id}-${f.vaga}`} className={i === 0 ? "border-t border-pedra-700" : "border-t border-pedra-800"}>
                <td className="px-4 py-3 font-semibold">
                  <Link href={`/admin/jogadores/${j.uuid}${f.emUso ? "" : `?vaga=${f.vaga}`}`} className="group flex items-center gap-2.5">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={`https://mc-heads.net/avatar/${j.uuid}/28`} alt="" width={28} height={28} className={`rounded [image-rendering:pixelated] ${f.emUso ? "" : "opacity-50"}`} />
                    <span>
                      <span className="group-hover:text-ouro group-hover:underline">{f.nick}</span>
                      {f.emUso && j.online && <span className="ml-1.5 text-xs text-green-400">● online</span>}
                      {dele.length > 1 && <span className={`ml-1.5 rounded-full border px-1.5 py-0.5 text-[10px] font-normal ${f.emUso ? "border-ouro-escuro text-ouro" : "border-pedra-700 text-pedra-500"}`}>{f.emUso ? "em uso" : "fora de uso"}</span>}
                      {f.nick !== j.nick && <span className="block text-xs font-normal text-pedra-500">conta {j.nick}</span>}
                      {f.titulo && <span className="block text-xs font-normal text-purple-300">«{f.titulo}»</span>}
                    </span>
                  </Link>
                </td>
                <td className="px-4 py-3">{f.nivel}</td>
                <td className="px-4 py-3 font-titulo text-lg text-ouro">{f.rank}</td>
                <td className="px-4 py-3">{CLASSES[f.classe as keyof typeof CLASSES]?.nome ?? <span className="text-pedra-500">—</span>}</td>
                <td className="px-4 py-3">🏆 {f.conquistas}</td>
                <td className="px-4 py-3 text-pedra-500">{f.mortes}</td>
                {i === 0 && <td rowSpan={dele.length} className="px-4 py-3 align-top">{j.ativo ? <span className="text-green-400">Liberado</span> : <span className="text-red-400">Revogado</span>}</td>}
                {i === 0 && <td rowSpan={dele.length} className="px-4 py-3 align-top text-pedra-500">{data(j.ultimoLogin)}</td>}
                {i === 0 && (
                  <td rowSpan={dele.length} className="px-4 py-3 text-right align-top">
                    <form action={alternarAcesso}>
                      <input type="hidden" name="id" value={j.id} />
                      <button className={j.ativo ? "text-red-400 hover:underline" : "text-ouro hover:underline"}>
                        {j.ativo ? "Revogar" : "Liberar"}
                      </button>
                    </form>
                  </td>
                )}
              </tr>
            )))}
          </tbody>
        </table>
      </div>
    </>
  );
}
