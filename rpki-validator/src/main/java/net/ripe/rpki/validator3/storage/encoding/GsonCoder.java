package net.ripe.rpki.validator3.storage.encoding;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.ripe.rpki.validator3.storage.Bytes;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TODO Either remove it of configure so that it works.
 *
 * @param <T>
 */
public class GsonCoder<T> implements Coder<T> {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(byte[].class, new ByteArraysGsonAdapter())
            .registerTypeAdapter(ImmutableList.class, new ImmutableListAdapter())
            .create();

    private final Class<T> class_;

    public GsonCoder(Class<T> class_) {
        this.class_ = class_;
    }

    @Override
    public ByteBuffer toBytes(T t) {
        String json = gson.toJson(t);
        return Bytes.toDirectBuffer(json.getBytes(UTF_8));
    }

    @Override
    public T fromBytes(ByteBuffer bb) {
        String json = new String(Bytes.toBytes(bb), UTF_8);
        return gson.fromJson(json, class_);
    }

    class ByteArraysGsonAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Base64.getDecoder().decode(json.getAsJsonPrimitive().getAsString());
        }

        @Override
        public JsonElement serialize(byte[] bytes, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(bytes));
        }
    }

    class ImmutableListAdapter implements JsonDeserializer<ImmutableList<?>> {
        @Override
        public ImmutableList<?> deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            final Type type2 = ParameterizedTypeImpl.make(List.class, ((ParameterizedType) type).getActualTypeArguments(), null);
            final List<?> list = context.deserialize(json, type2);
            return ImmutableList.copyOf(list);
        }
    }
}
