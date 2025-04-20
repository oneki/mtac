package net.oneki.mtac.framework.json;

import java.rmi.UnexpectedException;
import java.util.Collection;
import java.util.HashSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

import net.oneki.mtac.framework.introspect.ResourceField;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.introspect.annotation.Secret.SecretType;
import net.oneki.mtac.model.resource.Resource;

public class PasswordWriter extends BeanPropertyWriter {
    ResourceField resourceField;
    PasswordUtil passwordUtil;

    public PasswordWriter(BeanPropertyWriter w, ResourceField resourceField, PasswordUtil passwordUtil) {
        super(w);
        this.resourceField = resourceField;
        this.passwordUtil = passwordUtil;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void serializeAsField(Object bean, JsonGenerator gen,
            SerializerProvider prov) throws Exception {
        if (!resourceField.isSecret()) {
            throw new UnexpectedException("Field " + resourceField.getLabel() + " is not a secret");
        }
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
            var plainText = (String) value;
            String encrypted = null;
            var secretType = resourceField.getSecret().getType();
            if (secretType == SecretType.HASHING) {
                encrypted = passwordUtil.hash(plainText);
            } else if (secretType == SecretType.ENCRYPTION) {
                encrypted = passwordUtil.encrypt(plainText);
            } else {
                throw new UnexpectedException("Unknown secret type " + secretType);
            }

            gen.writeObjectField(fieldName, encrypted);
        }
    }
}