package net.oneki.mtac.framework.json;

import java.util.HashMap;
import java.util.HashSet;

import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializer;

public class RelationDeserializer extends BeanDeserializer /* implements ContextualDeserializer */ {

    private JavaType type;
    private BeanDescription beanDescription;
    private ValueDeserializer<?> originalDeserializer;

    public RelationDeserializer(BeanDescription beanDescription, ValueDeserializer<?> originalDeserializer) {
        super((BeanDeserializer) originalDeserializer);
        this.type = beanDescription.getType();
        this.beanDescription = beanDescription;
        this.originalDeserializer = originalDeserializer;
    }


    @Override
    public Object deserialize(JsonParser parser, DeserializationContext ctxt) throws JacksonException  {
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
            throws JacksonException {
        // var resourceDesc = ResourceRegistry.getResourceDesc(this.type.getRawClass());
        // System.out.println("type" + type.getRawClass() + ", fieldName: " + parser.getParsingContext().getParent().getCurrentName());
        // var resourceField = resourceDesc.getField(parser.getParsingContext().getParent().getCurrentName());
        // var clazz = resourceField.getFieldClass();
        var clazz = type.getRawClass();

        if (token == JsonToken.START_OBJECT) {
            var mapType = ctxt.constructType(HashMap.class);
            ValueDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(mapType);
            var value = (HashMap<String, Object>) deserializer.deserialize(parser, ctxt);
            var uid = (String) value.get("uid");
            return buildObjectFromUid(uid, clazz);
        } else if (token == JsonToken.VALUE_STRING) {
            try {
                var stringType = ctxt.constructType(String.class);
                ValueDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(stringType);
                var uid = (String) deserializer.deserialize(parser, ctxt);
                return buildObjectFromUid(uid, clazz);
            } catch (Exception e) {
                throw new JacksonException("Failed to deserialize resource", e) {};
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
