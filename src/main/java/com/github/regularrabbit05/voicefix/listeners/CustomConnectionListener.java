package com.github.regularrabbit05.voicefix.listeners;

import com.github.regularrabbit05.voicefix.BotInstance;
import net.dv8tion.jda.api.audio.hooks.ListenerProxy;

public class CustomConnectionListener extends ListenerProxy {
    private final BotInstance botInstance;
    private final Long channelId;
    public CustomConnectionListener(BotInstance botInstance, long idLong) {
        this.botInstance = botInstance;
        this.channelId = idLong;
    }

    @Override
    public void onPing(long ping) {
        super.onPing(ping);
        botInstance.updateChannelPing(channelId, ping);
    }
}
