package com.netflix.conductor.redis.jedis;

import redis.clients.jedis.commands.JedisCommands;

public interface OrkesJedisCommands extends JedisCommands {

    String set(byte[] key, byte[] value);

    byte[] getBytes(byte[] key);
}
