package net.oneki.mtac.framework.json;

import java.io.IOException;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;

import net.oneki.mtac.framework.json.RefDeserializer;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.resource.Resource;

public class RefDeserializer extends DelegatingDeserializer  /*implements ContextualDeserializer*/ {

    private JavaType type;
    private BeanDescription beanDescription;

    public RefDeserializer(BeanDescription beanDescription, JsonDeserializer<?> originalDeserializer) {
        super(originalDeserializer);
        this.type = beanDescription.getType();
        this.beanDescription = beanDescription;
    }

    
    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegate) {
        return new RefDeserializer(beanDescription, newDelegate);
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

    private Object deserialize(JsonParser parser, DeserializationContext ctxt, JsonToken token, JavaType type)
            throws IOException {
        
        if (token == JsonToken.START_OBJECT) {
            Ref ref = (Ref) super.deserialize(parser, ctxt);
            return null;
            
        } else if (token == JsonToken.VALUE_STRING) {
            try {
                var stringType = ctxt.constructType(String.class);
                JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(stringType);
                var value = (String) deserializer.deserialize(parser, ctxt);
                var instance = (Resource) beanDescription.instantiateBean(false);
                instance.setUrn(value);
                //Object instance = beanDeserializer.getValueInstantiator().getDefaultCreator().call();
                // SettableBeanProperty property = beanDeserializer.findProperty(primitiveName);
                // property.deserializeAndSet(parser, ctxt, instance);
                return instance;
            } catch (Exception e) {
                throw JsonMappingException.from(parser, e.getMessage());
            }
        }
        return null;
    }

    // @Override
    // public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {

    //     var deserializer = new RelationDeserializer();
    //     deserializer.primitiveName = "label";
    //     deserializer.type = property.getType();

    //     return deserializer;
    // }

}
