package com.github.regularrabbit05.voicefix;

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Bot {
    private final static String ENV_VAR = "BOT_TOKENS";
    private final static String ENV_VAR_PING = "BOT_PING";
    private final static String ENV_SEPARATOR = ",";
    private static int PING_THRESHOLD = 2000;

    private static boolean isAnyBotIn(VoiceChannel vc, List<Long> bots) {
        return vc.getMembers().stream().anyMatch(u -> bots.contains(u.getIdLong()));
    }

    static void main(String[] args) {
        try {
            if (args.length == 0) args = System.getenv(ENV_VAR).split(ENV_SEPARATOR);
            String pingParser = System.getenv(ENV_VAR_PING);
            if (pingParser != null && !pingParser.isBlank()) PING_THRESHOLD = Integer.parseInt(pingParser);
        } catch (Exception ignored) {
            System.err.println("Usage: java -jar voicefix.jar [token1] [token2] ...");
            System.exit(1);
        }
        final HashMap<Long, Long> channelPings = new HashMap<>();
        final LinkedList<BotInstance> bots = new LinkedList<>();
        for (String token : args) bots.push(new BotInstance(token, channelPings, PING_THRESHOLD));

        @SuppressWarnings("resource") final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            List<Long> botIds = bots.stream().map(b -> b.getJDA().getSelfUser().getIdLong()).toList();
            List<BotInstance> available = bots.stream().filter(BotInstance::isAvailable).toList();
            available.stream().findFirst().ifPresent(
                    first -> first.getJDA().getGuilds().forEach(g -> {
                        final List<VoiceChannel> channels = g.getVoiceChannels().stream()
                                .filter(vc -> vc.getMembers().size() > 1)
                                .filter(vc -> !isAnyBotIn(vc, botIds))
                                .sorted(Comparator.comparingInt(vc -> vc.getMembers().size()))
                                .limit(available.size()).toList();
                        for (int i = 0; i < channels.size(); i++) available.get(i).joinChannel(channels.get(i));
                    })
            );
        }, 5, 5, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggerFactory.getLogger(Bot.class).info("Shutting down...");
            executor.close();
            bots.forEach(BotInstance::shutdown);
        }));
    }
}
