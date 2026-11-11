import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "FRX-WORLD · Painel",
  description: "Painel administrativo do FRX-WORLD",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR" className="h-full antialiased">
      <body className="min-h-full">{children}</body>
    </html>
  );
}
