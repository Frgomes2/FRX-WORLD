package br.com.frxcode.frxworld.taverna;

import br.com.frxcode.frxworld.FrxWorld;
import br.com.frxcode.frxworld.missoes.Missoes;
import br.com.frxcode.frxworld.progressao.ConfigJogo;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tavernas: estrutura nas vilas (injetada nas listas de casas das vilas vanilla),
 * comando para construir uma nas capitais e o taverneiro que abre o Quadro de Missões.
 */
public class Tavernas {
    public static final ResourceLocation ESTRUTURA = new ResourceLocation(FrxWorld.MOD_ID, "taverna");
    public static final String TAG_TAVERNEIRO = "frx_taverneiro";
    private static final int PESO_NAS_VILAS = 6;
    private static final String[] VILAS = {"plains", "desert", "savanna", "taiga", "snowy"};

    // ------------------------------------------------------------------ vilas

    @SubscribeEvent
    public void aoPrepararServidor(ServerAboutToStartEvent evento) {
        MinecraftServer server = evento.getServer();
        Registry<StructureTemplatePool> pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processadores = server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
        Holder<StructureProcessorList> vazio = processadores.getHolderOrThrow(
                ResourceKey.create(Registries.PROCESSOR_LIST, new ResourceLocation("minecraft", "empty")));

        int ok = 0;
        for (String vila : VILAS) {
            if (adicionarNaPool(pools, new ResourceLocation("minecraft", "village/" + vila + "/houses"), vazio)) ok++;
        }
        FrxWorld.LOG.info("Taverna adicionada em {} tipos de vila.", ok);
    }

    @SuppressWarnings("unchecked")
    private static boolean adicionarNaPool(Registry<StructureTemplatePool> pools, ResourceLocation idPool,
                                           Holder<StructureProcessorList> processadores) {
        StructureTemplatePool pool = pools.get(idPool);
        if (pool == null) return false;
        try {
            SinglePoolElement peca = SinglePoolElement.legacy(ESTRUTURA.toString(), processadores)
                    .apply(StructureTemplatePool.Projection.RIGID);
            // campos privados da pool: templates (f_210560_) e rawTemplates (f_210559_)
            List<StructurePoolElement> templates = ObfuscationReflectionHelper.getPrivateValue(StructureTemplatePool.class, pool, "f_210560_");
            for (int i = 0; i < PESO_NAS_VILAS; i++) templates.add(peca);
            List<Pair<StructurePoolElement, Integer>> atuais =
                    ObfuscationReflectionHelper.getPrivateValue(StructureTemplatePool.class, pool, "f_210559_");
            List<Pair<StructurePoolElement, Integer>> brutos = new ArrayList<>(atuais);
            brutos.add(Pair.of(peca, PESO_NAS_VILAS));
            ObfuscationReflectionHelper.setPrivateValue(StructureTemplatePool.class, pool, brutos, "f_210559_");
            return true;
        } catch (Exception e) {
            FrxWorld.LOG.warn("Não consegui adicionar a taverna em {}: {}", idPool, e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ construir (capitais)

    /** Constrói a taverna à frente do jogador, com a porta virada para ele. */
    public static boolean construir(ServerPlayer jogador) {
        ServerLevel nivel = jogador.serverLevel();
        Optional<StructureTemplate> modelo = nivel.getStructureManager().get(ESTRUTURA);
        if (modelo.isEmpty()) return false;

        // A porta do modelo fica no lado oeste (x=0). Gira para ela ficar de frente para o jogador.
        Rotation rotacao = switch (jogador.getDirection()) {
            case EAST -> Rotation.NONE;          // jogador olha para leste: porta a oeste (perto dele)
            case SOUTH -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_180;
            default -> Rotation.COUNTERCLOCKWISE_90; // NORTH
        };
        BlockPos frente = jogador.blockPosition().relative(jogador.getDirection(), 2).below();
        // desloca para que a entrada (x=0, z=5 do modelo) fique na frente do jogador
        BlockPos entradaGirada = new BlockPos(0, 0, 5).rotate(rotacao);
        BlockPos origem = frente.subtract(entradaGirada);

        StructurePlaceSettings cfg = new StructurePlaceSettings().setRotation(rotacao).setIgnoreEntities(false)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        modelo.get().placeInWorld(nivel, origem, origem, cfg, nivel.random, 2);
        return true;
    }

    // ------------------------------------------------------------------ taverneiro

    private static boolean taverneiro(Entity e) {
        return e != null && e.getTags().contains(TAG_TAVERNEIRO);
    }

    @SubscribeEvent
    public void aoInteragir(PlayerInteractEvent.EntityInteract evento) {
        if (!taverneiro(evento.getTarget())) return;
        evento.setCanceled(true);
        evento.setCancellationResult(InteractionResult.SUCCESS);
        if (evento.getEntity() instanceof ServerPlayer jogador && evento.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            falar(jogador, evento.getTarget());
            Missoes.abrirQuadro(jogador);
        }
    }

    @SubscribeEvent
    public void aoInteragirEspecifico(PlayerInteractEvent.EntityInteractSpecific evento) {
        if (!taverneiro(evento.getTarget())) return;
        evento.setCanceled(true);
        evento.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Taverneiro não apanha. Para remover: modo criativo agachado. */
    @SubscribeEvent
    public void protegerTaverneiro(LivingAttackEvent evento) {
        if (!taverneiro(evento.getEntity())) return;
        if (evento.getSource().getEntity() instanceof Player p && p.isCreative() && p.isShiftKeyDown()) return;
        evento.setCanceled(true);
    }

    private static void falar(ServerPlayer jogador, Entity taverneiro) {
        String nome = ConfigJogo.taverneiroNome();
        taverneiro.setCustomName(Component.literal(nome).withStyle(ChatFormatting.GOLD));
        List<String> falas = ConfigJogo.taverneiroFalas();
        String fala = falas.isEmpty() ? "Bem-vindo, aventureiro!" : falas.get(ThreadLocalRandom.current().nextInt(falas.size()));
        fala = fala.replace("{jogador}", jogador.getGameProfile().getName());
        jogador.sendSystemMessage(Component.literal("[" + nome + "] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(fala).withStyle(ChatFormatting.WHITE)));
    }
}
