package com.github.regularrabbit05.voicefix;

import net.dv8tion.jda.api.audio.hooks.ListenerProxy;

public class CustomConnectionListener extends ListenerProxy {
    private final Bot botInstance;
    private final Long channelId;
    public CustomConnectionListener(Bot bot, long idLong) {
        this.botInstance = bot;
        this.channelId = idLong;
    }

    @Override
    public void onPing(long ping) {
        super.onPing(ping);
        botInstance.updateChannelPing(channelId, ping);
    }
}
