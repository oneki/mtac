package net.oneki.mtac.util.exception;

import net.oneki.mtac.util.exception.JsonException;

public interface JsonExceptionConvertible {
    
    JsonException asJsonException();
}
