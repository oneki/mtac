package net.oneki.mtac.core.util.json;

import java.util.Collection;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

import net.oneki.mtac.resource.Resource;
import net.oneki.mtac.core.resource.Ref;
import net.oneki.mtac.core.util.introspect.ResourceField;

public class RefWriter extends BeanPropertyWriter {
    ResourceField resourceField;

    public RefWriter(BeanPropertyWriter w, ResourceField resourceField) {
        super(w);
        this.resourceField = resourceField;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void serializeAsField(Object bean, JsonGenerator gen,
            SerializerProvider prov) throws Exception {
        resourceField.getField().setAccessible(true);
        var fieldName = resourceField.getLabel();
        Object value = resourceField.getField().get(bean);
        if (value == null) {
            gen.writeNullField(fieldName);
        } else if (resourceField.isMultiple()) {
            var refs = new HashSet<Ref>();
            for (Resource entity : (Collection<Resource>) value) {
                refs.add(entity.toRef());
            }
            gen.writeObjectField(fieldName, refs);
        } else {
            var resourceEntity = (Resource) value;
            gen.writeObjectField(fieldName, resourceEntity.toRef());
        }
    }
}