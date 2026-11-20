import { prisma } from "@/lib/prisma";
import { TabelaDinheiro } from "./tabela";
import { personagensGuardados, porPersonagem } from "@/lib/personagens";

export const dynamic = "force-dynamic";

export default async function DinheiroPage() {
  const jogadores = await prisma.jogador.findMany({
    orderBy: [{ nick: "asc" }],
    select: { uuid: true, nick: true, moedas: true, banco: true, online: true, nivel: true, rank: true, ultimoLogin: true, classe: true, titulo: true, mortes: true, conquistas: true },
  });
  const fichas = porPersonagem(jogadores, await personagensGuardados());
  return (
    <>
      <div className="mb-5">
        <h1 className="font-titulo text-3xl">Dinheiro dos jogadores</h1>
        <p className="text-pedra-500">Quanto cada um tem na bolsa e no banco. Em "Editar" você define o ouro, a prata e o cobre de cada um. O banco é da conta e muda mesmo com o jogador fora do jogo. A bolsa é de cada personagem: a edição vale para o personagem em uso, e só com ele dentro do jogo. Os outros personagens da conta aparecem embaixo, só para consulta.</p>
      </div>
      <TabelaDinheiro jogadores={jogadores.map((j) => ({
        uuid: j.uuid, nick: j.nick, moedas: j.moedas, banco: j.banco, online: j.online, nivel: j.nivel, rank: j.rank, ultimoLogin: j.ultimoLogin?.toISOString() ?? null,
        emUso: fichas.find((f) => f.uuid === j.uuid && f.emUso)?.nick ?? j.nick,
        outros: fichas.filter((f) => f.uuid === j.uuid && !f.emUso).map((f) => ({ nome: f.nick, vaga: f.vaga, moedas: f.moedas, nivel: f.nivel, rank: f.rank })),
      }))} />
    </>
  );
}
