package net.oneki.mtac.framework.json;

import java.io.IOException;
import java.util.HashSet;

import com.fasterxml.jackson.databind.JsonDeserializer;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.resource.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.TreeTraversingParser;

public class DbRelationDeserializer extends BeanDeserializer /* implements ContextualDeserializer */ {

    private JavaType type;
    private BeanDescription beanDescription;
    private final ObjectMapper entityMapper;

    public DbRelationDeserializer(BeanDescription beanDescription, ValueDeserializer<?> originalDeserializer, ObjectMapper entityMapper) {
        super((BeanDeserializer)originalDeserializer);
        this.type = beanDescription.getType();
        this.beanDescription = beanDescription;
        this.entityMapper = entityMapper;

    }


    @Override
    public Object deserialize(JsonParser parser, DeserializationContext ctxt) throws JacksonException {
        if (isRoot(parser) == true) {
            return super.deserialize(parser, ctxt);
        }
        var token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            return deserialize(parser, ctxt, token, type);
        } else if (parser.currentToken() == JsonToken.START_ARRAY) {
            var nextToken = parser.nextToken();
            var list = new HashSet<Object>();
            while (nextToken != JsonToken.END_ARRAY) {
                list.add(deserialize(parser, ctxt, nextToken, type.getContentType()));
                nextToken = parser.nextToken();
            }
            return list;
        } else {
            return super.deserialize(parser, ctxt);
        }
    }

    private boolean isRoot(JsonParser parser) {
        var result = true;
        // get parent 
        var parent = parser.

        var parent = parser.getParsingContext().getParent();
        while (parent != null) {
            if (parent.getCurrentName() != null) {
                result = false;
                break;
            }
            parent = parent.getParent();
        }

        return result;
    }

    private Object deserialize(JsonParser parser, DeserializationContext ctxt, JsonToken token, JavaType type)
            throws JacksonException {

        if (token == JsonToken.START_OBJECT) {
            JsonNode node = parser.readValueAsTree();
            var schemaNode = node.get("s");
            if (schemaNode == null) {
                return entityMapper.treeToValue(node, Object.class);
            }
            var schemaId = (Integer) ((IntNode) schemaNode).numberValue();
            // var tenantId = (Integer) ((IntNode) node.get("$t")).numberValue();
            var id = (Integer) ((IntNode) node.get("id")).numberValue();
            var label = node.get("label").asString();
            var schemaLabel = ResourceRegistry.getSchemaLabel(schemaId);
            // var tenantLabel = ResourceRegistry.getTenantLabel(tenantId);
            var clazz = ResourceRegistry.getClassBySchema(schemaLabel);
            try {
                var resource = (Resource) clazz.getConstructor().newInstance();
                resource.setId(id);
                resource.setSchemaId(schemaId);
                // resource.setTenantId(tenantId);
                // resource.setTenantLabel(tenantLabel);
                resource.setSchemaLabel(schemaLabel);
                resource.setLabel(label);
                return resource;
            } catch (Exception e) {
                throw new UnexpectedException("ENTITY_DB_DESERIALIZATION_ERROR", "Can deserialize resource ref with id " + id, e);
            }

        }
        return super.deserialize(parser, ctxt);
    }

}
