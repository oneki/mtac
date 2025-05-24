package net.oneki.mtac.framework.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.Resource;

public class RelationDeserializer extends DelegatingDeserializer /* implements ContextualDeserializer */ {

    private JavaType type;
    private BeanDescription beanDescription;

    public RelationDeserializer(BeanDescription beanDescription, JsonDeserializer<?> originalDeserializer) {
        super(originalDeserializer);
        this.type = beanDescription.getType();
        this.beanDescription = beanDescription;
    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegate) {
        return new RelationDeserializer(beanDescription, newDelegate);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
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

    @SuppressWarnings("unchecked")
    private Object deserialize(JsonParser parser, DeserializationContext ctxt, JsonToken token, JavaType type)
            throws IOException {
        // var resourceDesc = ResourceRegistry.getResourceDesc(this.type.getRawClass());
        // System.out.println("type" + type.getRawClass() + ", fieldName: " + parser.getParsingContext().getParent().getCurrentName());
        // var resourceField = resourceDesc.getField(parser.getParsingContext().getParent().getCurrentName());
        // var clazz = resourceField.getFieldClass();
        var clazz = type.getRawClass();

        if (token == JsonToken.START_OBJECT) {
            var mapType = ctxt.constructType(HashMap.class);
            JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(mapType);
            var value = (HashMap<String, Object>) deserializer.deserialize(parser, ctxt);
            var uid = (String) value.get("uid");
            return buildObjectFromUid(uid, clazz);
        } else if (token == JsonToken.VALUE_STRING) {
            try {
                var stringType = ctxt.constructType(String.class);
                JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(stringType);
                var uid = (String) deserializer.deserialize(parser, ctxt);
                return buildObjectFromUid(uid, clazz);
            } catch (Exception e) {
                throw JsonMappingException.from(parser, e.getMessage());
            }
        }
        return null;
    }

    private Object buildObjectFromUid(String uid, Class<?> clazz) {
        if (uid == null) {
            throw new BusinessException("BSN_INVALID_REQUEST", "a relation must be identified via a UID");
        }        

        try {
            var obj = (Resource) clazz.getDeclaredConstructor().newInstance();
            obj.setUid(uid);
            return obj;
        } catch (ReflectiveOperationException e) {
            throw new BusinessException("BSN_INVALID_REQUEST", "Error creating instance of class " + clazz.getName() + ": " + e.getMessage());
        }
    }

    // @Override
    // public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
    // BeanProperty property) {

    // var deserializer = new RelationDeserializer();
    // deserializer.primitiveName = "label";
    // deserializer.type = property.getType();

    // return deserializer;
    // }

}
