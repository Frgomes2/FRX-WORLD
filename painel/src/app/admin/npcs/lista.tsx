"use client";

import { useState } from "react";
import { BonecoCanvas } from "@/components/boneco-canvas";
import { NOMES_NPC, PAPEIS, RACA_DO_REINO, chavePadrao, chaveReino } from "@/lib/npcs";
import { EditorSkin } from "./editor";

type SkinInfo = { chave: string; slim: boolean; em: number };

const STEVE = `/api/painel/textura?p=${encodeURIComponent("minecraft/textures/entity/player/wide/steve.png")}`;
const ALEX = `/api/painel/textura?p=${encodeURIComponent("minecraft/textures/entity/player/slim/alex.png")}`;
const urlSkin = (s: SkinInfo) => `/api/painel/npc-skins/png?chave=${encodeURIComponent(s.chave)}&t=${s.em}`;
const urlRaca = (raca: string, k = 0) => `/api/painel/raca-skin?raca=${raca}&k=${k}`;
const NOME_RACA: Record<string, string> = { hobgoblin: "hobgoblin", anao: "anão", elfo: "elfo", oriental: "oriental", humano: "humano", sagrado: "sagrado", demonio: "demônio" };

/** Lê um PNG escolhido e o converte para 64×64 (skins antigas 64×32 ganham braço/perna esquerdos). */
function paraPng64(arquivo: File): Promise<string> {
  return new Promise((ok, falha) => {
    const im = new Image();
    im.onload = () => {
      if (im.naturalWidth !== 64 || (im.naturalHeight !== 64 && im.naturalHeight !== 32)) { falha(new Error(`A skin precisa ter 64×64 ou 64×32 pixels (esta tem ${im.naturalWidth}×${im.naturalHeight}).`)); return; }
      const c = document.createElement("canvas"); c.width = 64; c.height = 64;
      const x = c.getContext("2d")!;
      x.drawImage(im, 0, 0);
      if (im.naturalHeight === 32) { x.drawImage(c, 0, 16, 16, 16, 16, 48, 16, 16); x.drawImage(c, 40, 16, 16, 16, 32, 48, 16, 16); }
      ok(c.toDataURL("image/png"));
    };
    im.onerror = () => falha(new Error("Não consegui abrir esse arquivo como imagem."));
    im.src = URL.createObjectURL(arquivo);
  });
}

export function ListaNpcs({ reinos, skins: inicial }: { reinos: { id: string; nome: string }[]; skins: SkinInfo[] }) {
  const [skins, setSkins] = useState(inicial);
  const [aba, setAba] = useState<string>("padrao");
  const [editando, setEditando] = useState<{ chave: string; titulo: string; url: string | null; slim: boolean; bases: { nome: string; url: string }[] } | null>(null);
  const [aviso, setAviso] = useState<{ ok: boolean; texto: string } | null>(null);

  const mapa = new Map(skins.map((s) => [s.chave, s]));

  async function recarregar() {
    const r = await fetch("/api/painel/npc-skins", { cache: "no-store" });
    if (r.ok) setSkins((await r.json()).map((s: { chave: string; slim: boolean; atualizadoEm: number }) => ({ chave: s.chave, slim: s.slim, em: s.atualizadoEm })));
  }

  async function salvar(chave: string, png: string, slim: boolean): Promise<string | null> {
    const r = await fetch("/api/painel/npc-skins", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ chave, png, slim }) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) return j.erro ?? "Não salvou.";
    await recarregar();
    return null;
  }

  async function remover(chave: string) {
    if (!confirm("Voltar à aparência padrão deste NPC?")) return;
    await fetch(`/api/painel/npc-skins?chave=${encodeURIComponent(chave)}`, { method: "DELETE" });
    await recarregar();
    setAviso({ ok: true, texto: "Voltou ao padrão. Os jogadores veem a mudança em até 30 s." });
  }

  async function enviar(chave: string, arquivo: File, slim: boolean) {
    try {
      const png = await paraPng64(arquivo);
      const erro = await salvar(chave, png, slim);
      setAviso(erro ? { ok: false, texto: erro } : { ok: true, texto: "Skin enviada! Os jogadores recebem em até 30 s." });
    } catch (e) {
      setAviso({ ok: false, texto: e instanceof Error ? e.message : "Falhou." });
    }
  }

  const reinoAtual = reinos.find((r) => r.id === aba);
  const cards = PAPEIS.map((p) => {
    if (!reinoAtual) {
      const chave = chavePadrao(p.id);
      return { papel: p, chave, nome: p.nome, propria: mapa.get(chave) ?? null, herdada: null as SkinInfo | null, raca: null as string | null };
    }
    const chave = chaveReino(reinoAtual.id, p.id);
    const raca = p.id === "rei" || p.id === "guarda" ? null : RACA_DO_REINO[reinoAtual.id] ?? "humano";
    return {
      papel: p, chave, raca,
      nome: p.id === "morador" ? `Moradores de ${reinoAtual.nome}` : NOMES_NPC[reinoAtual.id]?.[p.id] ?? `${p.nome} de ${reinoAtual.nome}`,
      propria: mapa.get(chave) ?? null,
      herdada: mapa.get(chavePadrao(p.id)) ?? null,
    };
  });

  const abaBtn = (id: string, rot: string, n: number) => (
    <button key={id} onClick={() => setAba(id)}
      className={`rounded-lg border px-3 py-1.5 text-sm ${aba === id ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:text-pergaminho"}`}>
      {rot}{n > 0 && <span className="ml-1.5 rounded-full bg-ouro/20 px-1.5 text-xs text-ouro">{n}</span>}
    </button>
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {abaBtn("padrao", "🌍 Todos os reinos", skins.filter((s) => s.chave.startsWith("papel:")).length)}
        {reinos.map((r) => abaBtn(r.id, `👑 ${r.nome}`, skins.filter((s) => s.chave.startsWith(`reino:${r.id}:`)).length))}
      </div>

      {aviso && <p className={`rounded-lg px-3 py-2 text-sm ${aviso.ok ? "bg-green-950/50 text-green-200" : "bg-red-950/50 text-red-200"}`} onClick={() => setAviso(null)}>{aviso.texto}</p>}

      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-6">
        {cards.map((c) => {
          const usada = c.propria ?? c.herdada;
          const urlPrevia = usada ? urlSkin(usada) : c.raca ? urlRaca(c.raca) : null;
          const origem = c.propria ? "personalizada" : c.herdada ? "usa a de Todos os reinos" : c.raca ? `padrão: raça ${NOME_RACA[c.raca] ?? c.raca}` : c.papel.id === "rei" || c.papel.id === "guarda" ? "padrão: guarda do Guard Villagers" : "padrão do jogo";
          const bases = [
            ...(c.propria ? [{ nome: "Atual", url: urlSkin(c.propria) }] : []),
            ...(c.herdada && !c.propria ? [{ nome: "Todos os reinos", url: urlSkin(c.herdada) }] : []),
            ...(c.raca ? [0, 1, 2, 3, 4].map((k) => ({ nome: `${NOME_RACA[c.raca!] ?? c.raca} ${k + 1}`, url: urlRaca(c.raca!, k) })) : []),
            { nome: "Steve", url: STEVE }, { nome: "Alex", url: ALEX },
          ];
          return (
            <div key={c.chave} className={`flex flex-col rounded-xl border p-3 ${c.propria ? "border-ouro/50 bg-ouro/5" : "border-pedra-700 bg-pedra-900/70"}`}>
              <p className="text-xs uppercase tracking-wider text-pedra-500">{c.papel.icone} {c.papel.nome}</p>
              <p className="min-h-10 font-titulo leading-tight">{c.nome}</p>
              <div className="my-2 grid place-items-center rounded-lg" style={{ background: "linear-gradient(#7fb6ec 0%, #bfe1f7 70%, #5d9c3a 70%, #4c7f2f 100%)" }}>
                {urlPrevia
                  ? <BonecoCanvas sk={{ url: urlPrevia, slim: usada?.slim ?? false, altura: 64 }} largura={130} alturaPx={175} girar={false} anguloInicial={-28} />
                  : <div className="grid h-[175px] w-[130px] place-items-center text-center text-xs text-white/90">sem skin<br />personalizada</div>}
              </div>
              <p className={`mb-2 text-xs ${c.propria ? "text-ouro" : "text-pedra-500"}`}>{c.propria ? "✨ " : ""}{origem}</p>
              <div className="mt-auto grid grid-cols-2 gap-1.5">
                <button onClick={() => setEditando({ chave: c.chave, titulo: c.nome, url: c.propria ? urlSkin(c.propria) : null, slim: c.propria?.slim ?? false, bases })}
                  className="rounded-md border border-ouro/50 px-2 py-1 text-xs text-ouro hover:bg-ouro/15">🎨 Desenhar</button>
                <label className="cursor-pointer rounded-md border border-pedra-700 px-2 py-1 text-center text-xs hover:border-ouro/60">
                  ⬆️ Enviar PNG
                  <input type="file" accept="image/png" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) enviar(c.chave, f, false); e.target.value = ""; }} />
                </label>
                {c.propria && <button onClick={() => remover(c.chave)} className="col-span-2 rounded-md border border-pedra-700 px-2 py-1 text-xs text-red-300 hover:border-red-400">↺ Voltar ao padrão</button>}
              </div>
            </div>
          );
        })}
      </div>

      <p className="text-xs text-pedra-500">
        Dica: skins prontas de sites como NameMC ou The Skindex funcionam (baixe o PNG e use &quot;Enviar PNG&quot;). Rei e guardas usam o corpo de jogador com a armadura que já vestem por cima.
      </p>

      {editando && (
        <EditorSkin
          key={editando.chave}
          titulo={editando.titulo}
          inicial={editando.url}
          slimInicial={editando.slim}
          bases={editando.bases}
          aoSalvar={(png, slim) => salvar(editando.chave, png, slim)}
          aoFechar={() => setEditando(null)}
        />
      )}
    </div>
  );
}
