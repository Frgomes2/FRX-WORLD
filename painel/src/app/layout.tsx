import type { Metadata } from "next";
import "./globals.css";

const URL_SITE = "https://craft.frgomes.com.br";
const DESCRICAO = "FRX-WORLD é um servidor brasileiro de Minecraft MMORPG e RPG: modpack Forge 1.20.1 com 13 classes, raças, Guilda com ranks de F a S, reinos com NPCs, chefes, Torre de 100 andares, duelos X1 e expedições. Veja rankings, fichas e o mapa ao vivo.";

export const metadata: Metadata = {
  metadataBase: new URL(URL_SITE),
  title: { default: "FRX-WORLD · Servidor de Minecraft MMORPG e RPG", template: "%s · FRX-WORLD" },
  description: DESCRICAO,
  applicationName: "FRX-WORLD",
  keywords: ["minecraft rpg", "minecraft mmo", "minecraft mmorpg", "servidor de minecraft rpg", "servidor minecraft brasileiro", "modpack minecraft rpg",
    "modpack de minecraft", "minecraft 1.20.1 modpack", "minecraft forge rpg", "servidor rpg minecraft", "minecraft classes e raças", "frx-world"],
  authors: [{ name: "Flavio Gomes", url: "https://frgomes.com.br" }],
  alternates: { canonical: "/" },
  openGraph: {
    type: "website", locale: "pt_BR", url: URL_SITE, siteName: "FRX-WORLD",
    title: "FRX-WORLD · Servidor de Minecraft MMORPG e RPG", description: DESCRICAO,
  },
  twitter: { card: "summary_large_image", title: "FRX-WORLD · Servidor de Minecraft MMORPG e RPG", description: DESCRICAO },
  robots: { index: true, follow: true },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR" className="h-full antialiased">
      <body className="min-h-full">{children}</body>
    </html>
  );
}
