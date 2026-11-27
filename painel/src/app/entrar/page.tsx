import type { Metadata } from "next";
import { Casca, CabecaPagina, Miolo } from "@/components/publico/casca";

export const metadata: Metadata = {
  title: "Como entrar",
  description: "Como entrar no FRX-WORLD, servidor brasileiro de Minecraft MMORPG e RPG: peça um convite, instale o launcher e escolha sua raça e classe.",
  alternates: { canonical: "/entrar" },
};

const PASSOS: [string, string, string][] = [
  ["✉️", "Peça um convite", "O FRX-WORLD é um servidor privado, entre amigos. Peça o convite para quem já joga ou para o administrador."],
  ["⬇️", "Instale o launcher", "Com o convite você recebe o launcher. Ele baixa o Minecraft 1.20.1, o Forge e todos os mods sozinho, sem você mexer em nada."],
  ["🧙", "Crie seu personagem", "Abra o launcher, escolha seu nick e entre. Na primeira vez o jogo pede para escolher raça e classe."],
];
const PRIMEIROS: [string, string, string, string][] = [
  ["🛡️", "Procure a Guilda", "Todo aventureiro começa no rank F. As missões da Guilda dão moedas, experiência e sobem o seu rank.", "/biblioteca"],
  ["🏰", "Conheça as capitais", "Os reinos têm lojas, moradores, mestres de classe e o Mestre dos Clãs.", "/mundo"],
  ["🗼", "Encare a Torre", "100 andares de chefes, com um andar seguro a cada 10.", "/rankings"],
  ["🧭", "Chegue ao rank D", "Daí em diante o pórtico das Terras Inexploradas se abre para você.", "/expedicoes"],
];

export default function Entrar() {
  return (
    <Casca pagina="entrar">
      <CabecaPagina icone="🚪" sobre="Três passos" titulo="Como entrar" texto="Do convite à primeira missão em poucos minutos." />
      <Miolo>
        <ol className="grid gap-4 md:grid-cols-3">
          {PASSOS.map(([i, t, x], n) => (
            <li key={t} className="frx-card relative rounded-xl border border-ouro-escuro/60 bg-gradient-to-b from-pedra-800 to-pedra-900 p-6">
              <span className="absolute right-4 top-3 font-titulo text-5xl text-pedra-700">{n + 1}</span>
              <span className="frx-flutua inline-block text-4xl" style={{ animationDelay: `${n * 0.7}s` }} aria-hidden="true">{i}</span>
              <p className="mt-3 font-titulo text-2xl text-ouro">{t}</p>
              <p className="mt-1 text-sm text-pergaminho/80">{x}</p>
            </li>
          ))}
        </ol>
        <section>
          <h2 className="mb-4 font-titulo text-3xl">Primeiros passos no mundo</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {PRIMEIROS.map(([i, t, x, h]) => (
              <a key={t} href={h} className="frx-card group rounded-xl border border-pedra-700 bg-pedra-900 p-5 transition hover:-translate-y-0.5 hover:border-ouro">
                <span className="text-3xl" aria-hidden="true">{i}</span>
                <p className="mt-2 font-titulo text-xl text-ouro">{t}</p>
                <p className="mt-1 text-sm text-pergaminho/75">{x}</p>
              </a>
            ))}
          </div>
        </section>
        <p className="text-center text-sm text-pedra-500">Dúvidas sobre como algo funciona? A <a href="/biblioteca" className="text-ouro-escuro hover:text-ouro">Biblioteca</a> explica tudo.</p>
      </Miolo>
    </Casca>
  );
}
