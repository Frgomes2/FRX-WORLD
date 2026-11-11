"use client";

import { useEffect, useRef, useState } from "react";
import { Boneco } from "./boneco";

export type Item = {
  slot: number; id: string; qtd: number; nome: string; renomeado?: boolean;
  dano?: number; danoMax?: number; encantos?: { nome: string }[]; raridade?: number; forja?: number;
  material?: string; parte?: string;
};

const RARIDADE = [
  { nome: "Comum", cor: "#ffffff" },
  { nome: "Incomum", cor: "#55ff55" },
  { nome: "Raro", cor: "#55ffff" },
  { nome: "Épico", cor: "#ff55ff" },
  { nome: "Lendário", cor: "#ffaa00" },
];

function Icone({ id }: { id: string }) {
  const [falhou, setFalhou] = useState(false);
  const ref = useRef<HTMLImageElement>(null);
  // a imagem pode falhar antes do React ligar o onError (renderização no servidor)
  useEffect(() => { const im = ref.current; if (im && im.complete && im.naturalWidth === 0) setFalhou(true); }, []);
  if (falhou) {
    const nome = id.split(":")[1] ?? id;
    return <span className="grid h-8 w-8 place-items-center rounded bg-[#5a5a5a] text-[10px] font-bold uppercase text-white">{nome.slice(0, 3)}</span>;
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img ref={ref} src={`/api/painel/item-icone?id=${encodeURIComponent(id)}`} alt="" onError={() => setFalhou(true)}
      className="h-8 w-8 object-cover object-top [image-rendering:pixelated]" draggable={false} />
  );
}

/** Um quadradinho de inventário no estilo do Minecraft. */
export function Slot({ item, vazio, onHover, destaque }: { item?: Item; vazio?: string; onHover: (i: Item | null, e?: React.MouseEvent) => void; destaque?: boolean }) {
  const dur = item?.danoMax ? 1 - (item.dano ?? 0) / item.danoMax : null;
  return (
    <div
      className={`relative grid h-10 w-10 place-items-center border-2 border-b-white border-l-[#373737] border-r-white border-t-[#373737] bg-[#8b8b8b] ${destaque ? "outline outline-2 outline-white" : ""}`}
      onMouseMove={(e) => onHover(item ?? null, e)}
      onMouseLeave={() => onHover(null)}
    >
      {!item && vazio && <span className="text-lg opacity-25 grayscale">{vazio}</span>}
      {item && (
        <>
          <Icone id={item.id} />
          {item.encantos?.length ? <span className="pointer-events-none absolute inset-0 animate-pulse bg-purple-400/20 mix-blend-screen" /> : null}
          {item.qtd > 1 && <span className="absolute bottom-0 right-0.5 font-mono text-xs font-bold text-white [text-shadow:2px_2px_0_#3f3f3f]">{item.qtd}</span>}
          {dur !== null && dur < 1 && (
            <span className="absolute bottom-0.5 left-1 h-[3px] w-7 bg-black">
              <span className="block h-full" style={{ width: `${dur * 100}%`, background: `hsl(${dur * 120}, 100%, 45%)` }} />
            </span>
          )}
        </>
      )}
    </div>
  );
}

/** Caixinha de dica igual à do jogo. */
export function Dica({ item, x, y }: { item: Item; x: number; y: number }) {
  const r = item.raridade !== undefined ? RARIDADE[Math.max(0, Math.min(4, item.raridade))] : null;
  return (
    <div className="pointer-events-none fixed z-50 max-w-xs rounded-sm border-2 border-[#2a0a5e] bg-[#100010]/95 px-2.5 py-1.5 font-mono text-sm shadow-xl"
      style={{ left: x + 16, top: y + 12 }}>
      <p style={{ color: r?.cor ?? (item.encantos?.length ? "#55ffff" : "#ffffff") }} className={item.renomeado ? "italic" : ""}>
        {item.nome}{item.forja ? <span className="text-[#ffff55]"> +{item.forja}</span> : null}
      </p>
      {r && <p style={{ color: r.cor }}>★ {r.nome}</p>}
      {item.encantos?.map((e) => <p key={e.nome} className="text-[#aaaaaa]">{e.nome}</p>)}
      {item.danoMax ? <p className="text-[#aaaaaa]">Durabilidade: {item.danoMax - (item.dano ?? 0)} / {item.danoMax}</p> : null}
      {item.qtd > 1 && <p className="text-[#aaaaaa]">Quantidade: {item.qtd}</p>}
      <p className="text-xs text-[#555555]">{item.id}</p>
    </div>
  );
}

const FUNDOS = [
  { nome: "Céu", css: "linear-gradient(#7fb6ec 0%, #bfe1f7 70%, #5d9c3a 70%, #4c7f2f 100%)" },
  { nome: "Branco", css: "#f4f4f4" },
  { nome: "Cinza", css: "#8b8b8b" },
  { nome: "Preto", css: "#000000" },
  { nome: "Noite", css: "radial-gradient(circle at 50% 30%, #3b2f6b, #0b1030)" },
  { nome: "Xadrez", css: "repeating-conic-gradient(#cfcfcf 0% 25%, #f7f7f7 0% 50%) 50% / 20px 20px" },
];

/** Tela de inventário com o bonequinho, no estilo do jogo. */
export function Inventario({ uuid, nick, inventario, armadura, maoSecundaria, selecionado, ender }: {
  uuid: string; nick: string; inventario: Item[]; armadura: Item[]; maoSecundaria?: Item | null; selecionado: number; ender: Item[];
}) {
  const [dica, setDica] = useState<{ item: Item; x: number; y: number } | null>(null);
  const [aba, setAba] = useState<"inv" | "ender">("inv");
  const [fundo, setFundo] = useState(0);
  useEffect(() => {
    // lembra o fundo escolhido neste navegador
    // eslint-disable-next-line react-hooks/set-state-in-effect
    try { const v = Number(localStorage.getItem("frx_fundo_boneco")); if (v > 0 && v < FUNDOS.length) setFundo(v); } catch { /* sem armazenamento */ }
  }, []);
  const trocarFundo = (i: number) => { setFundo(i); try { localStorage.setItem("frx_fundo_boneco", String(i)); } catch { /* */ } };
  const hover = (i: Item | null, e?: React.MouseEvent) => setDica(i && e ? { item: i, x: e.clientX, y: e.clientY } : null);
  const porSlot = new Map(inventario.map((i) => [i.slot, i]));
  const arm = new Map(armadura.map((i) => [i.slot, i])); // 3 capacete, 2 peito, 1 calça, 0 botas
  const enderSlot = new Map(ender.map((i) => [i.slot, i]));
  const pecas = armadura.filter((a) => a.material && a.parte).map((a) => ({ material: a.material!, parte: a.parte! }));

  const aba_ = (id: "inv" | "ender", rot: string) => (
    <button onClick={() => setAba(id)}
      className={`rounded-t-md border-2 border-b-0 px-3 py-1 text-sm ${aba === id ? "border-[#555] bg-[#c6c6c6] text-[#3f3f3f]" : "border-transparent bg-[#8b8b8b]/60 text-[#2b2b2b] hover:bg-[#a0a0a0]"}`}>
      {rot}
    </button>
  );

  return (
    <div className="inline-block">
      <div className="flex gap-1 pl-2">{aba_("inv", "🎒 Inventário")}{aba_("ender", `🟪 Baú do End (${ender.length})`)}</div>
      <div className="rounded-md border-2 border-b-[#555555] border-l-white border-r-[#555555] border-t-white bg-[#c6c6c6] p-3 text-[#3f3f3f] shadow-2xl">
        {aba === "inv" ? (
          <>
            <div className="flex items-end gap-2">
              <div className="flex flex-col gap-0.5">
                {[3, 2, 1, 0].map((s, i) => <Slot key={s} item={arm.get(s)} vazio={["⛑️", "👕", "👖", "👢"][i]} onHover={hover} />)}
              </div>
              <div className="relative rounded-sm border-2 border-b-white border-l-[#373737] border-r-white border-t-[#373737]" style={{ background: FUNDOS[fundo].css }}>
                <Boneco uuid={uuid} nick={nick} armadura={pecas} />
                <div className="absolute bottom-1 right-1 flex gap-1">
                  {FUNDOS.map((f, i) => (
                    <button key={f.nome} title={`Fundo: ${f.nome}`} onClick={() => trocarFundo(i)}
                      className={`h-4 w-4 rounded-sm border ${i === fundo ? "border-ouro ring-1 ring-ouro" : "border-[#555]"}`} style={{ background: f.css }} />
                  ))}
                </div>
              </div>
              <div className="flex flex-col justify-end">
                <Slot item={maoSecundaria ?? undefined} vazio="🛡️" onHover={hover} />
              </div>
            </div>
            <p className="mt-2 font-mono text-xs">Inventário</p>
            <div className="mt-0.5 grid grid-cols-9 gap-0.5">
              {Array.from({ length: 27 }, (_, i) => <Slot key={i} item={porSlot.get(i + 9)} onHover={hover} />)}
            </div>
            <div className="mt-2 grid grid-cols-9 gap-0.5">
              {Array.from({ length: 9 }, (_, i) => <Slot key={i} item={porSlot.get(i)} onHover={hover} destaque={i === selecionado} />)}
            </div>
          </>
        ) : (
          <>
            <p className="font-mono text-xs">Baú do End</p>
            <div className="mt-0.5 grid grid-cols-9 gap-0.5">
              {Array.from({ length: 27 }, (_, i) => <Slot key={i} item={enderSlot.get(i)} onHover={hover} />)}
            </div>
          </>
        )}
      </div>
      {dica && <Dica item={dica.item} x={dica.x} y={dica.y} />}
    </div>
  );
}
