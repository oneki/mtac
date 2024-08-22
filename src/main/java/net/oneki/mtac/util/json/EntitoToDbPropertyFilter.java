package net.oneki.mtac.util.json;

import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;

public class EntitoToDbPropertyFilter extends SimpleBeanPropertyFilter {
    
    @Override
    protected boolean include(BeanPropertyWriter writer) {
        return false;
    }

    @Override
    protected boolean include(PropertyWriter writer) {
        return false;
    }
}
