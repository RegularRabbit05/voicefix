package com.github.regularrabbit05.voicefix;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Bot extends ListenerAdapter {
    private final static int PING_THRESHOLD = 2000;
    private final static String ENV_VAR = "BOT_TOKENS";
    private final static String ENV_SEPARATOR = ",";

    private static boolean isAnyBotIn(VoiceChannel vc, List<Long> bots) {
        return vc.getMembers().stream().anyMatch(u -> bots.contains(u.getIdLong()));
    }

    static void main(String[] args) {
        try {
            if (args.length == 0) args = System.getenv(ENV_VAR).split(ENV_SEPARATOR);
        } catch (Exception ignored) {
            System.out.println("Usage: java -jar voicefix.jar [token1] [token2] ...");
            System.exit(1);
        }
        final HashMap<Long, Long> channelPings = new HashMap<>();
        final LinkedList<Bot> bots = new LinkedList<>();
        for (String token : args) bots.push(new Bot(token, channelPings));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> bots.forEach(Bot::shutdown)));

        final Runnable scheduler = () -> {
            List<Long> botIds = bots.stream().map(b -> b.getJDA().getSelfUser().getIdLong()).toList();
            List<Bot> available = bots.stream().filter(Bot::isAvailable).toList();
            available.stream().findFirst().ifPresent(
                first -> first.getJDA().getGuilds().forEach(g -> {
                    final List<VoiceChannel> channels = g.getVoiceChannels().stream().filter(vc -> vc.getMembers().size() > 1).filter(vc -> !isAnyBotIn(vc, botIds)).sorted(Comparator.comparingInt(vc -> vc.getMembers().size())).limit(available.size()).toList();
                    for (int i = 0; i < channels.size(); i++) available.get(i).joinChannel(channels.get(i));
                })
            );
        };
        @SuppressWarnings("resource") ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(scheduler, 5, 5, TimeUnit.SECONDS);
    }

    private final HashMap<Long, Long> channelPings;
    private static int botID = 0;
    private JDA jda;
    private boolean isReady = false;
    private AudioManager audioManager = null;

    public void updateChannelPing(final Long channelId, final Long ping) {
        Long previous;
        synchronized (channelPings) { previous = channelPings.put(channelId, ping); }

        if (previous == null) return;
        final VoiceChannel vc = jda.getVoiceChannelById(channelId);
        if (vc == null) return;
        if (ping < PING_THRESHOLD || previous < PING_THRESHOLD) return;
        final Region current = vc.getRegion();
        final ArrayList<Region> regions = new ArrayList<>(Arrays.stream(Region.values()).filter(r -> !r.isVip()).toList());
        regions.remove(current);
        regions.remove(Region.AUTOMATIC);
        Region randomRegion = regions.get(ThreadLocalRandom.current().nextInt(regions.size()));
        if (current ==  Region.AUTOMATIC || current == Region.UNKNOWN) randomRegion = Region.SOUTH_AFRICA;
        System.out.println("Moving channel " + vc.getName() + " to region " +  randomRegion.getName() + " from " + current.getName());
        vc.getManager().setRegion(randomRegion).queue(_ -> {
            synchronized (channelPings) { channelPings.remove(channelId); }
            vc.getManager().setRegion(current).queue(_ -> { synchronized (channelPings) { channelPings.remove(channelId); } });
        });
    }

    public Bot(String token, HashMap<Long, Long> channelPings) {
        this.channelPings = channelPings;
        this.jda = null;
        final Thread task = new Thread(() -> jda = JDABuilder.createDefault(token).enableCache(CacheFlag.VOICE_STATE)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES).addEventListeners(new EventListener(this))
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .build(), "JDA-BOT-" + botID++);
        task.setDaemon(false);
        task.start();
    }

    public void shutdown() {
        isReady = false;
        if (jda != null) {
            JDA jda = this.jda;
            this.jda = null;
            jda.shutdown();
            try {
                jda.awaitShutdown();
            } catch (InterruptedException ignored) {}
        }
    }

    public JDA getJDA() {
        return jda;
    }

    public boolean isReady() {
        return jda != null && isReady;
    }

    public boolean isAvailable() {
        return isReady() && audioManager == null;
    }

    public void setReady() {
        isReady = true;
    }

    public void leaveChanel() {
        synchronized (this) {
            if (audioManager == null) return;
            audioManager.closeAudioConnection();
            audioManager = null;
        }
    }

    public void joinChannel(VoiceChannel channel) {
        leaveChanel();
        synchronized (this) {
            AudioManager manager = channel.getGuild().getAudioManager();
            manager.setConnectionListener(new CustomConnectionListener(this, channel.getIdLong()));
            manager.openAudioConnection(channel);
            manager.setSelfDeafened(true);
            manager.setSelfMuted(true);
            audioManager = manager;
        }
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public void unCacheChannel(Long channel) {
        synchronized (channelPings) {
            channelPings.remove(channel);
        }
    }
}
