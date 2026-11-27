import type { MetadataRoute } from "next";
import { prisma } from "@/lib/prisma";

export const dynamic = "force-dynamic";

const URL_SITE = "https://craft.frgomes.com.br";

/** Páginas públicas para o Google. */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const expedicoes = await prisma.expedicao.findMany({ select: { numero: true, atualizadaEm: true }, orderBy: { numero: "desc" } }).catch(() => []);
  return [
    { url: URL_SITE, changeFrequency: "hourly", priority: 1 },
    { url: `${URL_SITE}/expedicoes`, changeFrequency: "hourly", priority: 0.8 },
    { url: `${URL_SITE}/rankings`, changeFrequency: "hourly", priority: 0.7 },
    { url: `${URL_SITE}/aventureiros`, changeFrequency: "daily", priority: 0.6 },
    { url: `${URL_SITE}/mundo`, changeFrequency: "daily", priority: 0.7 },
    { url: `${URL_SITE}/biblioteca`, changeFrequency: "weekly", priority: 0.6 },
    { url: `${URL_SITE}/entrar`, changeFrequency: "monthly", priority: 0.7 },
    { url: `${URL_SITE}/novidades`, changeFrequency: "weekly", priority: 0.5 },
    { url: `${URL_SITE}/mods`, changeFrequency: "weekly", priority: 0.4 },
    ...expedicoes.map((e) => ({ url: `${URL_SITE}/expedicoes/${e.numero}`, lastModified: e.atualizadaEm, changeFrequency: "daily" as const, priority: 0.5 })),
  ];
}
