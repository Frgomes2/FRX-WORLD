import type { MetadataRoute } from "next";

/** O Google indexa o site público; painel, conta, login e APIs ficam de fora. */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/", disallow: ["/admin", "/api/", "/login", "/conta"] }],
    sitemap: "https://craft.frgomes.com.br/sitemap.xml",
    host: "https://craft.frgomes.com.br",
  };
}
