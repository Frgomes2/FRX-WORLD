"use client";

import { useEffect, useRef, useState } from "react";
import type { DadosPublicos, PerfilPublico } from "@/lib/publico";

export const COR_RANK: Record<string, string> = { F: "#8b8378", E: "#6fbf73", D: "#5aa9e6", C: "#b085f5", B: "#f0a04b", A: "#ef5350", S: "#f5d04a" };
export const ICONE_CLASSE: Record<string, string> = { Cartomante: "🃏", Samurai: "🎌", Guerreiro: "⚔️", Arqueiro: "🏹", Mago: "🔮", "Clérigo": "✨", Ladino: "🗡️", Assassino: "🥷", Invocador: "👻", Paladino: "🛡️", Bardo: "🎵", Monge: "👊", Druida: "🌿" };

const imagens = new Map<string, Promise<HTMLImageElement>>();
function carregarSkin(nick: string) {
  let p = imagens.get(nick);
  if (!p) {
    p = new Promise((ok, erro) => { const im = new Image(); im.onload = () => ok(im); im.onerror = erro; im.src = `/api/publico/skin?nick=${encodeURIComponent(nick)}`; });
    imagens.set(nick, p);
  }
  return p;
}

/** Skin do jogador desenhada em pixels: só o rosto ou o corpo inteiro de frente (com as camadas de chapéu e roupa). */
export function Skin({ nick, modo = "rosto", tam = 32, className = "" }: { nick: string; modo?: "rosto" | "corpo"; tam?: number; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const [l, a] = modo === "rosto" ? [8, 8] : [16, 32];
  useEffect(() => {
    let vivo = true;
    carregarSkin(nick).then((im) => {
      const cv = ref.current;
      if (!vivo || !cv) return;
      const ctx = cv.getContext("2d");
      if (!ctx) return;
      ctx.clearRect(0, 0, l, a);
      const d = (sx: number, sy: number, w: number, h: number, dx: number, dy: number) => ctx.drawImage(im, sx, sy, w, h, dx, dy, w, h);
      const nova = im.height >= 64;   // skins antigas (64x32) não têm braço e perna esquerdos próprios nem camadas no corpo
      if (modo === "rosto") { d(8, 8, 8, 8, 0, 0); d(40, 8, 8, 8, 0, 0); return; }
      d(8, 8, 8, 8, 4, 0); d(20, 20, 8, 12, 4, 8); d(44, 20, 4, 12, 0, 8); d(4, 20, 4, 12, 4, 20);
      if (nova) { d(36, 52, 4, 12, 12, 8); d(20, 52, 4, 12, 8, 20); } else { d(44, 20, 4, 12, 12, 8); d(4, 20, 4, 12, 8, 20); }
      d(40, 8, 8, 8, 4, 0);
      if (nova) { d(20, 36, 8, 12, 4, 8); d(44, 36, 4, 12, 0, 8); d(52, 52, 4, 12, 12, 8); d(4, 36, 4, 12, 4, 20); d(4, 52, 4, 12, 8, 20); }
    }).catch(() => {});
    return () => { vivo = false; };
  }, [nick, modo, l, a]);
  return <canvas ref={ref} width={l} height={a} className={className} aria-hidden="true"
    style={{ width: tam, height: modo === "rosto" ? tam : tam * 2, imageRendering: "pixelated", borderRadius: modo === "rosto" ? 4 : 0 }} />;
}

export function SeloRank({ rank }: { rank: string }) {
  const cor = COR_RANK[rank] ?? "#8b8378";
  return <span className="inline-flex h-5 min-w-5 items-center justify-center rounded px-1 font-titulo text-xs font-bold" style={{ color: cor, border: `1px solid ${cor}`, background: `${cor}22` }}>{rank}</span>;
}

/** Nome clicável: abre a ficha do jogador. */
export function Nick({ nick, abrir, className = "" }: { nick: string; abrir: (n: string) => void; className?: string }) {
  return <button type="button" onClick={() => abrir(nick)} className={`truncate text-left font-semibold underline-offset-2 hover:text-ouro hover:underline ${className}`}>{nick}</button>;
}

/** Pódio dos três de maior nível, com o boneco de cada um. */
export function Podio({ fortes, abrir }: { fortes: DadosPublicos["rankings"]["fortes"]; abrir: (n: string) => void }) {
  if (fortes.length < 3) return null;
  const ordem = [fortes[1], fortes[0], fortes[2]];
  const alt = ["h-20", "h-28", "h-14"], lugar = ["2º", "1º", "3º"], cor = ["#c0c0c0", "#d4a93a", "#b0703c"];
  return (
    <div className="mb-6 flex items-end justify-center gap-3 sm:gap-6">
      {ordem.map((j, i) => (
        <button key={`${j.conta}#${j.vaga}`} type="button" onClick={() => abrir(`${j.conta}#${j.vaga}`)} className="group flex w-28 flex-col items-center sm:w-36">
          {i === 1 && <span className="mb-1 text-2xl" aria-hidden="true">👑</span>}
          <span className="transition-transform group-hover:-translate-y-1"><Skin nick={j.conta} modo="corpo" tam={i === 1 ? 72 : 56} /></span>
          <span className="mt-1 max-w-full truncate font-semibold group-hover:text-ouro">{j.nick}</span>
          <span className="text-xs text-pedra-500">Nível {j.valor}</span>
          <span className={`mt-1 flex w-full items-start justify-center rounded-t-lg border border-b-0 border-pedra-700 bg-gradient-to-b from-pedra-700 to-pedra-900 pt-2 font-titulo text-2xl ${alt[i]}`} style={{ color: cor[i] }}>{lugar[i]}</span>
        </button>
      ))}
    </div>
  );
}

/** Todos os aventureiros em cartões. */
export function Galeria({ lista, abrir }: { lista: DadosPublicos["aventureiros"]; abrir: (n: string) => void }) {
  if (!lista.length) return <p className="text-sm text-pedra-500">Ninguém entrou no servidor ainda.</p>;
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {lista.map((j) => (
        <button key={`${j.conta}#${j.vaga}`} type="button" onClick={() => abrir(`${j.conta}#${j.vaga}`)}
          className="group relative flex flex-col items-center rounded-xl border border-pedra-700 bg-gradient-to-b from-pedra-800 to-pedra-900 px-3 pb-3 pt-4 text-center transition hover:-translate-y-1 hover:border-ouro">
          {j.online && <span className="absolute right-2 top-2 flex items-center gap-1 text-[10px] text-green-400"><span className="h-2 w-2 animate-pulse rounded-full bg-green-400" />online</span>}
          <span className="absolute left-2 top-2"><SeloRank rank={j.rank} /></span>
          <Skin nick={j.conta} modo="corpo" tam={56} />
          <span className="mt-2 max-w-full truncate font-semibold group-hover:text-ouro">{j.nick}</span>
          {j.titulo && <span className="max-w-full truncate text-[11px] text-ouro-escuro">«{j.titulo}»</span>}
          <span className="mt-1 text-xs text-pedra-500">{ICONE_CLASSE[j.classe] ?? "🎒"} {j.classe} · Nv {j.nivel}</span>
        </button>
      ))}
    </div>
  );
}

function horas(min: number) {
  const h = Math.floor(min / 60);
  return h >= 1 ? `${h.toLocaleString("pt-BR")} h` : `${min} min`;
}
function dataBr(iso: string) { return iso.split("-").reverse().join("/"); }

/** Ficha do jogador numa janela por cima da página. */
export function Perfil({ nick: pedido, fechar }: { nick: string; fechar: () => void }) {
  // "Conta#2" abre direto o personagem da vaga 2 daquela conta; um nome sozinho abre a conta (ou o personagem com esse nome)
  const [nick, vagaPedida] = pedido.split("#");
  const [p, setP] = useState<PerfilPublico | null>(null);
  const [erro, setErro] = useState(false);
  const [vaga, setVaga] = useState(Number(vagaPedida) || 0);   // 0 = o personagem em uso (ou o do nome clicado)
  useEffect(() => {
    let vivo = true;
    fetch(`/api/publico/jogador?nick=${encodeURIComponent(nick)}${vaga ? `&vaga=${vaga}` : ""}`).then((r) => (r.ok ? r.json() : Promise.reject())).then((j) => { if (vivo) setP(j); }).catch(() => { if (vivo) setErro(true); });
    const tecla = (e: KeyboardEvent) => { if (e.key === "Escape") fechar(); };
    window.addEventListener("keydown", tecla);
    document.body.style.overflow = "hidden";
    return () => { vivo = false; window.removeEventListener("keydown", tecla); document.body.style.overflow = ""; };
  }, [nick, fechar, vaga]);

  const cor = COR_RANK[p?.rank ?? "F"];
  const numero = (n: string, r: string) => (
    <div className="rounded-lg border border-pedra-700 bg-pedra-800/60 px-2 py-2 text-center"><p className="font-titulo text-xl text-ouro">{n}</p><p className="text-[11px] text-pedra-500">{r}</p></div>
  );
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/75 p-3 sm:p-8" role="dialog" aria-modal="true" aria-label={`Ficha de ${nick}`} onClick={fechar}>
      <div className="relative w-full max-w-3xl rounded-2xl border border-pedra-700 bg-pedra-900 shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <button type="button" onClick={fechar} aria-label="Fechar" className="absolute right-3 top-3 z-10 rounded-md px-2 py-1 text-xl text-pedra-500 hover:text-ouro">✕</button>
        <div className="flex flex-col items-center gap-5 rounded-t-2xl border-b border-pedra-700 p-6 sm:flex-row sm:items-end" style={{ background: `linear-gradient(135deg, ${cor}33, transparent 60%)` }}>
          <Skin nick={nick} modo="corpo" tam={96} />
          <div className="min-w-0 text-center sm:text-left">
            <p className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
              <span className="font-titulo text-4xl text-ouro">{p?.nick ?? nick}</span>
              {p?.online && <span className="flex items-center gap-1 rounded-full border border-green-700 px-2 py-0.5 text-xs text-green-400"><span className="h-2 w-2 animate-pulse rounded-full bg-green-400" />jogando agora</span>}
            </p>
            {p?.titulo && <p className="text-ouro-escuro">«{p.titulo}»</p>}
            {p && p.personagem && p.personagem !== p.nick && <p className="text-sm text-pedra-500">Personagem: <span className="text-pergaminho">{p.personagem}</span>{!p.personagemEmUso && " (fora de uso: ficha da última vez em que jogou)"}</p>}
            {(p?.personagens?.length ?? 0) > 1 && (
              <p className="flex flex-wrap justify-center gap-2 text-xs sm:justify-start">
                {p!.personagens.map((x) => {
                  const aberto = x.nome === p!.personagem;
                  return (
                    <button key={x.vaga} type="button" disabled={!x.temFicha} onClick={() => setVaga(x.emUso ? 0 : x.vaga)}
                      className={`rounded-full border px-2 py-0.5 disabled:opacity-50 ${aberto ? "border-ouro text-ouro" : "border-pedra-700 text-pedra-500 hover:border-ouro"}`}
                      title={!x.temFicha ? "Ainda sem ficha: aparece depois que ele jogar" : x.emUso ? "Personagem em uso" : "Ver a ficha deste personagem"}>
                      {x.nome} · {x.classe || "sem classe"} · Nv {x.nivel}{x.emUso ? " · em uso" : ""}
                    </button>
                  );
                })}
              </p>
            )}
            {!!p?.clas.length && <p className="flex flex-wrap justify-center gap-x-3 text-sm sm:justify-start">{p.clas.map((c) => <span key={c.nome} style={{ color: c.cor }}>⚑ {c.nome} <span className="text-xs text-pedra-500">· {c.cargo}</span></span>)}</p>}
            {p && <p className="mt-1 text-sm text-pergaminho/80">{ICONE_CLASSE[p.classe] ?? "🎒"} {p.classe} · Nível {p.nivel}{p.raca ? ` · ${p.raca.principal}${p.raca.secundaria && p.raca.secundaria !== p.raca.principal ? ` / ${p.raca.secundaria}` : ""}` : ""}</p>}
            {p && <p className="mt-1 text-xs text-pedra-500">No servidor desde {dataBr(p.desde)}{p.vistoEm ? ` · visto em ${dataBr(p.vistoEm)}` : ""}</p>}
          </div>
        </div>

        {erro && <p className="p-6 text-sm text-pedra-500">Não deu para carregar a ficha agora.</p>}
        {!p && !erro && <p className="p-6 text-sm text-pedra-500">Abrindo a ficha...</p>}
        {p && (
          <div className="space-y-5 p-5 sm:p-6">
            <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
              {numero(String(p.nivel), "nível")}
              {numero(p.xp.toLocaleString("pt-BR"), "XP")}
              {numero(String(p.conquistas), "conquistas")}
              {numero(String(p.mortes), "mortes")}
              {numero(p.stats ? p.stats.mobsMortos.toLocaleString("pt-BR") : "—", "monstros abatidos")}
              {numero(p.stats ? horas(p.stats.tempoJogoMin) : "—", "de jogo")}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-xl border p-4" style={{ borderColor: `${cor}88`, background: `${cor}11` }}>
                <p className="text-xs uppercase tracking-wider text-pedra-500">Guilda de Aventureiros</p>
                <p className="mt-1 flex items-center gap-3"><span className="font-titulo text-5xl" style={{ color: cor }}>{p.rank}</span>
                  <span className="text-sm text-pergaminho/85">Rank {p.rank}{p.guilda.proximo ? <><br /><span className="text-xs text-pedra-500">próximo: Rank {p.guilda.proximo}</span></> : <><br /><span className="text-xs text-ouro">rank máximo</span></>}</span></p>
                <ul className="mt-2 space-y-0.5 text-sm text-pergaminho/80">
                  <li>+{p.guilda.bonusMissao}% de recompensa em missões</li>
                  <li>{p.guilda.desconto}% de desconto nas lojas</li>
                  <li>Usa equipamentos até a classe {p.rank}</li>
                </ul>
              </div>
              <div className="rounded-xl border border-pedra-700 p-4">
                <p className="text-xs uppercase tracking-wider text-pedra-500">Feitos</p>
                <ul className="mt-2 space-y-1 text-sm">
                  <li>🗼 Torre Infinita: {p.torre ? <b className="text-ouro">andar {p.torre.recorde}</b> : "ainda não entrou"}{p.torre ? ` · ${p.torre.abates} abates` : ""}</li>
                  <li>🏆 Temporada: {p.temporada ? <><b className="text-ouro">{p.temporada.posicao}º lugar</b> · {p.temporada.pts.toLocaleString("pt-BR")} pts</> : "sem pontos este mês"}</li>
                  {p.stats && <li>🥾 Andou {(p.stats.blocosAndados / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} km</li>}
                  {p.reputacao.slice(0, 3).map((r) => <li key={r.reino}>👑 Reputação em {r.reino}: {r.pontos.toLocaleString("pt-BR")}</li>)}
                </ul>
              </div>
            </div>

            {p.colecao && (
              <div className="rounded-xl border border-pedra-700 p-4">
                <p className="text-xs uppercase tracking-wider text-pedra-500">Coleção</p>
                <p className="mt-1 text-sm">📖 <b className="text-ouro">{p.colecao.criaturas}</b>{p.colecao.total ? ` de ${p.colecao.total}` : ""} tipos de criatura derrotados{p.colecao.proximoMarco ? <span className="text-pedra-500"> · próximo marco: {p.colecao.proximoMarco}</span> : null}</p>
                {p.colecao.total > 0 && <div className="mt-2 h-2 overflow-hidden rounded bg-pedra-800"><div className="h-full bg-ouro-escuro" style={{ width: `${Math.min(100, (p.colecao.criaturas / p.colecao.total) * 100)}%` }} /></div>}
                <p className="mt-3 text-sm">☠ <b className="text-ouro">{p.colecao.chefes.length}</b> chefões diferentes derrotados</p>
                {!!p.colecao.chefes.length && <p className="mt-1 flex flex-wrap gap-1.5">{p.colecao.chefes.map((c) => <span key={c} className="rounded-full border border-pedra-700 bg-pedra-800/60 px-2 py-0.5 text-xs text-pergaminho/85">{c}</span>)}</p>}
              </div>
            )}

            {p.unica && (
              <div className="rounded-xl border border-ouro-escuro bg-gradient-to-r from-[#3a2c12] to-pedra-900 p-4">
                <p className="text-xs uppercase tracking-wider text-ouro-escuro">Habilidade única · nível {p.unica.nivel}</p>
                <p className="font-titulo text-xl text-ouro">{p.unica.nome}</p>
                <p className="text-sm text-pergaminho/80">{p.unica.descricao}</p>
              </div>
            )}

            <div>
              <h3 className="font-titulo text-xl text-ouro">Habilidades <span className="text-sm text-pedra-500">{p.habilidades.length}{p.totalHabilidades ? ` de ${p.totalHabilidades}` : ""} aprendidas</span></h3>
              {p.estilo && <p className="mb-2 text-xs text-pedra-500">{p.classe}: {p.estilo}</p>}
              {p.habilidades.length ? (
                <ul className="grid gap-2 sm:grid-cols-2">
                  {p.habilidades.map((h) => (
                    <li key={h.nome} className={`rounded-lg border p-3 ${h.ultimate ? "border-ouro-escuro bg-ouro-escuro/10" : "border-pedra-700 bg-pedra-800/50"}`}>
                      <p className="flex items-center justify-between gap-2"><span className="font-semibold">{h.ultimate ? "💥 " : ""}{h.nome}</span><span className="shrink-0 rounded bg-pedra-950 px-1.5 py-0.5 text-[11px] text-ouro">Nv {h.nivel}</span></p>
                      <p className="mt-0.5 text-xs text-pergaminho/70">{h.descricao}</p>
                      {h.usos > 0 && <p className="mt-1 text-[11px] text-pedra-500">usada {h.usos.toLocaleString("pt-BR")} vezes</p>}
                    </li>
                  ))}
                </ul>
              ) : <p className="text-sm text-pedra-500">{p.totalHabilidades ? "Ainda não aprendeu nenhuma habilidade." : "As habilidades aparecem depois que o jogador entrar no servidor de novo."}</p>}
            </div>
            {p.historico.length > 0 && (
              <div className="mt-6">
                <h3 className="mb-3 font-titulo text-xl text-ouro">Histórico</h3>
                <ol className="relative space-y-3 border-l border-pedra-700 pl-5">
                  {p.historico.map((h, i) => (
                    <li key={i} className="frx-surge relative" style={{ animationDelay: `${i * 40}ms` }}>
                      <span className={`absolute -left-[26px] top-1 h-3 w-3 rounded-full border-2 border-pedra-900 ${h.destaque ? "bg-ouro" : "bg-pedra-500"}`} aria-hidden="true" />
                      <p className="text-xs text-pedra-500">{new Date(h.quando).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</p>
                      <p className={h.destaque ? "text-ouro" : ""}><span aria-hidden="true">{h.icone}</span> {h.texto.charAt(0).toUpperCase() + h.texto.slice(1)}</p>
                    </li>
                  ))}
                </ol>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
