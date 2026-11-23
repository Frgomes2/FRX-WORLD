package br.com.frxcode.frxworld.cliente;

import br.com.frxcode.frxworld.rede.SyncHabilidades;
import br.com.frxcode.frxworld.rede.SyncRecarga;
import br.com.frxcode.frxworld.rede.SyncRecurso;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Classe, habilidades, barra, recurso e recargas do jogador (só no cliente). */
public final class ClienteHabilidades {
    public static String classe = "";
    public static String recurso = "";
    public static List<SyncHabilidades.Info> habilidades = new ArrayList<>();
    public static String[] barra = {"", "", "", "", "", "", "", "", ""};
    /** Espaços extras (5 a 8) que o rank da Guilda já liberou. */
    public static int extras;
    /** Habilidades ferem jogadores (botão da janela). */
    public static boolean pvp;
    public static int recursoAtual = 0, recursoMax = 100;
    public static boolean recebido = false;
    /** Despertar: barra 0-100, poder ativo (ticks), duração, cargas extras, se já liberou (nível 10). */
    public static int despertar = 0, despertarAtivo = 0, despertarTotal = 1, despertarExtra = 0;
    public static boolean despertarLiberado = false;
    /** código → [fim em ms, total em ms] */
    private static final Map<String, long[]> recargas = new HashMap<>();
    private static final Map<String, ItemStack> icones = new HashMap<>();

    private ClienteHabilidades() {}

    public static void aplicar(SyncHabilidades msg) {
        classe = msg.classe();
        recurso = msg.recurso();
        habilidades = msg.habilidades();
        for (int i = 0; i < barra.length; i++) barra[i] = i < msg.barra().size() ? msg.barra().get(i) : "";
        extras = Math.max(0, Math.min(4, msg.extras()));
        pvp = msg.pvp();
        recebido = true;
        if (Minecraft.getInstance().screen instanceof TelaHabilidades tela) tela.atualizar();
    }

    public static void aplicar(br.com.frxcode.frxworld.rede.SyncDespertar msg) {
        despertar = msg.carga();
        despertarAtivo = msg.ativo();
        despertarTotal = Math.max(1, msg.total());
        despertarExtra = msg.extra();
        despertarLiberado = msg.liberado();
    }

    /** Nome, anime de inspiração, como enche e o que faz — para a tela de Habilidades. */
    public static String[] despertarInfo(String classe) {
        return switch (classe) {
            case "GUERREIRO" -> new String[]{"Armadura do Berserker", "Berserk",
                    "Enche batendo e, mais ainda, apanhando.",
                    "10–20 s: +50% de dano, −25% de dano recebido, mais velocidade de ataque e de corrida, não sofre empurrão e NÃO CAI (fica com 1 de vida). A armadura cobra 1 de vida a cada 2 s e no fim você fica exausto (fraqueza e lentidão por 6 s)."};
            case "ARQUEIRO" -> new String[]{"Chuva de Luz", "Bleach (Quincy)",
                    "Enche acertando flechas; tiros de 20+ blocos valem mais.",
                    "Mire num ponto até 48 blocos: por 3 s, flechas espirituais caem do céu sobre até 12 inimigos num raio de 9 blocos. Não ferem aliados e não gastam flechas."};
            case "MAGO" -> new String[]{"Explosão!", "Konosuba",
                    "Enche gastando mana nas habilidades.",
                    "3 s de canto (lento; se levar um golpe forte, perde metade) e então uma explosão gigante no ponto mirado (até 50 blocos, raio 7, dano enorme, sem quebrar blocos). Depois você desaba: mana zerada e 8 s exausto."};
            case "CLERIGO" -> new String[]{"Estandarte da Donzela", "Fate (Jeanne d'Arc)",
                    "Enche gastando mana e ferindo, principalmente mortos-vivos.",
                    "Finca um estandarte de luz por 10–18 s (raio 7): aliados (jogadores, aldeões, invocações) curam toda hora e levam METADE do dano; inimigos ficam lentos e brilhando; mortos-vivos queimam."};
            case "LADINO" -> new String[]{"Roubar!", "Konosuba (Kazuma) · Lupin III",
                    "Enche com golpes; pelas costas e abates valem mais.",
                    "Rouba de até 10 inimigos num raio de 8: desarma (pega a arma), puxa um saque de cada um (uma vez por criatura) e moedas, deixando-os fracos e lentos. Você some (invisível e veloz por 5 s). Chance cresce com o nível; 2% de sair uma moeda de ouro."};
            case "ASSASSINO" -> new String[]{"Respiração do Trovão", "Demon Slayer (Zenitsu)",
                    "Enche com golpes; pelas costas ou em alvo quase morto vale o dobro.",
                    "Primeira Forma: investida-relâmpago de 12 blocos na direção do olhar, cortando todos no caminho (1,6× a 2,6× o dano da arma). Você ganha 3 investidas em 5 s — aperte de novo para encadear."};
            case "PALADINO" -> new String[]{"Égide da Aurora", "Original do FRX-WORLD",
                    "Enche apanhando, batendo de perto e gastando mana.",
                    "8–14 s: uma cúpula de luz de 6 blocos segue você. Você e os aliados dentro dela levam 60% menos dano e regeneram. Todo o dano que a cúpula barrar fica guardado e, no fim, explode em luz nos inimigos em volta (dobrado em mortos-vivos), curando os aliados."};
            case "BARDO" -> new String[]{"Bis!", "Original do FRX-WORLD",
                    "Enche gastando mana nas canções e ferindo inimigos.",
                    "Na hora: zera as recargas e enche o recurso de todos os jogadores a até 12 blocos (você incluso). Por 8 s o grupo causa 20% a mais de dano e regenera."};
            case "DRUIDA" -> new String[]{"Coração da Floresta", "Original do FRX-WORLD",
                    "Enche gastando mana, batendo de perto e apanhando.",
                    "10 s: as habilidades não gastam mana, as garras batem 50% mais forte, você regenera e as formas de lobo e de ave deixam de apanhar a mais."};
            case "SAMURAI" -> new String[]{"Lâmina das Três Formas", "Original do FRX-WORLD",
                    "Enche acertando golpes de perto; com uma katana na mão enche bem mais rápido.",
                    "8–12 s: todo golpe de katana é um Saque e alterna três formas: Brasa (queima), Maré (deixa lento) e Relâmpago (dano extra). Você corre e golpeia mais rápido."};
            case "CARTOMANTE" -> new String[]{"Mão Perfeita", "Original do FRX-WORLD",
                    "Enche acertando habilidades.",
                    "8–12 s: a mão vira três Coringas e toda carta nova também é Coringa. Cada golpe fere 40% a mais, cura você, devolve mana e prende o alvo."};
            case "MONGE" -> new String[]{"Portão Interior", "Original do FRX-WORLD",
                    "Enche acertando socos e golpes de perto.",
                    "8–12 s: seus socos causam o dobro e estouram em volta do alvo, você corre e salta mais e as habilidades não gastam chi. No fim você fica exausto (fraqueza e lentidão por 5 s)."};
            case "INVOCADOR" -> new String[]{"Levante-se!", "Solo Leveling",
                    "Sem barra: mate criaturas e erga as almas delas.",
                    "A tecla ergue como sombra a alma mais próxima (20 s depois do abate). Veja e comande o exército em ⚫ Exército."};
            default -> new String[]{"", "", "", ""};
        };
    }

    public static int corDespertar(String classe) {
        return switch (classe) {
            case "GUERREIRO" -> 0xFFB01E1E; case "ARQUEIRO" -> 0xFF6FE3F5; case "MAGO" -> 0xFFFF7A1A;
            case "CLERIGO" -> 0xFFFFE07A; case "LADINO" -> 0xFF5BD17A; case "ASSASSINO" -> 0xFFFFE01A; case "PALADINO" -> 0xFFFFF3B0; case "BARDO" -> 0xFFE879F9; case "MONGE" -> 0xFF3FD6C4; case "DRUIDA" -> 0xFF6BCB4B; case "SAMURAI" -> 0xFFE0524A; case "CARTOMANTE" -> 0xFF4FD6E8; default -> 0xFFC58CF0;
        };
    }

    public static void aplicar(SyncRecurso msg) {
        recursoAtual = msg.atual();
        recursoMax = Math.max(1, msg.maximo());
    }

    public static void aplicar(SyncRecarga msg) {
        recargas.put(msg.codigo(), new long[]{System.currentTimeMillis() + msg.restanteMs(), Math.max(1, msg.totalMs())});
    }

    public static SyncHabilidades.Info info(String codigo) {
        if (codigo == null || codigo.isEmpty()) return null;
        for (SyncHabilidades.Info i : habilidades) if (i.codigo().equals(codigo)) return i;
        return null;
    }

    /** Fração da recarga que falta (0 = pronta) e ms restantes. */
    public static float recargaFracao(String codigo) {
        long[] r = recargas.get(codigo);
        if (r == null) return 0;
        long resta = r[0] - System.currentTimeMillis();
        return resta <= 0 ? 0 : Math.min(1f, (float) resta / r[1]);
    }

    public static long recargaRestante(String codigo) {
        long[] r = recargas.get(codigo);
        return r == null ? 0 : Math.max(0, r[0] - System.currentTimeMillis());
    }

    public static ItemStack icone(String id) {
        return icones.computeIfAbsent(id, k -> {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(k));
            return new ItemStack(item == null || item == Items.AIR ? Items.BOOK : item);
        });
    }

    public static String nomeRecurso() {
        return switch (recurso) { case "FURIA" -> "Fúria"; case "ENERGIA" -> "Energia"; case "MANA" -> "Mana"; case "CHI" -> "Chi"; default -> ""; };
    }

    public static int corRecurso() {
        return switch (recurso) { case "FURIA" -> 0xFFC0392B; case "ENERGIA" -> 0xFFE5B91F; case "CHI" -> 0xFF3FD6C4; default -> 0xFF3B82F6; };
    }

    public static void limpar() {
        classe = "";
        recurso = "";
        habilidades = new ArrayList<>();
        for (int i = 0; i < barra.length; i++) barra[i] = "";
        recargas.clear();
        recebido = false;
        despertar = despertarAtivo = despertarExtra = 0;
        despertarLiberado = false;
    }
}
