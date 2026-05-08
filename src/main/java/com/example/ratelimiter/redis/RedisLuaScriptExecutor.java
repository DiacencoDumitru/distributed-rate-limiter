package com.example.ratelimiter.redis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisLuaScriptExecutor {
    private final StringRedisTemplate redisTemplate;
    private final ConcurrentMap<String, String> scriptShaByName = new ConcurrentHashMap<>();

    public RedisLuaScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<?> execute(String scriptName, String scriptText, List<String> keys, String... args) {
        String sha = scriptShaByName.computeIfAbsent(scriptName, name -> loadScript(scriptText));
        byte[][] keyBytes = toByteArray(keys.toArray(new String[0]));
        byte[][] argBytes = toByteArray(args);
        try {
            return evalSha(sha, keyBytes, argBytes);
        } catch (DataAccessException ex) {
            if (!isNoScript(ex)) {
                throw ex;
            }
            String reloadedSha = loadScript(scriptText);
            scriptShaByName.put(scriptName, reloadedSha);
            return evalSha(reloadedSha, keyBytes, argBytes);
        }
    }

    private List<?> evalSha(String sha, byte[][] keyBytes, byte[][] argBytes) {
        return redisTemplate.execute((RedisCallback<List<?>>) connection ->
                (List<?>) connection.scriptingCommands().evalSha(
                        sha,
                        ReturnType.MULTI,
                        keyBytes.length,
                        merge(keyBytes, argBytes)));
    }

    private String loadScript(String scriptText) {
        return redisTemplate.execute((RedisCallback<String>) connection ->
                connection.scriptingCommands().scriptLoad(scriptText.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[][] toByteArray(String[] values) {
        byte[][] bytes = new byte[values.length][];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = values[i].getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    private byte[][] merge(byte[][] left, byte[][] right) {
        byte[][] merged = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, merged, left.length, right.length);
        return merged;
    }

    private boolean isNoScript(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("NOSCRIPT")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
