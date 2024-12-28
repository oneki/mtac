package net.oneki.mtac.core.util.exception;

import net.oneki.mtac.core.util.exception.JsonException;

public interface JsonExceptionConvertible {
    
    JsonException asJsonException();
}
