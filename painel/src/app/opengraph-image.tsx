import { ImageResponse } from "next/og";

export const alt = "FRX-WORLD · Servidor de Minecraft MMORPG e RPG";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/** Imagem de prévia para Google, WhatsApp, Discord e redes (gerada, sem arquivo). */
export default function Imagem() {
  return new ImageResponse(
    (
      <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
        background: "linear-gradient(135deg, #17140f 0%, #2a2412 55%, #0f0d0b 100%)", color: "#e9dfc8", fontFamily: "serif" }}>
        <div style={{ fontSize: 30, letterSpacing: 10, color: "#9c7a22", textTransform: "uppercase" }}>Minecraft 1.20.1 · Forge · RPG</div>
        <div style={{ fontSize: 150, fontWeight: 700, color: "#d4a93a", marginTop: 10 }}>FRX-WORLD</div>
        <div style={{ fontSize: 44, marginTop: 10 }}>Servidor de Minecraft MMORPG e RPG</div>
        <div style={{ fontSize: 28, marginTop: 30, color: "#b5a98f" }}>13 classes · raças · Guilda F a S · reinos · chefes · Torre · X1 · expedições</div>
      </div>
    ),
    size,
  );
}
