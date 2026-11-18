/** Aparências do disco: usam os desenhos de disco que o próprio jogo já tem (o mod só escolhe qual pelo CustomModelData). */
export const ARTES_DISCO = [
  { id: 1, nome: "Dourado", textura: "music_disc_13", cor: "#d9b23c" },
  { id: 2, nome: "Verde", textura: "music_disc_cat", cor: "#5fd43a" },
  { id: 3, nome: "Laranja", textura: "music_disc_blocks", cor: "#e2713a" },
  { id: 4, nome: "Vermelho", textura: "music_disc_chirp", cor: "#c63a3a" },
  { id: 5, nome: "Verde-claro", textura: "music_disc_far", cor: "#b8e05a" },
  { id: 6, nome: "Roxo", textura: "music_disc_mall", cor: "#9a5ad9" },
  { id: 7, nome: "Lilás", textura: "music_disc_mellohi", cor: "#d58ae6" },
  { id: 8, nome: "Preto", textura: "music_disc_stal", cor: "#3a3a3a" },
  { id: 9, nome: "Branco", textura: "music_disc_strad", cor: "#e9e9e9" },
  { id: 10, nome: "Verde-escuro", textura: "music_disc_ward", cor: "#2f8f4a" },
  { id: 11, nome: "Rachado", textura: "music_disc_11", cor: "#6a6a6a" },
  { id: 12, nome: "Azul", textura: "music_disc_wait", cor: "#5aa0e0" },
  { id: 13, nome: "Azul e verde", textura: "music_disc_otherside", cor: "#3ac6b0" },
  { id: 14, nome: "Sombrio", textura: "music_disc_5", cor: "#1f5a6a" },
  { id: 15, nome: "Brasa", textura: "music_disc_pigstep", cor: "#e04a3a" },
  { id: 16, nome: "Relíquia", textura: "music_disc_relic", cor: "#4ad9d0" },
] as const;

/** Sugestões de estilo no cadastro (o campo é livre: dá para escrever outro). */
export const ESTILOS_DISCO = ["Rock", "Pop", "Sertanejo", "Funk", "Rap", "Eletrônica", "MPB", "Samba", "Pagode", "Forró", "Gospel", "Reggae", "Metal", "Clássica", "Trilha sonora", "Anime", "Games", "Lo-fi", "Instrumental"];

export const TAMANHO_MAX_DISCO = 9 * 1024 * 1024;

export function tempo(seg: number) {
  return `${Math.floor(seg / 60)}:${String(seg % 60).padStart(2, "0")}`;
}
