# FRX-WORLD · Painel

Next.js 16 + TypeScript + Tailwind 4, Prisma 7 (PostgreSQL) e Auth.js 5.

```bat
copy .env.example .env
npm install
npm run db:migrate -- --name inicial
npm run db:seed
npm run dev
```
Depois de editar o `.env`, o `npm install` também gera o Prisma Client, o `db:seed` cria o admin e o painel abre em http://localhost:3000.

## API usada pelo servidor Minecraft
Header obrigatório: `x-servidor-token: <SERVIDOR_TOKEN>`

| Rota | Corpo | Resposta |
|---|---|---|
| `POST /api/servidor/acesso` | `{ uuid, nick }` | `{ permitido }` |
| `POST /api/servidor/convite` | `{ uuid, nick, codigo }` | `{ ok, motivo }` |
