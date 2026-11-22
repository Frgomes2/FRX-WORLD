/** Códex de Criaturas: o que o painel e o mod combinam sobre chefes e monstros próprios. */

/** Golpes que um chefe pode ter. O id é o mesmo usado no mod (aventura/Chefes.java). */
export const GOLPES: { id: string; nome: string; icone: string; desc: string }[] = [
  { id: "INVOCAR", nome: "Chamar reforços", icone: "📯", desc: "Chama 2 lacaios (3 em fúria), no máximo 5 vivos." },
  { id: "ONDA", nome: "Onda de choque", icone: "💥", desc: "Avisa, e 1 s depois joga longe quem estiver a 6 blocos." },
  { id: "SALTO", nome: "Salto esmagador", icone: "🦘", desc: "Pula em cima de um jogador e solta uma onda menor ao cair." },
  { id: "RAIO", nome: "Raio", icone: "⚡", desc: "Marca o chão sob um jogador; o raio cai 1,5 s depois." },
  { id: "FOGO", nome: "Chão em chamas", icone: "🔥", desc: "Incendeia o chão sob cada jogador por 5 s." },
  { id: "GELO", nome: "Congelar", icone: "❄️", desc: "Dano, lentidão e congelamento em 8 blocos." },
  { id: "VENENO", nome: "Nuvem de veneno", icone: "☠️", desc: "Deixa uma nuvem venenosa em um jogador por 8 s." },
  { id: "FLECHAS", nome: "Chuva de flechas", icone: "🏹", desc: "6 flechas caem do alto sobre cada jogador." },
  { id: "CURA", nome: "Curar-se", icone: "💚", desc: "Recupera 6% da vida (só se estiver ferido)." },
  { id: "FURIA", nome: "Fúria", icone: "😡", desc: "Abaixo de 35% de vida fica mais rápido, mais forte e golpeia a cada 5 s." },
];

/** Lugares em que o jogo cria chefes e monstros por conta própria. */
export const LOCAIS: { id: string; nome: string; chefe: string; monstro: string }[] = [
  { id: "portal", nome: "Masmorras de portal", chefe: "chefe da última câmara", monstro: "nas câmaras" },
  { id: "torre", nome: "Torre Infinita", chefe: "andares 10, 30, 50, 70 e 90", monstro: "nos andares comuns" },
  { id: "nether", nome: "Emboscadas do Nether", chefe: "o campeão (1 em cada 4 emboscadas)", monstro: "na horda" },
  { id: "fim", nome: "Ilha do Dragão (End)", chefe: "o Guardião do Dragão (às vezes, durante a luta)", monstro: "os reforços que atrapalham a luta" },
];

export const RANKS = ["F", "E", "D", "C", "B", "A", "S"];

/** O mod marca a criatura para o cliente com este papel; a skin viaja junto com as dos NPCs. */
export const chaveSkin = (codigo: string) => `papel:bicho_${codigo}`;

/** Confere que o base64 é um PNG de skin 64×64 e devolve os bytes. */
export function pngDaSkin(b64: string): Buffer | null {
  if (!b64) return null;
  const buf = Buffer.from(b64, "base64");
  if (buf.length < 30 || buf.length > 150_000 || buf.readUInt32BE(0) !== 0x89504e47 || buf.toString("ascii", 12, 16) !== "IHDR") return null;
  return buf.readUInt32BE(16) === 64 && buf.readUInt32BE(20) === 64 ? buf : null;
}
