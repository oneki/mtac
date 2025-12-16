package net.oneki.mtac.framework.json;

import java.rmi.UnexpectedException;
import java.util.Collection;
import java.util.HashSet;

import com.fasterxml.jackson.databind.SerializerProvider;

import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;
import net.oneki.mtac.model.resource.Resource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

public class PasswordWriter extends BeanPropertyWriter {
    ResourceField resourceField;

    public PasswordWriter(BeanPropertyWriter w, ResourceField resourceField) {
        super(w);
        this.resourceField = resourceField;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void serializeAsProperty(Object bean, JsonGenerator gen,
            SerializationContext ctxt) throws Exception {
        if (!resourceField.isSecret()) {
            throw new UnexpectedException("Field " + resourceField.getLabel() + " is not a secret");
        }
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
            var plainText = (String) value;
            String encrypted = null;
            var secretType = resourceField.getSecret().getType();
            if (secretType == SecretType.HASHING) {
                // encrypted = passwordUtil.hash(plainText);
                encrypted = plainText;
            } else if (secretType == SecretType.ENCRYPTION) {
                encrypted = PasswordUtil.encrypt(plainText);
            } else {
                throw new UnexpectedException("Unknown secret type " + secretType);
            }

            gen.writePOJOProperty(fieldName, encrypted);
        }
    }
}