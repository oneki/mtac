package net.oneki.mtac.util.exception;

import java.util.HashMap;
import java.util.Map;

import net.oneki.mtac.util.exception.ICustomException;
import net.oneki.mtac.util.exception.TechnicalException;
import lombok.Getter;
import lombok.Setter;

/**
 * BusinessException
 */
@Getter
@Setter
public class TechnicalException extends RuntimeException implements ICustomException {
    private static final long serialVersionUID = 1L;
    private String errorCode = "EXC_DEFAULT";
    private Map<String, Object> params;
    private Integer status;

    /**
     * Constructs a new business exception with {@code null} as its detail message.
     * The cause is not initialized, and may subsequently be initialized by a call
     * to {@link #initCause}.
     */
    public TechnicalException() {
        super();
    }

    /**
     * Constructs a new business exception with the specified detail message. The
     * cause is not initialized, and may subsequently be initialized by a call to
     * {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link #getMessage()} method.
     */
    public TechnicalException(String message) {
        super(message);
    }

    /**
     * Constructs a new business exception with the specified detail message and
     * cause.
     * <p>
     * Note that the detail message associated with {@code cause} is <i>not</i>
     * automatically incorporated in this business exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the
     *                {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). (A {@code null} value is
     *                permitted, and indicates that the cause is nonexistent or
     *                unknown.)
     * @since 1.4
     */
    public TechnicalException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new business exception with the specified detail message, cause,
     * suppression enabled or disabled, and writable stack trace enabled or
     * disabled.
     *
     * @param message            the detail message.
     * @param cause              the cause. (A {@code null} value is permitted, and
     *                           indicates that the cause is nonexistent or
     *                           unknown.)
     * @param enableSuppression  whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     *
     * @since 1.7
     */
    protected TechnicalException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Constructs a new business exception with the specified detail message. The
     * cause is not initialized, and may subsequently be initialized by a call to
     * {@link #initCause}.
     *
     * @param errorCode the error code that identifies uniquely the type of error
     * @param message   the detail message. The detail message is saved for later
     *                  retrieval by the {@link #getMessage()} method.
     */
    public TechnicalException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new business exception with the specified detail message and
     * cause.
     * <p>
     * Note that the detail message associated with {@code cause} is <i>not</i>
     * automatically incorporated in this business exception's detail message.
     *
     * @param errorCode the error code that identifies uniquely the type of error
     * @param message   the detail message (which is saved for later retrieval by
     *                  the {@link #getMessage()} method).
     * @param cause     the cause (which is saved for later retrieval by the
     *                  {@link #getCause()} method). (A {@code null} value is
     *                  permitted, and indicates that the cause is nonexistent or
     *                  unknown.)
     * @since 1.4
     */
    public TechnicalException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new business exception with the specified cause and a detail
     * message of {@code (cause==null ? null : cause.toString())} (which typically
     * contains the class and detail message of {@code cause}). This constructor is
     * useful for business exceptions that are little more than wrappers for other
     * throwables.
     *
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method). (A {@code null} value is permitted,
     *              and indicates that the cause is nonexistent or unknown.)
     * @since 1.4
     */
    public TechnicalException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new business exception with the specified detail message, cause,
     * suppression enabled or disabled, and writable stack trace enabled or
     * disabled.
     *
     * @param errorCode          the error code that identifies uniquely the type of
     *                           error
     * @param message            the detail message.
     * @param cause              the cause. (A {@code null} value is permitted, and
     *                           indicates that the cause is nonexistent or
     *                           unknown.)
     * @param enableSuppression  whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     *
     * @since 1.7
     */
    protected TechnicalException(String errorCode, String message, Throwable cause,
                               boolean enableSuppression,
                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorCode = errorCode;        
    }

    public TechnicalException param(String key, Object value) {
        if(params == null) {
            params = new HashMap<>();
        }
        params.put(key, value);
        return this;
    }
    
}