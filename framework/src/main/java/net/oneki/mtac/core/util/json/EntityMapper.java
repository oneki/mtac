package net.oneki.mtac.core.util.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;

public class EntityMapper extends ObjectMapper {

    public static class Builder extends MapperBuilder<EntityMapper, Builder> {
        public Builder(EntityMapper m) {
            super(m);
        }
    }

    public static EntityMapper.Builder builder() {
        return new Builder(new EntityMapper());
    }
}