package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.habilidades.Habilidades;
import br.com.frxcode.frxworld.moedas.Banco;
import br.com.frxcode.frxworld.progressao.Guilda;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.racas.DefRacas;
import br.com.frxcode.frxworld.racas.RacasJogador;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Até 3 personagens por conta. Cada personagem é o arquivo inteiro do jogador (inventário, posição, raça, classe, nível,
 * habilidades, rank, missões...), guardado em <mundo>/frx_personagens/<uuid>-<vaga>.dat.
 *
 * A troca é feita com o jogador FORA do jogo: ele escolhe, o servidor o desconecta, guarda o arquivo do personagem que
 * estava em uso, põe o arquivo do outro no lugar e o jogo dele entra de novo sozinho. Assim todos os mods carregam o
 * personagem do zero, como num login comum. Personagem novo = sem arquivo: o jogo o trata como jogador novo e a
 * abertura (raça e classe) roda de novo.
 *
 * Da conta (igual para os 3): banco (o saldo passa junto na troca), terrenos, mercado, progressos do jogo e FTB Quests.
 * De cada personagem: tudo o que está no arquivo do jogador e os clãs (ver id()).
 *
 * Cada arquivo leva um selo com o número da vaga (frx_slot): se o servidor cair no meio de uma troca, vale o selo.
 */
public class Personagens {
    public static final int MAX = 3;
    /** Ações da tela (AcaoMenu de tipo "personagens"): ABRIR; JOGAR com valor = vaga; FECHAR = a tela da entrada foi fechada. */
    public static final int ABRIR = 0, JOGAR = 1, FECHAR = 2;
    /** Operações com nome (AcaoHabilidade.PERSONAGEM): numero = vaga × 10 + operação. */
    public static final int CRIAR = 1, RENOMEAR = 2;
    private static final String SELO = "frx_slot", BANCO = "frx_banco";

    private static final Map<UUID, Integer> TROCAS = new HashMap<>();       // esperando o jogador sair do jogo
    private static final Set<UUID> SEM_TELA = new HashSet<>();               // acabou de trocar: não mostra a escolha de novo
    private static final Map<UUID, JsonObject> INDICES = new HashMap<>();
    private static final Map<UUID, Integer> VAGA_DA_CONTA = new HashMap<>();   // vaga em uso de cada conta (vale também logo depois de sair)
    private static final Map<UUID, Integer> ABRIR_EM = new HashMap<>();     // tick em que a tela de escolha abre para quem acabou de entrar
    /**
     * Quem acabou de entrar e ainda está escolhendo o personagem: não toma dano e os monstros não o escolhem como alvo.
     * Vale da entrada até fechar a tela da entrada (ou trocar de personagem). Por segurança, acaba também se ele andar
     * (a tela já fechou e o aviso se perdeu), atacar algo ou passar 5 minutos.
     */
    private static final Map<UUID, Escolha> ESCOLHENDO = new HashMap<>();
    private record Escolha(double x, double z, int ate) {}

    public Personagens() {}

    // ------------------------------------------------------------------ arquivos

    private static Path pasta(MinecraftServer s) { return s.getWorldPath(LevelResource.ROOT).resolve("frx_personagens"); }
    private static Path arquivoIndice(MinecraftServer s, UUID u) { return pasta(s).resolve(u + ".json"); }
    private static Path arquivoVaga(MinecraftServer s, UUID u, int vaga) { return pasta(s).resolve(u + "-" + vaga + ".dat"); }
    private static Path arquivoDoJogo(MinecraftServer s, UUID u) { return s.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(u + ".dat"); }

    private static JsonObject indice(MinecraftServer s, UUID u) {
        JsonObject i = INDICES.get(u);
        if (i != null) return i;
        try {
            Path a = arquivoIndice(s, u);
            i = Files.exists(a) ? JsonParser.parseString(Files.readString(a, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            FrxWorld.LOG.warn("Personagens: não li o índice de {}: {}", u, e.toString());
            i = new JsonObject();
        }
        if (!i.has("ativo")) i.addProperty("ativo", 1);
        if (!i.has("banco")) i.addProperty("banco", -1);
        if (!i.has("vagas")) i.add("vagas", new JsonObject());
        INDICES.put(u, i);
        return i;
    }

    private static void gravar(MinecraftServer s, UUID u) {
        JsonObject i = INDICES.get(u);
        if (i == null) return;
        try {
            Files.createDirectories(pasta(s));
            Files.writeString(arquivoIndice(s, u), new GsonBuilder().setPrettyPrinting().create().toJson(i), StandardCharsets.UTF_8);
        } catch (Exception e) { FrxWorld.LOG.warn("Personagens: não gravei o índice de {}: {}", u, e.toString()); }
        avisarPainel(u, i);
    }

    /** O painel guarda o retrato dos personagens de cada conta (ficha do admin e do site). */
    private static void avisarPainel(UUID u, JsonObject i) {
        try {
            JsonObject corpo = new JsonObject();
            com.google.gson.JsonArray vagas = new com.google.gson.JsonArray();
            for (int n = 1; n <= MAX; n++) {
                if (!criada(i, n)) continue;
                JsonObject v = vaga(i, n), o = new JsonObject();
                o.addProperty("vaga", n);
                o.addProperty("idJogo", idDa(u, n).toString());
                o.addProperty("nome", texto(v, "nome"));
                o.addProperty("classe", texto(v, "classeNome"));
                o.addProperty("raca", texto(v, "raca"));
                o.addProperty("nivel", v.has("nivel") ? v.get("nivel").getAsInt() : 1);
                o.addProperty("rank", texto(v, "rank"));
                vagas.add(o);
            }
            if (vagas.isEmpty()) return;
            corpo.addProperty("uuid", u.toString());
            corpo.addProperty("ativo", Math.max(1, Math.min(MAX, i.get("ativo").getAsInt())));
            corpo.add("vagas", vagas);
            PainelApi.enviarPersonagens(corpo);
        } catch (Exception e) { FrxWorld.LOG.warn("Personagens: não avisei o painel sobre {}: {}", u, e.toString()); }
    }

    private static JsonObject vaga(JsonObject indice, int n) {
        JsonObject v = indice.getAsJsonObject("vagas");
        if (!v.has(String.valueOf(n))) v.add(String.valueOf(n), new JsonObject());
        return v.getAsJsonObject(String.valueOf(n));
    }

    private static boolean criada(JsonObject indice, int n) {
        JsonObject v = indice.getAsJsonObject("vagas");
        return v.has(String.valueOf(n)) && v.getAsJsonObject(String.valueOf(n)).has("criado");
    }

    private static String texto(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

    // ------------------------------------------------------------------ quem é o jogador agora

    private static CompoundTag persistente(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Vaga (1 a 3) do personagem em uso. */
    public static int vagaDe(Player p) {
        int v = persistente(p).getInt(SELO);
        return v >= 1 && v <= MAX ? v : 1;
    }

    /** Vaga em uso da conta, para o que é enviado ao painel sem o jogador em mãos (0 = ainda não se sabe). */
    public static int vagaDaConta(UUID conta) { return VAGA_DA_CONTA.getOrDefault(conta, 0); }

    /**
     * Identidade do personagem para o que é de cada um (clãs): na vaga 1 é a própria conta (o que já existia continua
     * valendo); nas outras, um id fixo derivado da conta e da vaga.
     */
    public static UUID id(Player p) { return idDa(p.getUUID(), vagaDe(p)); }
    public static UUID idDa(UUID conta, int vaga) { return vaga <= 1 ? conta : UUID.nameUUIDFromBytes(("frxworld:personagem:" + conta + "#" + vaga).getBytes(StandardCharsets.UTF_8)); }

    /** O jogador online cujo personagem em uso tem esta identidade. */
    public static ServerPlayer jogador(MinecraftServer s, UUID id) {
        ServerPlayer direto = s.getPlayerList().getPlayer(id);
        if (direto != null) return vagaDe(direto) == 1 ? direto : null;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (id(p).equals(id)) return p;
        return null;
    }

    /** Nome do personagem em uso (o nick da conta, se ele não deu nome). */
    public static String nome(Player p) {
        if (!(p instanceof ServerPlayer sp)) return p.getGameProfile().getName();
        String n = texto(vaga(indice(sp.server, sp.getUUID()), vagaDe(sp)), "nome");
        return n.isEmpty() ? sp.getGameProfile().getName() : n;
    }

    private static boolean nomeValido(String n) { return n.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ]{1,14}[\\p{L}\\p{N}]"); }

    /** Guarda no índice o retrato do personagem em uso (é o que a tela mostra dos que não estão em jogo). */
    private static void resumir(ServerPlayer p) {
        if (!Progressao.temDados(p)) return;
        JsonObject v = vaga(indice(p.server, p.getUUID()), vagaDe(p));
        String classe = Progressao.dados(p).classe();
        v.addProperty("criado", true);
        v.addProperty("classe", classe);
        v.addProperty("classeNome", classe.isEmpty() ? "" : Habilidades.nomeClasse(classe));
        v.addProperty("raca", RacasJogador.escolheu(p) ? DefRacas.nome(RacasJogador.racaVisivel(p)) : "");
        v.addProperty("nivel", Progressao.dados(p).nivel());
        v.addProperty("rank", Guilda.registrado(p) ? Guilda.rank(p) : "");
        gravar(p.server, p.getUUID());
    }

    // ------------------------------------------------------------------ entrada e saída

    /** Chamado quando a conta é liberada no login (depois da conferência com o painel). */
    public static void aoLiberar(ServerPlayer p) {
        JsonObject i = indice(p.server, p.getUUID());
        int selo = persistente(p).getInt(SELO);
        if (selo >= 1 && selo <= MAX) i.addProperty("ativo", selo);                 // o arquivo manda
        else persistente(p).putInt(SELO, Math.max(1, Math.min(MAX, i.get("ativo").getAsInt())));
        long banco = i.get("banco").getAsLong();
        if (banco >= 0) {                                                             // personagem novo: recebe o banco da conta
            Banco.definirSaldo(p, banco);
            i.addProperty("banco", -1);
        }
        VAGA_DA_CONTA.put(p.getUUID(), vagaDe(p));
        vaga(i, vagaDe(p)).addProperty("criado", true);
        resumir(p);
        gravar(p.server, p.getUUID());
        p.refreshDisplayName();
        p.refreshTabListName();
        boolean pronto = Abertura.etapa(p) >= Abertura.FIM || (RacasJogador.escolheu(p) && !Progressao.dados(p).classe().isEmpty());
        // a escolha aparece 3 s depois de entrar, para não ser atropelada pela tela de carregamento nem pelo pedido do pacote de recursos
        if (pronto && !SEM_TELA.remove(p.getUUID())) {
            ABRIR_EM.put(p.getUUID(), p.server.getTickCount() + 60);
            ESCOLHENDO.put(p.getUUID(), new Escolha(p.getX(), p.getZ(), p.server.getTickCount() + 20 * 60 * 5));
        }
        else SEM_TELA.remove(p.getUUID());
    }

    @SubscribeEvent
    public void aoSair(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) { resumir(p); ESCOLHENDO.remove(p.getUUID()); }
    }

    /** Está na tela de escolha da entrada (protegido). */
    public static boolean escolhendo(Player p) { return ESCOLHENDO.containsKey(p.getUUID()); }

    @SubscribeEvent
    public void protegerNaEscolha(net.minecraftforge.event.entity.living.LivingAttackEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p && escolhendo(p) && !ev.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void semAlvoNaEscolha(net.minecraftforge.event.entity.living.LivingChangeTargetEvent ev) {
        if (ev.getNewTarget() instanceof ServerPlayer p && escolhendo(p)) ev.setCanceled(true);
    }

    @SubscribeEvent
    public void atacouNaEscolha(net.minecraftforge.event.entity.player.AttackEntityEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) ESCOLHENDO.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ tela

    private static Item icone(String classe) {
        return switch (classe) {
            case "GUERREIRO" -> Items.IRON_SWORD;
            case "ARQUEIRO" -> Items.BOW;
            case "MAGO" -> Items.BLAZE_ROD;
            case "CLERIGO" -> Items.GOLDEN_APPLE;
            case "LADINO" -> Items.IRON_HOE;
            case "ASSASSINO" -> Items.NETHERITE_SWORD;
            case "INVOCADOR" -> Items.WITHER_SKELETON_SKULL;
            case "PALADINO" -> Items.SHIELD;
            case "BARDO" -> Items.NOTE_BLOCK;
            case "MONGE" -> Items.LEATHER_BOOTS;
            case "DRUIDA" -> Items.OAK_SAPLING;
            case "SAMURAI" -> Items.IRON_SWORD;
            case "CARTOMANTE" -> Items.PAPER;
            default -> Items.NETHER_STAR;
        };
    }

    private static void enviar(ServerPlayer p, boolean entrada, String msg, boolean ok) {
        resumir(p);
        JsonObject i = indice(p.server, p.getUUID());
        CompoundTag d = new CompoundTag();
        d.putBoolean("entrada", entrada);
        d.putInt("ativo", vagaDe(p));
        d.putString("conta", p.getGameProfile().getName());
        d.putString("msg", msg); d.putBoolean("ok", ok);
        ListTag vagas = new ListTag();
        for (int n = 1; n <= MAX; n++) {
            CompoundTag t = new CompoundTag();
            JsonObject v = vaga(i, n);
            boolean criado = criada(i, n);
            t.putInt("vaga", n);
            t.putBoolean("criado", criado);
            String nome = texto(v, "nome");
            t.putString("nome", !nome.isEmpty() ? nome : criado ? p.getGameProfile().getName() : "");
            t.putString("classe", texto(v, "classeNome"));
            t.putString("raca", texto(v, "raca"));
            t.putInt("nivel", v.has("nivel") ? v.get("nivel").getAsInt() : 0);
            t.putString("rank", texto(v, "rank"));
            t.put("item", new ItemStack(icone(texto(v, "classe"))).save(new CompoundTag()));
            vagas.add(t);
        }
        d.put("vagas", vagas);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("personagens", d));
    }

    /** Cliques da tela. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        if (a.acao() == ABRIR) { enviar(p, false, "", true); return; }
        if (a.acao() == FECHAR) { ESCOLHENDO.remove(p.getUUID()); return; }
        if (a.acao() != JOGAR) return;
        int alvo = (int) a.valor();
        if (alvo < 1 || alvo > MAX || alvo == vagaDe(p)) return;
        if (!criada(indice(p.server, p.getUUID()), alvo)) { enviar(p, false, "Essa vaga está livre: crie o personagem primeiro.", false); return; }
        trocar(p, alvo);
    }

    /** Criar (numero = vaga × 10 + CRIAR) ou renomear (… + RENOMEAR) um personagem; texto = o nome. */
    public static void nomear(ServerPlayer p, int numero, String texto) {
        int alvo = numero / 10, operacao = numero % 10;
        String nome = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (alvo < 1 || alvo > MAX) return;
        if (!nomeValido(nome)) { enviar(p, false, "O nome precisa ter de 3 a 16 letras ou números.", false); return; }
        JsonObject i = indice(p.server, p.getUUID());
        if (operacao == RENOMEAR) {
            if (!criada(i, alvo)) return;
            vaga(i, alvo).addProperty("nome", nome);
            gravar(p.server, p.getUUID());
            if (alvo == vagaDe(p)) { p.refreshDisplayName(); p.refreshTabListName(); }
            enviar(p, false, "Nome trocado para " + nome + ".", true);
        } else if (operacao == CRIAR) {
            if (criada(i, alvo) || alvo == vagaDe(p)) { enviar(p, false, "Essa vaga já tem um personagem.", false); return; }
            vaga(i, alvo).addProperty("nome", nome);
            gravar(p.server, p.getUUID());
            trocar(p, alvo);
        }
    }

    // ------------------------------------------------------------------ troca

    private static void trocar(ServerPlayer p, int alvo) {
        if (Abertura.emAndamento(p)) { enviar(p, false, "Termine de criar este personagem antes de trocar.", false); return; }
        if (!p.isAlive()) { enviar(p, false, "Você não pode trocar de personagem agora.", false); return; }
        resumir(p);
        TROCAS.put(p.getUUID(), alvo);
        String nome = texto(vaga(indice(p.server, p.getUUID()), alvo), "nome");
        CompoundTag d = new CompoundTag();
        d.putBoolean("reconectar", true);
        d.putString("nome", nome);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("personagens", d));   // o jogo dele entra de novo sozinho
        p.connection.disconnect(Component.literal("Trocando de personagem" + (nome.isEmpty() ? "" : " para " + nome) + ". Entre de novo no servidor."));
    }

    private static CompoundTag persistenteDoArquivo(CompoundTag jogador) {
        if (!jogador.contains("ForgeData")) jogador.put("ForgeData", new CompoundTag());
        CompoundTag forge = jogador.getCompound("ForgeData");
        if (!forge.contains(Player.PERSISTED_NBT_TAG)) forge.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return forge.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Com o jogador já fora (e o arquivo dele salvo pelo jogo): guarda o personagem em uso e põe o outro no lugar. */
    private static void executar(MinecraftServer s, UUID conta, int alvo) {
        try {
            Files.createDirectories(pasta(s));
            JsonObject i = indice(s, conta);
            int ativo = i.get("ativo").getAsInt();
            Path doJogo = arquivoDoJogo(s, conta);
            long banco = -1;
            if (Files.exists(doJogo)) {
                CompoundTag atual = NbtIo.readCompressed(doJogo.toFile());
                int selo = persistenteDoArquivo(atual).getInt(SELO);
                if (selo >= 1 && selo <= MAX) ativo = selo;
                if (ativo == alvo) { i.addProperty("ativo", alvo); gravar(s, conta); return; }
                banco = persistenteDoArquivo(atual).getLong(BANCO);
                Path guarda = arquivoVaga(s, conta, ativo);
                if (Files.exists(guarda)) Files.move(guarda, guarda.resolveSibling(guarda.getFileName() + ".anterior"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(doJogo, guarda, StandardCopyOption.REPLACE_EXISTING);
            }
            Path doAlvo = arquivoVaga(s, conta, alvo);
            if (Files.exists(doAlvo)) {
                CompoundTag novo = NbtIo.readCompressed(doAlvo.toFile());
                CompoundTag pers = persistenteDoArquivo(novo);
                pers.putInt(SELO, alvo);
                if (banco >= 0) pers.putLong(BANCO, banco);                 // o banco é da conta: o saldo acompanha
                Path temp = doJogo.resolveSibling(conta + ".dat.frx");
                NbtIo.writeCompressed(novo, temp.toFile());
                Files.move(temp, doJogo, StandardCopyOption.REPLACE_EXISTING);
            } else {
                i.addProperty("banco", banco);                                // personagem novo: recebe o saldo no primeiro login
                Files.deleteIfExists(doJogo);                                 // sem arquivo, o jogo o trata como jogador novo
            }
            i.addProperty("ativo", alvo);
            VAGA_DA_CONTA.put(conta, alvo);
            gravar(s, conta);
            SEM_TELA.add(conta);
            FrxWorld.LOG.info("Personagens: {} trocou da vaga {} para a {}.", conta, ativo, alvo);
        } catch (Exception e) {
            FrxWorld.LOG.error("Personagens: a troca de {} para a vaga {} falhou; o personagem em uso continua o mesmo.", conta, alvo, e);
        }
    }

    @SubscribeEvent
    public void aCadaTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || (TROCAS.isEmpty() && ABRIR_EM.isEmpty() && ESCOLHENDO.isEmpty())) return;
        MinecraftServer s = ev.getServer();
        for (UUID conta : Set.copyOf(ESCOLHENDO.keySet())) {
            Escolha e = ESCOLHENDO.get(conta);
            ServerPlayer p = s.getPlayerList().getPlayer(conta);
            double dx = p == null ? 0 : p.getX() - e.x(), dz = p == null ? 0 : p.getZ() - e.z();
            if (p == null || s.getTickCount() > e.ate() || dx * dx + dz * dz > 2.25) ESCOLHENDO.remove(conta);
        }
        for (UUID conta : Set.copyOf(ABRIR_EM.keySet())) {
            if (s.getTickCount() < ABRIR_EM.get(conta)) continue;
            ABRIR_EM.remove(conta);
            ServerPlayer p = s.getPlayerList().getPlayer(conta);
            if (p != null && p.isAlive() && !Abertura.emAndamento(p)) enviar(p, true, "", true);
        }
        for (UUID conta : Set.copyOf(TROCAS.keySet())) {
            if (s.getPlayerList().getPlayer(conta) != null) continue;   // ainda saindo
            executar(s, conta, TROCAS.remove(conta));
        }
    }
}
