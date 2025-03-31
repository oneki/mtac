package net.oneki.mtac.framework.json;

/**
 * JsonUtil
 */
public class JsonEntityMapper extends BaseJsonEntityMapper {
    static {
        mapper.registerModule(new EntityModule(mapper));
    }
}
