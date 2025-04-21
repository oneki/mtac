package net.oneki.mtac.framework.json;

import java.io.IOException;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.node.IntNode;

import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.framework.Urn;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;
import net.oneki.mtac.model.resource.Resource;

public class DbRelationDeserializer extends DelegatingDeserializer /* implements ContextualDeserializer */ {

    private JavaType type;
    private BeanDescription beanDescription;
    private final ObjectMapper entityMapper;

    public DbRelationDeserializer(BeanDescription beanDescription, JsonDeserializer<?> originalDeserializer, ObjectMapper entityMapper) {
        super(originalDeserializer);
        this.type = beanDescription.getType();
        this.beanDescription = beanDescription;
        this.entityMapper = entityMapper;

    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegate) {
        return new DbRelationDeserializer(beanDescription, newDelegate, entityMapper);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        var flag = ctxt.getAttribute("FIRST_PASS");
        if (flag != null && flag.equals("true")) {
            ctxt.setAttribute("FIRST_PASS", "false");
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

    private Object deserialize(JsonParser parser, DeserializationContext ctxt, JsonToken token, JavaType type)
            throws IOException {
        if (token == JsonToken.START_OBJECT) {
            JsonNode node = parser.getCodec().readTree(parser);
            var schemaNode = node.get("$s");
            if (schemaNode == null) {
                ctxt.setAttribute("FIRST_PASS", "true");
                //return entityMapper.treeToValue(node, type.getRawClass());
                parser.assignCurrentValue(node.asText());
                return deserialize(parser, ctxt);
            }
            var schemaId = (Integer) ((IntNode) schemaNode).numberValue();
            var tenantId = (Integer) ((IntNode) node.get("$t")).numberValue();
            var id = (Integer) ((IntNode) node.get("id")).numberValue();
            var label = node.get("$l").asText();
            var schemaLabel = ResourceRegistry.getSchemaLabel(schemaId);
            var tenantLabel = ResourceRegistry.getTenantLabel(tenantId);
            var urnRecord = new Urn(tenantLabel, schemaLabel, label);
            var clazz = ResourceRegistry.getClassBySchema(schemaLabel);
            try {
                var resource = (Resource) clazz.getConstructor().newInstance();
                resource.setUrn(urnRecord.toString());
                resource.setId(id);
                resource.setSchemaId(schemaId);
                resource.setTenantId(tenantId);
                resource.setTenantLabel(tenantLabel);
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
