package com.github.regularrabbit05.voicefix.listeners;

import com.github.regularrabbit05.voicefix.BotInstance;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class EventListener extends ListenerAdapter {
    private final BotInstance botInstance;
    public EventListener(BotInstance botInstance) {
        this.botInstance = botInstance;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        botInstance.setReady();
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (botInstance.isAvailable()) return;

        AudioChannelUnion botsChannel = botInstance.getAudioManager().getConnectedChannel();
        AudioChannelUnion oldChannel = event.getChannelLeft();

        if (event.getEntity().getIdLong() == event.getJDA().getSelfUser().getIdLong() && oldChannel != null && event.getChannelJoined() != null) {
            botInstance.unCacheChannel(oldChannel.getIdLong());
            botInstance.leaveChannel();
            return;
        }

        if (botsChannel == null || oldChannel == null) return;
        if (botsChannel.getIdLong() != event.getChannelLeft().getIdLong()) return;
        if (event.getEntity().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            botInstance.unCacheChannel(botsChannel.getIdLong());
            botInstance.leaveChannel();
            return;
        }

        if (event.getChannelLeft().getMembers().stream().filter(m -> !m.getUser().isBot()).count() <= 1) {
            botInstance.unCacheChannel(botsChannel.getIdLong());
            botInstance.leaveChannel();
        }
    }
}
