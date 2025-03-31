package net.oneki.mtac.framework.json;

import java.io.IOException;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

public class RelationKeyDeserializer extends KeyDeserializer {
    private JavaType type;

    public RelationKeyDeserializer(JavaType type) {
        super();
        this.type = type;
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        if (key == null) {
            return null;
        }
        JsonParser parser = ctxt.getParser();
        var token = parser.currentToken();
        if (token == JsonToken.START_OBJECT || token == JsonToken.VALUE_STRING) {
            return deserialize(parser, ctxt, token, type);
        } else if (parser.currentToken() == JsonToken.START_ARRAY) {
            var nextToken = parser.nextToken();
            var list = new HashSet<Object>();
            while (nextToken != JsonToken.END_ARRAY) {
                list.add(deserialize(parser, ctxt, nextToken, type.getContentType()));
                nextToken = parser.nextToken();
            }
            return list;
        }
        return null;
    }


    private Object deserialize(JsonParser parser, DeserializationContext ctxt, JsonToken token, JavaType type)
            throws IOException {
        JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(type);
        if (token == JsonToken.START_OBJECT) {
            return deserializer.deserialize(parser, ctxt);
        } else if (parser.currentToken() == JsonToken.VALUE_STRING) {
            BeanDeserializer beanDeserializer = (BeanDeserializer) deserializer;
            try {
                Object instance = beanDeserializer.getValueInstantiator().getDefaultCreator().call();
                SettableBeanProperty property = beanDeserializer.findProperty("label");
                property.deserializeAndSet(parser, ctxt, instance);
                return instance;
            } catch (Exception e) {
                throw JsonMappingException.from(parser, e.getMessage());
            }
        }
        return null;
    }

}
