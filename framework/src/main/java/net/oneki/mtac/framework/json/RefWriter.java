package net.oneki.mtac.framework.json;

import java.util.Collection;
import java.util.HashSet;


import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.resource.Resource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

public class RefWriter extends BeanPropertyWriter {
    ResourceField resourceField;

    public RefWriter(BeanPropertyWriter w, ResourceField resourceField) {
        super(w);
        this.resourceField = resourceField;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void serializeAsProperty(Object bean, JsonGenerator gen,
            SerializationContext ctxt) throws Exception {
        resourceField.getField().setAccessible(true);
        var fieldName = resourceField.getLabel();
        Object value = resourceField.getField().get(bean);
        if (value == null) {
            gen.writeNullProperty(fieldName);
        } else if (resourceField.isMultiple()) {
            var refs = new HashSet<Ref>();
            for (Resource entity : (Collection<Resource>) value) {
                refs.add(entity.toRef());
            }
            gen.writePOJOProperty(fieldName, refs);
        } else {
            var resourceEntity = (Resource) value;
            gen.writePOJOProperty(fieldName, resourceEntity.toRef());
        }
    }
}