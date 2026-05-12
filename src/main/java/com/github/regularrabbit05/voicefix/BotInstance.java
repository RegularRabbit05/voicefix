package com.github.regularrabbit05.voicefix;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.github.regularrabbit05.voicefix.listeners.CustomConnectionListener;
import com.github.regularrabbit05.voicefix.listeners.EventListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BotInstance extends ListenerAdapter {
    private static int botID = 0;

    private final HashMap<Long, Long> channelPings;
    private final int pingThreshold;
    private JDA jda;
    private boolean isReady = false;
    private AudioManager audioManager = null;

    public void updateChannelPing(final Long channelId, final Long ping) {
        if (jda == null) return;
        Long previous;
        synchronized (channelPings) { previous = channelPings.put(channelId, ping); }

        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.getPresence().setActivity(Activity.customStatus("Ping: " + ping + "ms"));

        if (previous == null) return;
        final VoiceChannel vc = jda.getVoiceChannelById(channelId);
        if (vc == null) return;
        if (ping < pingThreshold || previous < pingThreshold) return;
        final Region current = vc.getRegion();
        final ArrayList<Region> regions = new ArrayList<>(Arrays.stream(Region.values()).filter(r -> !r.isVip()).toList());
        regions.remove(current);
        regions.remove(Region.AUTOMATIC);
        Region randomRegion = regions.get(ThreadLocalRandom.current().nextInt(regions.size()));
        if (current ==  Region.AUTOMATIC || current == Region.UNKNOWN) randomRegion = Region.SOUTH_AFRICA;
        vc.getManager().setRegion(randomRegion).queue(_ -> {
            synchronized (channelPings) { channelPings.remove(channelId); }
            vc.getManager().setRegion(current).queue(_ -> { synchronized (channelPings) { channelPings.remove(channelId); } });
        });
        LoggerFactory.getLogger(this.getClass()).info("Moving channel {} to region {} from {}", vc.getName(), randomRegion.getName(), current.getName());
    }

    public BotInstance(final String token, final HashMap<Long, Long> channelPings, final int pingThreshold) {
        this.pingThreshold = pingThreshold;
        this.channelPings = channelPings;
        this.jda = null;
        final int botID = BotInstance.botID++;
        final Thread task = new Thread(() -> {
            try {
                jda = JDABuilder.createDefault(token).enableCache(CacheFlag.VOICE_STATE)
                        .enableIntents(GatewayIntent.GUILD_VOICE_STATES).addEventListeners(new EventListener(this))
                        .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                        .setActivity(null)
                        .setStatus(OnlineStatus.IDLE)
                        .build().awaitReady();
            } catch (InterruptedException e) {
                jda = null;
                LoggerFactory.getLogger(this.getClass()).error("Error for bot {}", botID, e);
            }
        }, "JDA-BOT-" + botID);
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

    public void leaveChannel() {
        if (jda == null) return;
        synchronized (this) {
            if (audioManager == null) return;
            audioManager.closeAudioConnection();
            audioManager = null;
        }
        jda.getPresence().setActivity(null);
        jda.getPresence().setStatus(OnlineStatus.IDLE);
    }

    public void joinChannel(VoiceChannel channel) {
        if (jda == null) return;
        leaveChannel();
        synchronized (this) {
            AudioManager manager = channel.getGuild().getAudioManager();
            manager.setConnectionListener(new CustomConnectionListener(this, channel.getIdLong()));
            manager.openAudioConnection(channel);
            manager.setSelfDeafened(true);
            manager.setSelfMuted(true);
            audioManager = manager;
        }
        jda.getPresence().setActivity(null);
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
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
