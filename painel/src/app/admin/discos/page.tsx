import { prisma } from "@/lib/prisma";
import { temArquivo } from "@/lib/discos";
import { TelaDiscos } from "./tela";

export const dynamic = "force-dynamic";

export default async function DiscosPage() {
  const discos = await prisma.disco.findMany({ orderBy: [{ ordem: "asc" }, { nome: "asc" }] });
  const total = discos.reduce((s, d) => s + d.tamanho, 0);
  return (
    <>
      <div className="mb-5">
        <h1 className="font-titulo text-3xl">Discos de música</h1>
        <p className="text-pedra-500">
          As músicas que o Mercador de Discos vende. Cada disco toca numa jukebox comum do jogo. O arquivo precisa ser <b>.ogg</b> (OGG Vorbis) de até 9 MB;
          em <b>mono</b> o som diminui com a distância, como os discos do Minecraft.
        </p>
        <p className="mt-1 text-sm text-pedra-500">
          As músicas vão no pacote de recursos que o jogo de cada jogador baixa ao entrar. Hoje: <b>{(total / 1048576).toFixed(1)} MB</b> em {discos.length} disco(s).
          Para pôr o vendedor numa capital: <code className="rounded bg-pedra-950 px-1.5 py-0.5">/frx npc aqui discos</code>.
        </p>
      </div>
      <TelaDiscos discos={discos.map((d) => ({
        id: d.id, codigo: d.codigo, nome: d.nome, artista: d.artista, estilo: d.estilo, descricao: d.descricao, preco: d.preco, rankMin: d.rankMin, limite: d.limite, emitidas: d.emitidas, arte: d.arte, textura: d.textura,
        duracao: d.duracao, canais: d.canais, tamanho: d.tamanho, ativo: d.ativo, ordem: d.ordem, temArquivo: temArquivo(d.codigo),
      }))} />
    </>
  );
}
