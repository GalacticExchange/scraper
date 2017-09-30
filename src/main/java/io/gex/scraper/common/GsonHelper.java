package io.gex.scraper.common;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


public class GsonHelper {
    public static final Gson GSON = createGson();

    public static Gson createGson() {
        JsonDeserializer<ZonedDateTime> dateTimeDeserializer = new JsonDeserializer<ZonedDateTime>() {
            private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .withZone(ZoneOffset.UTC);

            @Override
            public ZonedDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                    throws JsonParseException {
                if (json == null || json.isJsonNull()) {
                    return null;
                } else if (json.getAsJsonPrimitive().isString()) {
                    return ZonedDateTime.parse(json.getAsString(), dateTimeFormatter);
                } else {
                    throw new IllegalArgumentException("Cannot parse to date: " + json.toString());
                }
            }
        };

        JsonDeserializer<AtomicDouble> atomicDoubleDes = (json, typeOfT, context) -> {
            if (json == null || json.isJsonNull()) {
                return null;
            } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                return new AtomicDouble(json.getAsDouble());
            } else {
                throw new IllegalArgumentException("Cannot parse to atomic double: " + json.toString());
            }
        };
        JsonSerializer<AtomicDouble> atomicDoubleSer = (src, typeOfSrc, context) -> src == null ? null : new JsonPrimitive(src.get());

        JsonDeserializer<Duration> durationDes = (json, typeOfT, context) -> {
            if (json == null || json.isJsonNull()) {
                return null;
            } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                return Duration.ofSeconds(json.getAsBigInteger().longValue());
            } else {
                throw new IllegalArgumentException("Cannot parse to Duration: " + json.toString());
            }
        };
        JsonSerializer<Instant> instantSer = (src, typeOfSrc, context) -> src == null ? null : new JsonPrimitive(src.toEpochMilli());

        JsonDeserializer<Instant> instantDes = (json, typeOfT, context) -> {
            if (json == null || json.isJsonNull()) {
                return null;
            } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                return Instant.ofEpochMilli(json.getAsBigInteger().longValue());
            } else {
                throw new IllegalArgumentException("Cannot parse to Duration: " + json.toString());
            }
        };
        JsonSerializer<Duration> durationSer = (src, typeOfSrc, context) -> src == null ? null : new JsonPrimitive(src.getSeconds());


        return new GsonBuilder().registerTypeAdapter(ZonedDateTime.class, dateTimeDeserializer)
                .registerTypeAdapter(AtomicDouble.class, atomicDoubleDes)
                .registerTypeAdapter(AtomicDouble.class, atomicDoubleSer)
                .registerTypeAdapter(Duration.class, durationDes)
                .registerTypeAdapter(Duration.class, durationSer)
                .registerTypeAdapter(Instant.class, instantSer)
                .registerTypeAdapter(Instant.class, instantDes).create();
    }

}
