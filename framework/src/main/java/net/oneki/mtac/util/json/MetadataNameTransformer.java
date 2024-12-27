package net.oneki.mtac.util.json;

import com.fasterxml.jackson.databind.util.NameTransformer;

public class MetadataNameTransformer extends NameTransformer {

    @Override
    public String transform(String name) {
        return "@" + name;
    }

    @Override
    public String reverse(String transformed) {
        return transformed.substring(1);
    }
    
}
