"use client";

import { useEffect, useState } from "react";
import { BonecoCanvas, type Peca } from "@/components/boneco-canvas";

/**
 * Bonequinho 3D do Minecraft feito só com CSS: a skin do jogador e, por cima, a armadura que ele está vestindo
 * (texturas tiradas dos próprios jars). Arraste para girar.
 */




export type Skin = { url: string; slim: boolean; altura: number; origem: string; tamanho: string; vazia?: boolean };

/** A skin tem quase nada desenhado? (no jogo ela aparece toda preta) */
function quaseVazia(im: HTMLImageElement) {
  try {
    const c = document.createElement("canvas");
    c.width = im.naturalWidth; c.height = im.naturalHeight;
    const x = c.getContext("2d");
    if (!x) return false;
    x.drawImage(im, 0, 0);
    const d = x.getImageData(0, 0, c.width, c.height).data;
    let n = 0;
    for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++;
    return n < (c.width * c.height) / 10;
  } catch { return false; }
}

/** Busca a skin oficial pelo painel (sem depender de sites de terceiros). */
export function useSkin(uuid: string) {
  const [skin, setSkin] = useState<Skin | null>(null);
  useEffect(() => {
    let vivo = true;
    let objUrl: string | null = null;
    (async () => {
      try {
        const r = await fetch(`/api/painel/skin?uuid=${encodeURIComponent(uuid)}&t=${Date.now()}`, { cache: "no-store" });
        if (!r.ok) throw new Error();
        const slim = r.headers.get("x-skin-modelo") === "slim";
        const origem = r.headers.get("x-skin-origem") ?? "";
        objUrl = URL.createObjectURL(await r.blob());
        const im = new Image();
        im.onload = () => { if (vivo && objUrl) setSkin({ vazia: quaseVazia(im), url: objUrl, slim, altura: im.naturalWidth ? Math.round((64 * im.naturalHeight) / im.naturalWidth) : 64, origem, tamanho: `${im.naturalWidth}×${im.naturalHeight}` }); };
        im.src = objUrl;
      } catch {
        if (vivo) setSkin({ url: `/api/painel/textura?p=${encodeURIComponent("minecraft/textures/entity/player/wide/steve.png")}`, slim: false, altura: 64, origem: "steve", tamanho: "64×64" });
      }
    })();
    return () => { vivo = false; if (objUrl) URL.revokeObjectURL(objUrl); };
  }, [uuid]);
  return skin;
}

/** Rosto da skin (frente da cabeça + chapéu), para o cabeçalho. */
export function RostoSkin({ uuid, tam = 80 }: { uuid: string; tam?: number }) {
  const skin = useSkin(uuid);
  if (!skin) return <span className="block animate-pulse rounded-lg bg-pedra-700" style={{ width: tam, height: tam }} />;
  const k = tam / 8, h = skin.altura;
  const camada = (u: number, v: number) => ({
    backgroundImage: `url("${skin.url}")`, backgroundSize: `${64 * k}px ${h * k}px`, backgroundPosition: `${-u * k}px ${-v * k}px`,
  });
  return (
    <span className="relative block [image-rendering:pixelated]" style={{ width: tam, height: tam }}>
      <span className="absolute inset-0 bg-black" style={camada(8, 8)} />
      <span className="absolute inset-0" style={camada(40, 8)} />
    </span>
  );
}

export function Boneco({ uuid, armadura }: { uuid: string; nick?: string; armadura: Peca[] }) {
  const skOficial = useSkin(uuid);
  const [steve, setSteve] = useState(false);
  const STEVE = `/api/painel/textura?p=${encodeURIComponent("minecraft/textures/entity/player/wide/steve.png")}`;
  const sk: Skin | null = skOficial && steve ? { url: STEVE, slim: false, altura: 64, origem: "steve", tamanho: "64×64" } : skOficial;
  return (
    <BonecoCanvas sk={sk} armadura={armadura}>
      {sk && <span className="pointer-events-none absolute left-1 top-1 rounded bg-black/40 px-1 font-mono text-[9px] text-white/80">skin: {sk.origem} · {sk.tamanho}{sk.slim ? " · slim" : ""}</span>}
      {skOficial?.vazia && (
        <div className="absolute inset-x-1 top-6 rounded bg-black/70 p-1.5 text-center text-[10px] leading-tight text-white">
          {steve ? "Mostrando o Steve no lugar." : "A skin oficial desta conta está vazia: no jogo ela aparece toda preta."}
          <button className="ml-1 underline" onClick={() => setSteve((v) => !v)}>{steve ? "ver a skin real" : "ver como Steve"}</button>
        </div>
      )}
    </BonecoCanvas>
  );
}
