package net.oneki.mtac.framework.json;

import java.util.List;
import java.util.Set;


import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.json.JsonUtil;
import net.oneki.mtac.model.core.util.json.view.External;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

@RequiredArgsConstructor
public class EntityToDbSerializerModifier extends ValueSerializerModifier {

    private final static Set<String> skipFields = Set.of("id", "uid", "label", "schema", "s", "tenant", "createdAt", "createdBy", "type",
            "updatedAt", "updatedBy", "pub", "link", "acl");

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config, BeanDescription.Supplier beanDescRef,
            List<BeanPropertyWriter> beanProperties) {
        var beanDesc = beanDescRef.get();
        if(!beanDesc.getBeanClass().equals(Ref.class)) {
            var resourceDesc = ResourceRegistry.getResourceDesc(beanDesc.getBeanClass());
            if (resourceDesc == null) {
                return beanProperties;
            }
            var it = beanProperties.iterator();
            while (it.hasNext()) {
                BeanPropertyWriter writer = it.next();
                if (skipFields.contains(writer.getName()) || JsonUtil.hasView(writer.getViews(), External.class)) {
                    it.remove();
                }
            } 
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanPropertyWriter writer = beanProperties.get(i);
                var resourceField = resourceDesc.getField(writer.getName());
                if (resourceField != null && resourceField.isRelation()) {
                    beanProperties.set(i, new RefWriter(writer, resourceField));
                }
                if (resourceField != null && resourceField.isSecret()) {
                    beanProperties.set(i, new PasswordWriter(writer, resourceField));

                }
            }
        }
        
        
        return beanProperties;
    }
}