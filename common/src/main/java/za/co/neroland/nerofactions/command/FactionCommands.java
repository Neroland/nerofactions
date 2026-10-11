package za.co.neroland.nerofactions.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.block.TradeTerminalBlock;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.data.PlayerDataExport;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.content.ValidationIssue;
import za.co.neroland.nerofactions.integration.EconomyOutcome;
import za.co.neroland.nerofactions.integration.FactionEconomy;
import za.co.neroland.nerofactions.membership.FactionDecay;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.reputation.ReputationSources;
import za.co.neroland.nerofactions.telemetry.NeroFactionsTelemetry;
import za.co.neroland.nerofactions.trade.TerminalOffers;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * The {@code /nerofactions} command tree — the same shape as NeroQuests' {@code QuestCommands},
 * registered identically from all three loaders (NeoForge/Forge {@code RegisterCommandsEvent},
 * Fabric {@code CommandRegistrationCallback}), so the tree itself lives here in common.
 *
 * <pre>
 * /nerofactions standing                                  (players, no permission)
 * /nerofactions factions                                  (players, no permission)
 * /nerofactions join &lt;faction&gt;                            (players, no permission)
 * /nerofactions leave &lt;faction&gt;                           (players, no permission)
 * /nerofactions data export                               (players, no permission — own data)
 * /nerofactions data export &lt;player&gt;                      (LEVEL_GAMEMASTERS)
 * /nerofactions reload-check                              (LEVEL_GAMEMASTERS)
 * /nerofactions admin grant &lt;player&gt; &lt;faction&gt; &lt;amount&gt;   (LEVEL_GAMEMASTERS)
 * /nerofactions admin revoke &lt;player&gt; &lt;faction&gt; &lt;amount&gt;  (LEVEL_GAMEMASTERS)
 * /nerofactions admin reset &lt;player&gt; [&lt;faction&gt;]          (LEVEL_GAMEMASTERS)
 * /nerofactions treasury                                 (members; NeroEconomy 0.3.0+)
 * /nerofactions treasury dues &lt;faction&gt; &lt;amount&gt;         (members; NeroEconomy 0.3.0+)
 * /nerofactions treasury payout &lt;faction&gt; &lt;player&gt; &lt;amount&gt; (Inner Circle or LEVEL_GAMEMASTERS)
 * /nerofactions terminal credits                          (at a trade terminal; any credit economy)
 * /nerofactions terminal buy &lt;offer&gt;                       (at a trade terminal; any credit economy)
 * </pre>
 *
 * <p><b>Commands are the 0.1.0 join mechanism</b>: {@code join}/{@code leave} route through
 * {@link FactionMembership} and surface its typed results via their existing translation keys.
 * {@code standing} applies pending decay first, so the number shown is the standing the player
 * actually still has.
 *
 * <p><b>Admin subcommands write through {@link ReputationApi} directly</b> — never through
 * {@code ReputationSources} — because operator actions must be <em>exact</em>: no source weights,
 * no daily caps, and <b>no enemy bleed</b>. The {@code <player>} argument accepts an online
 * player's name or a raw UUID (the QuestCommands precedent), so offline players stay reachable
 * for grant/revoke/reset.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Output goes to the invoker only — every {@code sendSuccess}
 * passes {@code false} for "broadcast to ops". Player-facing subcommands show the invoker their
 * <em>own</em> data only; admin feedback names factions and amounts, never a player identity.
 * {@code data export} is the DSAR surface: self-service without permission, or gamemaster-level
 * for another player (an operator fulfilling an access request); the export travels only to the
 * invoker's chat and is never written to disk or logged.
 *
 * <p>Server thread only.
 */
public final class FactionCommands {

    private FactionCommands() {
    }

    /** Builds {@code /nerofactions …}. Called once per loader from its command-registration hook. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerofactions")
                .then(Commands.literal("standing")
                        .executes(ctx -> runSafely(ctx.getSource(), "standing",
                                () -> standing(ctx.getSource()))))
                .then(Commands.literal("factions")
                        .executes(ctx -> runSafely(ctx.getSource(), "factions",
                                () -> listFactions(ctx.getSource()))))
                .then(Commands.literal("join")
                        .then(factionArgument()
                                .executes(ctx -> runSafely(ctx.getSource(), "join",
                                        () -> join(ctx)))))
                .then(Commands.literal("leave")
                        .then(factionArgument()
                                .executes(ctx -> runSafely(ctx.getSource(), "leave",
                                        () -> leave(ctx)))))
                .then(Commands.literal("data")
                        .then(Commands.literal("export")
                                .executes(ctx -> runSafely(ctx.getSource(), "data export",
                                        () -> exportSelf(ctx.getSource())))
                                .then(playerArgument()
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(ctx -> runSafely(ctx.getSource(), "data export",
                                                () -> exportOther(ctx))))))
                .then(Commands.literal("treasury")
                        .executes(ctx -> runSafely(ctx.getSource(), "treasury",
                                () -> treasuryBalances(ctx.getSource())))
                        .then(Commands.literal("dues")
                                .then(factionArgument()
                                        .then(amountArgument()
                                                .executes(ctx -> runSafely(ctx.getSource(),
                                                        "treasury dues", () -> payDues(ctx))))))
                        .then(Commands.literal("payout")
                                .then(factionArgument()
                                        .then(playerArgument()
                                                .then(amountArgument()
                                                        .executes(ctx -> runSafely(ctx.getSource(),
                                                                "treasury payout", () -> payout(ctx))))))))
                .then(Commands.literal("terminal")
                        .then(Commands.literal("credits")
                                .executes(ctx -> runSafely(ctx.getSource(), "terminal credits",
                                        () -> creditCatalogue(ctx.getSource()))))
                        .then(Commands.literal("buy")
                                .then(Commands.argument("offer", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> runSafely(ctx.getSource(), "terminal buy",
                                                () -> creditBuy(ctx))))))
                .then(Commands.literal("reload-check")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> runSafely(ctx.getSource(), "reload-check",
                                () -> reloadCheck(ctx.getSource()))))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("grant")
                                .then(playerArgument()
                                        .then(factionArgument()
                                                .then(amountArgument()
                                                        .executes(ctx -> runSafely(ctx.getSource(),
                                                                "admin grant",
                                                                () -> adjust(ctx, +1)))))))
                        .then(Commands.literal("revoke")
                                .then(playerArgument()
                                        .then(factionArgument()
                                                .then(amountArgument()
                                                        .executes(ctx -> runSafely(ctx.getSource(),
                                                                "admin revoke",
                                                                () -> adjust(ctx, -1)))))))
                        .then(Commands.literal("reset")
                                .then(playerArgument()
                                        .executes(ctx -> runSafely(ctx.getSource(), "admin reset",
                                                () -> resetAll(ctx)))
                                        .then(factionArgument()
                                                .executes(ctx -> runSafely(ctx.getSource(),
                                                        "admin reset",
                                                        () -> resetOne(ctx))))))));
    }

    // --- arguments ------------------------------------------------------------------------------

    /**
     * {@code <faction>} — a faction id, suggesting the loaded set. {@link IdentifierArgument}
     * parses {@code namespace:path} natively; a bare path defaults to the {@code minecraft:}
     * namespace, so {@link #resolveFaction} retries bare input against {@code nerofactions:} for
     * operator convenience.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> factionArgument() {
        return Commands.argument("faction", IdentifierArgument.id())
                .suggests((ctx, builder) -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    if (server != null) {
                        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                        for (Identifier id : FactionDefinitions.factionsForServer(server).keySet()) {
                            String text = id.toString();
                            if (text.startsWith(prefix) || id.getPath().startsWith(prefix)) {
                                builder.suggest(text);
                            }
                        }
                    }
                    return builder.buildFuture();
                });
    }

    /**
     * {@code <player>} — an online player's name or a raw UUID, suggesting the names of everyone
     * currently online (the QuestCommands precedent; offline players stay reachable by UUID).
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument("player", StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    if (server != null) {
                        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                            String name = online.getName().getString();
                            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                                builder.suggest(name);
                            }
                        }
                    }
                    return builder.buildFuture();
                });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> amountArgument() {
        return Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000));
    }

    // --- standing (player-facing) ---------------------------------------------------------------

    /**
     * The invoker's own standing, decay applied first: one line per faction they hold any
     * standing with (or belong to), value + tier name.
     */
    private static int standing(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        UUID playerId = player.getUUID();
        FactionDecay.apply(server, playerId);
        Set<Identifier> memberships = FactionMembership.membershipOf(server, playerId);

        int lines = 0;
        source.sendSuccess(() -> Component.translatable("command.nerofactions.standing.header"), false);
        for (FactionDefinition faction : FactionDefinitions.factionsForServer(server).values()) {
            int value = ReputationApi.getReputation(playerId, faction.id());
            if (value == 0 && !memberships.contains(faction.id())) {
                continue;
            }
            FactionTier tier = FactionTiers.tierOf(faction, value);
            Component line = Component.translatable("command.nerofactions.standing.line",
                    faction.displayName(), value, Component.translatable(tier.translationKey()));
            source.sendSuccess(() -> line, false);
            lines++;
        }
        if (lines == 0) {
            source.sendSuccess(() -> Component.translatable("command.nerofactions.standing.none"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- factions (player-facing) ---------------------------------------------------------------

    /** Lists every loaded faction, marking the ones the invoker belongs to. */
    private static int listFactions(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        if (factions.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerofactions.factions.none"), false);
            return Command.SINGLE_SUCCESS;
        }
        ServerPlayer player = source.getPlayer();
        Set<Identifier> memberships = player == null
                ? Set.of()
                : FactionMembership.membershipOf(server, player.getUUID());
        int count = factions.size();
        source.sendSuccess(() -> Component.translatable("command.nerofactions.factions.header",
                count), false);
        for (FactionDefinition faction : factions.values()) {
            String key = memberships.contains(faction.id())
                    ? "command.nerofactions.factions.line_member"
                    : "command.nerofactions.factions.line";
            Component line = Component.translatable(key,
                    faction.displayName(), faction.id().toString());
            source.sendSuccess(() -> line, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- join / leave (player-facing, the 0.1.0 join mechanism) ---------------------------------

    private static int join(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        Identifier faction = resolveFaction(server, ctx);
        FactionMembership.JoinResult result =
                FactionMembership.join(server, player.getUUID(), faction);
        Component message = Component.translatable(result.translationKey(),
                displayNameOf(server, faction));
        if (result == FactionMembership.JoinResult.JOINED) {
            source.sendSuccess(() -> message, false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(message);
        return 0;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        Identifier faction = resolveFaction(server, ctx);
        FactionMembership.LeaveResult result =
                FactionMembership.leave(server, player.getUUID(), faction);
        Component message = Component.translatable(result.translationKey(),
                displayNameOf(server, faction));
        if (result == FactionMembership.LeaveResult.LEFT) {
            source.sendSuccess(() -> message, false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(message);
        return 0;
    }

    // --- data export (DSAR — GDPR Art. 15/20, POPIA s23) ----------------------------------------

    /**
     * {@code data export}: the invoker's own export, no permission needed — access to one's own
     * data is a data-subject right, not an operator privilege.
     */
    private static int exportSelf(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        return sendExport(source, server, player.getUUID());
    }

    /**
     * {@code data export <player>} (LEVEL_GAMEMASTERS): an operator answering a data-subject
     * access request for someone else — online name or raw UUID, so departed players stay
     * reachable, exactly like the admin subcommands.
     */
    private static int exportOther(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        UUID player = resolvePlayer(source, server, ctx);
        if (player == null) {
            return 0;
        }
        return sendExport(source, server, player);
    }

    /**
     * Sends the JSON export to the <em>invoker only</em>: a summary line plus a click-to-copy
     * component carrying the full JSON in its {@link ClickEvent.CopyToClipboard} (the vanilla/Forge
     * copy pattern). Nothing is written to disk and nothing — neither the content nor the target
     * identity — is logged; {@code runSafely}'s telemetry captures the subcommand name only.
     */
    private static int sendExport(CommandSourceStack source, MinecraftServer server, UUID player) {
        String json = PlayerDataExport.forPlayer(server, player);
        Component copy = Component.translatable("command.nerofactions.export.copy")
                .withStyle(style -> style
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(json))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("command.nerofactions.export.copy_hover"))));
        Component line = Component.translatable("command.nerofactions.export.summary", copy);
        source.sendSuccess(() -> line, false);
        return Command.SINGLE_SUCCESS;
    }

    // --- admin: grant / revoke / reset (exact, through ReputationApi — see class javadoc) -------

    /** {@code admin grant}/{@code admin revoke}: an exact adjustment, no weights/caps/bleed. */
    private static int adjust(CommandContext<CommandSourceStack> ctx, int sign) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        UUID player = resolvePlayer(source, server, ctx);
        if (player == null) {
            return 0;
        }
        Identifier faction = resolveFaction(server, ctx);
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) {
            source.sendFailure(Component.translatable("command.nerofactions.faction.unknown",
                    faction.toString()));
            return 0;
        }
        int amount = sign * IntegerArgumentType.getInteger(ctx, "amount");
        int now = ReputationApi.adjust(player, faction, amount);
        source.sendSuccess(() -> Component.translatable("command.nerofactions.admin.adjusted",
                definition.displayName(), amount, now), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code admin reset <player> <faction>}: standing with one faction back to exactly 0. */
    private static int resetOne(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        UUID player = resolvePlayer(source, server, ctx);
        if (player == null) {
            return 0;
        }
        Identifier faction = resolveFaction(server, ctx);
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) {
            source.sendFailure(Component.translatable("command.nerofactions.faction.unknown",
                    faction.toString()));
            return 0;
        }
        ReputationApi.setReputation(player, faction, 0);
        source.sendSuccess(() -> Component.translatable("command.nerofactions.admin.reset.one",
                definition.displayName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code admin reset <player>}: standing with every loaded faction back to exactly 0. */
    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        UUID player = resolvePlayer(source, server, ctx);
        if (player == null) {
            return 0;
        }
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        for (Identifier faction : factions.keySet()) {
            ReputationApi.setReputation(player, faction, 0);
        }
        int count = factions.size();
        source.sendSuccess(() -> Component.translatable("command.nerofactions.admin.reset.all",
                count), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- treasury (NeroEconomy 0.3.0+ only) -----------------------------------------------------

    /**
     * {@code treasury}: what the treasury of each faction the invoker belongs to holds. A faction
     * treasury is faction data, not personal data; only members are shown it.
     */
    private static int treasuryBalances(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        if (!FactionEconomy.treasuriesAvailable()) {
            source.sendFailure(Component.translatable(EconomyOutcome.UNAVAILABLE_KEY));
            return 0;
        }
        List<Identifier> memberships = new ArrayList<>(
                FactionMembership.membershipOf(server, player.getUUID()));
        memberships.sort(null);
        if (memberships.isEmpty()) {
            source.sendFailure(Component.translatable("command.nerofactions.treasury.no_membership"));
            return 0;
        }
        for (Identifier faction : memberships) {
            long balance = FactionEconomy.treasuryBalance(server, faction);
            Component line = Component.translatable("command.nerofactions.treasury.line",
                    displayNameOf(server, faction), balance);
            source.sendSuccess(() -> line, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@code treasury dues <faction> <amount>}: a member pays credits into the faction treasury. */
    private static int payDues(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        if (!FactionEconomy.treasuriesAvailable()) {
            source.sendFailure(Component.translatable(EconomyOutcome.UNAVAILABLE_KEY));
            return 0;
        }
        Identifier faction = resolveFaction(server, ctx);
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) {
            source.sendFailure(Component.translatable("command.nerofactions.faction.unknown",
                    faction.toString()));
            return 0;
        }
        if (!FactionMembership.membershipOf(server, player.getUUID()).contains(faction)) {
            source.sendFailure(Component.translatable("command.nerofactions.treasury.not_member",
                    definition.displayName()));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        EconomyOutcome outcome = FactionEconomy.payDues(server, player.getUUID(), faction, amount);
        if (!outcome.ok()) {
            source.sendFailure(Component.translatable(outcome.translationKey()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.nerofactions.treasury.dues_paid",
                amount, definition.displayName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code treasury payout <faction> <player> <amount>}: the treasury pays a member. Allowed for
     * the faction's Inner Circle (its officers — NeroFactions has no other faction roles), who may
     * not pay themselves, and for operators. The payee must belong to the faction. The invoker's
     * feedback names the faction and amount only; the payee, if online, is told which treasury
     * paid them, never who issued it.
     */
    private static int payout(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        if (!FactionEconomy.treasuriesAvailable()) {
            source.sendFailure(Component.translatable(EconomyOutcome.UNAVAILABLE_KEY));
            return 0;
        }
        Identifier faction = resolveFaction(server, ctx);
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) {
            source.sendFailure(Component.translatable("command.nerofactions.faction.unknown",
                    faction.toString()));
            return 0;
        }
        UUID payee = resolvePlayer(source, server, ctx);
        if (payee == null) {
            return 0;
        }
        boolean operator = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
        if (!operator) {
            ServerPlayer officer = source.getPlayer();
            if (officer == null) {
                source.sendFailure(Component.translatable("command.nerofactions.players_only"));
                return 0;
            }
            UUID officerId = officer.getUUID();
            FactionDecay.apply(server, officerId);
            boolean member = FactionMembership.membershipOf(server, officerId).contains(faction);
            FactionTier tier = FactionTiers.tierOf(definition, ReputationApi.getReputation(officerId, faction));
            if (!member || tier != FactionTier.INNER_CIRCLE) {
                source.sendFailure(Component.translatable("command.nerofactions.treasury.not_officer",
                        definition.displayName()));
                return 0;
            }
            if (officerId.equals(payee)) {
                source.sendFailure(Component.translatable("command.nerofactions.treasury.self_payout"));
                return 0;
            }
        }
        if (!FactionMembership.membershipOf(server, payee).contains(faction)) {
            source.sendFailure(Component.translatable("command.nerofactions.treasury.payee_not_member",
                    definition.displayName()));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        EconomyOutcome outcome = FactionEconomy.payout(server, faction, payee, amount);
        if (!outcome.ok()) {
            source.sendFailure(Component.translatable(outcome.translationKey()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.nerofactions.treasury.paid_out",
                amount, definition.displayName()), false);
        ServerPlayer online = server.getPlayerList().getPlayer(payee);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("message.nerofactions.treasury.received",
                    definition.displayName(), amount));
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- terminal credit counter (any credit economy) -------------------------------------------

    /** {@code terminal credits}: the credit catalogue of the terminal the player stands at. */
    private static int creditCatalogue(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        if (!creditCounterOn(source)) {
            return 0;
        }
        FactionDefinition faction = terminalFaction(source, server, player);
        if (faction == null) {
            return 0;
        }
        List<TerminalOffers.CreditOffer> offers = creditOffersFor(server, player, faction);
        source.sendSuccess(() -> Component.translatable("command.nerofactions.terminal.credits.header",
                faction.displayName()), false);
        for (int index = 0; index < offers.size(); index++) {
            TerminalOffers.CreditOffer offer = offers.get(index);
            if (!BuiltInRegistries.ITEM.containsKey(offer.item())) {
                continue;
            }
            Component name = new ItemStack(BuiltInRegistries.ITEM.getValue(offer.item())).getHoverName();
            String command = "/nerofactions terminal buy " + (index + 1);
            Component line = Component.translatable("command.nerofactions.terminal.credits.line",
                    offer.count(), name, offer.price())
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable(
                                    "command.nerofactions.terminal.credits.hover"))));
            source.sendSuccess(() -> line, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code terminal buy <offer>}: buys one credit-counter line. The credits are charged first;
     * the goods are delivered only when the charge went through.
     */
    private static int creditBuy(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerofactions.players_only"));
            return 0;
        }
        if (!creditCounterOn(source)) {
            return 0;
        }
        FactionDefinition faction = terminalFaction(source, server, player);
        if (faction == null) {
            return 0;
        }
        List<TerminalOffers.CreditOffer> offers = creditOffersFor(server, player, faction);
        int index = IntegerArgumentType.getInteger(ctx, "offer") - 1;
        if (index < 0 || index >= offers.size()
                || !BuiltInRegistries.ITEM.containsKey(offers.get(index).item())) {
            source.sendFailure(Component.translatable("command.nerofactions.terminal.credits.no_offer"));
            return 0;
        }
        TerminalOffers.CreditOffer offer = offers.get(index);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(offer.item()), offer.count());
        Component name = stack.getHoverName();
        EconomyOutcome outcome = FactionEconomy.chargeTerminal(server, player.getUUID(), offer.price());
        if (!outcome.ok()) {
            source.sendFailure(Component.translatable(outcome.translationKey()));
            return 0;
        }
        // Charged: deliver (vanilla give behaviour — fill the inventory, drop the remainder).
        //? if >=26.3 {
        /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.getInventory().placeItemBackInInventory(stack);
        //?}
        int base = NeroFactionsConfig.TRADE_AWARD_BASE.get();
        if (base > 0) {
            ReputationSources.award(server, player.getUUID(), faction.id(), ReputationSources.Source.TRADE, base);
        }
        source.sendSuccess(() -> Component.translatable("command.nerofactions.terminal.credits.bought",
                offer.count(), name, offer.price()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Whether the credit counter runs here; reports why not to the invoker. */
    private static boolean creditCounterOn(CommandSourceStack source) {
        if (!FactionEconomy.creditsAvailable() || NeroFactionsConfig.TERMINAL_CREDITS_PER_EMERALD.get() <= 0) {
            source.sendFailure(Component.translatable(EconomyOutcome.UNAVAILABLE_KEY));
            return false;
        }
        return true;
    }

    /** The credit offers the player's current standing unlocks at this faction's counter. */
    private static List<TerminalOffers.CreditOffer> creditOffersFor(MinecraftServer server,
            ServerPlayer player, FactionDefinition faction) {
        FactionDecay.apply(server, player.getUUID());
        FactionTier tier = FactionTiers.tierOf(faction,
                ReputationApi.getReputation(player.getUUID(), faction.id()));
        return TerminalOffers.creditOffers(faction, tier,
                NeroFactionsConfig.DISCOUNT_CAP_PERCENT.get(),
                NeroFactionsConfig.SURCHARGE_CAP_PERCENT.get(),
                NeroFactionsConfig.TERMINAL_CREDITS_PER_EMERALD.get());
    }

    /**
     * The faction whose counter the player is at: the terminal they last used must still be a
     * trade terminal within 8 blocks in their dimension, and the faction it last showed them must
     * still be one of theirs. Null after reporting why not.
     */
    private static FactionDefinition terminalFaction(CommandSourceStack source, MinecraftServer server,
            ServerPlayer player) {
        UUID playerId = player.getUUID();
        TerminalSessions.TerminalVisit visit = TerminalSessions.lastTerminal(playerId);
        boolean near = false;
        if (visit != null && player.level().dimension().identifier().equals(visit.dimension())) {
            BlockPos pos = BlockPos.of(visit.pos());
            near = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                    && player.level().getBlockState(pos).getBlock() instanceof TradeTerminalBlock;
        }
        if (!near) {
            source.sendFailure(Component.translatable("command.nerofactions.terminal.not_near"));
            return null;
        }
        Identifier selected = TerminalSessions.selected(playerId);
        FactionDefinition faction = selected == null
                ? null
                : FactionDefinitions.factionsForServer(server).get(selected);
        if (faction == null || !FactionMembership.membershipOf(server, playerId).contains(selected)) {
            source.sendFailure(Component.translatable("message.nerofactions.terminal.not_member"));
            return null;
        }
        return faction;
    }

    // --- reload-check ---------------------------------------------------------------------------

    /**
     * Re-reads every faction definition from the current datapacks, then reports what loaded and
     * everything the loader dropped or ignored — the operator's view of a pack problem without
     * reading the server log.
     */
    private static int reloadCheck(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noServer(source);
        }
        FactionDefinitions.reload(server);
        int factionCount = FactionDefinitions.factions().size();
        source.sendSuccess(() -> Component.translatable("command.nerofactions.reload.header",
                factionCount), false);

        List<ValidationIssue> issues = FactionDefinitions.validationIssues();
        if (issues.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.nerofactions.reload.clean"), false);
        } else {
            int issueCount = issues.size();
            source.sendSuccess(() -> Component.translatable("command.nerofactions.reload.issues",
                    issueCount), false);
            for (ValidationIssue issue : issues) {
                String line = "  " + (issue.severity() == ValidationIssue.Severity.DROPPED
                        ? "§c[dropped]§r " : "§6[ignored]§r ") + issue.id() + " — " + issue.detail();
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    // --- resolution helpers ---------------------------------------------------------------------

    /**
     * The {@code faction} argument, with the bare-path convenience: {@code space_guild} parses as
     * {@code minecraft:space_guild}, so if that names no loaded faction but
     * {@code nerofactions:<path>} does, the shipped faction is meant.
     */
    static Identifier resolveFaction(MinecraftServer server, CommandContext<CommandSourceStack> ctx) {
        Identifier raw = IdentifierArgument.getId(ctx, "faction");
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        if (!factions.containsKey(raw) && "minecraft".equals(raw.getNamespace())) {
            Identifier shipped = Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID,
                    raw.getPath());
            if (factions.containsKey(shipped)) {
                return shipped;
            }
        }
        return raw;
    }

    /** A faction's display name for messages — its data-driven name, or the raw id if unknown. */
    private static String displayNameOf(MinecraftServer server, Identifier faction) {
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        return definition == null ? String.valueOf(faction) : definition.displayName();
    }

    /**
     * The {@code player} argument as a UUID — an online player's name or a raw UUID — or null
     * after reporting that it named nobody. Offline players are reachable by UUID on purpose:
     * grant, revoke and reset must work for someone who has left.
     */
    private static UUID resolvePlayer(CommandSourceStack source, MinecraftServer server,
            CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "player").trim();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getName().getString().equalsIgnoreCase(raw)) {
                return online.getUUID();
            }
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("command.nerofactions.player.unknown"));
            return null;
        }
    }

    // --- plumbing -------------------------------------------------------------------------------

    private static int noServer(CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.nerofactions.no_server"));
        return 0;
    }

    /**
     * Runs one subcommand body, turning an unexpected failure into a polite message plus an
     * anonymous telemetry event instead of a Brigadier stack trace in chat. The captured context is
     * the subcommand name only — never its arguments.
     */
    private static int runSafely(CommandSourceStack source, String subcommand, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException e) {
            NeroFactionsTelemetry.captureHandledException(e, "command", "/nerofactions " + subcommand);
            NeroFactionsCommon.LOGGER.error("[NeroFactions] /nerofactions {} failed", subcommand, e);
            source.sendFailure(Component.translatable("command.nerofactions.failed", subcommand));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandBody {

        int run();
    }
}
