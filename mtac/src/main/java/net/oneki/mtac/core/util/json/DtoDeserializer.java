package net.oneki.mtac.core.util.json;

import net.oneki.mtac.model.resource.UpsertRequest;
import net.oneki.mtac.resource.ResourceService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializer;

public class DtoDeserializer extends BeanDeserializer {

    private BeanDescription beanDescription;
    private ResourceService resourceService;
    ValueDeserializer<?> delegate;

    public DtoDeserializer(ValueDeserializer<?> delegate, BeanDescription beanDescription,
            ResourceService resourceService) {
        super((BeanDeserializer) delegate);
        this.beanDescription = beanDescription;
        this.resourceService = resourceService;
        this.delegate = delegate;
    }


    @SuppressWarnings("unchecked")
    @Override
    public UpsertRequest deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        // UpsertRequest deserializedObject = (UpsertRequest) delegate.deserialize(p, ctxt);
        // if (p.currentToken() == JsonToken.START_OBJECT) {
        //     p.nextToken(); // Avancer au premier champ (ou END_OBJECT)
        // }

        var request = super.deserialize(p, ctxt);

        if (UpsertRequest.class.isAssignableFrom(beanDescription.getBeanClass())) {

            try {
                resourceService.loadRequestRelations((UpsertRequest) request);
            } catch (Exception e) {
                throw new JacksonException("Failed to load request relations", e) {
                };
            }
        }

        return (UpsertRequest) request;
    }

}
