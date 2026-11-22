package br.com.frxcode.frxworld.progressao;

import br.com.frxcode.frxworld.moedas.Moedas;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zonas de perigo: quanto mais longe do spawn, maior o nível dos monstros (vida, dano, armadura),
 * mais XP (a XP segue a vida máxima) e mais moedas. Nether (55–80) vem depois da superfície inteira (1–60) e o End (75–100) depois do Nether.
 * Valores na aba Progressão do painel (seção "zonas").
 */
public class Zonas {
    private static final String CHAVE = "frx_zona";
    private static final UUID VIDA = id("zona_vida"), DANO = id("zona_dano"), ARMADURA = id("zona_armadura");
    private static final Map<UUID, Integer> FAIXA = new HashMap<>();

    private static UUID id(String n) { return UUID.nameUUIDFromBytes(("frxworld:" + n).getBytes(StandardCharsets.UTF_8)); }

    /**
     * Faixas de nível por dimensão (mínimo–máximo). A dimensão é uma zona de progressão:
     * abaixo do mínimo é muito perigoso; dentro da faixa é o normal; muito acima você domina.
     * Pode ser trocado no painel (zonas → dimensoes: {"id": [min, max]}).
     */
    private static final Map<String, int[]> FAIXAS_PADRAO = Map.of(
            "twilightforest:twilight_forest", new int[]{14, 30},
            "minecraft:the_nether", new int[]{55, 80},
            "undergarden:undergarden", new int[]{20, 35},
            "blue_skies:everbright", new int[]{25, 40},
            "aether:the_aether", new int[]{30, 45},
            "minecraft:the_end", new int[]{75, 100},
            "blue_skies:everdawn", new int[]{35, 50},
            "deeperdarker:otherside", new int[]{45, 60});

    private static final Map<String, String> NOMES_DIM = Map.of(
            "twilightforest:twilight_forest", "Floresta do Crepúsculo", "minecraft:the_nether", "Nether", "undergarden:undergarden", "Undergarden",
            "blue_skies:everbright", "Everbright", "aether:the_aether", "Aether", "minecraft:the_end", "O Fim",
            "blue_skies:everdawn", "Everdawn", "deeperdarker:otherside", "O Outro Lado", "minecraft:overworld", "Superfície");

    /** [mínimo, máximo] da dimensão; a superfície vai de 1 ao nível máximo das zonas. */
    public static int[] faixa(Level nivel) {
        String id = nivel.dimension().location().toString();
        try {
            com.google.gson.JsonObject z = ConfigJogo.secaoCrua("zonas");
            if (z != null && z.has("dimensoes") && z.getAsJsonObject("dimensoes").has(id)) {
                com.google.gson.JsonArray a = z.getAsJsonObject("dimensoes").getAsJsonArray(id);
                return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
            }
        } catch (Exception ignorado) { }
        int[] f = FAIXAS_PADRAO.get(id);
        if (f != null) return f;
        if (nivel.dimension() == Level.OVERWORLD) return new int[]{1, ConfigJogo.zonaNivelMax()};
        return new int[]{20, 40};   // dimensão de outro mod que não está na tabela
    }

    public static String nomeDimensao(Level nivel) {
        String id = nivel.dimension().location().toString();
        return NOMES_DIM.getOrDefault(id, id);
    }

    /** Nível da zona num ponto. Na superfície cresce com a distância do spawn; nas dimensões, a partir do mínimo da faixa. */
    public static int nivel(Level nivel, BlockPos pos) {
        int blocos = ConfigJogo.zonaBlocosPorNivel();
        int[] f = faixa(nivel);
        if (nivel.dimension() == Level.OVERWORLD) {
            int fixo = AreasNivel.em(pos);   // masmorra com nível próprio
            if (fixo > 0) return fixo;
        }
        double dx, dz;
        if (nivel.dimension() == Level.OVERWORLD) {
            BlockPos spawn = nivel.getServer() != null ? nivel.getServer().overworld().getSharedSpawnPos() : BlockPos.ZERO;
            dx = pos.getX() - spawn.getX(); dz = pos.getZ() - spawn.getZ();
        } else if (nivel.dimension() == Level.NETHER) {
            dx = pos.getX() * 8.0; dz = pos.getZ() * 8.0;
        } else {
            dx = pos.getX(); dz = pos.getZ();
        }
        int n = f[0] + (int) (Math.sqrt(dx * dx + dz * dz) / blocos);
        return Math.max(f[0], Math.min(f[1], n));
    }

    public static int nivelDo(LivingEntity e) { return e.getPersistentData().getInt(CHAVE); }

    /** Grava um nível fixo sem aplicar os bônus da zona (quem chama cuida dos atributos; ex.: aventura/DragaoFim). */
    public static void marcarNivel(Mob m, int n) { m.getPersistentData().putInt(CHAVE, n); }

    private static boolean chefe(LivingEntity e) { return e.getType().is(Tags.EntityTypes.BOSSES); }

    private static void modificar(LivingEntity e, Attribute a, UUID id, double valor, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null || valor <= 0) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, "FRX zona", valor, op));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void aoNascer(EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide() || !(ev.getEntity() instanceof Mob m) || !(m instanceof Enemy)) return;
        if (m.getPersistentData().contains(CHAVE)) return;
        aplicarNivel(m, nivel(ev.getLevel(), m.blockPosition()));
    }

    /** Dureza extra da dimensão: vida e dano a mais para todo monstro de lá (fração: 0.8 = +80%). */
    private static final Map<String, double[]> DUREZA_PADRAO = Map.of("twilightforest:twilight_forest", new double[]{0.8, 0.5},
            "minecraft:the_nether", new double[]{1.5, 1.0},    // Nether: +150% de vida e +100% de dano
            "minecraft:the_end", new double[]{2.5, 1.5});      // End: +250% de vida e +150% de dano

    /** [vida, dano] extras da dimensão. O painel pode trocar em zonas.dureza = { "dimensao": [vida, dano] }. */
    public static double[] dureza(Level nivel) {
        String id = nivel.dimension().location().toString();
        try {
            com.google.gson.JsonObject z = ConfigJogo.secaoCrua("zonas");
            if (z != null && z.has("dureza") && z.getAsJsonObject("dureza").has(id)) {
                com.google.gson.JsonArray a = z.getAsJsonObject("dureza").getAsJsonArray(id);
                return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
            }
        } catch (Exception ignorado) { }
        double[] d = DUREZA_PADRAO.get(id);
        return d != null ? d : new double[]{0, 0};
    }

    /** Chefes das masmorras dos reinos: conteúdo de fim de jogo. +300% de vida e +100% de dano além do nível; abaixo do nível 50 o jogador mal arranha. */
    private static final String CHEFE_MASMORRA = "frx_chefe_masmorra";
    private static final double CHEFE_MASMORRA_VIDA = 3.0, CHEFE_MASMORRA_DANO = 1.0;
    public static final int NIVEL_CHEFE_MASMORRA = 50;

    /** Grava o nível no mob e aplica os bônus de vida, dano e armadura dele. */
    public static void aplicarNivel(Mob m, int n) {
        m.getPersistentData().putInt(CHAVE, n);
        if (n <= 1) return;
        // chefe dentro de masmorra com nível próprio (as que ficam embaixo dos reinos): bônus inteiro e ainda mais vida e dano
        boolean chefeDeMasmorra = chefe(m) && m.level().dimension() == Level.OVERWORLD && AreasNivel.em(m.blockPosition()) > 0;
        double f = chefeDeMasmorra ? 1.0 : chefe(m) ? 0.5 : 1.0;   // os outros chefes já são fortes: metade do bônus
        double[] dureza = dureza(m.level());
        if (chefeDeMasmorra) {
            m.getPersistentData().putBoolean(CHEFE_MASMORRA, true);
            dureza = new double[]{dureza[0] + CHEFE_MASMORRA_VIDA, dureza[1] + CHEFE_MASMORRA_DANO};
        }
        modificar(m, Attributes.MAX_HEALTH, VIDA, (n - 1) * ConfigJogo.zonaVidaPct() / 100.0 * f + dureza[0] * f, AttributeModifier.Operation.MULTIPLY_TOTAL);
        modificar(m, Attributes.ATTACK_DAMAGE, DANO, (n - 1) * ConfigJogo.zonaDanoPct() / 100.0 * f + dureza[1] * f, AttributeModifier.Operation.MULTIPLY_TOTAL);
        modificar(m, Attributes.ARMOR, ARMADURA, Math.min(20, (n - 1) * ConfigJogo.zonaArmaduraPorNivel() * f), AttributeModifier.Operation.ADDITION);
        m.setHealth(m.getMaxHealth());
    }

    // ------------------------------------------------------------------ zona de progressão: perigo e domínio

    /** Fator de dano que um mob de nível n causa num jogador de nível L (dentro da dimensão d). */
    public static float fatorContraJogador(int n, int jogador, int minimoDim) {
        float f = 1f;
        int dif = n - jogador;
        if (dif > 0) f += 0.08f * dif;
        if (jogador < minimoDim) f += 0.5f;            // entrou antes da hora: muito perigoso
        if (jogador >= n + 10) f *= 0.85f;              // domina a região
        return Math.min(2.2f, f);
    }

    /** Fator do dano do jogador de nível L num mob de nível n. */
    public static float fatorDoJogador(int n, int jogador) {
        int dif = n - jogador;
        if (dif > 0) return Math.max(0.4f, 1f - 0.05f * dif);
        if (jogador >= n + 10) return 1.15f;
        return 1f;
    }

    /** XP de um mob para um jogador: monstros muito mais fracos (cinzas) dão pouco. */
    public static double fatorXp(LivingEntity mob, ServerPlayer p) {
        if (!Progressao.temDados(p)) return 1;
        int dif = Progressao.dados(p).nivel() - Math.max(1, nivelDo(mob));
        if (dif >= 15) return 0.1;
        if (dif >= 10) return 0.4;
        if (dif >= 6) return 0.75;
        return 1;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void perigo(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        if (vitima.level().isClientSide()) return;
        if (vitima instanceof ServerPlayer p && ev.getSource().getEntity() instanceof Mob m && m.getPersistentData().contains(CHAVE) && Progressao.temDados(p)) {
            int n = Math.max(1, nivelDo(m));
            float fator = fatorContraJogador(n, Progressao.dados(p).nivel(), faixa(p.level())[0]);
            if (m.getPersistentData().getBoolean(CHEFE_MASMORRA) && Progressao.dados(p).nivel() < NIVEL_CHEFE_MASMORRA) fator = Math.max(fator, 3f);
            ev.setAmount(ev.getAmount() * fator);
        } else if (ev.getSource().getEntity() instanceof ServerPlayer p && vitima instanceof Mob m && m.getPersistentData().contains(CHAVE) && Progressao.temDados(p)) {
            float fator = fatorDoJogador(Math.max(1, nivelDo(m)), Progressao.dados(p).nivel());
            if (m.getPersistentData().getBoolean(CHEFE_MASMORRA) && Progressao.dados(p).nivel() < NIVEL_CHEFE_MASMORRA) {
                fator = Math.min(fator, 0.2f);
                if (p.getRandom().nextInt(5) == 0) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Este chefe é para aventureiros de nível " + NIVEL_CHEFE_MASMORRA
                        + " ou mais: seus golpes mal arranham.").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            ev.setAmount(ev.getAmount() * fator);
        }
    }

    /** Rank mínimo da Guilda para entrar na dimensão (índice: 0 = F ... 6 = S). Nether (monstros 55–80) pede Rank C (entra quem quiser arriscar); End (75–100) pede Rank A. */
    private static final Map<String, Integer> RANK_DIM_PADRAO = Map.of("minecraft:the_nether", 3, "minecraft:the_end", 5);
    private static final Map<UUID, Long> AVISO_DIM = new HashMap<>();

    /** O painel pode trocar em zonas.rankMinimo = { "dimensao": "D" } ("livre" ou "F" libera). */
    public static int rankMinimoDaDimensao(String id) {
        try {
            com.google.gson.JsonObject z = ConfigJogo.secaoCrua("zonas");
            if (z != null && z.has("rankMinimo") && z.getAsJsonObject("rankMinimo").has(id)) {
                String letra = z.getAsJsonObject("rankMinimo").get(id).getAsString().trim().toUpperCase(java.util.Locale.ROOT);
                for (int i = 0; i < ConfigJogo.totalRanks(); i++) if (ConfigJogo.nomeRank(i).equals(letra)) return i;
                return 0;
            }
        } catch (Exception ignorado) { }
        return RANK_DIM_PADRAO.getOrDefault(id, 0);
    }

    /** Sem o rank da Guilda, o portal não leva: vale para portal, waystone e qualquer outro teleporte entre dimensões. */
    @SubscribeEvent
    public void barrarDimensao(net.minecraftforge.event.entity.EntityTravelToDimensionEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        String id = ev.getDimension().location().toString();
        int precisa = rankMinimoDaDimensao(id);
        if (precisa <= 0 || Guilda.indice(p) >= precisa) return;
        ev.setCanceled(true);
        p.setPortalCooldown();
        long agora = System.currentTimeMillis();
        if (agora - AVISO_DIM.getOrDefault(p.getUUID(), 0L) < 3000) return;
        AVISO_DIM.put(p.getUUID(), agora);
        String nome = NOMES_DIM.getOrDefault(id, id);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(nome + ": só aventureiros de Rank " + ConfigJogo.nomeRank(precisa)
                + " ou superior da Guilda atravessam (você é Rank " + Guilda.rank(p) + ").").withStyle(net.minecraft.ChatFormatting.RED), true);
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("⛔ " + nome + " exige Rank " + ConfigJogo.nomeRank(precisa)
                + " da Guilda de Aventureiros. Suba de rank na recepcionista da Guilda para liberar a passagem.").withStyle(net.minecraft.ChatFormatting.RED));
    }

    /** Ao entrar numa dimensão: mostra a faixa e o quanto ela é perigosa para você. */
    @SubscribeEvent
    public void trocarDimensao(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !Progressao.temDados(p)) return;
        int[] f = faixa(p.level());
        int l = Progressao.dados(p).nivel();
        String sub;
        ChatFormatting cor;
        if (l < f[0]) { sub = "PERIGO EXTREMO para você (Nv " + l + "): os monstros batem muito mais forte"; cor = ChatFormatting.DARK_RED; }
        else if (l <= f[1]) { sub = "Faixa ideal para o seu nível"; cor = ChatFormatting.GREEN; }
        else if (l >= f[1] + 10) { sub = "Você domina esta região · Elites e Campeões mais fortes podem surgir"; cor = ChatFormatting.LIGHT_PURPLE; }
        else { sub = "Você já está acima desta região"; cor = ChatFormatting.AQUA; }
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal(nomeDimensao(p.level())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal("Nv " + f[0] + "–" + f[1] + " · " + sub).withStyle(cor)));
    }

    private record Alvo(net.minecraft.server.level.ServerBossEvent barra, UUID alvo, long ate) {}
    private static final Map<UUID, Alvo> ALVOS = new HashMap<>();

    /** Ao bater num monstro: nível e vida na barra de ação e numa barra de alvo no topo da tela (some em 5 s). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void aoBater(LivingHurtEvent ev) {
        if (!(ev.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity alvo = ev.getEntity();
        if (alvo == p || !(alvo instanceof Enemy || alvo instanceof Mob m && m.getTarget() == p)) return;
        int n = Math.max(1, nivelDo(alvo));
        float vida = Math.max(0, alvo.getHealth() - ev.getAmount());
        ChatFormatting cor = n >= 40 ? ChatFormatting.DARK_RED : n >= 25 ? ChatFormatting.RED : n >= 10 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        Component texto = Ranks.rotulo(alvo)
                .append(Component.literal("  ❤ " + Math.round(vida) + "/" + Math.round(alvo.getMaxHealth())).withStyle(ChatFormatting.WHITE));
        p.displayClientMessage(texto, true);
        if (alvo.getTags().contains("frx_chefe_mundial") || Ranks.CAMPEAO.equals(Ranks.rank(alvo))) return; // já têm barra própria
        Alvo a = ALVOS.get(p.getUUID());
        net.minecraft.server.level.ServerBossEvent barra;
        if (a != null) barra = a.barra();
        else {
            barra = new net.minecraft.server.level.ServerBossEvent(texto, net.minecraft.world.BossEvent.BossBarColor.RED,
                    net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            barra.addPlayer(p);
        }
        barra.setName(texto);
        barra.setColor(n >= 25 ? net.minecraft.world.BossEvent.BossBarColor.RED : n >= 10 ? net.minecraft.world.BossEvent.BossBarColor.YELLOW
                : net.minecraft.world.BossEvent.BossBarColor.GREEN);
        barra.setProgress(Math.max(0, Math.min(1, vida / alvo.getMaxHealth())));
        ALVOS.put(p.getUUID(), new Alvo(barra, alvo.getUUID(), p.serverLevel().getGameTime() + 100));
    }

    @SubscribeEvent
    public void barraDoAlvo(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        Alvo a = ALVOS.get(p.getUUID());
        if (a == null) return;
        net.minecraft.world.entity.Entity e = p.serverLevel().getEntity(a.alvo());
        if (e instanceof LivingEntity v && v.isAlive()) a.barra().setProgress(Math.max(0, Math.min(1, v.getHealth() / v.getMaxHealth())));
        if (!(e instanceof LivingEntity v2) || !v2.isAlive() || p.serverLevel().getGameTime() > a.ate()) {
            a.barra().removeAllPlayers();
            ALVOS.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public void aoSair(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent ev) {
        Alvo a = ALVOS.remove(ev.getEntity().getUUID());
        if (a != null) a.barra().removeAllPlayers();
    }

    /** Monstros derrubam moedas conforme o nível da zona. */
    @SubscribeEvent
    public void moedas(LivingDropsEvent ev) {
        if (true) return;   // desligado em 16/11: moedas só por missão da Guilda e venda de itens
        LivingEntity e = ev.getEntity();
        if (!(e instanceof Enemy) || !(ev.getSource().getEntity() instanceof ServerPlayer) || !(e.level() instanceof ServerLevel)) return;
        int n = Math.max(1, nivelDo(e));
        boolean chefe = chefe(e);
        if (!chefe && e.getRandom().nextDouble() * 100 >= ConfigJogo.zonaMoedaChance()) return;
        long valor = Math.round(n * ConfigJogo.zonaMoedaPorNivel() * (0.5 + e.getRandom().nextDouble()) * (chefe ? 25 : 1));
        if (valor < 1) valor = 1;
        long ouro = valor / 100, prata = (valor % 100) / 10, cobre = valor % 10;
        soltar(ev, e, Moedas.ouro((int) Math.min(64, ouro)), ouro);
        soltar(ev, e, Moedas.prata((int) prata), prata);
        soltar(ev, e, Moedas.cobre((int) cobre), cobre);
    }

    private static void soltar(LivingDropsEvent ev, LivingEntity e, ItemStack pilha, long qtd) {
        if (qtd <= 0) return;
        ev.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), pilha));
    }

    /** Aviso ao mudar de faixa (a cada 5 níveis). */
    @SubscribeEvent
    public void aoAndar(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 60 != 0) return;
        for (Reino r : Reinos.todos()) if (r.distancia(p.getBlockX(), p.getBlockZ()) == 0) return;   // capitais são seguras
        int n = nivel(p.level(), p.blockPosition());
        int faixa = n / 5;
        Integer antes = FAIXA.put(p.getUUID(), faixa);
        if (antes == null || antes == faixa) return;
        ChatFormatting cor = n >= 40 ? ChatFormatting.DARK_RED : n >= 25 ? ChatFormatting.RED : n >= 10 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        p.displayClientMessage(Component.literal((faixa > antes ? "⚠ Zona mais perigosa: " : "Zona mais calma: ") + "monstros nível " + n).withStyle(cor), true);
    }
}
