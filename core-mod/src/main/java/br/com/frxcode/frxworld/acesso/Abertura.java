package br.com.frxcode.frxworld.acesso;

import br.com.frxcode.frxworld.habilidades.Unica;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import br.com.frxcode.frxworld.progressao.Progressao;
import br.com.frxcode.frxworld.progressao.Sistema;
import br.com.frxcode.frxworld.racas.RacasJogador;
import br.com.frxcode.frxworld.rede.AbrirMenu;
import br.com.frxcode.frxworld.rede.AcaoMenu;
import br.com.frxcode.frxworld.rede.Rede;
import br.com.frxcode.frxworld.reinos.Reino;
import br.com.frxcode.frxworld.reinos.Reinos;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Abertura do primeiro login: tela escura, a voz do Sistema contando que você renasceu neste mundo,
 * escolha da raça e da classe, o despertar da Habilidade Única e o nascimento na capital.
 */
public class Abertura {
    private static final String CHAVE = "frx_abertura";
    /** 0 = ainda não viu, 1 = esperando a raça, 2 = esperando a classe, 3 = concluída. */
    public static final int NADA = 0, RACA = 1, CLASSE = 2, FIM = 3, PREVIA = 4;

    public Abertura() {}

    private static CompoundTag dados(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag pers = raiz.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(CHAVE)) pers.put(CHAVE, new CompoundTag());
        return pers.getCompound(CHAVE);
    }

    public static int etapa(Player p) { return dados(p).getInt("etapa"); }
    private static void etapa(Player p, int e) { dados(p).putInt("etapa", e); }

    /** Está no meio da abertura (serve para a raça e a classe saberem que devem continuar a cena). */
    public static boolean emAndamento(Player p) {
        int e = etapa(p);
        return e == RACA || e == CLASSE;
    }

    private static JsonObject cfg() { return ConfigJogo.secaoCrua("abertura"); }

    private static boolean ligada() {
        try { JsonObject o = cfg(); return o == null || !o.has("ativo") || o.get("ativo").getAsBoolean(); } catch (Exception e) { return true; }
    }

    private static String reinoInicial() {
        try { JsonObject o = cfg(); if (o != null && o.has("reino")) return o.get("reino").getAsString(); } catch (Exception ignorado) { }
        return "tempest";
    }

    /** Falas do Sistema na tela escura (editáveis no painel). */
    private static List<String> falas(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        try {
            JsonObject o = cfg();
            if (o != null && o.has("falas") && o.get("falas").isJsonArray())
                for (com.google.gson.JsonElement e : o.getAsJsonArray("falas")) l.add(e.getAsString());
        } catch (Exception ignorado) { }
        if (l.isEmpty()) l = List.of(
                "Você morreu.",
                "…mas a sua história não acabou aqui.",
                "Um mundo de oito reinos abre os olhos com você.",
                "Monstros caminham pelas estradas. Reis procuram heróis.",
                "Eu sou o Sistema. Vou acompanhar cada passo seu daqui em diante.",
                "Antes de nascer, preciso saber quem você é.");
        List<String> r = new ArrayList<>();
        for (String s : l) r.add(s.replace("{jogador}", p.getGameProfile().getName()));
        return r;
    }

    // ------------------------------------------------------------------ cena

    private static void enviarTela(ServerPlayer p, String fase, List<String> linhas, String botao) {
        CompoundTag d = new CompoundTag();
        d.putBoolean("previa", etapa(p) == PREVIA);
        d.putString("fase", fase);
        d.putString("jogador", p.getGameProfile().getName());
        d.putString("botao", botao);
        ListTag l = new ListTag();
        for (String s : linhas) l.add(StringTag.valueOf(s));
        d.put("linhas", l);
        Rede.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AbrirMenu("abertura", d));
    }

    /** Chamado quando o convite é aceito (ou ao entrar, se o jogador ainda não escolheu raça). */
    public static void iniciar(ServerPlayer p) {
        if (!ligada() || etapa(p) >= FIM) return;
        if (RacasJogador.escolheu(p) && !Progressao.dados(p).classe().isEmpty()) { etapa(p, FIM); return; }
        etapa(p, RACA);
        comecar(p);
    }

    /** Prévia para admin: mostra as telas da cena sem mexer na raça nem na classe. */
    public static void previa(ServerPlayer p) {
        etapa(p, PREVIA);
        dados(p).putInt("passo", 0);
        comecar(p);
    }

    // ------------------------------------------------------------------ música

    /** Som escolhido no painel ("" = sem música). "frxworld:abertura" é o arquivo abertura.ogg do próprio mod. */
    private static net.minecraft.resources.ResourceLocation musica() {
        String id = "frxworld:abertura";
        try { JsonObject o = cfg(); if (o != null && o.has("musica")) id = o.get("musica").getAsString().trim(); } catch (Exception ignorado) { }
        return id.isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
    }

    private static float volume() {
        try { JsonObject o = cfg(); if (o != null && o.has("volume")) return (float) Math.max(0, Math.min(2, o.get("volume").getAsDouble())); } catch (Exception ignorado) { }
        return 1f;
    }

    private static void tocarMusica(ServerPlayer p) {
        net.minecraft.resources.ResourceLocation id = musica();
        if (id == null) return;
        pararMusica(p);
        // cala a música de fundo do jogo e toca a da abertura presa ao jogador (acompanha o teleporte)
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, SoundSource.MUSIC));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
                net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id)), SoundSource.MASTER, p, volume(), 1f, p.getRandom().nextLong()));
        dados(p).putString("musica", id.toString());
    }

    private static void pararMusica(ServerPlayer p) {
        String id = dados(p).getString("musica");
        if (id.isEmpty()) return;
        dados(p).remove("musica");
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl != null) p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(rl, SoundSource.MASTER));
    }

    private static boolean pararAoNascer() {
        try { JsonObject o = cfg(); return o == null || !o.has("pararAoNascer") || o.get("pararAoNascer").getAsBoolean(); } catch (Exception e) { return true; }
    }

    private static void comecar(ServerPlayer p) {
        tocarMusica(p);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 4, false, false));   // ninguém morre no meio da cena
        p.level().playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6f, 0.6f);
        enviarTela(p, "intro", falas(p), "Continuar");
    }

    /** Resposta do cliente: o jogador leu a introdução. */
    public static void acao(ServerPlayer p, AcaoMenu a) {
        if (etapa(p) != RACA) return;
        RacasJogador.sincronizar(p, true);   // abre a tela de raças
    }

    /** Prévia: pula a escolha e vai direto para a próxima tela da cena. */
    public static void avancarPrevia(ServerPlayer p) {
        if (etapa(p) != PREVIA) return;
        int passo = dados(p).getInt("passo") + 1;
        dados(p).putInt("passo", passo);
        if (passo == 1) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.3f);
            enviarTela(p, "classe", List.of("O corpo está escolhido.", "Agora escolha o caminho que vai trilhar."), "Continuar");
            return;
        }
        Unica.Def u = Unica.def(p);
        List<String> linhas = new ArrayList<>();
        linhas.add("Resta uma coisa que ninguém escolhe.");
        if (u != null) {
            linhas.add("Habilidade Única despertada:");
            linhas.add("« " + u.nome() + " »");
            linhas.add(u.descricao());
        }
        linhas.add("Ela é só sua, e vai crescer junto com você.");
        enviarTela(p, "unica", linhas, "Nascer neste mundo");
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.4f, 0.7f);
    }

    /** A raça foi escolhida: segue para a classe. */
    public static void aposRaca(ServerPlayer p) {
        if (etapa(p) != RACA) return;
        etapa(p, CLASSE);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.3f);
        enviarTela(p, "classe", List.of("O corpo está escolhido.", "Agora escolha o caminho que vai trilhar."), "Escolher classe");
    }

    /** A classe foi escolhida: desperta a Habilidade Única e nasce na capital. */
    public static void aposClasse(ServerPlayer p) {
        if (etapa(p) != CLASSE) return;
        etapa(p, FIM);
        Unica.Def u = Unica.def(p);
        List<String> linhas = new ArrayList<>();
        linhas.add("Resta uma coisa que ninguém escolhe.");
        if (u != null) {
            linhas.add("Habilidade Única despertada:");
            linhas.add("« " + u.nome() + " »");
            linhas.add(u.descricao());
        }
        linhas.add("Ela é só sua, e vai crescer junto com você.");
        enviarTela(p, "unica", linhas, "Nascer neste mundo");
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.4f, 0.7f);
        if (p.level() instanceof ServerLevel n)
            n.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 80, 0.5, 1.0, 0.5, 0.1);
    }

    /** Último clique: teleporta para a capital inicial e solta o jogador no mundo. */
    public static void nascer(ServerPlayer p) {
        if (etapa(p) != FIM && etapa(p) != PREVIA) return;
        etapa(p, FIM);
        Reino r = null;
        for (Reino x : Reinos.todos()) if (x.id().equals(reinoInicial())) r = x;
        if (r == null && !Reinos.todos().isEmpty()) r = Reinos.todos().get(0);
        net.minecraft.core.BlockPos vila = pontoInicial(p);
        boolean dentroDeCapital = false;
        if (vila != null) {
            // o ponto marcado fica dentro de uma capital? Então é ela a cidade inicial, qualquer que seja a do painel
            for (Reino x : Reinos.todos()) {
                double d = Math.sqrt(Math.pow(x.centroX() - vila.getX(), 2) + Math.pow(x.centroZ() - vila.getZ(), 2));
                if (d <= Math.max(x.larguraChunks(), x.profundidadeChunks()) * 8.0) { r = x; dentroDeCapital = true; break; }
            }
        }
        if (vila != null) {
            // o admin marcou um ponto inicial com /setworldspawn: nasce e renasce ali, e segue a pé até a capital
            ServerLevel nivel = p.getServer().overworld();
            nivel.getChunk(vila.getX() >> 4, vila.getZ() >> 4);
            p.teleportTo(nivel, vila.getX() + 0.5, vila.getY(), vila.getZ() + 0.5, nivel.getSharedSpawnAngle(), 0);
            p.setRespawnPosition(nivel.dimension(), vila, nivel.getSharedSpawnAngle(), true, false);
        } else if (r != null && p.getServer() != null) {
            ServerLevel nivel = p.getServer().overworld();
            nivel.getChunk(r.centroX() >> 4, r.centroZ() >> 4);
            int y = nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, r.centroX(), r.centroZ());
            p.teleportTo(nivel, r.centroX() + 0.5, y + 1, r.centroZ() + 0.5, p.getYRot(), p.getXRot());
            p.setRespawnPosition(nivel.dimension(), new net.minecraft.core.BlockPos(r.centroX(), y + 1, r.centroZ()), p.getYRot(), true, false);
        }
        p.removeEffect(MobEffects.BLINDNESS);
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        kitInicial(p);
        if (pararAoNascer()) pararMusica(p); else dados(p).remove("musica");
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.2f);
        String reino = r != null ? r.nome() : "este mundo";
        if (vila != null && !dentroDeCapital) {
            String rumo = r == null ? "" : direcao(vila.getX(), vila.getZ(), r.centroX(), r.centroZ());
            int dist = r == null ? 0 : (int) Math.sqrt(Math.pow(r.centroX() - vila.getX(), 2) + Math.pow(r.centroZ() - vila.getZ(), 2));
            Sistema.avisar(p, "Você despertou", "A capital " + reino + " fica a cerca de " + dist + " blocos, na direção " + rumo
                    + ". Lá você encontra a Guilda, o taverneiro e as lojas.", Sistema.OURO);
        } else
        Sistema.avisar(p, "Bem-vindo a " + reino, "Fale com o taverneiro para pegar missões e com a recepcionista da Guilda para se registrar.", Sistema.OURO);
        p.sendSystemMessage(Component.literal("Dicas: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("tecla K = habilidades · tecla E = status · /raca · /unica · /banco").withStyle(ChatFormatting.WHITE)));
    }

    /**
     * Kit de quem acaba de nascer (uma vez por personagem): armadura de couro e ferramentas de pedra, tudo de classe F
     * (qualquer um pode usar, mesmo sem registro na Guilda), e pão para os primeiros dias.
     * A armadura já vem vestida; se o espaço estiver ocupado, vai para a mochila.
     */
    private static void kitInicial(ServerPlayer p) {
        if (dados(p).getBoolean("kit")) return;
        dados(p).putBoolean("kit", true);
        net.minecraft.world.item.Item[] armadura = {net.minecraft.world.item.Items.LEATHER_HELMET, net.minecraft.world.item.Items.LEATHER_CHESTPLATE,
                net.minecraft.world.item.Items.LEATHER_LEGGINGS, net.minecraft.world.item.Items.LEATHER_BOOTS};
        net.minecraft.world.entity.EquipmentSlot[] onde = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < armadura.length; i++) {
            net.minecraft.world.item.ItemStack peca = new net.minecraft.world.item.ItemStack(armadura[i]);
            if (p.getItemBySlot(onde[i]).isEmpty()) p.setItemSlot(onde[i], peca);
            else if (!p.getInventory().add(peca)) p.drop(peca, false);
        }
        net.minecraft.world.item.ItemStack[] itens = {new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_AXE), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SHOVEL),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 32)};
        for (net.minecraft.world.item.ItemStack s : itens) if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /**
     * Ponto inicial escolhido pelo admin: é o spawn do mundo, quando ele foi mudado com /setworldspawn
     * (o mod grava em frx_spawn.txt o spawn que ele mesmo pôs na capital; se for diferente, alguém mudou).
     */
    private static net.minecraft.core.BlockPos pontoInicial(ServerPlayer p) {
        try {
            if (p.getServer() == null) return null;
            ServerLevel nivel = p.getServer().overworld();
            net.minecraft.core.BlockPos s = nivel.getSharedSpawnPos();
            java.nio.file.Path marca = p.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("frx_spawn.txt");
            if (!java.nio.file.Files.exists(marca)) return null;
            String[] t = java.nio.file.Files.readString(marca).trim().split("\\s+");
            if (t.length < 3) return null;
            boolean igual = Integer.parseInt(t[0]) == s.getX() && Integer.parseInt(t[2]) == s.getZ();
            return igual ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private static String direcao(int x0, int z0, int x1, int z1) {
        double ang = Math.toDegrees(Math.atan2(x1 - x0, -(z1 - z0)));   // 0 = norte, 90 = leste
        if (ang < 0) ang += 360;
        String[] nomes = {"norte", "nordeste", "leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste"};
        return nomes[(int) Math.round(ang / 45) % 8];
    }

    // ------------------------------------------------------------------ comando de teste

    @SubscribeEvent
    public void comandos(RegisterCommandsEvent ev) {
        ev.getDispatcher().register(Commands.literal("frx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abertura")
                        .executes(ctx -> { previa(ctx.getSource().getPlayerOrException()); return 1; })
                        .then(Commands.literal("reset").executes(ctx -> {
                            // teste de verdade: apaga raça e classe e roda a cena como um jogador novo
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            RacasJogador.limpar(p);
                            br.com.frxcode.frxworld.habilidades.Habilidades.escolherClasse(p, "", true);
                            etapa(p, NADA);
                            iniciar(p);
                            return 1;
                        }))));
    }
}
