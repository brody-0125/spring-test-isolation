package session;

import io.github.brody0125.springtestisolation.ClassBoundary;
import io.github.brody0125.springtestisolation.WorkerStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Minimal Spring Session indexed-repository channel fixture; not a Session compatibility harness. */
@SpringBootConfiguration
@EnableAutoConfiguration
public class SessionApplication {
    @Bean SessionIndexer sessionIndexer(StringRedisTemplate redis, WorkerStore store) {
        return new SessionIndexer(redis, store);
    }

    static final class SessionIndexer implements ClassBoundary, AutoCloseable {
        final StringRedisTemplate redis;
        final WorkerStore store;
        final BlockingQueue<String> keyEvents = new LinkedBlockingQueue<>();
        final RedisMessageListenerContainer listener = new RedisMessageListenerContainer();

        SessionIndexer(StringRedisTemplate redis, WorkerStore store) {
            this.redis = redis;
            this.store = store;
            listener.setConnectionFactory(redis.getConnectionFactory());
            var keyeventHandler = (org.springframework.data.redis.connection.MessageListener) (message, pattern) ->
                    keyEvents.add(new String(message.getChannel(), StandardCharsets.UTF_8) + " "
                            + new String(message.getBody(), StandardCharsets.UTF_8));
            listener.addMessageListener(keyeventHandler, new ChannelTopic(store.redisKeyeventChannel("del")));
            listener.addMessageListener(keyeventHandler, new ChannelTopic(store.redisKeyeventChannel("expired")));
            listener.addMessageListener((message, pattern) -> keyEvents.add("created "
                            + new String(message.getChannel(), StandardCharsets.UTF_8)),
                    new PatternTopic(store.channelPattern("event:" + store.redisDatabase + ":created:*")));
            listener.afterPropertiesSet();
        }

        String createSession(String principal) {
            String sessionId = UUID.randomUUID().toString();
            String sessionKey = store.channel("sessions:" + sessionId);
            String indexKey = store.channel("index:PRINCIPAL:" + principal);
            redis.opsForHash().put(sessionKey, "sessionAttr:principal", principal);
            redis.opsForValue().set(indexKey, sessionId);
            redis.convertAndSend(store.channel("event:" + store.redisDatabase + ":created:" + sessionId), principal);
            return sessionId;
        }

        void deleteSession(String sessionId, String principal) {
            redis.delete(store.channel("sessions:" + sessionId));
            redis.delete(store.channel("index:PRINCIPAL:" + principal));
        }

        void expireSession(String sessionId) {
            redis.opsForValue().set(store.channel("sessions:expires:" + sessionId), sessionId, Duration.ofSeconds(1));
        }

        @Override public void quiesce() throws Exception { listener.stop(); }
        @Override public void reset() { keyEvents.clear(); }
        @Override public void resume() { listener.start(); }
        @Override public void close() throws Exception {
            quiesce();
            listener.destroy();
        }
    }
}
