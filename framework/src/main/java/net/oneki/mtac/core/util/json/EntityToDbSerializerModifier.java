package net.oneki.mtac.core.util.json;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import net.oneki.mtac.core.resource.Ref;
import net.oneki.mtac.core.util.cache.ResourceRegistry;

public class EntityToDbSerializerModifier extends BeanSerializerModifier {

    private final static Set<String> skipFields = Set.of("id", "@l", "@s", "@t", "@createdAt", "@createdBy",
            "@updatedAt", "@updatedBy", "@pub", "link", "acl", "@urn");

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config, BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties) {
        
        if(!beanDesc.getBeanClass().equals(Ref.class)) {
            var resourceDesc = ResourceRegistry.getResourceDesc(beanDesc.getBeanClass());
            if (resourceDesc == null) {
                return beanProperties;
            }
            var it = beanProperties.iterator();
            while (it.hasNext()) {
                BeanPropertyWriter writer = it.next();
                if (skipFields.contains(writer.getName())) {
                    it.remove();
                }
            } 
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanPropertyWriter writer = beanProperties.get(i);
                var resourceField = resourceDesc.getField(writer.getName());
                if (resourceField != null && resourceField.isRelation()) {
                    beanProperties.set(i, new RefWriter(writer, resourceField));
                }
            }
        }
        
        
        return beanProperties;
    }
}