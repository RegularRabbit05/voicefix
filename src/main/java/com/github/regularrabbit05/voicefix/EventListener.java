package com.github.regularrabbit05.voicefix;

import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class EventListener extends ListenerAdapter {
    private final Bot bot;
    public EventListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        bot.setReady();
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (bot.isAvailable()) return;
        AudioChannelUnion botsChannel = bot.getAudioManager().getConnectedChannel();
        AudioChannelUnion oldChannel = event.getChannelLeft();
        if (botsChannel == null || oldChannel == null) return;
        if (botsChannel.getIdLong() != event.getChannelLeft().getIdLong()) return;
        if (event.getEntity().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            bot.unCacheChannel(botsChannel.getIdLong());
            bot.leaveChanel();
            return;
        }

        if (event.getChannelLeft().getMembers().stream().allMatch(m -> m.getUser().isBot())) {
            bot.unCacheChannel(botsChannel.getIdLong());
            bot.leaveChanel();
        }
    }
}
