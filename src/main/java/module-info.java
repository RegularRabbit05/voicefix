module voicefix {
    requires net.dv8tion.jda;
    requires org.slf4j;
    requires annotations;
    requires jdave.api;
    requires ch.qos.logback.core;

    exports com.github.regularrabbit05.voicefix;
    exports com.github.regularrabbit05.voicefix.listeners;
}