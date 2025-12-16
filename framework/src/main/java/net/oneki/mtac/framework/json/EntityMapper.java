package net.oneki.mtac.framework.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper.Builder;

public class EntityMapper extends ObjectMapper {

    // public static class Builder extends MapperBuilder<EntityMapper, Builder> {
    //     public Builder(EntityMapper m) {
    //         super(m);
    //     }
    // }

    // public static EntityMapper.Builder builder() {
    //     ObjectMapper m = new ObjectMapper(JsonMapper..builder());
    //     return new Builder(new EntityMapper());
    // }

    public EntityMapper(Builder builder) {
        super(builder);
    }

    // public <M extends ObjectMapper, B extends MapperBuilder<M,B>> MapperBuilder<M,B> rebuild() {
    //     return super.rebuild();
    // }
}