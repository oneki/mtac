package net.oneki.mtac.model.core.util.exception;

import net.oneki.mtac.model.core.util.exception.JsonException;

public interface JsonExceptionConvertible {
    
    JsonException asJsonException();
}
