package net.oneki.mtac.core.util.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;

import net.oneki.mtac.core.util.json.UpsertRequestDeserializer;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.UpsertRequest;

public class UpsertRequestDeserializer extends DelegatingDeserializer {

    private BeanDescription beanDescription;
    private ResourceService resourceService;

    public UpsertRequestDeserializer(JsonDeserializer<?> delegate, BeanDescription beanDescription, ResourceService resourceService) {
        super(delegate);
        this.beanDescription = beanDescription;
        this.resourceService = resourceService;
    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
        return new UpsertRequestDeserializer(newDelegatee, beanDescription, resourceService);
    }

    @Override
    public Object deserialize( JsonParser p, DeserializationContext ctxt ) throws IOException
    {
        Object deserializedObject = super.deserialize( p, ctxt );

        if (UpsertRequest.class.isAssignableFrom(beanDescription.getBeanClass())) {
            try {
                resourceService.loadRequestRelations((UpsertRequest) deserializedObject);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                throw new IOException(e);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }

        return deserializedObject;
    }

}
