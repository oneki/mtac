package net.oneki.mtac.util.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;

public class EntityToDbMapper extends ObjectMapper {

    public static class Builder extends MapperBuilder<EntityToDbMapper, Builder> {
        public Builder(EntityToDbMapper m) {
            super(m);
        }
    }

    public static EntityToDbMapper.Builder builder() {
        return new Builder(new EntityToDbMapper());
    }
}