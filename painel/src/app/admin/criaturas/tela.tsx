"use client";

import { useActionState, useEffect, useMemo, useState } from "react";
import { BonecoCanvas } from "@/components/boneco-canvas";
import { IconeCriatura, SeletorCriaturas, useCriaturas, type Criatura as Bicho } from "@/components/criaturas";
import { CampoItem, CampoMoedas, Moedas } from "@/components/itens";
import { GOLPES, LOCAIS, RANKS } from "@/lib/criaturas-codex";
import { EditorSkin } from "../npcs/editor";
import { excluirCriatura, invocarCriatura, salvarCriatura, type Estado } from "./actions";

export type CriaturaCodex = {
  id: string; codigo: string; nome: string; descricao: string; tipo: "chefe" | "monstro"; entidade: string; rankMin: number; rankMax: number;
  vida: number; dano: number; velocidade: number; golpes: string[]; lacaio: string; locais: string[]; peso: number;
  xp: number; moedas: number; dropItem: string; dropChance: number; dropQtd: number; escala: number; brilho: boolean;
  skin: string; slim: boolean; derrotas: number; ativa: boolean;
};

const NOVO: CriaturaCodex = {
  id: "", codigo: "", nome: "", descricao: "", tipo: "chefe", entidade: "minecraft:zombie", rankMin: 0, rankMax: 2, vida: 1, dano: 1, velocidade: 1,
  golpes: ["ONDA", "FURIA"], lacaio: "minecraft:zombie", locais: ["portal", "torre"], peso: 10, xp: 0, moedas: 0, dropItem: "", dropChance: 10, dropQtd: 1,
  escala: 1, brilho: true, skin: "", slim: false, derrotas: 0, ativa: true,
};

const STEVE = `/api/painel/textura?p=${encodeURIComponent("minecraft/textures/entity/player/wide/steve.png")}`;
const ALEX = `/api/painel/textura?p=${encodeURIComponent("minecraft/textures/entity/player/slim/alex.png")}`;
const dados = (b64: string) => `data:image/png;base64,${b64}`;

/** Lê um PNG escolhido e o converte para 64×64 (skins antigas 64×32 ganham braço e perna esquerdos). */
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

function Retrato({ c, bicho, largura = 96 }: { c: Pick<CriaturaCodex, "skin" | "slim" | "entidade">; bicho?: Bicho; largura?: number }) {
  if (c.skin) return <BonecoCanvas sk={{ url: dados(c.skin), slim: c.slim, altura: 64 }} largura={largura} alturaPx={Math.round(largura * 1.35)} girar={false} anguloInicial={-28} />;
  return <span className="grid place-items-center" style={{ width: largura, height: Math.round(largura * 1.35) }}><IconeCriatura c={bicho} id={c.entidade} tam={Math.round(largura * 0.6)} /></span>;
}

function CampoCriatura({ valor, aoMudar, titulo, mapa, filtro = "monstros" }: { valor: string; aoMudar: (id: string) => void; titulo: string; mapa: Map<string, Bicho>; filtro?: string }) {
  const [abrir, setAbrir] = useState(false);
  const b = mapa.get(valor);
  return (
    <>
      <button type="button" onClick={() => setAbrir(true)} className="flex w-full items-center gap-2 rounded-lg border border-pedra-700 bg-pedra-950 px-2.5 py-1.5 text-left text-sm hover:border-ouro">
        {valor ? <IconeCriatura c={b} id={valor} tam={24} /> : <span className="grid h-6 w-6 place-items-center rounded bg-pedra-800">?</span>}
        <span className="min-w-0 flex-1 truncate">{b?.nome ?? (valor || "Escolher…")}</span>
        <span className="font-mono text-[11px] text-pedra-500">{valor}</span>
        <span className="text-xs text-pedra-500">trocar</span>
      </button>
      {abrir && <SeletorCriaturas titulo={titulo} selecionadas={valor ? [valor] : []} aoMudar={(ids) => aoMudar(ids[0] ?? "")} aoFechar={() => setAbrir(false)} filtroInicial={filtro} unico />}
    </>
  );
}

function Editor({ inicial, fechar, mapa, armas }: { inicial: CriaturaCodex; fechar: () => void; mapa: Map<string, Bicho>; armas: { codigo: string; nome: string }[] }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(salvarCriatura, null);
  const [c, setC] = useState(inicial);
  const [desenhando, setDesenhando] = useState(false);
  const [avisoSkin, setAvisoSkin] = useState("");
  const mudar = <K extends keyof CriaturaCodex>(k: K, v: CriaturaCodex[K]) => setC((x) => ({ ...x, [k]: v }));
  const alternar = (k: "golpes" | "locais", id: string) => setC((x) => ({ ...x, [k]: x[k].includes(id) ? x[k].filter((y) => y !== id) : [...x[k], id] }));
  const campo = "w-full rounded-md border border-pedra-700 bg-pedra-950 px-3 py-2 text-sm outline-none focus:border-ouro";
  const rotulo = "mb-1 block text-xs uppercase tracking-wider text-pedra-500";
  const bloco = "rounded-lg border border-pedra-700 p-3";
  const chefe = c.tipo === "chefe";
  const armaCodex = c.dropItem.startsWith("codex:");
  return (
    <>
    <form action={acao} className="mb-8 rounded-xl border border-ouro-escuro bg-pedra-900 p-5">
      <input type="hidden" name="id" value={c.id} />
      <input type="hidden" name="tipo" value={c.tipo} />
      <input type="hidden" name="entidade" value={c.entidade} />
      <input type="hidden" name="lacaio" value={c.lacaio} />
      <input type="hidden" name="moedas" value={c.moedas} />
      <input type="hidden" name="dropItem" value={c.dropItem} />
      <input type="hidden" name="skin" value={c.skin} />
      {c.slim && <input type="hidden" name="slim" value="on" />}
      {c.golpes.map((g) => <input key={g} type="hidden" name={`golpe_${g}`} value="on" />)}
      {c.locais.map((l) => <input key={l} type="hidden" name={`local_${l}`} value="on" />)}
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-titulo text-2xl text-ouro">{c.id ? `Editar ${inicial.nome}` : "Nova criatura"}</h2>
        <button type="button" onClick={fechar} className="rounded-md border border-pedra-700 px-3 py-1.5 text-sm hover:border-ouro">Fechar</button>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-2">
            {([["chefe", "☠ Chefe", "Barra de vida, golpes anunciados, um por vez."], ["monstro", "👹 Monstro", "Aparece no meio dos monstros comuns."]] as const).map(([t, nome, desc]) => (
              <button key={t} type="button" onClick={() => mudar("tipo", t)} className={`rounded-lg border p-3 text-left ${c.tipo === t ? "border-ouro bg-ouro/10" : "border-pedra-700 hover:border-ouro-escuro"}`}>
                <p className={`font-titulo text-lg ${c.tipo === t ? "text-ouro" : ""}`}>{nome}</p><p className="text-xs text-pedra-500">{desc}</p>
              </button>
            ))}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <label><span className={rotulo}>Nome</span><input name="nome" required value={c.nome} onChange={(e) => mudar("nome", e.target.value)} className={campo} placeholder={chefe ? "Rainha das Cinzas" : "Goblin Saqueador"} /></label>
            <label><span className={rotulo}>Código {c.id ? "(não muda)" : "(opcional)"}</span>
              <input name="codigo" maxLength={30} value={c.codigo} readOnly={!!c.id} onChange={(e) => mudar("codigo", e.target.value)} className={`${campo} font-mono ${c.id ? "opacity-60" : ""}`} placeholder="criado a partir do nome" /></label>
          </div>
          <label className="block"><span className={rotulo}>História (para você e para o site)</span>
            <textarea name="descricao" rows={2} maxLength={240} value={c.descricao} onChange={(e) => mudar("descricao", e.target.value)} className={campo} /></label>

          <div className={bloco}>
            <p className="mb-2 font-titulo text-lg text-ouro">Corpo e força</p>
            <span className={rotulo}>Criatura base (dá o tamanho, o jeito de andar, de bater e os sons)</span>
            <CampoCriatura valor={c.entidade} aoMudar={(v) => mudar("entidade", v)} titulo="Criatura base" mapa={mapa} />
            <div className="mt-3 grid gap-3 sm:grid-cols-5">
              <label><span className={rotulo}>Rank de</span>
                <select name="rankMin" value={c.rankMin} onChange={(e) => mudar("rankMin", Number(e.target.value))} className={campo}>{RANKS.map((r, i) => <option key={r} value={i}>{r}</option>)}</select></label>
              <label><span className={rotulo}>até</span>
                <select name="rankMax" value={c.rankMax} onChange={(e) => mudar("rankMax", Number(e.target.value))} className={campo}>{RANKS.map((r, i) => <option key={r} value={i}>{r}</option>)}</select></label>
              <label><span className={rotulo}>Vida ×</span><input name="vida" type="number" step="0.1" min={0.1} value={c.vida} onChange={(e) => mudar("vida", Number(e.target.value))} className={campo} /></label>
              <label><span className={rotulo}>Dano ×</span><input name="dano" type="number" step="0.1" min={0.1} value={c.dano} onChange={(e) => mudar("dano", Number(e.target.value))} className={campo} /></label>
              <label><span className={rotulo}>Velocidade ×</span><input name="velocidade" type="number" step="0.05" min={0.3} value={c.velocidade} onChange={(e) => mudar("velocidade", Number(e.target.value))} className={campo} /></label>
            </div>
            <p className="mt-2 text-xs text-pedra-500">
              O nível vem do lugar (rank do portal, andar da Torre, zona do Nether){chefe ? " e todo chefe já ganha a vida de chefe" : ""}. Os multiplicadores entram por cima: 1 = igual aos outros, 2 = o dobro.
            </p>
          </div>

          {chefe && (
            <div className={bloco}>
              <p className="mb-2 font-titulo text-lg text-ouro">Golpes</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {GOLPES.map((g) => (
                  <label key={g.id} className={`flex cursor-pointer items-start gap-2 rounded-md border p-2 text-sm ${c.golpes.includes(g.id) ? "border-ouro bg-ouro/10" : "border-pedra-700"}`}>
                    <input type="checkbox" className="mt-1" checked={c.golpes.includes(g.id)} onChange={() => alternar("golpes", g.id)} />
                    <span><b>{g.icone} {g.nome}</b><span className="block text-xs text-pedra-500">{g.desc}</span></span>
                  </label>
                ))}
              </div>
              <p className="mt-2 text-xs text-pedra-500">O chefe usa um golpe sorteado a cada 7 segundos. Quanto mais golpes, menos previsível.</p>
              {c.golpes.includes("INVOCAR") && (
                <div className="mt-3"><span className={rotulo}>Lacaio que ele chama</span>
                  <CampoCriatura valor={c.lacaio} aoMudar={(v) => mudar("lacaio", v)} titulo="Lacaio do chefe" mapa={mapa} /></div>
              )}
            </div>
          )}

          <div className={bloco}>
            <p className="mb-2 font-titulo text-lg text-ouro">Onde aparece</p>
            <div className="grid gap-2 sm:grid-cols-3">
              {LOCAIS.map((l) => (
                <label key={l.id} className={`flex cursor-pointer items-start gap-2 rounded-md border p-2 text-sm ${c.locais.includes(l.id) ? "border-ouro bg-ouro/10" : "border-pedra-700"}`}>
                  <input type="checkbox" className="mt-1" checked={c.locais.includes(l.id)} onChange={() => alternar("locais", l.id)} />
                  <span><b>{l.nome}</b><span className="block text-xs text-pedra-500">{chefe ? l.chefe : l.monstro}</span></span>
                </label>
              ))}
            </div>
            <label className="mt-3 block sm:w-72"><span className={rotulo}>{chefe ? "Peso no sorteio (1 a 100)" : "Chance de aparecer (%)"}</span>
              <input name="peso" type="number" min={1} max={100} value={c.peso} onChange={(e) => mudar("peso", Number(e.target.value))} className={campo} /></label>
            <p className="mt-1 text-xs text-pedra-500">
              {chefe ? "O jogo sorteia entre os chefes do rank do lugar. Peso 20 sai o dobro de vezes que peso 10."
                : `A cada monstro comum criado nesses lugares, ${c.peso}% de chance de este entrar no lugar dele.`}
            </p>
          </div>

          <div className={bloco}>
            <p className="mb-2 font-titulo text-lg text-ouro">O que deixa ao morrer</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <label><span className={rotulo}>XP a mais (cada jogador na luta)</span><input name="xp" type="number" min={0} value={c.xp} onChange={(e) => mudar("xp", Number(e.target.value))} className={campo} /></label>
              <div><span className={rotulo}>Moedas a mais (cada jogador na luta)</span><CampoMoedas cobre={c.moedas} onChange={(v) => mudar("moedas", v)} /></div>
            </div>
            <div className="mt-3"><span className={rotulo}>Item especial (vai para quem deu o golpe final)</span>
              <div className="flex flex-wrap items-center gap-2">
                <div className="min-w-64 flex-1">
                  {armaCodex ? <p className="rounded-lg border border-pedra-700 bg-pedra-950 px-3 py-2 text-sm">🗡️ Arma do Códex: <b className="text-ouro">{armas.find((a) => `codex:${a.codigo}` === c.dropItem)?.nome ?? c.dropItem}</b></p>
                    : <CampoItem valor={c.dropItem} onChange={(v) => mudar("dropItem", v)} titulo="Item que a criatura deixa" />}
                </div>
                {!!armas.length && (
                  <select value={armaCodex ? c.dropItem : ""} onChange={(e) => mudar("dropItem", e.target.value)} className="rounded-md border border-pedra-700 bg-pedra-950 px-2 py-2 text-sm">
                    <option value="">ou uma arma do Códex…</option>
                    {armas.map((a) => <option key={a.codigo} value={`codex:${a.codigo}`}>{a.nome}</option>)}
                  </select>
                )}
                {c.dropItem && <button type="button" onClick={() => mudar("dropItem", "")} className="rounded-md border border-pedra-700 px-2 py-2 text-xs hover:border-ouro">Sem item</button>}
              </div>
            </div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label><span className={rotulo}>Chance do item (%)</span><input name="dropChance" type="number" step="0.5" min={0} max={100} value={c.dropChance} onChange={(e) => mudar("dropChance", Number(e.target.value))} className={campo} /></label>
              <label><span className={rotulo}>Quantidade</span><input name="dropQtd" type="number" min={1} max={64} value={c.dropQtd} onChange={(e) => mudar("dropQtd", Number(e.target.value))} className={campo} /></label>
            </div>
            <p className="mt-2 text-xs text-pedra-500">Isso soma ao XP e às moedas que o lugar já dá. Arma do Códex respeita o total no mundo e o rank de quem recebe.</p>
          </div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" name="ativa" checked={c.ativa} onChange={(e) => mudar("ativa", e.target.checked)} /> Ativa (desmarque para tirar do jogo sem apagar)</label>
        </div>

        <div className={`${bloco} h-fit`}>
          <p className="mb-2 font-titulo text-lg text-ouro">Aparência</p>
          <div className="grid place-items-center rounded-lg" style={{ background: "linear-gradient(#3a2a4a 0%, #1d1626 70%, #2a2420 70%, #1c1815 100%)" }}>
            <Retrato c={c} bicho={mapa.get(c.entidade)} largura={170} />
          </div>
          <p className="mt-2 text-xs text-pedra-500">{c.skin ? "Skin própria: a criatura aparece com corpo de jogador e este desenho." : "Sem skin: aparece como a criatura base."}</p>
          <div className="mt-2 grid grid-cols-2 gap-1.5">
            <button type="button" onClick={() => setDesenhando(true)} className="rounded-md border border-ouro/50 px-2 py-1.5 text-xs text-ouro hover:bg-ouro/15">🎨 Desenhar</button>
            <label className="cursor-pointer rounded-md border border-pedra-700 px-2 py-1.5 text-center text-xs hover:border-ouro/60">⬆️ Importar skin
              <input type="file" accept="image/png" className="hidden" onChange={(e) => {
                const arq = e.target.files?.[0];
                if (arq) paraPng64(arq).then((png) => { mudar("skin", png.replace(/^data:image\/png;base64,/, "")); setAvisoSkin(""); }).catch((er: Error) => setAvisoSkin(er.message));
                e.target.value = "";
              }} />
            </label>
            {c.skin && <button type="button" onClick={() => mudar("skin", "")} className="col-span-2 rounded-md border border-pedra-700 px-2 py-1.5 text-xs text-red-300 hover:border-red-400">↺ Voltar à aparência da criatura base</button>}
          </div>
          {avisoSkin && <p className="mt-2 text-xs text-red-300">{avisoSkin}</p>}
          <label className="mt-3 block"><span className={rotulo}>Tamanho do desenho × (só com skin)</span>
            <input name="escala" type="number" step="0.05" min={0.4} max={3} value={c.escala} onChange={(e) => mudar("escala", Number(e.target.value))} className={campo} /></label>
          <label className="mt-2 flex items-center gap-2 text-sm"><input type="checkbox" name="brilho" checked={c.brilho} onChange={(e) => mudar("brilho", e.target.checked)} /> Contorno brilhante (visto através das paredes)</label>
          <p className="mt-3 text-xs text-pedra-500">
            Com skin, prefira uma base de duas pernas (zumbi, esqueleto, saqueador, piglin): o corpo desenhado é o de jogador, mas a área de acerto continua a da base.
            O tamanho muda só o desenho.
          </p>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <button disabled={enviando} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:brightness-110 disabled:opacity-50">{enviando ? "Salvando..." : "Salvar criatura"}</button>
        {estado?.erro && <span className="text-sm text-red-300">{estado.erro}</span>}
        {estado?.ok && <span className="text-sm text-green-300">{estado.ok}</span>}
      </div>
    </form>

      {desenhando && (
        <EditorSkin
          titulo={c.nome || "Nova criatura"}
          inicial={c.skin ? dados(c.skin) : null}
          slimInicial={c.slim}
          bases={[...(c.skin ? [{ nome: "Atual", url: dados(c.skin) }] : []), { nome: "Steve", url: STEVE }, { nome: "Alex", url: ALEX }]}
          aoSalvar={async (png, slim) => { setC((x) => ({ ...x, skin: png.replace(/^data:image\/png;base64,/, ""), slim })); setAvisoSkin(""); setDesenhando(false); return null; }}
          aoFechar={() => setDesenhando(false)}
        />
      )}
    </>
  );
}

function Invocar({ codigo }: { codigo: string }) {
  const [estado, acao, enviando] = useActionState<Estado, FormData>(invocarCriatura, null);
  const [nick, setNick] = useState("");
  useEffect(() => { try { setNick(localStorage.getItem("frx-nick-teste") ?? ""); } catch { /* sem armazenamento */ } }, []);
  return (
    <form action={acao} className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
      <input type="hidden" name="codigo" value={codigo} />
      <input name="nick" value={nick} onChange={(e) => { setNick(e.target.value); try { localStorage.setItem("frx-nick-teste", e.target.value); } catch { /* sem armazenamento */ } }}
        placeholder="seu nick no jogo" className="w-32 rounded-md border border-pedra-700 bg-pedra-950 px-2 py-1 outline-none focus:border-ouro" />
      <button disabled={enviando} className="rounded-md border border-pedra-700 px-2 py-1 hover:border-ouro disabled:opacity-50">Invocar perto de mim</button>
      {estado?.erro && <span className="text-red-300">{estado.erro}</span>}
      {estado?.ok && <span className="text-green-300">{estado.ok}</span>}
    </form>
  );
}

export function TelaCriaturas({ criaturas, armas }: { criaturas: CriaturaCodex[]; armas: { codigo: string; nome: string }[] }) {
  const [editando, setEditando] = useState<CriaturaCodex | null>(null);
  const [aba, setAba] = useState<"chefe" | "monstro">("chefe");
  const todas = useCriaturas();
  const mapa = useMemo(() => new Map((todas ?? []).map((b) => [b.id, b])), [todas]);
  const visiveis = criaturas.filter((c) => c.tipo === aba);
  const abaBtn = (t: "chefe" | "monstro", nome: string) => (
    <button type="button" onClick={() => setAba(t)} className={`rounded-md border px-4 py-1.5 text-sm ${aba === t ? "border-ouro bg-ouro/15 text-ouro" : "border-pedra-700 text-pedra-500 hover:text-pergaminho"}`}>
      {nome} <span className="text-xs opacity-70">{criaturas.filter((c) => c.tipo === t).length}</span>
    </button>
  );
  return (
    <>
      {editando ? <Editor key={editando.id || `nova-${editando.tipo}`} inicial={editando} fechar={() => setEditando(null)} mapa={mapa} armas={armas} /> : (
        <div className="mb-6 flex flex-wrap gap-2">
          <button type="button" onClick={() => setEditando({ ...NOVO, tipo: "chefe" })} className="rounded-md bg-ouro px-5 py-2 font-semibold text-pedra-950 hover:brightness-110">+ Novo chefe</button>
          <button type="button" onClick={() => setEditando({ ...NOVO, tipo: "monstro", golpes: [], lacaio: "", brilho: false, peso: 15 })} className="rounded-md border border-ouro px-5 py-2 font-semibold text-ouro hover:bg-ouro/10">+ Novo monstro</button>
        </div>
      )}
      <div className="mb-4 flex gap-2">{abaBtn("chefe", "☠ Chefes")}{abaBtn("monstro", "👹 Monstros")}</div>
      {!visiveis.length && <p className="rounded-xl border border-pedra-700 bg-pedra-900 p-6 text-pedra-500">{aba === "chefe" ? "Nenhum chefe cadastrado: o jogo usa os chefes de fábrica." : "Nenhum monstro próprio ainda. Os lugares usam só os monstros comuns."}</p>}
      <div className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
        {visiveis.map((c) => (
          <div key={c.id} className={`rounded-xl border bg-pedra-900 p-4 ${c.ativa ? "border-pedra-700" : "border-pedra-700 opacity-60"}`}>
            <div className="flex items-start gap-3">
              <div className="shrink-0 rounded-lg" style={{ background: "linear-gradient(#3a2a4a 0%, #1d1626 70%, #2a2420 70%, #1c1815 100%)" }}><Retrato c={c} bicho={mapa.get(c.entidade)} largura={84} /></div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-titulo text-lg text-ouro">{c.tipo === "chefe" ? "☠ " : ""}{c.nome}</p>
                <p className="text-xs text-pedra-500">Rank {RANKS[c.rankMin]}{c.rankMax !== c.rankMin ? ` a ${RANKS[c.rankMax]}` : ""} · {mapa.get(c.entidade)?.nome ?? c.entidade}{c.skin ? " · skin própria" : ""}{c.ativa ? "" : " · inativa"}</p>
                {c.descricao && <p className="mt-1 text-xs text-pergaminho/70">{c.descricao}</p>}
                <p className="mt-1 text-xs text-pedra-500">Vida ×{c.vida} · Dano ×{c.dano} · Velocidade ×{c.velocidade}</p>
                {!!c.golpes.length && <p className="mt-1 text-sm" title={c.golpes.map((g) => GOLPES.find((x) => x.id === g)?.nome ?? g).join(", ")}>{c.golpes.map((g) => GOLPES.find((x) => x.id === g)?.icone ?? "").join(" ")}</p>}
              </div>
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-pedra-500">
              <span>{c.locais.length ? c.locais.map((l) => LOCAIS.find((x) => x.id === l)?.nome ?? l).join(" · ") : "Só invocada"}</span>
              <span>{c.tipo === "chefe" ? `peso ${c.peso}` : `${c.peso}% de chance`}</span>
            </div>
            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-pedra-500">
              {c.xp > 0 && <span>+{c.xp} XP</span>}
              {c.moedas > 0 && <span className="flex items-center gap-1">+<Moedas cobre={c.moedas} tam={12} /></span>}
              {c.dropItem && <span>{c.dropChance}% {c.dropItem.startsWith("codex:") ? `arma ${c.dropItem.slice(6)}` : c.dropItem}</span>}
              <span className="text-ouro">Derrotado {c.derrotas}×</span>
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <button type="button" onClick={() => { setEditando(c); window.scrollTo({ top: 0, behavior: "smooth" }); }} className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro">Editar</button>
              <button type="button" onClick={() => { setEditando({ ...c, id: "", codigo: "", nome: `${c.nome} (cópia)`, derrotas: 0 }); window.scrollTo({ top: 0, behavior: "smooth" }); }} className="rounded-md border border-pedra-700 px-3 py-1 text-xs hover:border-ouro">Duplicar</button>
              <form action={excluirCriatura} onSubmit={(e) => { if (!window.confirm(`Apagar ${c.nome} do Códex?`)) e.preventDefault(); }}>
                <input type="hidden" name="id" value={c.id} /><button className="rounded-md border border-sangue px-3 py-1 text-xs text-red-300 hover:bg-sangue/20">Apagar</button>
              </form>
            </div>
            {c.ativa && <Invocar codigo={c.codigo} />}
          </div>
        ))}
      </div>
      <p className="mt-6 text-xs text-pedra-500">
        O servidor relê o Códex a cada minuto e os jogadores recebem as skins em até 30 s. Os chefes de mods (Cataclysm, Mowzie&apos;s…) continuam na aba “Chefes de mods”;
        os da Torre nos andares 20, 40, 60 e 80 são sempre deles.
      </p>
    </>
  );
}
