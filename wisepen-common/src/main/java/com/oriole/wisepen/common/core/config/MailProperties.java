package com.oriole.wisepen.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.mail")
public class MailProperties {
    private String host;
    private int port = 465;
    private String username;
    private String password;
    private String defaultEncoding = "UTF-8";
    private Properties properties = new Properties();

    @Data
    public static class Properties {
        private Mail mail = new Mail();
    }

    @Data
    public static class Mail {
        private Smtp smtp = new Smtp();
        private boolean debug = false;
    }

    @Data
    public static class Smtp {
        private boolean auth = true;
        private Ssl ssl = new Ssl();
        private SocketFactory socketFactory = new SocketFactory();
    }

    @Data
    public static class Ssl {
        private boolean enable = true;
    }

    @Data
    public static class SocketFactory {
        private String clazz = "javax.net.ssl.SSLSocketFactory";
        private boolean fallback = false;
        private int port = 465;
    }
}