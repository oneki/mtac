package net.oneki.mtac.model.core.util.exception;

import java.util.Map;

import net.oneki.mtac.model.core.util.exception.ICustomException;

/**
 * ICustomException
 */
public interface ICustomException {
    
    public String getMessage();
    public String getErrorCode();
    public void setErrorCode(String errorCode);
    public Map<String, Object> getParams();
    public ICustomException param(String key, Object value);
    public Integer getStatus();
    public void setStatus(Integer status);
    
}